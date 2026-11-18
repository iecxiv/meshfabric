package iecxiv.meshfabric.ui.desktop

import androidx.compose.runtime.*
import java.lang.ref.WeakReference
import java.util.WeakHashMap

internal class DesktopViewportState {
    val scale=mutableFloatStateOf(1f)
    val offsetX=mutableFloatStateOf(0f)
    val offsetY=mutableFloatStateOf(0f)
    val barRelativeY=mutableFloatStateOf(0f)
    private var connected=false
    private var transport:WeakReference<Any>?=null
    private var edge=0f

    private fun finishConnection() {
        edge=if(barRelativeY.floatValue>0.5f) 1f else 0f
        reset()
        connected=false
        transport=null
    }
    private fun reset() {
        scale.floatValue=1f
        offsetX.floatValue=0f
        offsetY.floatValue=0f
        barRelativeY.floatValue=edge
    }
    fun reconcile(isConnected:Boolean,socket:Any?) {
        if(!isConnected || socket==null) {
            if(connected) finishConnection()
            return
        }
        if(connected && transport?.get()!==socket) finishConnection()
        if(!connected) {
            reset()
            transport=WeakReference(socket)
            connected=true
        }
    }
}
private class DesktopViewportStore(val identity:List<Any?>) {
    val nodes=mutableMapOf<String,DesktopViewportState>()
}
private val desktopViewportStores=WeakHashMap<RemoteDesktopViewModel,DesktopViewportStore>()

@Composable internal fun rememberDesktopViewport(viewModel:RemoteDesktopViewModel,nodeId:String,connected:Boolean):DesktopViewportState {
    val user by viewModel.meshClient.userInfo.collectAsState()
    val identity=listOf(viewModel.meshClient.serverUrl,viewModel.meshClient.authCookie,viewModel.meshClient.ignoreSsl,user?.id)
    val key=nodeId.removePrefix("node//").removePrefix("node/").trim()
    val state=remember(viewModel,identity,key) {
        synchronized(desktopViewportStores) {
            val store=desktopViewportStores[viewModel]?.takeIf {it.identity==identity}
                ?: DesktopViewportStore(identity).also {desktopViewportStores[viewModel]=it}
            store.nodes.getOrPut(key) {DesktopViewportState()}
        }
    }
    state.reconcile(connected,viewModel.desktopClient.getSession(viewModel.meshClient,nodeId).webSocket)
    return state
}
