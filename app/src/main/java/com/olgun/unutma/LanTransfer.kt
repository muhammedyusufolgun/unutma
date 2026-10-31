package com.olgun.unutma

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.Executors

/** Local Wi-Fi transport. Only use on a trusted private network; no public internet exposure. */
object LanTransfer {
 private const val PORT = 39471
 private val pool = Executors.newCachedThreadPool()
 @Volatile private var server: ServerSocket? = null
 private fun prefs(c:Context)=c.getSharedPreferences("lan_transfer",Context.MODE_PRIVATE)
 private fun code(c:Context):String {
  val p=prefs(c);val existing=p.getString("code",null)
  if(existing!=null)return existing
  val alphabet="ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
  val random=SecureRandom()
  val fresh=(1..24).map{alphabet[random.nextInt(alphabet.length)]}.joinToString("")
  check(p.edit().putString("code",fresh).commit())
  return fresh
 }
 fun pairingCode(c:Context)=code(c)
 fun localIp():String=try{
  NetworkInterface.getNetworkInterfaces().toList().flatMap{it.inetAddresses.toList()}
   .filterIsInstance<Inet4Address>().firstOrNull{!it.isLoopbackAddress && it.isSiteLocalAddress}?.hostAddress?:"IP bulunamadı"
 }catch(_:Exception){"IP bulunamadı"}
 fun peer(c:Context)=prefs(c).getString("peer","")?:""
 fun pair(c:Context,ip:String,remoteCode:String) {
  require(ip.matches(Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) && ip.split('.').all{it.toInt() in 0..255}){"Geçersiz IPv4"}
  require(remoteCode.matches(Regex("[A-Z2-9]{24}"))){"24 karakterlik eşleştirme kodunu girin"}
  check(prefs(c).edit().putString("peer",ip).putString("peer_code",remoteCode).commit())
 }
 fun start(c:Context) {
  if(server!=null)return
  synchronized(this){
   if(server!=null)return
   try {
    val s=ServerSocket(PORT,8)
    server=s
    pool.execute {
     while(!s.isClosed)try{val client=s.accept();pool.execute{handle(c.applicationContext,client)}}catch(e:Exception){
      if(!s.isClosed)Log.w("TimeIkizLAN","Listener error",e)
     }
    }
   }catch(e:Exception){Log.w("TimeIkizLAN","Cannot listen on Wi-Fi",e)}
  }
 }
 private fun handle(c:Context,s:Socket){
  s.use { socket ->
   socket.soTimeout=6000
   try {
    val reader=socket.getInputStream().bufferedReader(Charsets.UTF_8)
    val line=reader.readLine()?:return
    require(line.length<=40000)
    val data=JSONObject(line)
    // The recipient code is a shared capability; the sender must have received it via trusted pairing.
    require(data.getString("code")==code(c)){"Unauthorized sender"}
    val text=data.getString("text")
    val time=data.getLong("time")
    require(text.isNotBlank()&&text.length<=20000&&time>0)
    val messageId=data.getString("id")
    val r=Store.addIncomingAtomic(c,messageId,time,text,data.getBoolean("repeat"))
    if(r!=null)Alarms.plan(c,r)
    socket.getOutputStream().write("OK\n".toByteArray(Charsets.UTF_8))
   }catch(e:Exception){
    Log.w("TimeIkizLAN","Rejected LAN message",e)
    try{socket.getOutputStream().write("ERROR\n".toByteArray(Charsets.UTF_8))}catch(_:Exception){}
   }
  }
 }
 fun send(c:Context,r:Reminder):String {
  val ip=peer(c);require(ip.isNotBlank()){"Önce alıcıyı eşleştirin"}
  val token=prefs(c).getString("peer_code",null)?:error("Eşleştirme kodu eksik")
  val payload=JSONObject().put("code",token).put("id",UUID.randomUUID().toString())
   .put("time",r.time).put("repeat",r.repeat).put("text",r.text).toString()
  Socket().use { socket ->
   socket.connect(java.net.InetSocketAddress(ip,PORT),5000);socket.soTimeout=6000
   socket.getOutputStream().write((payload+"\n").toByteArray(Charsets.UTF_8))
   val reply=socket.getInputStream().bufferedReader(Charsets.UTF_8).readLine()
   check(reply=="OK"){"Alıcı aktarımı kabul etmedi"}
  }
  return "Hatırlatma aynı ağdaki telefona aktarıldı"
 }
}
