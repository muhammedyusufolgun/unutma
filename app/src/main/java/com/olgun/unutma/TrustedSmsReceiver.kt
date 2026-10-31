package com.olgun.unutma
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.net.Uri
import android.util.Log

class TrustedSmsReceiver:BroadcastReceiver(){
 override fun onReceive(context:Context,intent:Intent){
  if(intent.action!=Telephony.Sms.Intents.SMS_RECEIVED_ACTION)return
  val parts=Telephony.Sms.Intents.getMessagesFromIntent(intent)
  if(parts.isEmpty())return
  val sender=parts.first().originatingAddress?:return
  val normalized=sender.filter{it.isDigit()}.takeLast(10)
  if(normalized.length!=10)return
  val trusted=context.getSharedPreferences("trusted_sms",Context.MODE_PRIVATE)
  if(!trusted.getBoolean(normalized,false))return
  val body=parts.joinToString(""){it.messageBody?:""}
  val marker="timeikiz://reminder?"
  val start=body.indexOf(marker)
  if(start<0)return
  val link=body.substring(start).substringBefore("\n").trim()
  try{
   val uri=Uri.parse(link)
   if(uri.scheme!="timeikiz"||uri.host!="reminder")return
   val at=uri.getQueryParameter("at")?.toLongOrNull()?:return
   val repeat=uri.getQueryParameter("repeat")=="true"
   val msg=uri.getQueryParameter("text")?:return
   if(msg.isBlank()||msg.length>20000||at<=0||(!repeat&&at<=System.currentTimeMillis()))return
   val fingerprint=java.security.MessageDigest.getInstance("SHA-256")
    .digest((normalized+"|"+at+"|"+repeat+"|"+msg).toByteArray())
    .joinToString(""){"%02x".format(it)}
   val id="sms"+fingerprint
   val reminder=Store.addIncomingAtomic(context,id,at,msg,repeat)
   if(reminder!=null)Alarms.plan(context,reminder)
  }catch(e:Exception){Log.e("TimeIkizSMS","Trusted reminder import failed",e)}
 }
}
