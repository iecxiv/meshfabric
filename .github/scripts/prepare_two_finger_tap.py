#!/usr/bin/env python3
"""In Scroll mode a two-finger tap anywhere on the desktop canvas switches to left click."""
from pathlib import Path

MAIN = Path('app/src/main/java/iecxiv/meshfabric/ui/desktop')
TEST = Path('app/src/test/java/iecxiv/meshfabric/ui/desktop')
ANDROID_TEST = Path('app/src/androidTest/java/iecxiv/meshfabric/ui/desktop')
RES = Path('app/src/main/res')


def fail(message):
    raise SystemExit(message)


def rep(text, old, new):
    found = text.count(old)
    if found != 1:
        fail('anchor count %d (expected 1): %r' % (found, old[:90]))
    return text.replace(old, new)


screen_path = MAIN / 'RemoteDesktopScreen.kt'
screen = screen_path.read_text(encoding='utf-8')
screen = rep(screen,
    '.pointerInput(nodeId, selectedMode, desktopKeyboardActive, containerWidth, containerHeight, drawWidth, drawHeight) {',
    '.desktopTwoFingerTap(enabled = desktopKeyboardActive && selectedMode == MouseMode.SCROLL) {\n'
    '                                viewModel.releaseDesktopKeys(nodeId)\n'
    '                                lastClickMode = MouseMode.LEFT_CLICK\n'
    '                                selectedMode = MouseMode.LEFT_CLICK\n'
    '                            }\n'
    '                            .pointerInput(nodeId, selectedMode, desktopKeyboardActive, containerWidth, containerHeight, drawWidth, drawHeight) {')
screen_path.write_text(screen, encoding='utf-8')

