package iecxiv.meshfabric.ui.management
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
@RunWith(AndroidJUnit4::class) class UserGroupsSectionSpacingTest {
    @get:Rule val compose=createComposeRule()
    private class Fixture:AutoCloseable {
        val client=MeshCentralClient();val session=ProfileSession(client)
        override fun close() {session.close();val f=client.javaClass.getDeclaredField("scope").apply {isAccessible=true};(f.get(client) as CoroutineScope).cancel()}
    }
    @Test fun actionHeadingGapMatchesUsersColumnLayout() {Fixture().use {f->
        compose.setContent {MaterialTheme {Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f).testTag("actual-groups")) {UserGroupsDirectoryPane(f.session,ProfileState(),onOpenUser={},onOpenDeviceGroup={})}
            Column(Modifier.weight(1f).padding(16.dp).testTag("users-reference"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                ServerGeneralActions {Column(Modifier.testTag("reference-action-controls")) {TextButton(onClick={}) {Text("Refresh reference")};TextButton(onClick={},enabled=false) {Text("Create reference")}}}
            }
        }}}
        val heading=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(iecxiv.meshfabric.R.string.my_server_actions)
        val actualHeading=compose.onNode(hasText(heading) and hasAnyAncestor(hasTestTag("actual-groups"))).fetchSemanticsNode().boundsInRoot
        val referenceHeading=compose.onNode(hasText(heading) and hasAnyAncestor(hasTestTag("users-reference"))).fetchSemanticsNode().boundsInRoot
        val actualControls=compose.onNodeWithTag("user-groups-server-actions").fetchSemanticsNode().boundsInRoot
        val referenceControls=compose.onNodeWithTag("reference-action-controls").fetchSemanticsNode().boundsInRoot
        val actualGap=actualControls.top-actualHeading.bottom
        val referenceGap=referenceControls.top-referenceHeading.bottom
        assertTrue("Action heading must precede controls",actualGap>=0f)
        assertTrue("Group spacing must match the Users reference",kotlin.math.abs(actualGap-referenceGap)<=1f)
    }}
    @Test fun resultsHeadingPrecedesGroupsAndIsAbsentWhenNoGroupsExist() {Fixture().use {f->
        val state=androidx.compose.runtime.mutableStateOf(ProfileState(listedUserGroups=listOf(JSONObject().put("_id","ugrp//one").put("name","Oficina")),userGroupPhase="ready"))
        compose.setContent {
            val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale("es"))}
            CompositionLocalProvider(LocalConfiguration provides config) {MaterialTheme {UserGroupsDirectoryPane(f.session,state.value,onOpenUser={},onOpenDeviceGroup={})}}
        }
        compose.onNodeWithTag("user-groups-list-heading").performScrollTo().assertTextEquals("Grupos")
        val title=compose.onNodeWithTag("user-groups-list-heading").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Oficina").performScrollTo().assertExists()
        compose.runOnIdle {state.value=state.value.copy(listedUserGroups=emptyList())}
        compose.onNodeWithTag("user-groups-list-heading").assertDoesNotExist()
        assertTrue(title.height>0f)
    }}
}
