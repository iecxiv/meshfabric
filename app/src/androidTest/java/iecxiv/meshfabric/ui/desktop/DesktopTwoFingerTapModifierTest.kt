package iecxiv.meshfabric.ui.desktop

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
