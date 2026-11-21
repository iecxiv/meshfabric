package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.window.DialogProperties
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
@Composable private fun SharedLayoutExample(mode:Int,body:@Composable ()->Unit) {
    val title:@Composable ()->Unit={Text("Title")}
    val footer:@Composable ()->Unit={TextButton(onClick={}) {Text("Back",Modifier.testTag("footer"))}}
    when(mode) {
        0->ProfileUserDetailSurface(inline=true,onDismissRequest={},title=title,text=body,confirmButton=footer,dismissButton={})
        1->AccountOptionSurface(inline=true,onDismissRequest={},properties=DialogProperties(),title=title,text=body,confirmButton=footer)
        else->CompositionLocalProvider(LocalServerReadInline provides true) {ServerReadSurface(onDismissRequest={},title=title,text=body,confirmButton=footer)}
    }
}
class SharedSubviewBodyLayoutTest {
    @get:Rule val rule=createComposeRule()
    @Test fun shortContentIsCenteredInAllThreeSurfaces() {
        val mode=mutableIntStateOf(0)
        rule.setContent {MaterialTheme {SharedLayoutExample(mode.intValue) {Text("Short",Modifier.testTag("body"))}}}
        for(i in 0..2) {
            rule.runOnIdle {mode.intValue=i}
            val viewport=rule.onNodeWithTag("shared_subview_body").fetchSemanticsNode().boundsInRoot
            val body=rule.onNodeWithTag("body").fetchSemanticsNode().boundsInRoot
            assertEquals(viewport.center.y,body.center.y,2f)
            rule.onNodeWithTag("footer").assertIsDisplayed()
            rule.onNode(isDialog()).assertDoesNotExist()
        }
    }
    @Test fun existingScrollWorksWithoutMovingFooters() {
        val mode=mutableIntStateOf(0);val long=mutableStateOf(false)
        rule.setContent {MaterialTheme {SharedLayoutExample(mode.intValue) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {repeat(if(long.value) 80 else 1) {Text("Line $it",Modifier.testTag("line_$it"))}}
        }}}
        for(i in 0..2) {
            rule.runOnIdle {mode.intValue=i;long.value=false}
            val top=rule.onNodeWithTag("footer").fetchSemanticsNode().boundsInRoot.top
            rule.runOnIdle {long.value=true}
            rule.onNodeWithTag("line_79").performScrollTo().assertIsDisplayed()
            assertEquals(top,rule.onNodeWithTag("footer").fetchSemanticsNode().boundsInRoot.top,2f)
            rule.onNodeWithTag("footer").assertIsDisplayed()
        }
    }
    @Test fun lazyListsKeepFiniteFullSizeViewport() {
        rule.setContent {MaterialTheme {SharedLayoutExample(0) {
            LazyColumn(Modifier.fillMaxSize().testTag("list")) {items((0..79).toList()) {Text("Item $it",Modifier.testTag("item_$it"))}}
        }}}
        rule.onNodeWithTag("item_0").assertIsDisplayed()
        rule.onNodeWithTag("list").performScrollToIndex(79)
        rule.onNodeWithTag("item_79").assertIsDisplayed()
        rule.onNodeWithTag("footer").assertIsDisplayed()
    }
}
