package iecxiv.meshfabric

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import iecxiv.meshfabric.data.remote.DefaultNetworkTracker

class MeshFabricApp : Application() {
    private val networkHandler=Handler(Looper.getMainLooper())
    private val dispatchNetworkChange=Runnable { onNetworkChanged() }

    override fun onCreate() {
        super.onCreate()
        instance=this
        registerNetworkCallback()
    }

    private fun scheduleNetworkChange(reason:String) {
        // Android delivers callbacks serially; coalesce a handover's adjacent events.
        iecxiv.meshfabric.diagnostics.AppDiagnostics.trace(3, "MeshNetwork", "MeshFabricApp.kt:25 SCHEDULE reason=") { "SCHEDULE reason=$reason" }
        networkHandler.removeCallbacks(dispatchNetworkChange)
        networkHandler.postDelayed(dispatchNetworkChange,250L)
    }

    private fun transportSignature(capabilities:NetworkCapabilities):Set<Int> {
        val supported=intArrayOf(
            NetworkCapabilities.TRANSPORT_CELLULAR,
            NetworkCapabilities.TRANSPORT_WIFI,
            NetworkCapabilities.TRANSPORT_BLUETOOTH,
            NetworkCapabilities.TRANSPORT_ETHERNET,
            NetworkCapabilities.TRANSPORT_VPN
        )
        return supported.filter { capabilities.hasTransport(it) }.toSet()
    }

    private fun registerNetworkCallback() {
        try {
            val cm=getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            // Seed outside callbacks; subsequent state comes only from callback arguments.
            val initialNetwork=cm.activeNetwork
            val initialTransports=initialNetwork?.let { cm.getNetworkCapabilities(it) }
                ?.let { transportSignature(it) }
            val tracker=DefaultNetworkTracker<Network>(initialNetwork,initialTransports)
            cm.registerDefaultNetworkCallback(object:ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network:Network) {
                    val changed=tracker.available(network)
                    iecxiv.meshfabric.diagnostics.AppDiagnostics.trace(3, "MeshNetwork", "MeshFabricApp.kt:52 AVAILABLE network=") { "AVAILABLE network=${network.hashCode()} changed=$changed" }
                    if(changed) scheduleNetworkChange("available")
                }
                override fun onLost(network:Network) {
                    val changed=tracker.lost(network)
                    iecxiv.meshfabric.diagnostics.AppDiagnostics.trace(3, "MeshNetwork", "MeshFabricApp.kt:57 LOST network=") { "LOST network=${network.hashCode()} changed=$changed" }
                    if(changed) scheduleNetworkChange("lost")
                }
                override fun onCapabilitiesChanged(network:Network,networkCapabilities:NetworkCapabilities) {
                    val transports=transportSignature(networkCapabilities)
                    val changed=tracker.capabilitiesChanged(network,transports)
                    // Do not log capabilities themselves: they may contain identifying data.
                    iecxiv.meshfabric.diagnostics.AppDiagnostics.trace(3, "MeshNetwork", "MeshFabricApp.kt:64 CAPABILITIES network=") { "CAPABILITIES network=${network.hashCode()} transports=${transports.sorted()} changed=$changed" }
                    if(changed) scheduleNetworkChange("transports")
                }
            })
        } catch(e:Exception) {
            iecxiv.meshfabric.diagnostics.AppDiagnostics.fault("MeshFabricApp", "MeshFabricApp.kt:69 Failed to register default network callback", e)
        }
    }

    companion object {
        lateinit var instance:MeshFabricApp
            private set
        var onNetworkChangedListener:(()->Unit)?=null
        fun onNetworkChanged() {
            val listener=onNetworkChangedListener
            iecxiv.meshfabric.diagnostics.AppDiagnostics.trace(3, "MeshNetwork", "MeshFabricApp.kt:79 DISPATCH listenerAttached=") { "DISPATCH listenerAttached=${listener!=null}" }
            listener?.invoke()
        }
    }
}
