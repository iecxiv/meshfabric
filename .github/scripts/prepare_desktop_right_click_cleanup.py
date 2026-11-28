#!/usr/bin/env python3
"""Remove the dead floating-bubble code, fix gesture help, tint the badge and migrate the compose rule."""
import subprocess
import re
from pathlib import Path

MAIN = Path('app/src/main/java/iecxiv/meshfabric/ui/desktop')
TEST = Path('app/src/test/java/iecxiv/meshfabric/ui/desktop')
ANDROID_TEST = Path('app/src/androidTest/java/iecxiv/meshfabric/ui/desktop')
RES = Path('app/src/main/res')


def fail(message):
    raise SystemExit(message)


def blob(path):
    return subprocess.check_output(['git', 'hash-object', str(path)], text=True).strip()


def rep(text, old, new, count=1):
    found = text.count(old)
    if found != count:
        fail('anchor count %d (expected %d): %r' % (found, count, old[:90]))
    return text.replace(old, new)


expected = {
    MAIN / 'DesktopMouseCapture.kt': '6a37def83ed2105dcf5d5bd8bea03de027f77f29',
    MAIN / 'DesktopRightClickAssist.kt': 'a22ef18ef2a30fda9ee8061fa2e2b1b97c53de12',
    MAIN / 'DesktopRightClickAssistPolicy.kt': '4d50d2ee17d78d930256b6488ddf46d07c946682',
    TEST / 'DesktopRightClickAssistPolicyTest.kt': 'bbd710ea696ca276f59b7ec44c063466aff6e945',
}
for path, digest in expected.items():
    if blob(path) != digest:
        fail('%s changed; review required' % path)

# Mouse capture: drop the assist hooks.
capture_path = MAIN / 'DesktopMouseCapture.kt'
c = capture_path.read_text(encoding='utf-8')
c = rep(c, '    var assistControls: Rect? = null\n', '')
c = rep(c, '    var assist: DesktopRightClickAssistState? = null\n', '')
c = rep(c, 'localControls?.contains(position) == true || assistControls?.contains(position) == true) return null', 'localControls?.contains(position) == true) return null')
c = rep(c, '        if (assist?.mouse(event, this) == true) return true\n', '')
c = rep(c, '        assist?.reset()\n', '')
capture_path.write_text(c, encoding='utf-8')

# Screen: use the one-shot state and the plain toolbar layout.
screen_path = MAIN / 'RemoteDesktopScreen.kt'
s = screen_path.read_text(encoding='utf-8')
s = rep(s, '    val rightClickAssist = rememberDesktopRightClickAssist(nodeId)\n', '    val rightClickOnce = rememberDesktopRightClickOnce()\n')
s, removed = re.subn(r'^[ \t]*rightClickAssist\.latch\.cancel\(\)\n', '', s, flags=re.M)
if removed != 7:
    fail('expected 7 standalone latch.cancel() lines, found %d' % removed)
s = rep(s, 'moved = true; rightClickAssist.latch.cancel() }', 'moved = true }')
s = rep(s, ' && !rightClickAssist.latch.armed', '')
s = rep(s, '    DesktopRightClickAssistBinding(rightClickAssist, desktopMouseCapture, desktopKeyboardActive)\n', '')
s = rep(s, 'DesktopRightAssistLayout(rightClickAssist, desktopMouseCapture, barRelativeY, isControlsBarCollapsed, desktopKeyboardActive) {', 'DesktopToolbarLayout(barRelativeY, isControlsBarCollapsed) {')
s = rep(s, 'DesktopRightClickAssistToolbar(rightClickAssist)', 'DesktopRightClickOnceToggle(rightClickOnce)')
s = rep(s, 'rightClickAssist.latch.visible', 'rightClickOnce.enabled')
if 'rightClickAssist' in s:
    fail('rightClickAssist still referenced in the screen')
screen_path.write_text(s, encoding='utf-8')

# Delete the bubble sources, its policy test and its strings.
to_remove = [MAIN / 'DesktopRightClickAssist.kt', MAIN / 'DesktopRightClickAssistPolicy.kt', TEST / 'DesktopRightClickAssistPolicyTest.kt']
for string_file in sorted(RES.glob('values*/desktop_right_assist.xml')):
    names = re.findall(r'<string name="([^"]+)"', string_file.read_text(encoding='utf-8'))
    if not names or any(not name.startswith('desktop_right_assist_') for name in names):
        fail('%s holds other strings; review required' % string_file)
    to_remove.append(string_file)
