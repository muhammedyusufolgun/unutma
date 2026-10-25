package com.olgun.unutma
import android.app.*
import android.content.Intent
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class SpeechService:Service(),TextToSpeech.OnInitListener {
 companion object {
  const val STOP="com.olgun.unutma.STOP_SPEECH"
  fun activeId(c:android.content.Context)=c.getSharedPreferences("speech_state",0).getInt("active",-1)
 }
 private var tts:TextToSpeech?=null
 private var message=""
 private var reminderId=-1
 private var startedId=0
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  if(intent?.action==STOP){stopSelf();return START_NOT_STICKY}
  startedId=startId
  message=intent?.getStringExtra("message").orEmpty()
  reminderId=intent?.getIntExtra("id",-1)?:-1
  getSharedPreferences("speech_state",0).edit().putInt("active",reminderId).apply()
  ReminderWidget.refresh(this)
  val manager=getSystemService(NotificationManager::class.java)
  manager.createNotificationChannel(NotificationChannel("speech","Sesli hatırlatmalar",NotificationManager.IMPORTANCE_HIGH))
  val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val stop=PendingIntent.getService(this,1,Intent(this,SpeechService::class.java).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val n=Notification.Builder(this,"speech").setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Zamanlı Sesli Mesaj").setContentText(message).setContentIntent(open).addAction(android.R.drawable.ic_media_pause,"Durdur",stop).setAutoCancel(false).build()
  startForeground(22,n)
  tts?.stop();tts?.shutdown()
  tts=TextToSpeech(this,this)
  return START_NOT_STICKY
 }
 override fun onInit(status:Int){
  if(status!=TextToSpeech.SUCCESS){stopSelf();return}
  val t=tts?:return
  val lang=t.setLanguage(Locale.forLanguageTag("tr-TR"))
  if(lang==TextToSpeech.LANG_MISSING_DATA || lang==TextToSpeech.LANG_NOT_SUPPORTED){stopSelf();return}
  val max=TextToSpeech.getMaxSpeechInputLength().coerceAtLeast(1000)-100
  val parts=message.chunked(max).ifEmpty{listOf("")}
  var index=0
  t.setOnUtteranceProgressListener(object:UtteranceProgressListener(){
   override fun onStart(id:String){}
   override fun onDone(id:String){index++;if(index<parts.size)t.speak(parts[index],TextToSpeech.QUEUE_ADD,null,"part"+index) else stopSelfResult(startedId)}
   override fun onError(id:String){stopSelf()}
  })
  t.speak(parts[0],TextToSpeech.QUEUE_FLUSH,null,"part0")
 }
 override fun onDestroy(){
  tts?.stop();tts?.shutdown();tts=null
  getSharedPreferences("speech_state",0).edit().putInt("active",-1).apply()
  ReminderWidget.refresh(this)
  super.onDestroy()
 }
}
