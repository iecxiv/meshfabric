package iecxiv.meshfabric.ui.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

@Composable
internal fun DesktopResumeRefreshEffect(sessionKey: String, owner: LifecycleOwner, onResume: () -> Unit) {
    val currentResume = rememberUpdatedState(onResume)
    DisposableEffect(sessionKey, owner) {
        var paused = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> paused = true
                Lifecycle.Event.ON_RESUME -> if (paused) {
                    paused = false
                    currentResume.value()
                }
                Lifecycle.Event.ON_DESTROY -> paused = false
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
