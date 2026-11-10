UI=r"""package iecxiv.meshfabric.ui.management
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
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
    private fun show(scale:Float=1f) {
        compose.setContent {
            val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale("es"))}
            val context=LocalContext.current.createConfigurationContext(config)
            val density=LocalDensity.current
            CompositionLocalProvider(LocalConfiguration provides config,LocalContext provides context,LocalDensity provides Density(density.density,scale)) {
                var selection by remember {mutableStateOf(ServerEventsQuery())}
                MaterialTheme {Box(Modifier.width(320.dp)) {ServerEventsControls(selection,true,{selection=it})}}
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
            assertEquals(1,results.size);assertFalse(results.single().hasVisualOverflow)
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
}
"""
from pathlib import Path
import subprocess
p=Path('app/src/main/java/iecxiv/meshfabric/ui/management/ServerEventsControls.kt')
assert subprocess.check_output(['git','hash-object',str(p)],text=True).strip()=='a34d0ba55a4c221b1eede26396ba8d828b28e776'
s=p.read_text()
def replace(old,new):
    global s
    assert s.count(old)==1,old
    s=s.replace(old,new,1)
replace('import androidx.compose.ui.text.style.TextOverflow','import androidx.compose.ui.text.style.TextOverflow\nimport androidx.compose.ui.text.AnnotatedString\nimport androidx.compose.ui.text.rememberTextMeasurer\nimport androidx.compose.ui.platform.LocalDensity')
replace('modifier=Modifier.fillMaxWidth().testTag(tag),shape=', 'modifier=Modifier.fillMaxWidth().testTag(tag),contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),shape=')
replace('@Composable internal fun ServerEventsControls','@OptIn(ExperimentalLayoutApi::class)\n@Composable internal fun ServerEventsControls')
replace('    BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {\n        val selectorWidth=minOf((maxWidth-8.dp)/2,280.dp)\n        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {\n            Box(Modifier.width(selectorWidth)) {', '    val measurer=rememberTextMeasurer()\n    val density=LocalDensity.current\n    val textStyle=MaterialTheme.typography.bodyMedium.copy(fontWeight=FontWeight.Medium)\n    val countLabels=serverEventLimitChoices.map {if(it==0) stringResource(R.string.log_event_unlimited) else stringResource(R.string.log_event_latest,it)}\n    fun textWidth(text:String)=with(density) {measurer.measure(AnnotatedString(text),style=textStyle,softWrap=false,maxLines=1).size.width.toDp()}\n    val typeIdeal=labels.maxOf {textWidth(it)}+74.dp\n    val countMinimum=countLabels.flatMap {it.split(" ")}.maxOf {textWidth(it)}+66.dp\n    BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {\n        val together=typeIdeal+8.dp+countMinimum<=maxWidth\n        val typeWidth=if(together) typeIdeal else maxWidth\n        val countWidth=if(together) maxWidth-typeWidth-8.dp-1.dp else maxWidth\n        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp),maxItemsInEachRow=2) {\n            Box(Modifier.width(typeWidth)) {')
replace('modifier=Modifier.width(selectorWidth).heightIn(max=280.dp).testTag("log-event-type-menu")','modifier=Modifier.width(typeWidth).heightIn(max=280.dp).testTag("log-event-type-menu")')
replace('            Box(Modifier.width(selectorWidth)) {','            Box(Modifier.width(countWidth)) {')
replace('modifier=Modifier.width(selectorWidth).heightIn(max=280.dp).testTag("log-event-count-menu")','modifier=Modifier.width(countWidth).heightIn(max=280.dp).testTag("log-event-count-menu")')
replace('Text(labels[type.ordinal],style=','Text(labels[type.ordinal],Modifier.testTag("log-event-type-text-"+type.wire.ifBlank {"all"}),style=')
assert s.count('maxLines=1,overflow=TextOverflow.Ellipsis')==3
s=s.replace('maxLines=1,overflow=TextOverflow.Ellipsis','softWrap=true,overflow=TextOverflow.Clip')
p.write_text(s)
t=Path('app/src/androidTest/java/iecxiv/meshfabric/ui/management/EventsSelectorWidthTest.kt');assert not t.exists();t.write_text(UI)
d=Path('docs/log.md');d.write_text(d.read_text()+'\nEvents selector sizing uses the widest localized type label measured with the rendered font, density and font scale, plus icon/padding space. Count uses remaining width and may wrap centered labels. Both popup widths equal their own anchors. At insufficient total width, controls reflow vertically; options wrap instead of ellipsizing. Selection does not change the type width.\n')
expected={str(p),str(t),str(d)}
subprocess.run(['git','add','--',*sorted(expected)],check=True)
assert set(subprocess.check_output(['git','diff','--cached','--name-only'],text=True).splitlines())==expected
