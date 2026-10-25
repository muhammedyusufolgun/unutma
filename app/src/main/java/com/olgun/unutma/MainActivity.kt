package com.olgun.unutma
import android.app.*
import android.content.*
import android.graphics.Color
import android.os.*
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity:Activity(){
 private lateinit var root:LinearLayout;private lateinit var list:LinearLayout;private lateinit var input:EditText;private lateinit var date:EditText;private lateinit var time:EditText;private lateinit var done:Button;private lateinit var mode:TextView
 private val handler=Handler(Looper.getMainLooper());private var refreshTask:Runnable?=null
 private var editing:Int?=null;private var dateChosen=false;private var timeChosen=false
 private val fmt=SimpleDateFormat("dd.MM.yyyy",Locale("tr","TR"));private val clock=SimpleDateFormat("HH:mm",Locale("tr","TR"))
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun blackOutline():android.graphics.drawable.GradientDrawable=android.graphics.drawable.GradientDrawable().apply{setColor(Color.WHITE);setCornerRadius(dp(6).toFloat());setStroke(dp(1),Color.BLACK)}
 private fun redOutline():android.graphics.drawable.GradientDrawable=android.graphics.drawable.GradientDrawable().apply{setColor(Color.WHITE);setCornerRadius(dp(6).toFloat());setStroke(dp(1),Color.rgb(220,55,55))}
 private fun button(label:String,color:Int):Button=Button(this).apply{text=label;setTextColor(Color.BLACK);setAllCaps(false);setBackgroundTintList(android.content.res.ColorStateList.valueOf(color))}
 override fun onCreate(b:Bundle?){super.onCreate(b);window.statusBarColor=Color.rgb(244,246,248);window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);show();checkPermissions()}
 private fun show(){val scroll=object:ScrollView(this){override fun onOverScrolled(x:Int,y:Int,clampedX:Boolean,clampedY:Boolean){super.onOverScrolled(x,y,clampedX,clampedY);if(y==0 && clampedY)render()}};scroll.isFillViewport=true;root=LinearLayout(this).apply{orientation=1;setPadding(dp(16),dp(38),dp(16),dp(12));setBackgroundColor(Color.rgb(248,250,252))};scroll.addView(root);setContentView(scroll)
 root.addView(TextView(this).apply{text="Zamanlı Sesli Mesaj";textSize=23f;setTextColor(Color.BLACK);gravity=Gravity.CENTER;setTypeface(null,1);setPadding(0,0,0,dp(20))})
 val row=LinearLayout(this).apply{orientation=0};root.addView(row)
 date=EditText(this).apply{hint="Tarih: GG.AA.YYYY";setSingleLine(true);inputType=android.text.InputType.TYPE_CLASS_DATETIME or android.text.InputType.TYPE_DATETIME_VARIATION_DATE};time=EditText(this).apply{hint="Saat: SS:DD (isteğe bağlı)";setSingleLine(true);inputType=android.text.InputType.TYPE_CLASS_DATETIME or android.text.InputType.TYPE_DATETIME_VARIATION_TIME}
 date.background=blackOutline();time.background=blackOutline();date.setPadding(dp(8),0,dp(8),0);time.setPadding(dp(8),0,dp(8),0)
 row.addView(date,LinearLayout.LayoutParams(0,dp(55),1f));row.addView(time,LinearLayout.LayoutParams(0,dp(55),1f))
 mode=TextView(this).apply{text="Tekrarlı Alarm";textSize=15f;setTextColor(Color.DKGRAY);gravity=Gravity.CENTER;setPadding(0,dp(8),0,dp(8))};root.addView(mode)
 date.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,st:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,st:Int,before:Int,count:Int){mode.text=if(s.isNullOrBlank())"Tekrarlı Alarm" else "Birkez Alarm"};override fun afterTextChanged(s:android.text.Editable?) {}})
 val pick=LinearLayout(this);root.addView(pick);pick.addView(button("Tarih seç",0xffeeeeee.toInt()).apply{backgroundTintList=null;background=redOutline();setOnClickListener{val c=Calendar.getInstance();DatePickerDialog(this@MainActivity,{_,y,m,d->date.setText("%02d.%02d.%04d".format(d,m+1,y));dateChosen=true},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show()}},LinearLayout.LayoutParams(0,dp(45),1f));pick.addView(button("Tarihi kaldır",0xffeeeeee.toInt()).apply{backgroundTintList=null;background=blackOutline();setOnClickListener{date.text.clear()}},LinearLayout.LayoutParams(0,dp(45),1f));pick.addView(button("Saat seç",0xffeeeeee.toInt()).apply{backgroundTintList=null;background=redOutline();setOnClickListener{val c=Calendar.getInstance();TimePickerDialog(this@MainActivity,{_,h,m->time.setText("%02d:%02d".format(h,m));timeChosen=true},c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE),true).show()}},LinearLayout.LayoutParams(0,dp(45),1f))
 input=EditText(this).apply{hint="Sesli okunacak mesajı yazın";minLines=2;gravity=Gravity.TOP;setTextColor(Color.BLACK);setTypeface(null,android.graphics.Typeface.BOLD);inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE};root.addView(input,LinearLayout.LayoutParams(-1,-2))
 done=button("Tamam",0xffdddddd.toInt());done.visibility=View.GONE;root.addView(done,LinearLayout.LayoutParams(-1,dp(48)));input.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,a:Int,c:Int,f:Int){};override fun onTextChanged(s:CharSequence?,st:Int,b:Int,count:Int){done.visibility=if(s.isNullOrBlank())View.GONE else View.VISIBLE};override fun afterTextChanged(s:android.text.Editable?) {}})
 done.setOnClickListener{save()};root.addView(button("Yeni Ekle",0xffb9efb7.toInt()).apply{setOnClickListener{editing=null;date.text.clear();time.text.clear();input.text.clear();dateChosen=false;timeChosen=false;input.requestFocus();(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT)}},LinearLayout.LayoutParams(-1,dp(52)))
 list=LinearLayout(this).apply{orientation=1};root.addView(list);root.addView(Space(this),LinearLayout.LayoutParams(1,dp(28)));root.addView(TextView(this).apply{text="Muhammed Yusuf OLGUN";gravity=Gravity.CENTER;textSize=12f;setTextColor(0xffaaaaaa.toInt())},LinearLayout.LayoutParams(-1,dp(35)));render()}
 private fun save(){try{val dateRaw=date.text.toString().trim();val repeat=dateRaw.isEmpty();val d=if(repeat)Date() else fmt.parse(dateRaw)?:throw Exception("Tarih GG.AA.YYYY olmalı");val raw=time.text.toString().trim();if(raw.isEmpty())throw Exception("Saat seçin");val hm=raw;val t=clock.parse(hm)?:throw Exception();val cal=Calendar.getInstance().apply{time=d};val tc=Calendar.getInstance().apply{time=t};cal.set(Calendar.HOUR_OF_DAY,tc.get(Calendar.HOUR_OF_DAY));cal.set(Calendar.MINUTE,tc.get(Calendar.MINUTE));cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);if(!repeat && cal.timeInMillis<=System.currentTimeMillis())throw IllegalArgumentException("Gelecekte bir tarih ve saat girin")
 val items=Store.all(this).toMutableList();val id=editing?:((items.maxOfOrNull{it.id}?:0)+1);Alarms.cancel(this,id);items.removeAll{it.id==id};val r=Reminder(id,cal.timeInMillis,input.text.toString().trim(),repeat);items.add(r);Store.save(this,items);Alarms.plan(this,r);editing=null;date.text.clear();time.text.clear();input.text.clear();(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken,0);input.clearFocus();render();Toast.makeText(this,"Mesaj kaydedildi",Toast.LENGTH_SHORT).show()
 }catch(e:Exception){Toast.makeText(this,e.message?:"Tarih GG.AA.YYYY, saat SS:DD olmalı",Toast.LENGTH_LONG).show()}}
 private fun render(){
  list.removeAllViews()
  val now=System.currentTimeMillis()
  val f=SimpleDateFormat("dd.MM.yyyy HH:mm",Locale("tr","TR"))
  Store.all(this).filter{it.repeat||it.time>=now}.sortedWith(compareBy<Reminder>({Store.next(it,now)},{it.id})).forEach{r->
   val active=SpeechService.activeId(this)==r.id
   val panel=LinearLayout(this).apply{orientation=1;setPadding(dp(12),dp(12),dp(12),dp(12));background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.WHITE);setStroke(dp(1),if(active && (SystemClock.uptimeMillis()/500)%2L==0L)Color.RED else Color.TRANSPARENT)}}
   list.addView(panel,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)})
   val until=(Store.next(r,now)-now).coerceAtLeast(0)/1000
   val countdown="%02d Saat %02d Dakika %02d Saniye".format(until/3600,(until%3600)/60,until%60)
   val title=if(r.repeat)SimpleDateFormat("HH:mm",Locale("tr","TR")).format(r.time) else f.format(r.time)
   panel.addView(TextView(this).apply{text=title+"  "+(if(r.repeat)"Tekrarlı Alarm" else "Birkez Alarm")+"  "+countdown;textSize=14f;setTextColor(Color.BLACK);setTypeface(null,android.graphics.Typeface.BOLD)})
   panel.addView(TextView(this).apply{text=r.text;textSize=16f;setTextColor(Color.BLACK)})
   val actions=LinearLayout(this).apply{orientation=0};panel.addView(actions)
   actions.addView(button("Düzenle",0xffffef9b.toInt()).apply{setOnClickListener{editing=r.id;date.setText(if(r.repeat)"" else fmt.format(r.time));time.setText(clock.format(r.time));input.setText(r.text);input.requestFocus();(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT)}},LinearLayout.LayoutParams(0,dp(46),1f))
   actions.addView(button("Sil",0xffffb8b8.toInt()).apply{setOnClickListener{AlertDialog.Builder(this@MainActivity).setMessage("Mesaj silinsin mi?").setPositiveButton("Sil"){_,_->Alarms.cancel(this@MainActivity,r.id);Store.save(this@MainActivity,Store.all(this@MainActivity).filterNot{it.id==r.id});render()}.setNegativeButton("Vazgeç",null).show()}},LinearLayout.LayoutParams(0,dp(46),1f))
   actions.addView(button("Durdur",0xffe2e2e2.toInt()).apply{setOnClickListener{(getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator).vibrate(android.os.VibrationEffect.createOneShot(100,android.os.VibrationEffect.DEFAULT_AMPLITUDE));startService(Intent(this@MainActivity,SpeechService::class.java).setAction(SpeechService.STOP));render()}},LinearLayout.LayoutParams(0,dp(46),1f))
  }
 }
 override fun onResume(){super.onResume();refreshTask?.let{handler.removeCallbacks(it)};val tick=object:Runnable{override fun run(){render();handler.postDelayed(this,500)}};refreshTask=tick;handler.post(tick)}
 override fun onPause(){refreshTask?.let{handler.removeCallbacks(it)};refreshTask=null;super.onPause()}
 private fun checkPermissions(){if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),10)
 if(Build.VERSION.SDK_INT>=31 && !getSystemService(AlarmManager::class.java).canScheduleExactAlarms())AlertDialog.Builder(this).setMessage("Hatırlatmaların zamanında çalışması için kesin alarm iznini etkinleştirin.").setPositiveButton("Ayarlar"){_,_->startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))}.setNegativeButton("Sonra",null).show()
 val tts=TextToSpeech(this){status->if(status==TextToSpeech.SUCCESS){ /* device engine initializes asynchronously */ }}
 }
}
