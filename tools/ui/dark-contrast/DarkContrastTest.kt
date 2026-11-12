package iecxiv.meshfabric.ui.theme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test
class DarkContrastTest {
    @Test fun lightSchemeIsUnchanged() {val s=lightColorScheme();assertSame(s,darkContrastScheme(s,false))}
    @Test fun darkButtonOutlineIsLighter() {assertEquals(Color(0xFF94A3B8),darkContrastScheme(darkColorScheme(),true).outline)}
    @Test fun darkCardOutlineIsLighter() {assertEquals(Color(0xFF7C899D),darkContrastScheme(darkColorScheme(),true).outlineVariant)}
    @Test fun otherThemeColorsAreUnchanged() {
        val s=darkColorScheme();val result=darkContrastScheme(s,true)
        assertEquals(s.primary,result.primary);assertEquals(s.surface,result.surface)
        assertEquals(s.surfaceContainerHighest,result.surfaceContainerHighest);assertEquals(s.onSurface,result.onSurface)
        assertEquals(s.error,result.error);assertEquals(s.background,result.background)
    }
    @Test fun lightActivityTextKeepsOriginalAlpha() {val c=Color.Red.copy(alpha=0.3f);assertEquals(c,readableActivityColor(c,Color.White,false))}
    @Test fun darkActivityTextUsesEventForeground() {assertEquals(Color.White,readableActivityColor(Color.Gray,Color.White,true))}
}
