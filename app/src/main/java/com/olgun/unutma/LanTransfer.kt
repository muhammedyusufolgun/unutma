package com.olgun.unutma

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.UUID
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
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
 private fun key(token:String)=SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(("Time-ikiz LAN v2:"+token).toByteArray(Charsets.UTF_8)),"AES")
 private fun encrypt(token:String,plain:String):JSONObject {
  val cipher=Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(Cipher.ENCRYPT_MODE,key(token))
  return JSONObject().put("v",2).put("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)).put("data",Base64.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP))
 }
 private fun decrypt(token:String,envelope:JSONObject):JSONObject {
  require(envelope.getInt("v")==2)
  val iv=Base64.decode(envelope.getString("iv"),Base64.NO_WRAP)
  require(iv.size==12)
  val cipher=Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(Cipher.DECRYPT_MODE,key(token),GCMParameterSpec(128,iv))
  val ciphertext=Base64.decode(envelope.getString("data"),Base64.NO_WRAP)
  require(ciphertext.size<=40000)
  return JSONObject(String(cipher.doFinal(ciphertext),Charsets.UTF_8))
 }
 private fun handle(c:Context,s:Socket){
  s.use { socket ->
   socket.soTimeout=6000
   try {
    val reader=socket.getInputStream().bufferedReader(Charsets.UTF_8)
    val line=reader.readLine()?:return
    require(line.length<=40000)
    val data=decrypt(code(c),JSONObject(line))
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
  val plain=JSONObject().put("id",UUID.randomUUID().toString())
   .put("time",r.time).put("repeat",r.repeat).put("text",r.text).toString()
  val payload=encrypt(token,plain).toString()
  Socket().use { socket ->
   socket.connect(java.net.InetSocketAddress(ip,PORT),5000);socket.soTimeout=6000
   socket.getOutputStream().write((payload+"\n").toByteArray(Charsets.UTF_8))
   val reply=socket.getInputStream().bufferedReader(Charsets.UTF_8).readLine()
   check(reply=="OK"){"Alıcı aktarımı kabul etmedi"}
  }
  return "Hatırlatma aynı ağdaki telefona aktarıldı"
 }
}
