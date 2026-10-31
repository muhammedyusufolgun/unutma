package com.olgun.unutma
import android.app.*
import android.content.Intent
import android.os.IBinder
import android.os.Build
import android.content.pm.ServiceInfo
class LanForegroundService:Service(){
 companion object { const val STOP="com.olgun.unutma.STOP_LAN" }

 override fun onBind(intent:Intent?):IBinder?=null
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  if(intent?.action==STOP){
   LanTransfer.stop()
   LanDiscovery.stop(applicationContext)
   stopSelf()
   return START_NOT_STICKY
  }
  val nm=getSystemService(NotificationManager::class.java)
  nm.createNotificationChannel(NotificationChannel("lan_service","Time-ikiz Wi-Fi",NotificationManager.IMPORTANCE_LOW))
  val stop=PendingIntent.getService(this,502,Intent(this,LanForegroundService::class.java).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val n=Notification.Builder(this,"lan_service").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Time-ikiz Wi-Fi alımı").setContentText("Yerel ağ bağlantısı açık").addAction(android.R.drawable.ic_media_pause,"Durdur",stop).setOngoing(true).build()
  if(Build.VERSION.SDK_INT>=29)startForeground(501,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) else startForeground(501,n)
  LanTransfer.start(applicationContext)
  LanDiscovery.advertise(applicationContext)
  LanDiscovery.discover(applicationContext)
  return START_STICKY
 }
 override fun onDestroy(){
  LanTransfer.stop()
  LanDiscovery.stop(applicationContext)
  super.onDestroy()
 }
}