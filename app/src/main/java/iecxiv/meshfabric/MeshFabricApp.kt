package iecxiv.meshfabric

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log

class MeshFabricApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        registerNetworkCallback()
    }

    private fun registerNetworkCallback() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                private var isFirst = true

                override fun onAvailable(network: Network) {
                    if (isFirst) {
                        isFirst = false
                        return
                    }
                    Log.d("MeshFabricApp", "Network changed/available, triggering reconnection checks...")
                    onNetworkChanged()
                }

                override fun onLost(network: Network) {
                    Log.d("MeshFabricApp", "Network lost")
                }
            })
        } catch (e: Exception) {
            Log.e("MeshFabricApp", "Failed to register network callback", e)
        }
    }

    companion object {
        lateinit var instance: MeshFabricApp
            private set

        var onNetworkChangedListener: (() -> Unit)? = null

        fun onNetworkChanged() {
            onNetworkChangedListener?.invoke()
        }
    }
}
