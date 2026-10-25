package com.olgun.unutma
import android.app.*
import android.content.*
import android.os.Build
object Alarms {
 fun plan(c:Context,r:Reminder){val at=Store.next(r);if(at<=System.currentTimeMillis())return;val am=c.getSystemService(AlarmManager::class.java);val i=Intent(c,AlarmReceiver::class.java).putExtra("id",r.id);val pi=PendingIntent.getBroadcast(c,r.id,i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);if(Build.VERSION.SDK_INT>=31 && !am.canScheduleExactAlarms())am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi) else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi)}
 fun cancel(c:Context,id:Int){val pi=PendingIntent.getBroadcast(c,id,Intent(c,AlarmReceiver::class.java).putExtra("id",id),PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE);if(pi!=null)c.getSystemService(AlarmManager::class.java).cancel(pi)}
}
class AlarmReceiver:android.content.BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){val r=Store.all(c).firstOrNull {it.id==i.getIntExtra("id",-1)}?:return;val now=System.currentTimeMillis();if(r.repeat){Alarms.plan(c,r)}else if(kotlin.math.abs(now-r.time)>20*60*1000)return
 try {androidxPlaceholder(c,r.id,r.text)}catch(_:Exception){};ReminderWidget.refresh(c)}
 private fun androidxPlaceholder(c:Context,id:Int,s:String){val i=Intent(c,SpeechService::class.java).putExtra("message",s).putExtra("id",id);c.startForegroundService(i)}
}
class BootReceiver:android.content.BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){Store.all(c).forEach { Alarms.plan(c,it) };ReminderWidget.refresh(c)}}
