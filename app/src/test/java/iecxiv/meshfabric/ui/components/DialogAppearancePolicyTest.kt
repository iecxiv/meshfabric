package iecxiv.meshfabric.ui.components

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class DialogAppearancePolicyTest {
    @Test fun outlineAndCornersAreShared() {
        assertEquals(1.dp,AppDialogAppearance.borderWidth)
        assertEquals(16.dp,AppDialogAppearance.cornerRadius)
    }
    @Test fun noAdditionalTonalOverlayIsApplied() { assertEquals(0.dp,AppDialogAppearance.tonalElevation) }
    @Test fun lightThemeUsesSharedBackground() {
        val scheme=lightColorScheme()
        assertEquals(scheme.surfaceColorAtElevation(6.dp),appDialogBackground(scheme))
    }
    @Test fun darkThemeUsesSharedBackground() {
        val scheme=darkColorScheme()
        assertEquals(scheme.surfaceColorAtElevation(6.dp),appDialogBackground(scheme))
    }
}
