package iecxiv.meshfabric.ui.desktop

import android.view.KeyEvent
import android.view.Window
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy

@RunWith(AndroidJUnit4::class)
class DesktopMouseWindowCallbackTest {
    private fun original(result: Boolean, calls: MutableList<String>, keyEvents: MutableList<KeyEvent>): Window.Callback =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Window.Callback::class.java)) { _, method, args ->
            calls.add(method.name)
            if (method.name == "dispatchKeyEvent") keyEvents.add(args!![0] as KeyEvent)
            when (method.returnType) {
                java.lang.Boolean.TYPE -> result
                java.lang.Integer.TYPE -> 0
                else -> null
            }
        } as Window.Callback

    @Test fun unconsumedKeyIsDelegatedExactlyOnceWithSameResult() {
        for (result in listOf(false, true)) {
            val calls = mutableListOf<String>()
            val events = mutableListOf<KeyEvent>()
            val delegate = original(result, calls, events)
            var canceled = 0
            val callback = DesktopMouseWindowCallback(delegate, { false }) { _, _ -> canceled++ }
            val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)
            assertEquals(result, callback.dispatchKeyEvent(event))
            assertEquals(listOf("dispatchKeyEvent"), calls)
            assertSame(event, events.single())
            assertEquals(0, canceled)
        }
    }
    @Test fun consumedDownCancelsPointerGestureAndDoesNotDelegate() {
        val calls = mutableListOf<String>()
        val delegate = original(false, calls, mutableListOf())
        val cancellations = mutableListOf<Pair<Long, Long>>()
        val callback = DesktopMouseWindowCallback(delegate, { true }) { down, time -> cancellations.add(down to time) }
        val event = KeyEvent(10L, 20L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0)
        assertTrue(callback.dispatchKeyEvent(event))
        assertEquals(listOf(10L to 20L), cancellations)
        assertTrue(calls.isEmpty())
    }
    @Test fun consumedUpDoesNotCancelAgain() {
        val calls = mutableListOf<String>()
        var canceled = 0
        val callback = DesktopMouseWindowCallback(original(false, calls, mutableListOf()), { true }) { _, _ -> canceled++ }
        assertTrue(callback.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)))
        assertEquals(0, canceled)
        assertTrue(calls.isEmpty())
    }
    @Test fun consumedRepeatDoesNotCancelAgain() {
        val calls = mutableListOf<String>()
        var canceled = 0
        val callback = DesktopMouseWindowCallback(original(false, calls, mutableListOf()), { true }) { _, _ -> canceled++ }
        assertTrue(callback.dispatchKeyEvent(KeyEvent(10L, 20L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 1)))
        assertEquals(0, canceled)
        assertTrue(calls.isEmpty())
    }
    @Test fun unrelatedWindowCallbacksAreDelegatedUnchanged() {
        val calls = mutableListOf<String>()
        var captured = 0
        val delegate = original(false, calls, mutableListOf())
        val callback = DesktopMouseWindowCallback(delegate, { captured++; false }) { _, _ -> fail("Unexpected cancel") }
        callback.onWindowFocusChanged(true)
        assertSame(delegate, callback.original)
        assertEquals(listOf("onWindowFocusChanged"), calls)
        assertEquals(0, captured)
    }
}
