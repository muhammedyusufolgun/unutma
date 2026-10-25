package com.olgun.unutma
import android.app.*
import android.content.Intent
import android.os.IBinder
import android.speech.tts.TextToSpeech
import java.util.Locale
class SpeechService:Service(),TextToSpeech.OnInitListener {
 private var tts:TextToSpeech?=null;private var message=""
 override fun onBind(i:Intent?):IBinder?=null
 override fun onStartCommand(i:Intent?,flags:Int,startId:Int):Int {message=i?.getStringExtra("message")?:"";val channel=NotificationChannel("speech","Sesli hatırlatmalar",NotificationManager.IMPORTANCE_HIGH);getSystemService(NotificationManager::class.java).createNotificationChannel(channel);val n=Notification.Builder(this,"speech").setContentTitle("Zamanlı Sesli Mesaj").setContentText(message).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).build();startForeground(22,n);tts?.stop();tts?.shutdown();tts=TextToSpeech(this,this);return START_NOT_STICKY}
 override fun onInit(status:Int){if(status!=TextToSpeech.SUCCESS){stopSelf();return};val t=tts?:return;val result=t.setLanguage(Locale.forLanguageTag("tr-TR"));if(result==TextToSpeech.LANG_MISSING_DATA || result==TextToSpeech.LANG_NOT_SUPPORTED){stopSelf();return};t.setOnUtteranceProgressListener(object:android.speech.tts.UtteranceProgressListener(){override fun onStart(id:String){};override fun onDone(id:String){stopSelf()};override fun onError(id:String){stopSelf()}});t.speak(message,TextToSpeech.QUEUE_FLUSH,null,"reminder")}
 override fun onDestroy(){tts?.stop();tts?.shutdown();super.onDestroy()}
}
