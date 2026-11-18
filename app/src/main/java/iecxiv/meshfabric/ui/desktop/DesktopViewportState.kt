package iecxiv.meshfabric.ui.desktop

import androidx.compose.runtime.*
import java.lang.ref.WeakReference
import java.util.WeakHashMap

internal class DesktopViewportState {
    val scale=mutableFloatStateOf(1f)
    val offsetX=mutableFloatStateOf(0f)
    val offsetY=mutableFloatStateOf(0f)
    val barRelativeY=mutableFloatStateOf(0f)
    private var logicalSession:WeakReference<Any>?=null

    fun resetForNewSession() {
        val edge=if(barRelativeY.floatValue>0.5f) 1f else 0f
        scale.floatValue=1f
        offsetX.floatValue=0f
        offsetY.floatValue=0f
        barRelativeY.floatValue=edge
    }

    fun bindSession(session:Any) {
        if(logicalSession?.get()===session) return
        resetForNewSession()
        logicalSession=WeakReference(session)
    }
}
private class DesktopViewportStore(val identity:List<Any?>) {
    val nodes=mutableMapOf<String,DesktopViewportState>()
}
private val desktopViewportStores=WeakHashMap<RemoteDesktopViewModel,DesktopViewportStore>()

internal fun resetDesktopViewportForNewSession(viewModel:RemoteDesktopViewModel,nodeId:String) {
    val key=nodeId.removePrefix("node//").removePrefix("node/").trim()
    synchronized(desktopViewportStores) {
        desktopViewportStores[viewModel]?.nodes?.get(key)?.resetForNewSession()
    }
}

@Composable internal fun rememberDesktopViewport(viewModel:RemoteDesktopViewModel,nodeId:String,connected:Boolean):DesktopViewportState {
    val user by viewModel.meshClient.userInfo.collectAsState()
    val identity=listOf(viewModel.meshClient.serverUrl,viewModel.meshClient.authCookie,viewModel.meshClient.ignoreSsl,user?.id)
    val key=nodeId.removePrefix("node//").removePrefix("node/").trim()
    val state=remember(viewModel,identity,key,connected) {
        synchronized(desktopViewportStores) {
            val store=desktopViewportStores[viewModel]?.takeIf {it.identity==identity}
                ?: DesktopViewportStore(identity).also {desktopViewportStores[viewModel]=it}
            store.nodes.getOrPut(key) {DesktopViewportState()}
        }
    }
    state.bindSession(viewModel.desktopClient.getSession(viewModel.meshClient,nodeId))
    return state
}
