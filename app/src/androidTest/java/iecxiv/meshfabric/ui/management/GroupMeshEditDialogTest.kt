package iecxiv.meshfabric.ui.management
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupMeshEditDialogTest {
    @get:Rule val compose=createComposeRule()
    private val actor="user//admin";private val gid="ugrp//group";private val mid="mesh//group"
    private fun state(rights:Long=0x80000008L)=ProfileState(ready=true,user=JSONObject().put("_id",actor).put("siteadmin",256L),listedUserGroups=listOf(JSONObject().put("_id",gid).put("links",JSONObject().put(mid,JSONObject().put("rights",rights)))),groups=listOf(JSONObject().put("_id",mid).put("links",JSONObject().put(gid,JSONObject().put("rights",rights)).put(actor,JSONObject().put("rights",0xffffffffL)))),userGroupPhase="ready")
    private fun field(target:Any,name:String)=target.javaClass.getDeclaredField(name).apply {isAccessible=true}
    private inner class Fixture(initial:ProfileState=state()):AutoCloseable {
        val client=MeshCentralClient();val session=ProfileSession(client)
        val current=mutableStateOf(initial)
        init {
            field(client,"serverUrl").set(client,"https://example.invalid/");field(client,"authCookie").set(client,"offline-ui-test")
            @Suppress("UNCHECKED_CAST")
            val connected=field(client,"_connectionState").get(client) as MutableStateFlow<Boolean>
            connected.value=true
            field(session,"binding").set(session,Triple(client.serverUrl,client.authCookie,false))
            compose.setContent {MaterialTheme {GroupMeshEditDialog(session,current.value,gid,mid) {}}}
        }
        override fun close() {assertNull(field(client,"webSocket").get(client));assertNull(field(session,"socket").get(session));session.close();(field(client,"scope").get(client) as CoroutineScope).cancel()}
    }
    @Test fun preparingDoesNotConfirmOrSend() {Fixture().use {
        compose.onNodeWithTag("group-edit-send").assertDoesNotExist()
        compose.onNodeWithTag("group-edit-prepare").assertIsNotEnabled()
        chooseEdit()
        compose.onNodeWithTag("group-edit-prepare").assertIsEnabled().performClick()
        compose.onNodeWithTag("group-edit-send").assertIsNotEnabled()
        compose.onNodeWithTag("group-edit-consent").performScrollTo().performClick()
        compose.onNodeWithTag("group-edit-send").assertIsEnabled()
    }}
    @Test fun changedBilateralRightsInvalidateFrozenConfirmation() {Fixture().use {f->
        chooseEdit()
        compose.onNodeWithTag("group-edit-prepare").performClick()
        compose.onNodeWithTag("group-edit-consent").performScrollTo().performClick()
        compose.onNodeWithTag("group-edit-send").assertIsEnabled()
        compose.runOnIdle {f.current.value=state(16)}
        compose.onNodeWithTag("group-edit-send").assertIsNotEnabled()
    }}
    @Test fun loadingReadFailureOrLostRoleBlocksPreparation() {Fixture().use {f->
        chooseEdit()
        for(s in listOf(state().copy(loading=true),state().copy(readError=true),state().copy(busy=true),state().copy(detailsBusy=true),state().copy(user=JSONObject().put("_id",actor).put("siteadmin",0)))) {
            compose.runOnIdle {f.current.value=s}
            compose.onNodeWithTag("group-edit-prepare").assertIsNotEnabled()
        }
    }}
    private fun chooseEdit() {compose.onNodeWithTag("group-edit-hex").performScrollTo().performTextReplacement("10")}
    @Test fun unknownBitsRemainInBothDisplayedMasks() {Fixture().use {
        chooseEdit()
        compose.onNodeWithTag("group-edit-before").assertTextEquals("0x80000008")
        compose.onNodeWithTag("group-edit-after").assertTextEquals("0x80000010")
        compose.onNodeWithTag("group-edit-prepare").performClick()
        compose.onNodeWithTag("group-edit-frozen").assertTextContains("0x80000010",substring=true)
    }}
    @Test fun invalidMasksAndNoOpCannotPrepare() {Fixture().use {
        for(value in listOf("", "8", "FFFFFFFF", "80000010", "1", "2", "4", "100000000")) {
            compose.onNodeWithTag("group-edit-hex").performScrollTo().performTextReplacement(value)
            compose.onNodeWithTag("group-edit-prepare").assertIsNotEnabled()
        }
    }}
    @Test fun zeroFinalMaskRequiresRevocation() {Fixture(state(8L)).use {
        compose.onNodeWithTag("group-edit-hex").performScrollTo().performTextReplacement("0")
        compose.onNodeWithTag("group-edit-prepare").assertIsNotEnabled()
    }}
    @Test fun fullAdministratorAssignmentCannotEdit() {
        val initial=state(0xffffffffL)
        Fixture(initial).use {compose.onNodeWithTag("group-edit-prepare").assertIsNotEnabled()}
    }
    @Test fun oneSidedLinkCannotEdit() {
        val initial=state();initial.listedUserGroups!!.single().getJSONObject("links").remove(mid)
        Fixture(initial).use {
            chooseEdit();compose.onNodeWithTag("group-edit-prepare").assertIsNotEnabled()
        }
    }
    @Test fun invalidatedReviewDoesNotReactivateIfDataReturns() {Fixture().use {f->
        chooseEdit();compose.onNodeWithTag("group-edit-prepare").performClick()
        compose.onNodeWithTag("group-edit-consent").performScrollTo().performClick()
        compose.runOnIdle {f.current.value=state(16L)}
        compose.onNodeWithTag("group-edit-send").assertIsNotEnabled()
        compose.runOnIdle {f.current.value=state()}
        compose.onNodeWithTag("group-edit-consent").assertIsNotEnabled()
        compose.onNodeWithTag("group-edit-send").assertIsNotEnabled()
    }}
    @Test fun directoryFailureMissingRecordsAndWrongRoleBlockPreparation() {Fixture().use {f->
        chooseEdit()
        for(s in listOf(state().copy(userGroupPhase="loading"),state().copy(userGroupPhase="failed"),state().copy(listedUserGroups=null),state().copy(listedUserGroups=emptyList()),state().copy(groups=emptyList()),state().copy(user=JSONObject().put("_id",actor).put("siteadmin",2)))) {
            compose.runOnIdle {f.current.value=s}
            compose.onNodeWithTag("group-edit-prepare").assertIsNotEnabled()
        }
    }}
    @Test fun administrativeBitsArePreservedInTheProposedMask() {Fixture(state(0x8000000aL)).use {
        chooseEdit()
        compose.onNodeWithTag("group-edit-before").assertTextEquals("0x8000000A")
        compose.onNodeWithTag("group-edit-after").assertTextEquals("0x80000012")
    }}
    @Test fun missingBothAssignmentLinksCannotBecomeAnInitialGrant() {
        val initial=state()
        initial.listedUserGroups!!.single().getJSONObject("links").remove(mid)
        initial.groups.single().getJSONObject("links").remove(gid)
        Fixture(initial).use {chooseEdit();compose.onNodeWithTag("group-edit-prepare").assertIsNotEnabled()}
    }
    @Test fun checkboxChangesOnlyTheEditablePortion() {Fixture().use {
        compose.onNode(hasText("Seleccionar permisos y restricciones") or hasText("Select rights and restrictions")).performScrollTo().performClick()
        compose.onNodeWithTag("group-edit-bit-16").performScrollTo().performClick()
        compose.onNodeWithTag("group-edit-after").assertTextEquals("0x80000018")
        compose.onNodeWithTag("group-edit-bit-8").performScrollTo().performClick()
        compose.onNodeWithTag("group-edit-after").assertTextEquals("0x80000010")
        compose.onNodeWithTag("group-edit-prepare").assertIsEnabled()
    }}
}
