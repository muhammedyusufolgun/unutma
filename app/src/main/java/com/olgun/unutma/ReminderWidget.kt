package com.olgun.unutma
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Locale
class ReminderWidget:AppWidgetProvider(){override fun onUpdate(c:Context,m:AppWidgetManager,ids:IntArray){update(c,m,ids)}
 companion object {fun refresh(c:Context){val m=AppWidgetManager.getInstance(c);val ids=m.getAppWidgetIds(ComponentName(c,ReminderWidget::class.java));update(c,m,ids)}
 private fun update(c:Context,m:AppWidgetManager,ids:IntArray){val fmt=SimpleDateFormat("dd.MM HH:mm",Locale("tr","TR"));val now=System.currentTimeMillis();val lines=Store.all(c).sortedBy{Store.next(it)}.take(5).joinToString("\n"){ val delta=(Store.next(it)-now).coerceAtLeast(0)/60000;"${fmt.format(Store.next(it))} · ${delta/60}sa ${delta%60}dk · ${it.text.take(35)}" };ids.forEach {val v=RemoteViews(c.packageName,R.layout.widget);v.setTextViewText(R.id.details,lines.ifEmpty{"Henüz mesaj yok"});val pi=PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT);v.setOnClickPendingIntent(R.id.title,pi);m.updateAppWidget(it,v)}} }
}
