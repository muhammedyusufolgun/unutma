package com.olgun.unutma

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.OAEPParameterSpec
import java.security.spec.MGF1ParameterSpec
import javax.crypto.spec.PSource

/** Experimental client. Key fingerprints MUST be checked out of band. */
object InternetMessaging {
 private const val RSA_ALIAS="time_ikiz_message_key"
 private const val SIGN_ALIAS="time_ikiz_signing_wrap_v1"
 private const val DOMAIN="TIME-IKIZ-MSG-V1\u0000"
 private fun prefs(c:Context)=c.getSharedPreferences("internet_messaging",Context.MODE_PRIVATE)
 private fun enc(b:ByteArray)=Base64.encodeToString(b,Base64.NO_WRAP)
 private fun dec(s:String)=Base64.decode(s,Base64.NO_WRAP)
 private fun sha(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){"%02x".format(it)}
 private fun fingerprint(s:String)=s.replace(":","").replace(" ","").lowercase(java.util.Locale.ROOT).also{require(it.matches(Regex("[0-9a-f]{64}")))}
 private fun rsa():java.security.KeyPair {
  val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
  if(!ks.containsAlias(RSA_ALIAS)){
   val g=KeyPairGenerator.getInstance("RSA","AndroidKeyStore")
   g.initialize(KeyGenParameterSpec.Builder(RSA_ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setKeySize(3072).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP).setDigests(KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA1).build())
   g.generateKeyPair()
  }
  return java.security.KeyPair(ks.getCertificate(RSA_ALIAS).publicKey,ks.getKey(RSA_ALIAS,null) as java.security.PrivateKey)
 }
 private fun seed(c:Context):ByteArray {
  val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
  if(!ks.containsAlias(SIGN_ALIAS)){
   val g=KeyGenerator.getInstance("AES","AndroidKeyStore")
   g.init(KeyGenParameterSpec.Builder(SIGN_ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setKeySize(256).setBlockModes("GCM").setEncryptionPaddings("NoPadding").build());g.generateKey()
  }
  val key=ks.getKey(SIGN_ALIAS,null) as javax.crypto.SecretKey
  val stored=prefs(c).getString("signing_seed_wrapped",null)
  if(stored!=null){
   val raw=dec(stored);require(raw.size==60){"Invalid wrapped signing seed"}
   return Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,raw.copyOfRange(0,12)))}.doFinal(raw.copyOfRange(12,raw.size)).also{require(it.size==32)}
  }
  val fresh=ByteArray(32).also{SecureRandom().nextBytes(it)}
  val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,key)}
  check(prefs(c).edit().putString("signing_seed_wrapped",enc(cipher.iv+cipher.doFinal(fresh))).commit())
  return fresh
 }
 private fun signPublic(c:Context)=Ed25519PrivateKeyParameters(seed(c),0).generatePublicKey().encoded
 fun myId(c:Context)=prefs(c).getString("id","")?:""
 fun server(c:Context)=prefs(c).getString("server","")?:""
 fun configured(c:Context)=myId(c).isNotBlank() && server(c).startsWith("https://")
 fun myFingerprint()=sha(rsa().public.encoded)
 fun mySigningFingerprint(c:Context)=sha(signPublic(c))
 private fun req(c:Context,path:String,body:JSONObject?=null,auth:Boolean=true):JSONObject {
  val base=server(c).trimEnd('/')
  require(base.startsWith("https://")){"HTTPS required"}
  val conn=(URL(base+path).openConnection() as HttpURLConnection)
  conn.instanceFollowRedirects=false;conn.connectTimeout=10000;conn.readTimeout=10000
  conn.setRequestProperty("Accept","application/json")
  if(auth)conn.setRequestProperty("Authorization","Bearer "+(prefs(c).getString("token","")?:""))
  try {
   if(body!=null){conn.requestMethod="POST";conn.doOutput=true;conn.setRequestProperty("Content-Type","application/json");conn.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}}
   val code=conn.responseCode
   val response=(if(code in 200..299)conn.inputStream else conn.errorStream)?.bufferedReader()?.use{it.readText()}?:"{}"
   check(code in 200..299){"Relay HTTP $code"}
   return JSONObject(response)
  }finally{conn.disconnect()}
 }
 private fun path(peer:String):String{require(peer.matches(Regex("[a-zA-Z0-9_-]{8,64}")));return URLEncoder.encode(peer,"UTF-8")}
 fun register(c:Context,base:String,id:String){
  require(base.startsWith("https://") && id.matches(Regex("[a-zA-Z0-9_-]{8,64}")))
  val token=enc(ByteArray(32).also{SecureRandom().nextBytes(it)})
  check(!configured(c)){"This device is already registered; changing identity requires an explicit recovery flow"}
  val old=server(c)
  check(prefs(c).edit().putString("server",base.trimEnd('/')).commit())
  try {
   req(c,"/register",JSONObject().put("id",id).put("token",token).put("public_key",enc(rsa().public.encoded)).put("signing_public_key",enc(signPublic(c))),false)
  }catch(e:Exception){prefs(c).edit().putString("server",old).commit();throw e}
  check(prefs(c).edit().putString("id",id).putString("token",token).commit())
 }
 fun add(c:Context,peer:String)=req(c,"/contact/add",JSONObject().put("peer",peer.also{path(it)}))
 fun delete(c:Context,peer:String):JSONObject {
  val response=req(c,"/contact/delete",JSONObject().put("peer",peer.also{path(it)}))
  check(prefs(c).edit().remove("fingerprint_$peer").remove("signing_fingerprint_$peer").commit())
  return response
 }
 fun trustPeer(c:Context,peer:String,expected:String){
  val key=dec(req(c,"/key/"+path(peer)).getString("public_key"))
  val expectedHash=fingerprint(expected)
  if(sha(key)!=expectedHash)throw SecurityException("Encryption fingerprint mismatch")
  check(prefs(c).edit().putString("fingerprint_$peer",expectedHash).commit())
 }
 fun trustSigningPeer(c:Context,peer:String,expected:String){
  val key=dec(req(c,"/signing-key/"+path(peer)).getString("signing_public_key"))
  require(key.size==32)
  val expectedHash=fingerprint(expected)
  if(sha(key)!=expectedHash)throw SecurityException("Signing fingerprint mismatch")
  check(prefs(c).edit().putString("signing_fingerprint_$peer",expectedHash).commit())
 }
 private fun canonical(sender:String,recipient:String,id:String,ciphertext:String):ByteArray {
  fun q(s:String)=JSONObject.quote(s)
  val data="{\"ciphertext\":"+q(ciphertext)+",\"message_id\":"+q(id)+",\"recipient\":"+q(recipient)+",\"sender\":"+q(sender)+",\"v\":1}"
  return DOMAIN.toByteArray(Charsets.US_ASCII)+data.toByteArray(Charsets.UTF_8)
 }
 private fun sign(c:Context,peer:String,id:String,ciphertext:String):String {
  val s=Ed25519Signer();s.init(true,Ed25519PrivateKeyParameters(seed(c),0))
  val bytes=canonical(myId(c),peer,id,ciphertext);s.update(bytes,0,bytes.size)
  return enc(s.generateSignature())
 }
 private fun verifyPeer(c:Context,sender:String,id:String,ciphertext:String,signature:String){
  val key=dec(req(c,"/signing-key/"+path(sender)).getString("signing_public_key"))
  require(key.size==32)
  if(prefs(c).getString("signing_fingerprint_$sender",null)!=sha(key))throw SecurityException("Unverified sender signing key")
  val s=Ed25519Signer();s.init(false,Ed25519PublicKeyParameters(key,0))
  val bytes=canonical(sender,myId(c),id,ciphertext);s.update(bytes,0,bytes.size)
  if(!s.verifySignature(dec(signature)))throw SecurityException("Invalid sender signature")
 }
 private fun oaep()=OAEPParameterSpec("SHA-256","MGF1",MGF1ParameterSpec.SHA1,PSource.PSpecified.DEFAULT)
 private fun encrypt(peerKey:ByteArray,plain:ByteArray):String {
  val aes=ByteArray(32).also{SecureRandom().nextBytes(it)}
  val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,SecretKeySpec(aes,"AES"))}
  val pub=KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(peerKey))
  val wrap=Cipher.getInstance("RSA/ECB/OAEPPadding").apply{init(Cipher.ENCRYPT_MODE,pub,oaep())}
  return JSONObject().put("v",1).put("key",enc(wrap.doFinal(aes))).put("iv",enc(cipher.iv)).put("data",enc(cipher.doFinal(plain))).toString()
 }
 private fun decrypt(payload:String):JSONObject {
  val o=JSONObject(payload);require(o.getInt("v")==1)
  val wrap=Cipher.getInstance("RSA/ECB/OAEPPadding").apply{init(Cipher.DECRYPT_MODE,rsa().private,oaep())}
  val aes=wrap.doFinal(dec(o.getString("key")))
  val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.DECRYPT_MODE,SecretKeySpec(aes,"AES"),GCMParameterSpec(128,dec(o.getString("iv"))))}
  return JSONObject(String(cipher.doFinal(dec(o.getString("data"))),Charsets.UTF_8))
 }
 fun send(c:Context,peer:String,r:Reminder){
  val key=dec(req(c,"/key/"+path(peer)).getString("public_key"))
  if(prefs(c).getString("fingerprint_$peer",null)!=sha(key))throw SecurityException("Unverified recipient encryption key")
  val message=encrypt(key,JSONObject().put("text",r.text).put("time",r.time).put("repeat",r.repeat).toString().toByteArray(Charsets.UTF_8))
  val id=java.util.UUID.randomUUID().toString()
  req(c,"/send",JSONObject().put("recipient",peer).put("message_id",id).put("ciphertext",message).put("signature",sign(c,peer,id,message)))
 }
 fun sync(c:Context):Int{
  Store.reconcileAlarms(c)
  if(!configured(c))return 0
  val messages=req(c,"/inbox").getJSONArray("messages")
  var count=0
  for(i in 0 until messages.length()){
   try {
   val m=messages.getJSONObject(i);val id=m.getString("message_id")
   val seen=c.getSharedPreferences("data",Context.MODE_PRIVATE).getBoolean("incoming_$id",false)
   if(!seen){
    val sender=m.getString("sender");val ciphertext=m.getString("ciphertext")
    verifyPeer(c,sender,id,ciphertext,m.getString("signature"))
    val d=decrypt(ciphertext);val text=d.getString("text");val time=d.getLong("time")
    require(text.length<=10000 && time>0)
    val reminder=Store.addIncomingAtomic(c,id,time,text,d.getBoolean("repeat"))
    if(reminder!=null){Alarms.plan(c,reminder);count++}
   }
   req(c,"/ack",JSONObject().put("message_id",id))
   } catch(e:Exception) {
    android.util.Log.e("TimeIkizSync","Inbox item $i failed; retry on next sync",e)
   }
  }
  Store.reconcileAlarms(c)
  return count
 }
}
