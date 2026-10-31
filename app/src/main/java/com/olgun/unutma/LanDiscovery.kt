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
 private var discovery: NsdManager.DiscoveryListener? = null
 private val devices=java.util.concurrent.ConcurrentHashMap<String,String>()
 fun nearby():List<Pair<String,String>> = devices.entries.map{it.key to it.value}.sortedBy{it.first}
 @Synchronized fun discover(context:Context) {
  if(discovery!=null)return
  val manager=context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
  val listener=object:NsdManager.DiscoveryListener {
   override fun onDiscoveryStarted(type:String) {}
   override fun onDiscoveryStopped(type:String) {}
   override fun onStartDiscoveryFailed(type:String,errorCode:Int){Log.w("TimeIkizNSD","Discovery failed: $errorCode");discovery=null}
   override fun onStopDiscoveryFailed(type:String,errorCode:Int){discovery=null}
   override fun onServiceFound(info:NsdServiceInfo){
    if(!info.serviceType.startsWith("_timeikiz._tcp"))return
    try{manager.resolveService(info,object:NsdManager.ResolveListener{
     override fun onResolveFailed(serviceInfo:NsdServiceInfo,errorCode:Int){}
     override fun onServiceResolved(serviceInfo:NsdServiceInfo){
      val ip=serviceInfo.host?.hostAddress?:return
      if(serviceInfo.port==39471 && ip.contains("."))devices[serviceInfo.serviceName]=ip
     }
    })}catch(e:Exception){Log.w("TimeIkizNSD","Resolve failed",e)}
   }
   override fun onServiceLost(info:NsdServiceInfo){devices.remove(info.serviceName)}
  }
  discovery=listener
  try{manager.discoverServices(SERVICE_TYPE,NsdManager.PROTOCOL_DNS_SD,listener)}catch(e:Exception){discovery=null;Log.w("TimeIkizNSD","Discovery start failed",e)}
 }
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
