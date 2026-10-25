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
   val reminders=Store.all(c).filter{it.repeat || it.time>now}.sortedWith(compareBy<Reminder>({Store.next(it,now)},{it.id}))
   val fmt=SimpleDateFormat("dd.MM.yyyy HH:mm",Locale("tr","TR"))
   ids.forEach { id ->
    val v=RemoteViews(c.packageName,R.layout.widget)
    v.removeAllViews(R.id.reminder_rows)
    if(reminders.isEmpty()) {v.setTextViewText(R.id.details,"Henüz yaklaşan alarm yok")}
    else {
     v.setTextViewText(R.id.details,"")
     reminders.forEach { r ->
      val item=RemoteViews(c.packageName,R.layout.widget_row)
      item.setInt(R.id.row_container,"setBackgroundResource",if(SpeechService.activeId(c)==r.id && (android.os.SystemClock.uptimeMillis()/500)%2L==0L)R.drawable.widget_active else R.drawable.widget_normal)
      val base=android.os.SystemClock.elapsedRealtime()+(Store.next(r,now)-System.currentTimeMillis())
      item.setChronometerCountDown(R.id.row_countdown,true)
      item.setChronometer(R.id.row_countdown,base,null,true)
      item.setTextViewText(R.id.row_message,(if(r.repeat) "Her gün" else fmt.format(r.time))+" · "+r.text)
      item.setOnClickPendingIntent(R.id.row_container,PendingIntent.getActivity(c,r.id,Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
      item.setOnClickPendingIntent(R.id.row_message,PendingIntent.getActivity(c,r.id,Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
      item.setOnClickPendingIntent(R.id.row_countdown,PendingIntent.getActivity(c,r.id,Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
      v.addView(R.id.reminder_rows,item)
     }
    }
    val pi=PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    v.setOnClickPendingIntent(R.id.title,pi)
    v.setOnClickPendingIntent(R.id.details,pi)
    v.setOnClickPendingIntent(R.id.reminder_rows,pi)
    m.updateAppWidget(id,v)
   }
  }
 }
}
