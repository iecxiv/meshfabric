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
class UserMeshAssignmentReviewTest {
    @get:Rule val compose=createComposeRule()
    private val actor="user//admin";private val uid="user//target";private val mid="mesh//group"
    private fun state(rights:Long=8)=ProfileState(ready=true,user=JSONObject().put("_id",actor).put("siteadmin",2),users=listOf(JSONObject().put("_id",uid).put("siteadmin",0).put("links",JSONObject().put(mid,JSONObject().put("rights",rights)))),groups=listOf(JSONObject().put("_id",mid).put("links",JSONObject().put(uid,JSONObject().put("rights",rights)).put(actor,JSONObject().put("rights",0xffffffffL)))))
    private fun field(target:Any,name:String)=target.javaClass.getDeclaredField(name).apply {isAccessible=true}
    private inner class Fixture:AutoCloseable {
        val client=MeshCentralClient();val session=ProfileSession(client)
        val current=mutableStateOf(state())
        init {
            field(client,"serverUrl").set(client,"https://example.invalid/");field(client,"authCookie").set(client,"offline-ui-test")
            @Suppress("UNCHECKED_CAST")
            val connected=field(client,"_connectionState").get(client) as MutableStateFlow<Boolean>
            connected.value=true
            field(session,"binding").set(session,Triple(client.serverUrl,client.authCookie,false))
            compose.setContent {MaterialTheme {UserMeshAssignmentReview(session,current.value,uid,mid) {}}}
        }
        override fun close() {assertNull(field(client,"webSocket").get(client));assertNull(field(session,"socket").get(session));session.close();(field(client,"scope").get(client) as CoroutineScope).cancel()}
    }
    @Test fun preparingDoesNotConfirmOrSend() {Fixture().use {
        compose.onNodeWithTag("direct-revoke-send").assertDoesNotExist()
        compose.onNodeWithTag("direct-revoke-prepare").assertIsEnabled().performClick()
        compose.onNodeWithTag("direct-revoke-send").assertIsNotEnabled()
        compose.onNodeWithTag("direct-revoke-consent").performScrollTo().performClick()
        compose.onNodeWithTag("direct-revoke-send").assertIsEnabled()
    }}
    @Test fun changedBilateralRightsInvalidateFrozenConfirmation() {Fixture().use {f->
        compose.onNodeWithTag("direct-revoke-prepare").performClick()
        compose.onNodeWithTag("direct-revoke-consent").performScrollTo().performClick()
        compose.onNodeWithTag("direct-revoke-send").assertIsEnabled()
        compose.runOnIdle {f.current.value=state(16)}
        compose.onNodeWithTag("direct-revoke-send").assertIsNotEnabled()
    }}
    @Test fun loadingReadFailureOrLostRoleBlocksPreparation() {Fixture().use {f->
        for(s in listOf(state().copy(loading=true),state().copy(readError=true),state().copy(busy=true),state().copy(detailsBusy=true),state().copy(user=JSONObject().put("_id",actor).put("siteadmin",0)))) {
            compose.runOnIdle {f.current.value=s}
            compose.onNodeWithTag("direct-revoke-prepare").assertIsNotEnabled()
        }
    }}
}
