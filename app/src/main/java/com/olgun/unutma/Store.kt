package com.olgun.unutma
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Reminder(val id:Int,val time:Long,val text:String,val repeat:Boolean)
object Store {
 fun all(c:Context):List<Reminder> = synchronized(this) { try { val a=JSONArray(c.getSharedPreferences("data",0).getString("items","[]")); (0 until a.length()).map { val o=a.getJSONObject(it);Reminder(o.getInt("id"),o.getLong("time"),o.getString("text"),o.getBoolean("repeat")) } } catch (e:Exception){ throw IllegalStateException("Hatırlatma verileri okunamadı; veri kaybını önlemek için işlem durduruldu", e) } }
 fun save(c:Context,items:List<Reminder>) = synchronized(this) {
  val prefs=c.getSharedPreferences("data",Context.MODE_PRIVATE)
  val old=JSONArray(prefs.getString("items","[]") ?: "[]")
  val sources=mutableMapOf<Int,String>()
  for(i in 0 until old.length()) {
   val obj=old.getJSONObject(i)
   if(obj.has("source_message_id"))sources[obj.getInt("id")]=obj.getString("source_message_id")
  }
  val array=JSONArray()
  items.forEach { r ->
   val obj=JSONObject().put("id",r.id).put("time",r.time).put("text",r.text).put("repeat",r.repeat)
   sources[r.id]?.let { obj.put("source_message_id",it) }
   array.put(obj)
  }
  check(prefs.edit().putString("items",array.toString()).commit()) { "Hatırlatmalar kalıcı olarak kaydedilemedi" }
  try { ReminderWidget.refresh(c) } catch (e:Exception) { android.util.Log.w("TimeIkizStore","Widget refresh failed after successful save",e) }
 }
 fun addIncomingAtomic(c:Context,messageId:String,time:Long,text:String,repeat:Boolean):Reminder? = synchronized(this) {
  require(messageId.matches(Regex("[a-zA-Z0-9_-]{1,128}"))) { "Invalid message ID" }
  val prefs=c.getSharedPreferences("data",Context.MODE_PRIVATE)
  val marker="incoming_"+messageId
  if(prefs.getBoolean(marker,false))return@synchronized null
  val items=JSONArray(prefs.getString("items","[]") ?: "[]")
  var maxId=0
  for(i in 0 until items.length()) {
   val item=items.getJSONObject(i)
   if(item.optString("source_message_id")==messageId) {
    check(prefs.edit().putBoolean(marker,true).commit()) { "Could not repair delivery marker" }
    return@synchronized null
   }
   maxId=maxOf(maxId,item.getInt("id"))
  }
  val reminder=Reminder(maxId+1,time,text,repeat)
  items.put(JSONObject().put("id",reminder.id).put("time",time).put("text",text).put("repeat",repeat).put("source_message_id",messageId))
  check(prefs.edit().putString("items",items.toString()).putBoolean(marker,true).commit()) {
   "Incoming reminder and delivery marker could not be saved"
  }
  try { ReminderWidget.refresh(c) } catch (e:Exception) { android.util.Log.w("TimeIkizStore","Widget refresh failed after successful save",e) }
  reminder
 }
 fun reconcileAlarms(c:Context) {
  all(c).forEach { reminder ->
   if(reminder.repeat || reminder.time>System.currentTimeMillis()) Alarms.plan(c,reminder)
  }
 }
 fun next(r:Reminder,now:Long=System.currentTimeMillis()):Long { if(!r.repeat || r.time>now)return r.time; val cal=Calendar.getInstance().apply { timeInMillis=r.time }; val hour=cal.get(Calendar.HOUR_OF_DAY);val minute=cal.get(Calendar.MINUTE);cal.timeInMillis=now;cal.set(Calendar.HOUR_OF_DAY,hour);cal.set(Calendar.MINUTE,minute);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);if(cal.timeInMillis<=now)cal.add(Calendar.DAY_OF_YEAR,1);return cal.timeInMillis }
}
