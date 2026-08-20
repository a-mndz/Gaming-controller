package com.europad.app.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class EuroPadDiscovery(private val context: Context) {
    private val serviceType = "_europad._udp."
    private val nsdManager by lazy { context.getSystemService(Context.NSD_SERVICE) as NsdManager }
    private val wifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    private var multicastLock: WifiManager.MulticastLock? = null

    data class DiscoveredHost(val name: String, val host: String, val port: Int)

    private val _hosts = MutableStateFlow<Map<String, DiscoveredHost>>(emptyMap())
    val hosts: StateFlow<Map<String, DiscoveredHost>> = _hosts

    private var listener: NsdManager.DiscoveryListener? = null
    private val resolving = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun start() {
        if (listener != null) return
        listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) { }
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceType == serviceType) resolve(info)
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                _hosts.value = _hosts.value - info.serviceName
            }
            override fun onDiscoveryStopped(serviceType: String) { }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { }
        }
        try {
            // Some OEM Wi-Fi stacks drop multicast to save power, which starves NSD; the lock
            // keeps 224.0.0.251 flowing while the picker is open.
            multicastLock = wifiManager.createMulticastLock("europad-nsd").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) { }
        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) { }
    }

    fun stop() {
        try { multicastLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) { }
        multicastLock = null
        // A resolve whose callback never arrives would otherwise still be "in flight" next time the
        // picker opens, permanently hiding that host.
        resolving.clear()
        val l = listener ?: return
        listener = null
        try { nsdManager.stopServiceDiscovery(l) } catch (_: Exception) { }
    }

    /**
     * One resolve in flight *per service name*, not one globally.
     *
     * The old gate was a single counter reset by whichever callback came back first: NSD delivers
     * these on binder threads, so two hosts announcing together raced it, and any callback the
     * framework never delivered left the counter stuck above zero — discovery then ignored every
     * later onServiceFound for the life of the picker. Keying on the service name also stops a
     * multi-PC LAN from surfacing only whichever server answered first.
     */
    @Suppress("DEPRECATION") // resolveService/host: registerServiceInfoCallback is API 34, minSdk is 29
    private fun resolve(info: NsdServiceInfo) {
        val name = info.serviceName ?: return
        if (!resolving.add(name)) return
        nsdManager.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                resolving.remove(name)
            }
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                resolving.remove(name)
                val host = serviceInfo.host?.hostAddress ?: return
                _hosts.value = _hosts.value + (name to DiscoveredHost(name, host, serviceInfo.port))
            }
        })
    }
}
