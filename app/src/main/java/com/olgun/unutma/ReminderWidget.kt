package com.olgun.unutma
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Locale

class ReminderWidget:AppWidgetProvider(){
 override fun onUpdate(c:Context,m:AppWidgetManager,ids:IntArray){update(c,m,ids)}
 companion object {
  fun refresh(c:Context){val m=AppWidgetManager.getInstance(c);update(c,m,m.getAppWidgetIds(ComponentName(c,ReminderWidget::class.java)))}
  private fun update(c:Context,m:AppWidgetManager,ids:IntArray){
   val now=System.currentTimeMillis()
   val next=Store.all(c).filter{it.repeat || it.time>now}.minByOrNull{Store.next(it)}
   val fmt=SimpleDateFormat("dd.MM.yyyy HH:mm",Locale("tr","TR"))
   ids.forEach { id ->
    val v=RemoteViews(c.packageName,R.layout.widget)
    if(next==null){v.setTextViewText(R.id.details,"Henüz yaklaşan alarm yok");v.setChronometer(R.id.countdown,0,null,false)}
    else {
     val whenMs=Store.next(next)
     v.setTextViewText(R.id.details,"${fmt.format(whenMs)} · ${if(next.repeat)"Tekrarlı" else "Birkez"}\n${next.text.take(90)}")
     // Android Chronometer counts down on the system clock, updating every second without a background timer.
     val base=android.os.SystemClock.elapsedRealtime()+(whenMs-System.currentTimeMillis())
     v.setChronometerCountDown(R.id.countdown,true)
     v.setChronometer(R.id.countdown,base,null,true)
    }
    val pi=PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    v.setOnClickPendingIntent(R.id.title,pi)
    m.updateAppWidget(id,v)
   }
  }
 }
}
