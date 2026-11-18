package iecxiv.meshfabric.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private fun Context.statusBarActivity():Activity? {
    var current=this
    while(current is ContextWrapper) {
        if(current is Activity) return current
        val next=current.baseContext
        if(next===current) break
        current=next
    }
    return current as? Activity
}

@Composable internal fun AppStatusBarContrast(background:Color) {
    val view=LocalView.current
    val activity=LocalContext.current.statusBarActivity()
    val owner=LocalLifecycleOwner.current
    val darkIcons=rememberUpdatedState(statusBarUsesDarkIcons(background.luminance()))
    if(view.isInEditMode || activity==null) return
    val window=activity.window
    SideEffect {WindowCompat.getInsetsController(window,view).isAppearanceLightStatusBars=darkIcons.value}
    DisposableEffect(window,view,owner) {
        fun applyContrast() {WindowCompat.getInsetsController(window,view).isAppearanceLightStatusBars=darkIcons.value}
        val lifecycleObserver=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_RESUME) applyContrast()}
        val focusObserver=ViewTreeObserver.OnWindowFocusChangeListener {focused->if(focused) applyContrast()}
        owner.lifecycle.addObserver(lifecycleObserver)
        val tree=view.viewTreeObserver
        tree.addOnWindowFocusChangeListener(focusObserver)
        applyContrast()
        onDispose {
            owner.lifecycle.removeObserver(lifecycleObserver)
            if(tree.isAlive) tree.removeOnWindowFocusChangeListener(focusObserver)
        }
    }
}
