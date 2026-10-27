package iecxiv.meshfabric

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
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

    private fun scheduleNetworkChange() {
        // Android delivers callbacks serially; coalesce a handover's adjacent events.
        networkHandler.removeCallbacks(dispatchNetworkChange)
        networkHandler.postDelayed(dispatchNetworkChange,250L)
    }

    private fun registerNetworkCallback() {
        try {
            val cm=getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val tracker=DefaultNetworkTracker<Network>(cm.activeNetwork)
            cm.registerDefaultNetworkCallback(object:ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network:Network) {
                    if(tracker.available(network)) scheduleNetworkChange()
                }
                override fun onLost(network:Network) {
                    if(tracker.lost(network)) scheduleNetworkChange()
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
            onNetworkChangedListener?.invoke()
        }
    }
}
