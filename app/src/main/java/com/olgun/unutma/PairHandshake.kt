package com.olgun.unutma

import android.content.Context
import android.app.*
import android.content.Intent
import android.util.Base64
import org.json.JSONObject
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher

/** Explicit recipient approval before a one-time encrypted exchange of LAN capabilities. */
object PairHandshake {
 private data class Pending(val ip:String,val publicKey:PublicKey,val created:Long)
 private val requests=ConcurrentHashMap<String,Pending>()
 private val approved=ConcurrentHashMap<String,String>()
 private fun b64(bytes:ByteArray)=Base64.encodeToString(bytes,Base64.NO_WRAP)
 private fun un64(value:String)=Base64.decode(value,Base64.NO_WRAP)
 private fun rsa(publicKey:PublicKey,secret:String):String {
  val cipher=Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
  cipher.init(Cipher.ENCRYPT_MODE,publicKey)
  return b64(cipher.doFinal(secret.toByteArray(Charsets.UTF_8)))
 }
 fun receive(c:Context,s:Socket,data:JSONObject):Boolean {
  val type=data.optString("type")
  if(type!="pair_request" && type!="pair_poll")return false
  val id=data.optString("id")
  require(id.matches(Regex("[a-zA-Z0-9_-]{8,64}")))
  val ip=s.inetAddress.hostAddress?:return true
  if(type=="pair_request"){
   val encoded=un64(data.getString("publicKey"))
   require(encoded.size<=1024)
   val key=KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(encoded))
   require((key as java.security.interfaces.RSAPublicKey).modulus.bitLength()>=2048)
   require(requests.size<64){"Too many pending requests"}
   requests[id]=Pending(ip,key,System.currentTimeMillis())
   val intent=Intent(c,MainActivity::class.java).apply {
    action="com.olgun.timeikiz.PAIR_REQUEST"
    putExtra("pair_id",id)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
   }
   val manager=c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
   manager.createNotificationChannel(NotificationChannel("lan_pair_requests","Time-ikiz eşleştirme",NotificationManager.IMPORTANCE_HIGH))
   val pending=PendingIntent.getActivity(c,id.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
   manager.notify(id.hashCode(),Notification.Builder(c,"lan_pair_requests").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Time-ikiz eşleştirme isteği").setContentText("Onaylamak veya reddetmek için dokunun").setContentIntent(pending).setAutoCancel(true).build())
   s.getOutputStream().write("PENDING\n".toByteArray())
  }else{
   val record=requests[id]
   if(record==null || record.ip!=ip || System.currentTimeMillis()-record.created>120000L){
    s.getOutputStream().write("EXPIRED\n".toByteArray())
   }else{
    val result=approved.remove(id)
    if(result==null)s.getOutputStream().write("PENDING\n".toByteArray())
    else {s.getOutputStream().write((result+"\n").toByteArray());requests.remove(id)}
   }
  }
  return true
 }
 fun approve(c:Context,id:String):Boolean {
  val request=requests[id]?:return false
  if(System.currentTimeMillis()-request.created>120000L)return false
  approved[id]=rsa(request.publicKey,LanTransfer.pairingCode(c))
  return true
 }
 fun reject(id:String){requests.remove(id);approved.remove(id)}
 fun request(c:Context,ip:String):Boolean {
  val keys=KeyPairGenerator.getInstance("RSA").apply{initialize(2048)}.generateKeyPair()
  val id=UUID.randomUUID().toString().replace("-","")
  fun exchange(message:JSONObject):String {
   Socket().use{socket->
    socket.connect(java.net.InetSocketAddress(ip,39471),5000)
    socket.soTimeout=7000
    socket.getOutputStream().write((message.toString()+"\n").toByteArray())
    return socket.getInputStream().bufferedReader().readLine()?:""
   }
  }
  check(exchange(JSONObject().put("type","pair_request").put("id",id).put("publicKey",b64(keys.public.encoded)))=="PENDING")
  repeat(24){
   Thread.sleep(4000)
   val result=exchange(JSONObject().put("type","pair_poll").put("id",id))
   if(result=="EXPIRED")return false
   if(result!="PENDING" && result.isNotBlank()){
    val cipher=Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
    cipher.init(Cipher.DECRYPT_MODE,keys.private)
    val token=String(cipher.doFinal(un64(result)),Charsets.UTF_8)
    LanTransfer.pair(c,ip,token)
    return true
   }
  }
  return false
 }
}
