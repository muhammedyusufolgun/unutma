package com.olgun.unutma

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log

/** Advertise this device on a private LAN. Discovery is advisory; pairing remains explicit. */
object LanDiscovery {
 private const val SERVICE_TYPE = "_timeikiz._tcp."
 private var registration: NsdManager.RegistrationListener? = null
 @Synchronized fun advertise(context:Context) {
  if(registration!=null)return
  val manager=context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
  val info=NsdServiceInfo().apply {
   serviceName="Time-ikiz"
   serviceType=SERVICE_TYPE
   port=39471
  }
  val listener=object:NsdManager.RegistrationListener {
   override fun onServiceRegistered(serviceInfo:NsdServiceInfo) { Log.i("TimeIkizNSD","LAN service advertised") }
   override fun onRegistrationFailed(serviceInfo:NsdServiceInfo,errorCode:Int) {
    Log.w("TimeIkizNSD","Registration failed: $errorCode")
    synchronized(this@LanDiscovery){registration=null}
   }
   override fun onServiceUnregistered(serviceInfo:NsdServiceInfo) {}
   override fun onUnregistrationFailed(serviceInfo:NsdServiceInfo,errorCode:Int) {}
  }
  registration=listener
  try{manager.registerService(info,NsdManager.PROTOCOL_DNS_SD,listener)}
  catch(e:Exception){registration=null;Log.w("TimeIkizNSD","Registration error",e)}
 }
}
