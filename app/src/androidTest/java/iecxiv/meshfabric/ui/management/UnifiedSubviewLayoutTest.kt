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
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.ui.devices.DeviceGeneralFrame
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
@Composable private fun UnifiedLayoutFixture(mode:Int,content:@Composable ()->Unit) {
    val title:@Composable ()->Unit={Text("Title")}
    val footer:@Composable ()->Unit={TextButton(onClick={},modifier=Modifier.testTag("unified_footer")) {Text("Back")}}
    when(mode) {
        0->ProfileUserDetailSurface(true,{},title=title,text=content,confirmButton=footer,dismissButton={})
        1->AccountOptionSurface(true,{},DialogProperties(),title,content,footer)
        2->CompositionLocalProvider(LocalServerReadInline provides true) {ServerReadSurface(onDismissRequest={},title=title,text=content,confirmButton=footer)}
        else->DeviceGeneralFrame(MeshNode(id="node//fixture",name="Title"),{},inline=true,tagPrefix="unified_child",body={content()},footer={footer()})
    }
}
class UnifiedSubviewLayoutTest {
    @get:Rule val rule=createComposeRule()
    @Test fun allInlineFamiliesCenterShortBodies() {
        val mode=mutableIntStateOf(0)
        rule.setContent {MaterialTheme {UnifiedLayoutFixture(mode.intValue) {Text("Short",Modifier.testTag("short"))}}}
        for(i in 0..3) {
            rule.runOnIdle {mode.intValue=i}
            val tag=if(i==3) "centered_body_viewport" else "shared_subview_body"
            val viewport=rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            val body=rule.onNodeWithTag("short").fetchSemanticsNode().boundsInRoot
            assertEquals(viewport.center.y,body.center.y,2f)
            rule.onNodeWithText("Title").assertIsDisplayed()
            rule.onNodeWithTag("unified_footer").assertIsDisplayed()
            rule.onNode(isDialog()).assertDoesNotExist()
        }
    }
    @Test fun longBodiesScrollWithoutMovingFooters() {
        val mode=mutableIntStateOf(0);val long=mutableStateOf(false)
        rule.setContent {MaterialTheme {UnifiedLayoutFixture(mode.intValue) {
            val modifier=if(mode.intValue==3) Modifier else Modifier.verticalScroll(rememberScrollState())
            Column(modifier.fillMaxWidth()) {repeat(if(long.value) 80 else 1) {Text("Line $it",Modifier.testTag("line_$it"))}}
        }}}
        for(i in 0..3) {
            rule.runOnIdle {mode.intValue=i;long.value=false}
            val top=rule.onNodeWithTag("unified_footer").fetchSemanticsNode().boundsInRoot.top
            rule.runOnIdle {long.value=true}
            rule.onNodeWithTag("line_79").performScrollTo().assertIsDisplayed()
            assertEquals(top,rule.onNodeWithTag("unified_footer").fetchSemanticsNode().boundsInRoot.top,2f)
        }
    }
    @Test fun multipleBodyNodesAreGroupedInsteadOfOverlapping() {
        rule.setContent {MaterialTheme {UnifiedLayoutFixture(0) {
            Text("First",Modifier.testTag("first"));Text("Second",Modifier.testTag("second"))
        }}}
        val a=rule.onNodeWithTag("first").fetchSemanticsNode().boundsInRoot
        val b=rule.onNodeWithTag("second").fetchSemanticsNode().boundsInRoot
        val viewport=rule.onNodeWithTag("shared_subview_body").fetchSemanticsNode().boundsInRoot
        assertTrue(b.top>=a.bottom)
        assertEquals(viewport.center.y,(a.top+b.bottom)/2f,2f)
    }
    @Test fun listsKeepTheirFullBoundedWorkArea() {
        rule.setContent {MaterialTheme {UnifiedLayoutFixture(0) {
            LazyColumn(Modifier.fillMaxSize().testTag("list")) {items((0..79).toList()) {Text("Item $it",Modifier.testTag("item_$it"))}}
        }}}
        rule.onNodeWithTag("item_0").assertIsDisplayed()
        rule.onNodeWithTag("list").performScrollToIndex(79)
        rule.onNodeWithTag("item_79").assertIsDisplayed()
        rule.onNodeWithTag("unified_footer").assertIsDisplayed()
    }
    @Test fun eventDetailsRetainTheirDialogAndCloseAction() {
        var closed=0
        val event=ServerEventRecord("manual","Event","user","node//fixture","Device",1800000000000L)
        rule.setContent {MaterialTheme {ServerEventDetailsDialog(event) {closed++}}}
        rule.onNode(isDialog()).assertExists()
        rule.onNodeWithTag("log-event-details-close").performClick()
        rule.runOnIdle {assertEquals(1,closed)}
    }
}