subprocess.run(['git', 'rm', '-q', '--'] + [str(p) for p in to_remove], check=True)

# Gesture help: describe the badge button instead of the floating button.
replacements = [
    ('\u2022 Floating right-click button: arm it, then tap the desktop once for a right click.\\n',
     '\u2022 Right-click button with a badge: when it shows 1, switching to Right mode lasts for one click and then returns to Left mode.\\n'),
    ('\u2022 Bot\u00f3n flotante de clic derecho: activarlo y tocar una vez el escritorio para hacer un clic derecho.\\n',
     '\u2022 Bot\u00f3n de clic derecho con insignia: si muestra 1, el modo Derecho dura un solo clic y vuelve a Izquierdo.\\n'),
]
gesture_files = sorted(RES.glob('values*/desktop_gestures.xml'))
if not gesture_files:
    fail('no desktop_gestures.xml found')
for gesture_file in gesture_files:
    text = gesture_file.read_text(encoding='utf-8')
    for old, new in replacements:
        if old in text:
            text = text.replace(old, new)
            break
    else:
        fail('floating button text not found in %s' % gesture_file)
    gesture_file.write_text(text, encoding='utf-8')

# New strings for the toggle.
(RES / 'values' / 'desktop_right_click_once.xml').write_text('''<resources>
    <string name="desktop_right_click_once_on">One-shot right click is on. Tap to turn it off.</string>
    <string name="desktop_right_click_once_off">Turn on one-shot right click</string>
</resources>
''', encoding='utf-8')
(RES / 'values-es' / 'desktop_right_click_once.xml').write_text('''<resources>
    <string name="desktop_right_click_once_on">Clic derecho de un solo uso activado. Pulsa para desactivarlo.</string>
    <string name="desktop_right_click_once_off">Activar clic derecho de un solo uso</string>
</resources>
''', encoding='utf-8')

(MAIN / 'DesktopRightClickOnce.kt').write_text(r'''package iecxiv.meshfabric.ui.desktop

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
''', encoding='utf-8')

(TEST / 'DesktopRightClickOnceTest.kt').write_text('''package iecxiv.meshfabric.ui.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopRightClickOnceTest {
    @Test fun rightClickReturnsToLeftWhenOneShotIsOn() =
        assertEquals(MouseMode.LEFT_CLICK, desktopModeAfterClick(MouseMode.RIGHT_CLICK, true))

    @Test fun rightClickStaysWhenOneShotIsOff() =
        assertEquals(MouseMode.RIGHT_CLICK, desktopModeAfterClick(MouseMode.RIGHT_CLICK, false))

    @Test fun otherModesAreNeverChanged() {
        for (mode in MouseMode.entries.filter { it != MouseMode.RIGHT_CLICK }) {
            assertEquals(mode, desktopModeAfterClick(mode, true))
            assertEquals(mode, desktopModeAfterClick(mode, false))
        }
    }
}
''', encoding='utf-8')

(ANDROID_TEST / 'DesktopRightClickOnceToggleTest.kt').write_text('''package iecxiv.meshfabric.ui.desktop

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DesktopRightClickOnceToggleTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun cleanPreferences() {
        context.getSharedPreferences("desktop_right_click_assist", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun badgeFollowsTheToggleAndIsRemembered() {
        val state = DesktopRightClickOnceState(context)
        compose.setContent { MaterialTheme { DesktopRightClickOnceToggle(state) } }
        compose.onNodeWithTag("desktop-right-click-once-badge", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("desktop-right-click-once-toggle").performClick()
        compose.onNodeWithTag("desktop-right-click-once-badge", useUnmergedTree = true).assertExists()
        assertTrue(DesktopRightClickOnceState(context).enabled)
        compose.onNodeWithTag("desktop-right-click-once-toggle").performClick()
        compose.onNodeWithTag("desktop-right-click-once-badge", useUnmergedTree = true).assertDoesNotExist()
        assertFalse(DesktopRightClickOnceState(context).enabled)
    }
}
''', encoding='utf-8')

# Nothing may still refer to the removed bubble.
forbidden = ['DesktopRightClickAssist', 'DesktopRightAssistLayout', 'DesktopAssistBounds', 'desktopAssistPosition',
             'DesktopRightClickArm', 'rightClickAssist', 'assistControls', 'desktop_right_assist']
hits = subprocess.run(['grep', '-rIlE', '|'.join(forbidden), 'app/src'], capture_output=True, text=True).stdout.split()
if hits:
    fail('leftover references to the removed bubble: ' + ', '.join(hits))
print('prepared OK')
