package iecxiv.meshfabric.ui.management
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale
class EventsSelectorWidthTest {
    @get:Rule val compose=createComposeRule()
    private fun show(scale:Float=1f,width:Int=320) {
        compose.setContent {
            val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale("es"))}
            val context=LocalContext.current.createConfigurationContext(config)
            val density=LocalDensity.current
            CompositionLocalProvider(LocalConfiguration provides config,LocalContext provides context,LocalDensity provides Density(density.density,scale)) {
                var selection by remember {mutableStateOf(ServerEventsQuery())}
                MaterialTheme {Box(Modifier.requiredWidth(width.dp)) {ServerEventsControls(selection,true,{selection=it})}}
            }
        }
    }
    private fun sameWidth(anchor:String,menu:String) {
        assertEquals(compose.onNodeWithTag(anchor).fetchSemanticsNode().boundsInRoot.width,compose.onNodeWithTag(menu).fetchSemanticsNode().boundsInRoot.width,1f)
    }
    private fun checkTypes() {
        compose.onNodeWithTag("log-event-type").performClick()
        sameWidth("log-event-type","log-event-type-menu")
        ServerEventType.entries.forEach {type->
            val key=type.wire.ifBlank {"all"}
            compose.onNodeWithTag("log-event-type-$key").performScrollTo()
            val results=mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("log-event-type-text-$key",useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(results)}
            assertEquals(1,results.size);assertFalse("type=$key widthOverflow=${results.single().didOverflowWidth} heightOverflow=${results.single().didOverflowHeight} size=${results.single().size}",results.single().hasVisualOverflow)
            for(line in 0 until results.single().lineCount) assertFalse(results.single().isLineEllipsized(line))
        }
    }
    @Test fun portraitSpanishOptionsHaveNoEllipsisAndMatchAnchor() {show();checkTypes()}
    @Test fun enlargedFontKeepsEveryTypeReadable() {show(1.8f);checkTypes()}
    @Test fun selectionDoesNotResizeTypeAndCountMenuMatchesItsHeader() {
        show();val before=compose.onNodeWithTag("log-event-type").fetchSemanticsNode().boundsInRoot.width
        compose.onNodeWithTag("log-event-type").performClick()
        compose.onNodeWithTag("log-event-type-"+ServerEventType.REMOVE.wire).performScrollTo().performClick()
        assertEquals(before,compose.onNodeWithTag("log-event-type").fetchSemanticsNode().boundsInRoot.width,1f)
        compose.onNodeWithTag("log-event-count").performClick();sameWidth("log-event-count","log-event-count-menu")
    }
    @Test fun portraitUsesTwoToOneSplit() {
        show()
        val type=compose.onNodeWithTag("log-event-type").fetchSemanticsNode().size.width
        val count=compose.onNodeWithTag("log-event-count").fetchSemanticsNode().size.width
        assertEquals(type.toFloat(),2f*count,2f)
    }
    @Test fun largeLayoutIsCappedAt560Dp() {
        show(width=800)
        val group=compose.onNodeWithTag("log-event-controls").fetchSemanticsNode().size.width
        val type=compose.onNodeWithTag("log-event-type").fetchSemanticsNode().size.width
        val count=compose.onNodeWithTag("log-event-count").fetchSemanticsNode().size.width
        val scale=compose.density.density
        assertEquals(560f*scale,group.toFloat(),1f)
        assertEquals(type.toFloat(),2f*count,2f)
    }
}
