package com.olgun.unutma
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Reminder(val id:Int,val time:Long,val text:String,val repeat:Boolean)
object Store {
 fun all(c:Context):List<Reminder> = try { val a=JSONArray(c.getSharedPreferences("data",0).getString("items","[]")); (0 until a.length()).map { val o=a.getJSONObject(it);Reminder(o.getInt("id"),o.getLong("time"),o.getString("text"),o.getBoolean("repeat")) } } catch (_:Exception){emptyList()}
 fun save(c:Context,items:List<Reminder>){val a=JSONArray();items.forEach { a.put(JSONObject().put("id",it.id).put("time",it.time).put("text",it.text).put("repeat",it.repeat)) };c.getSharedPreferences("data",0).edit().putString("items",a.toString()).apply();ReminderWidget.refresh(c)}
 fun next(r:Reminder,now:Long=System.currentTimeMillis()):Long { if(!r.repeat || r.time>now)return r.time; val cal=Calendar.getInstance().apply { timeInMillis=r.time }; val hour=cal.get(Calendar.HOUR_OF_DAY);val minute=cal.get(Calendar.MINUTE);cal.timeInMillis=now;cal.set(Calendar.HOUR_OF_DAY,hour);cal.set(Calendar.MINUTE,minute);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);if(cal.timeInMillis<=now)cal.add(Calendar.DAY_OF_YEAR,1);return cal.timeInMillis }
}
