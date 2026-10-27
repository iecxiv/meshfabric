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
        Log.d("MeshNetwork","SCHEDULE reason=$reason")
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
                    Log.d("MeshNetwork","AVAILABLE network=${network.hashCode()} changed=$changed")
                    if(changed) scheduleNetworkChange("available")
                }
                override fun onLost(network:Network) {
                    val changed=tracker.lost(network)
                    Log.d("MeshNetwork","LOST network=${network.hashCode()} changed=$changed")
                    if(changed) scheduleNetworkChange("lost")
                }
                override fun onCapabilitiesChanged(network:Network,networkCapabilities:NetworkCapabilities) {
                    val transports=transportSignature(networkCapabilities)
                    val changed=tracker.capabilitiesChanged(network,transports)
                    // Do not log capabilities themselves: they may contain identifying data.
                    Log.d("MeshNetwork","CAPABILITIES network=${network.hashCode()} transports=${transports.sorted()} changed=$changed")
                    if(changed) scheduleNetworkChange("transports")
                }
            })
        } catch(e:Exception) {
            Log.e("MeshFabricApp","Failed to register default network callback",e)
        }
    }

    companion object {
        lateinit var instance:MeshFabricApp
            private set
        var onNetworkChangedListener:(()->Unit)?=null
        fun onNetworkChanged() {
            val listener=onNetworkChangedListener
            Log.d("MeshNetwork","DISPATCH listenerAttached=${listener!=null}")
            listener?.invoke()
        }
    }
}
