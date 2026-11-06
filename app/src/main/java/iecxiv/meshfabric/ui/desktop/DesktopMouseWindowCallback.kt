package iecxiv.meshfabric.ui.desktop

import android.view.KeyEvent
import android.view.Window

/** Wrap the public framework callback; do not override or call restricted AndroidX dispatch methods. */
internal class DesktopMouseWindowCallback(
    val original: Window.Callback,
    private val consume: (KeyEvent) -> Boolean,
    private val cancelGesture: (Long, Long) -> Unit
) : Window.Callback by original {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!consume(event)) return original.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0)
            cancelGesture(event.downTime, event.eventTime)
        return true
    }
}
