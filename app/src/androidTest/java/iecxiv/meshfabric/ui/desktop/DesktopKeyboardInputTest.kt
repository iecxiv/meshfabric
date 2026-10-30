package iecxiv.meshfabric.ui.desktop

import android.view.InputDevice
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DesktopKeyboardInputTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val events = mutableListOf<Pair<Int, Boolean>>()
    private lateinit var router: DesktopKeyboardRouter
    private fun install() {
        router = DesktopKeyboardRouter { key, down -> events.add(key to down) }.apply { setActive(true) }
        compose.setContent {
            val focus = remember { FocusRequester() }
            var text by remember { mutableStateOf("") }
            BasicTextField(value=text,onValueChange={ text=it },
                modifier=Modifier.size(1.dp).desktopKeyEvents(router).focusRequester(focus),
                keyboardOptions=KeyboardOptions(showKeyboardOnFocus=false))
            LaunchedEffect(Unit) { focus.requestFocus() }
        }
        compose.waitForIdle()
    }
    private fun send(action: Int, code: Int, meta: Int=0, soft: Boolean=false) {
        compose.runOnIdle {
            val now=android.os.SystemClock.uptimeMillis()
            val event=KeyEvent(now,now,action,code,0,meta,if(soft) -1 else 1,0,
                if(soft) KeyEvent.FLAG_SOFT_KEYBOARD else 0,InputDevice.SOURCE_KEYBOARD)
            assertTrue(compose.activity.dispatchKeyEvent(event))
        }
    }
    @Test fun softKeyboardArrowEventsReachRemoteRouter() {
        install();send(0,KeyEvent.KEYCODE_DPAD_UP,soft=true);send(1,KeyEvent.KEYCODE_DPAD_UP,soft=true)
        compose.runOnIdle { assertEquals(listOf(38 to true,38 to false),events) }
    }
    @Test fun hardwareCtrlArrowAndFunctionKeysReachRouter() {
        install();send(0,KeyEvent.KEYCODE_CTRL_LEFT);send(0,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.META_CTRL_ON)
        send(1,KeyEvent.KEYCODE_DPAD_RIGHT);send(1,KeyEvent.KEYCODE_CTRL_LEFT)
        send(0,KeyEvent.KEYCODE_F12);send(1,KeyEvent.KEYCODE_F12)
        compose.runOnIdle { assertEquals(listOf(17 to true,39 to true,39 to false,17 to false,123 to true,123 to false),events) }
    }
    @Test fun disablingInputReleasesHeldKeys() {
        install();send(0,KeyEvent.KEYCODE_ALT_LEFT);send(0,KeyEvent.KEYCODE_TAB,KeyEvent.META_ALT_ON)
        compose.runOnIdle {
            router.setActive(false)
            assertEquals(listOf(18 to true,9 to true,9 to false,18 to false),events)
        }
    }
}
