package iecxiv.meshfabric.ui.desktop

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.R

/** With one-shot right click on, a right click returns the mode button to left click after one use. */
internal fun desktopModeAfterClick(mode: MouseMode, oneShot: Boolean): MouseMode =
    if (oneShot && mode == MouseMode.RIGHT_CLICK) MouseMode.LEFT_CLICK else mode

/** Remembered switch for the one-shot behavior; it stays on until it is turned off by hand. */
internal class DesktopRightClickOnceState(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("desktop_right_click_assist", Context.MODE_PRIVATE)

    var enabled by mutableStateOf(runCatching { prefs.getBoolean(KEY, false) }.getOrDefault(false))
        private set

    init {
        if (prefs.contains("visible")) {
            prefs.edit().remove("visible").remove("portrait_x").remove("portrait_y")
                .remove("landscape_x").remove("landscape_y").apply()
        }
    }

    fun toggle() {
        enabled = !enabled
        prefs.edit().putBoolean(KEY, enabled).apply()
    }

    private companion object {
        const val KEY = "once_enabled"
    }
}

@Composable
internal fun rememberDesktopRightClickOnce(): DesktopRightClickOnceState {
    val context = LocalContext.current.applicationContext
    return remember(context) { DesktopRightClickOnceState(context) }
}

@Composable
internal fun DesktopRightClickOnceToggle(state: DesktopRightClickOnceState) {
    val on = state.enabled
    val label = stringResource(if (on) R.string.desktop_right_click_once_on else R.string.desktop_right_click_once_off)
    BadgedBox(badge = {
        if (on) Badge(
            modifier = Modifier.testTag("desktop-right-click-once-badge"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) { Text("1") }
    }) {
        IconButton(
            onClick = state::toggle,
            modifier = Modifier.size(32.dp).testTag("desktop-right-click-once-toggle").semantics { selected = on }
        ) {
            DesktopRightClickHeaderGlyph(on, label, iconSize = 20.dp)
        }
    }
}

@Composable
internal fun DesktopToolbarLayout(relativeY: Float, collapsed: Boolean, controls: @Composable () -> Unit) {
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    Layout(modifier = Modifier.fillMaxSize(), content = { Box { controls() } }) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val margin = 12.dp.roundToPx()
        val bar = measurables[0].measure(Constraints(0, (width - margin * 2).coerceAtLeast(0), 0, height))
        val (x, y) = desktopToolbarOrigin(width, height, bar.width, bar.height, relativeY, margin, collapsed, rightToLeft)
        layout(width, height) { bar.place(x, y) }
    }
}