replacements = [
    ('\u2022 Scroll mode: swipe vertically to scroll the remote desktop.\\n',
     '\u2022 Scroll mode: swipe vertically to scroll the remote desktop.\\n'
     '\u2022 In Scroll mode, tap with two fingers anywhere on the canvas, on the desktop image or on the black area, to leave Scroll mode and switch to left click.\\n'),
    ('\u2022 Modo Scroll: deslizar verticalmente para desplazar el contenido remoto.\\n',
     '\u2022 Modo Scroll: deslizar verticalmente para desplazar el contenido remoto.\\n'
     '\u2022 En modo Scroll, tocar con dos dedos en cualquier parte del lienzo, sobre la imagen del escritorio o la zona negra, desactiva el modo Scroll y activa el clic izquierdo.\\n'),
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
        fail('scroll mode text not found in %s' % gesture_file)
    gesture_file.write_text(text, encoding='utf-8')

(MAIN / 'DesktopTwoFingerTap.kt').write_text(r'''package iecxiv.meshfabric.ui.desktop

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.hypot

internal data class DesktopPointerSample(val id: Long, val x: Float, val y: Float)

/** Recognizes a short touch with exactly two fingers that do not move beyond the touch slop. */
internal class DesktopTwoFingerTap(private val slop: Float, private val timeoutMillis: Long) {
    private val origins = HashMap<Long, DesktopPointerSample>()
    private var startTime = 0L
    private var maxPointers = 0
    private var invalid = false

    fun reset() {
        origins.clear()
        startTime = 0L
        maxPointers = 0
        invalid = false
    }

    fun update(time: Long, pressed: List<DesktopPointerSample>) {
        if (pressed.isEmpty()) return
        if (origins.isEmpty()) startTime = time
        for (sample in pressed) {
            val origin = origins.getOrPut(sample.id) { sample }
            if (hypot(sample.x - origin.x, sample.y - origin.y) > slop) invalid = true
        }
        maxPointers = maxOf(maxPointers, pressed.size)
        if (maxPointers > 2 || time - startTime > timeoutMillis) invalid = true
    }

    fun isTap(endTime: Long): Boolean = !invalid && maxPointers == 2 && endTime - startTime <= timeoutMillis
}

private fun PointerInputChange.toSample() = DesktopPointerSample(id.value, position.x, position.y)

/** Observes touches without consuming them, so it works over the desktop image and over the black area alike. */
@Composable
internal fun Modifier.desktopTwoFingerTap(enabled: Boolean, onTap: () -> Unit): Modifier {
    val latest = rememberUpdatedState(onTap)
    return pointerInput(enabled) {
        if (!enabled) return@pointerInput
        val detector = DesktopTwoFingerTap(viewConfiguration.touchSlop, viewConfiguration.longPressTimeoutMillis)
        awaitEachGesture {
            detector.reset()
            val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (first.type == PointerType.Mouse) return@awaitEachGesture
            var last = first.uptimeMillis
            detector.update(last, listOf(first.toSample()))
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                last = event.changes.maxOf { it.uptimeMillis }
                detector.update(last, event.changes.filter { it.pressed }.map { it.toSample() })
            } while (event.changes.any { it.pressed })
            if (detector.isTap(last)) latest.value()
        }
    }
}
''', encoding='utf-8')

(TEST / 'DesktopTwoFingerTapTest.kt').write_text('''package iecxiv.meshfabric.ui.desktop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopTwoFingerTapTest {
    private fun p(id: Long, x: Float = 0f, y: Float = 0f) = DesktopPointerSample(id, x, y)
    private fun detector() = DesktopTwoFingerTap(slop = 10f, timeoutMillis = 500L)

    @Test fun twoFingersLiftedWithoutMovingAreATap() {
        val d = detector()
        d.update(0, listOf(p(1, 10f, 10f)))
        d.update(20, listOf(p(1, 10f, 10f), p(2, 100f, 100f)))
        d.update(120, listOf(p(1, 11f, 10f), p(2, 100f, 101f)))
        assertTrue(d.isTap(130))
    }

    @Test fun oneFingerIsNotATap() {
        val d = detector()
        d.update(0, listOf(p(1)))
        d.update(50, listOf(p(1)))
        assertFalse(d.isTap(60))
    }

    @Test fun movingBeyondTheSlopIsNotATap() {
        val d = detector()
        d.update(0, listOf(p(1, 10f, 10f)))
        d.update(20, listOf(p(1, 10f, 10f), p(2, 100f, 100f)))
        d.update(80, listOf(p(1, 10f, 10f), p(2, 140f, 100f)))
        assertFalse(d.isTap(100))
    }

    @Test fun threeFingersAreNotATap() {
        val d = detector()
        d.update(0, listOf(p(1), p(2, 50f, 0f), p(3, 100f, 0f)))
        assertFalse(d.isTap(50))
    }

    @Test fun aSlowTouchIsNotATap() {
        val d = detector()
        d.update(0, listOf(p(1)))
        d.update(20, listOf(p(1), p(2, 80f, 0f)))
        assertFalse(d.isTap(900))
    }

    @Test fun fingersOneAfterAnotherAreNotATap() {
        val d = detector()
        d.update(0, listOf(p(1)))
        d.update(100, listOf(p(2, 80f, 0f)))
        assertFalse(d.isTap(150))
    }

    @Test fun resetStartsOver() {
        val d = detector()
        d.update(0, listOf(p(1), p(2, 80f, 0f), p(3, 160f, 0f)))
        d.reset()
        d.update(1000, listOf(p(4)))
        d.update(1010, listOf(p(4), p(5, 80f, 0f)))
        assertTrue(d.isTap(1050))
    }
}
''', encoding='utf-8')

(ANDROID_TEST / 'DesktopTwoFingerTapModifierTest.kt').write_text('''package iecxiv.meshfabric.ui.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DesktopTwoFingerTapModifierTest {
    @get:Rule val compose = createComposeRule()
    private var taps = 0

    private fun show(enabled: Boolean = true) = compose.setContent {
        Box(Modifier.size(300.dp).testTag("canvas").desktopTwoFingerTap(enabled) { taps++ })
    }

    @Test fun twoFingerTapCallsBack() {
        show()
        compose.onNodeWithTag("canvas").performTouchInput {
            down(0, Offset(60f, 60f)); down(1, Offset(220f, 200f)); up(0); up(1)
        }
        compose.runOnIdle { assertEquals(1, taps) }
    }

    @Test fun oneFingerDoesNothing() {
        show()
        compose.onNodeWithTag("canvas").performTouchInput { down(0, Offset(60f, 60f)); up(0) }
        compose.runOnIdle { assertEquals(0, taps) }
    }

    @Test fun pinchingDoesNothing() {
        show()
        compose.onNodeWithTag("canvas").performTouchInput {
            down(0, Offset(60f, 60f)); down(1, Offset(220f, 200f))
            moveTo(1, Offset(380f, 200f))
            up(0); up(1)
        }
        compose.runOnIdle { assertEquals(0, taps) }
    }

    @Test fun disabledDoesNothing() {
        show(enabled = false)
        compose.onNodeWithTag("canvas").performTouchInput {
            down(0, Offset(60f, 60f)); down(1, Offset(220f, 200f)); up(0); up(1)
        }
        compose.runOnIdle { assertEquals(0, taps) }
    }
}
''', encoding='utf-8')
print('prepared OK')
