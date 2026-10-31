package com.olgun.unutma
import android.app.*
import android.content.*
import android.graphics.Color
import android.os.*
import android.provider.Settings
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity:Activity(){
 private lateinit var root:LinearLayout;private lateinit var list:LinearLayout;private lateinit var input:EditText;private lateinit var date:EditText;private lateinit var time:EditText;private lateinit var done:Button;private lateinit var mode:TextView
 private val handler=Handler(Looper.getMainLooper());private var refreshTask:Runnable?=null
 private var pendingShare:Reminder?=null
 private var editing:Int?=null;private var dateChosen=false;private var timeChosen=false
 private val fmt=SimpleDateFormat("dd.MM.yyyy",Locale("tr","TR"));private val clock=SimpleDateFormat("HH:mm",Locale("tr","TR"))
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun blackOutline():android.graphics.drawable.GradientDrawable=android.graphics.drawable.GradientDrawable().apply{setColor(Color.WHITE);setCornerRadius(dp(6).toFloat());setStroke(dp(1),Color.BLACK)}
 private fun redOutline():android.graphics.drawable.GradientDrawable=android.graphics.drawable.GradientDrawable().apply{setColor(Color.WHITE);setCornerRadius(dp(6).toFloat());setStroke(dp(1),Color.rgb(220,55,55))}
 private fun button(label:String,color:Int):Button=Button(this).apply{text=label;setTextColor(Color.BLACK);setAllCaps(false);setBackgroundTintList(android.content.res.ColorStateList.valueOf(color))}
 override fun onCreate(b:Bundle?){super.onCreate(b);window.statusBarColor=Color.rgb(244,246,248);window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);show();LanTransfer.start(this);receiveSharedReminder(intent);checkPermissions();try{Store.reconcileAlarms(this)}catch(e:Exception){android.util.Log.e("TimeIkiz", "Recovery failed", e)}}
 override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);receiveSharedReminder(intent)}
 private fun receiveSharedReminder(intent:Intent?){
  val u=intent?.data?:return
  if(u.scheme!="timeikiz"||u.host!="reminder")return
  try{
   val at=u.getQueryParameter("at")?.toLongOrNull()?:return
   val repeat=u.getQueryParameter("repeat")=="true"
   val msg=u.getQueryParameter("text")?:return
   require(msg.isNotBlank()&&msg.length<=20000&&at>0){"Geçersiz hatırlatma"}
   require(repeat||at>System.currentTimeMillis()){"Hatırlatma zamanı geçmiş"}
   AlertDialog.Builder(this).setTitle("Gelen hatırlatma")
    .setMessage(msg+"\n"+java.text.DateFormat.getDateTimeInstance().format(Date(at)))
    .setPositiveButton("Kaydet"){_,_->
     try{val items=Store.all(this).toMutableList();val id=(items.maxOfOrNull{it.id}?:0)+1
      val r=Reminder(id,at,msg,repeat);items.add(r);Store.save(this,items);Alarms.plan(this,r);render()
      Toast.makeText(this,"Hatırlatma kaydedildi",Toast.LENGTH_SHORT).show()
     }catch(e:Exception){Toast.makeText(this,e.message?:"Kayıt başarısız",Toast.LENGTH_LONG).show()}
    }.setNegativeButton("Reddet",null).show()
  }catch(e:Exception){Toast.makeText(this,"Geçersiz bağlantı",Toast.LENGTH_LONG).show()}
 }
 private fun show(){val scroll=ScrollView(this);scroll.isFillViewport=true;var pullStart=0f;scroll.setOnTouchListener{v,event->when(event.actionMasked){android.view.MotionEvent.ACTION_DOWN->pullStart=event.y;android.view.MotionEvent.ACTION_UP->if(scroll.scrollY==0 && event.y-pullStart>dp(75))render()};false};root=LinearLayout(this).apply{orientation=1;setPadding(dp(16),dp(38),dp(16),dp(12));setBackgroundColor(Color.rgb(248,250,252))};scroll.addView(root);setContentView(scroll)
 root.addView(TextView(this).apply{text="Time-ikiz • Zamanlı Sesli Mesaj";textSize=23f;setTextColor(Color.BLACK);gravity=Gravity.CENTER;setTypeface(null,1);setPadding(0,0,0,dp(20))})
 val row=LinearLayout(this).apply{orientation=0};root.addView(row)
 date=EditText(this).apply{hint="Tarih: GG.AA.YYYY";setSingleLine(true);inputType=android.text.InputType.TYPE_CLASS_DATETIME or android.text.InputType.TYPE_DATETIME_VARIATION_DATE};time=EditText(this).apply{hint="Saat: SS:DD (isteğe bağlı)";setSingleLine(true);inputType=android.text.InputType.TYPE_CLASS_DATETIME or android.text.InputType.TYPE_DATETIME_VARIATION_TIME}
 date.background=blackOutline();time.background=blackOutline();date.setPadding(dp(8),0,dp(8),0);time.setPadding(dp(8),0,dp(8),0)
 row.addView(date,LinearLayout.LayoutParams(0,dp(55),1f));row.addView(time,LinearLayout.LayoutParams(0,dp(55),1f))
 mode=TextView(this).apply{text="Tekrarlı Alarm";textSize=15f;setTextColor(Color.DKGRAY);gravity=Gravity.CENTER;setPadding(0,dp(8),0,dp(8))};root.addView(mode)
 date.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,st:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,st:Int,before:Int,count:Int){mode.text=if(s.isNullOrBlank())"Tekrarlı Alarm" else "Birkez Alarm"};override fun afterTextChanged(s:android.text.Editable?) {}})
 val pick=LinearLayout(this);root.addView(pick);pick.addView(button("Tarih seç",0xffeeeeee.toInt()).apply{backgroundTintList=null;background=redOutline();setOnClickListener{val c=Calendar.getInstance();DatePickerDialog(this@MainActivity,{_,y,m,d->date.setText("%02d.%02d.%04d".format(d,m+1,y));dateChosen=true},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show()}},LinearLayout.LayoutParams(0,dp(45),1f));pick.addView(button("Tarihi kaldır",0xffeeeeee.toInt()).apply{backgroundTintList=null;background=blackOutline();setOnClickListener{date.text.clear()}},LinearLayout.LayoutParams(0,dp(45),1f));pick.addView(button("Saat seç",0xffeeeeee.toInt()).apply{backgroundTintList=null;background=redOutline();setOnClickListener{val c=Calendar.getInstance();TimePickerDialog(this@MainActivity,{_,h,m->time.setText("%02d:%02d".format(h,m));timeChosen=true},c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE),true).show()}},LinearLayout.LayoutParams(0,dp(45),1f))
 input=EditText(this).apply{hint="Sesli okunacak mesajı yazın";minLines=2;gravity=Gravity.TOP;setTextColor(Color.BLACK);setTypeface(null,android.graphics.Typeface.BOLD);inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE};root.addView(input,LinearLayout.LayoutParams(-1,-2))
 done=button("Tamam",0xffdddddd.toInt());done.visibility=View.GONE;root.addView(done,LinearLayout.LayoutParams(-1,dp(48)));input.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,a:Int,c:Int,f:Int){};override fun onTextChanged(s:CharSequence?,st:Int,b:Int,count:Int){done.visibility=if(s.isNullOrBlank())View.GONE else View.VISIBLE};override fun afterTextChanged(s:android.text.Editable?) {}})
 done.setOnClickListener{save()};root.addView(button("Kaydet ve İlet",0xffd9edf9.toInt()).apply{setOnClickListener{save()?.let{sendDialog(it)}}},LinearLayout.LayoutParams(-1,dp(48)));root.addView(button("Yeni Ekle",0xffb9efb7.toInt()).apply{setOnClickListener{editing=null;date.text.clear();time.text.clear();input.text.clear();dateChosen=false;timeChosen=false;input.requestFocus();(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT)}},LinearLayout.LayoutParams(-1,dp(52)))
 

  root.addView(button("Wi-Fi eşleştir",0xffc8e6ff.toInt()).apply{setOnClickListener{lanPairDialog()}},LinearLayout.LayoutParams(-1,dp(48)))
  list=LinearLayout(this).apply{orientation=1};root.addView(list);root.addView(Space(this),LinearLayout.LayoutParams(1,dp(28)));root.addView(TextView(this).apply{text="Muhammed Yusuf OLGUN";gravity=Gravity.CENTER;textSize=12f;setTextColor(0xffaaaaaa.toInt())},LinearLayout.LayoutParams(-1,dp(35)));render()}
 private fun save():Reminder?{try{val dateRaw=date.text.toString().trim();val repeat=dateRaw.isEmpty();val d=if(repeat)Date() else {require(dateRaw.matches(Regex("\\d{2}\\.\\d{2}\\.\\d{4}"))){"Tarih GG.AA.YYYY olmalı"};fmt.apply{isLenient=false}.parse(dateRaw)?:throw Exception("Geçersiz tarih")};val raw=time.text.toString().trim();require(raw.matches(Regex("([01]\\d|2[0-3]):[0-5]\\d"))){"Saat SS:DD biçiminde olmalı"};require(input.text.toString().isNotBlank()){"Sesli mesaj boş olamaz"};val hm=raw;val t=clock.apply{isLenient=false}.parse(hm)?:throw Exception("Geçersiz saat");val cal=Calendar.getInstance().apply{time=d};val tc=Calendar.getInstance().apply{time=t};cal.set(Calendar.HOUR_OF_DAY,tc.get(Calendar.HOUR_OF_DAY));cal.set(Calendar.MINUTE,tc.get(Calendar.MINUTE));cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);if(!repeat && cal.timeInMillis<=System.currentTimeMillis())throw IllegalArgumentException("Gelecekte bir tarih ve saat girin")
 val items=Store.all(this).toMutableList();val id=editing?:((items.maxOfOrNull{it.id}?:0)+1);require(id>0){"Hatırlatma kimliği geçersiz"};if(editing!=null)require(items.any{it.id==id}){"Düzenlenecek hatırlatma bulunamadı"};items.removeAll{it.id==id};val r=Reminder(id,cal.timeInMillis,input.text.toString().trim(),repeat);items.add(r);Store.save(this,items);Alarms.cancel(this,id);Alarms.plan(this,r);editing=null;date.text.clear();time.text.clear();input.text.clear();(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken,0);input.clearFocus();render();Toast.makeText(this,"Mesaj kaydedildi",Toast.LENGTH_SHORT).show();return r
 }catch(e:Exception){Toast.makeText(this,e.message?:"Tarih GG.AA.YYYY, saat SS:DD olmalı",Toast.LENGTH_LONG).show();return null}}
 private fun render(){
  list.removeAllViews()
  val now=System.currentTimeMillis()
  val f=SimpleDateFormat("dd.MM.yyyy HH:mm",Locale("tr","TR"))
  Store.all(this).filter{it.repeat||it.time>=now||SpeechService.activeId(this)==it.id}.sortedWith(compareBy<Reminder>({Store.next(it,now)},{it.id})).forEach{r->
   val active=SpeechService.activeId(this)==r.id
   val panel=LinearLayout(this).apply{orientation=1;setPadding(dp(12),dp(12),dp(12),dp(12));background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.WHITE);setStroke(dp(1),if(active && (SystemClock.uptimeMillis()/500)%2L==0L)Color.RED else Color.TRANSPARENT)}}
   list.addView(panel,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)})
   val until=(Store.next(r,now)-now).coerceAtLeast(0)/1000
   val countdown="%02d Saat %02d Dakika %02d Saniye".format(until/3600,(until%3600)/60,until%60)
   val title=if(r.repeat)SimpleDateFormat("HH:mm",Locale("tr","TR")).format(r.time) else f.format(r.time)
   panel.addView(TextView(this).apply{text=title+"  "+(if(r.repeat)"Tekrarlı Alarm" else "Tek Alarm");textSize=14f;setTextColor(Color.BLACK);includeFontPadding=false;setPadding(0,0,0,0)})
   panel.addView(TextView(this).apply{text=countdown;textSize=14f;setTextColor(Color.BLACK);setTypeface(null,android.graphics.Typeface.BOLD);includeFontPadding=false;setPadding(0,0,0,0)})
   panel.addView(TextView(this).apply{text=r.text;textSize=16f;setTextColor(Color.BLACK)})
   val actions=LinearLayout(this).apply{orientation=0};panel.addView(actions)
   actions.addView(button("Düzenle",0xffffef9b.toInt()).apply{setOnClickListener{editing=r.id;date.setText(if(r.repeat)"" else fmt.format(r.time));time.setText(clock.format(r.time));input.setText(r.text);input.requestFocus();(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT)}},LinearLayout.LayoutParams(0,dp(46),1f))
   actions.addView(button("Sil",0xffffb8b8.toInt()).apply{setOnClickListener{AlertDialog.Builder(this@MainActivity).setMessage("Mesaj silinsin mi?").setPositiveButton("Sil"){_,_->Alarms.cancel(this@MainActivity,r.id);Store.save(this@MainActivity,Store.all(this@MainActivity).filterNot{it.id==r.id});render()}.setNegativeButton("Vazgeç",null).show()}},LinearLayout.LayoutParams(0,dp(46),1f))
   actions.addView(button("İlet",0xffd9edf9.toInt()).apply{setOnClickListener{sendDialog(r)}},LinearLayout.LayoutParams(0,dp(46),1f))
   actions.addView(button("Durdur",0xffe2e2e2.toInt()).apply{setOnClickListener{(getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator).vibrate(android.os.VibrationEffect.createOneShot(100,android.os.VibrationEffect.DEFAULT_AMPLITUDE));startService(Intent(this@MainActivity,SpeechService::class.java).setAction(SpeechService.STOP));render()}},LinearLayout.LayoutParams(0,dp(46),1f))
  }
 }
 override fun onResume(){super.onResume();try{Store.reconcileAlarms(this)}catch(e:Exception){android.util.Log.e("TimeIkizAlarm","Resume alarm reconciliation failed",e)};refreshTask?.let{handler.removeCallbacks(it)};val tick=object:Runnable{override fun run(){render();handler.postDelayed(this,500)}};refreshTask=tick;handler.post(tick)}
 override fun onPause(){refreshTask?.let{handler.removeCallbacks(it)};refreshTask=null;super.onPause()}

 private fun lanPairDialog(){
  val fields=LinearLayout(this).apply{orientation=1;setPadding(dp(16),dp(8),dp(16),dp(8))}
  val ip=EditText(this).apply{hint="Karşı telefonun IP adresi";setSingleLine(true);setText(LanTransfer.peer(this@MainActivity))}
  val token=EditText(this).apply{hint="Karşı telefonun 24 karakterlik kodu";setSingleLine(true)}
  fields.addView(TextView(this).apply{text="Bu telefon: "+LanTransfer.localIp()+" : 39471\nBu telefonun kodu: "+LanTransfer.pairingCode(this@MainActivity)+"\\nKodları yalnızca güvendiğiniz kişiyle paylaşın."})
  fields.addView(ip);fields.addView(token)
  AlertDialog.Builder(this).setTitle("Aynı Wi-Fi ağında eşleştir").setView(fields).setPositiveButton("Kaydet"){_,_->try{LanTransfer.pair(this,ip.text.toString().trim(),token.text.toString().trim().uppercase(Locale.ROOT));Toast.makeText(this,"Eşleştirme kaydedildi",Toast.LENGTH_SHORT).show()}catch(e:Exception){Toast.makeText(this,e.message,Toast.LENGTH_LONG).show()}}.setNegativeButton("Vazgeç",null).show()
 }
 private fun sendDialog(r:Reminder){
  if(LanTransfer.peer(this).isNotBlank()){
   Thread{val result=try{LanTransfer.send(this,r)}catch(e:Exception){"Wi-Fi iletim hatası: "+(e.message?:"Bağlantı yok")};runOnUiThread{Toast.makeText(this,result,Toast.LENGTH_LONG).show()}}.start()
   return
  }

  pendingShare=r
  try{
   startActivityForResult(Intent(Intent.ACTION_PICK,android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI),42)
  }catch(e:Exception){Toast.makeText(this,"Telefon rehberi açılamadı",Toast.LENGTH_LONG).show()}
 }
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode!=42||resultCode!=RESULT_OK)return
  val r=pendingShare?:return
  pendingShare=null
  val uri=data?.data?:return
  try{
   contentResolver.query(uri,arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER),null,null,null)?.use{cursor->
    if(!cursor.moveToFirst())return
    val phone=cursor.getString(0)
    if(r.text.length>1500){Toast.makeText(this,"SMS iletimi için mesaj en fazla 1500 karakter olmalı",Toast.LENGTH_LONG).show();return}
    val payload=android.net.Uri.Builder().scheme("timeikiz").authority("reminder")
     .appendQueryParameter("at",r.time.toString()).appendQueryParameter("repeat",r.repeat.toString())
     .appendQueryParameter("text",r.text).build().toString()
    if(payload.length>1200){Toast.makeText(this,"SMS bağlantısı çok uzun; mesajı kısaltın",Toast.LENGTH_LONG).show();return}
    val sms=Intent(Intent.ACTION_SENDTO,android.net.Uri.parse("smsto:"+android.net.Uri.encode(phone)))
    sms.putExtra("sms_body","Time-ikiz hatırlatması: "+payload+"\nAlıcı Time-ikiz bağlantısını açıp kaydı onaylamalıdır.")
    startActivity(sms)
   }
  }catch(e:Exception){Toast.makeText(this,"İletim başlatılamadı: "+e.message,Toast.LENGTH_LONG).show()}
 }
 private fun checkPermissions(){if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),10)
 if(Build.VERSION.SDK_INT>=31 && !getSystemService(AlarmManager::class.java).canScheduleExactAlarms())AlertDialog.Builder(this).setMessage("Hatırlatmaların zamanında çalışması için kesin alarm iznini etkinleştirin.").setPositiveButton("Ayarlar"){_,_->startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))}.setNegativeButton("Sonra",null).show()
 // SpeechService owns and releases the TTS engine when an alarm fires.
 }
}
