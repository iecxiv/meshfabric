package iecxiv.meshfabric.ui.desktop

import android.view.KeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent

internal fun Modifier.desktopKeyEvents(router: DesktopKeyboardRouter): Modifier =
    onFocusChanged { if (!it.isFocused) router.releaseAll() }.onPreviewKeyEvent {
        val event = it.nativeKeyEvent
        router.handle(event.action, event.keyCode, event.repeatCount,
            KeyEvent.normalizeMetaState(event.metaState), event.deviceId)
    }
