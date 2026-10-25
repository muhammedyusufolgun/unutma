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
   val reminders=Store.all(c).filter{it.repeat || it.time>now}.sortedWith(compareBy<Reminder>({if(it.repeat) 0 else 1},{Store.next(it,now)},{it.id}))
   val fmt=SimpleDateFormat("dd.MM.yyyy HH:mm",Locale("tr","TR"))
   ids.forEach { id ->
    val v=RemoteViews(c.packageName,R.layout.widget)
    v.removeAllViews(R.id.reminder_rows)
    if(reminders.isEmpty()) {v.setTextViewText(R.id.details,"Henüz yaklaşan alarm yok")}
    else {
     v.setTextViewText(R.id.details,"")
     reminders.forEach { r ->
      val item=RemoteViews(c.packageName,R.layout.widget_row)
      val base=android.os.SystemClock.elapsedRealtime()+(Store.next(r,now)-System.currentTimeMillis())
      item.setChronometerCountDown(R.id.row_countdown,true)
      item.setChronometer(R.id.row_countdown,base,null,true)
      item.setTextViewText(R.id.row_message,(if(r.repeat) "Her gün" else fmt.format(r.time))+" · "+r.text)
      v.addView(R.id.reminder_rows,item)
     }
    }
    val pi=PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    v.setOnClickPendingIntent(R.id.title,pi)
    m.updateAppWidget(id,v)
   }
  }
 }
}
