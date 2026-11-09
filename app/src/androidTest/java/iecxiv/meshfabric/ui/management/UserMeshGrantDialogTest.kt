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
class UserMeshGrantDialogTest {
    @get:Rule val compose=createComposeRule()
    private val actor="user//admin";private val uid="user//target";private val mid="mesh//group"
    private fun state(rights:Long=8):ProfileState {
        val user=JSONObject().put("_id",uid).put("name","Cuenta destino").put("siteadmin",0).put("links",JSONObject().apply {if(rights!=8L) put(mid,JSONObject().put("rights",rights))})
        val mesh=JSONObject().put("_id",mid).put("name","Group").put("links",JSONObject().put(actor,JSONObject().put("rights",0xffffffffL)).apply {if(rights!=8L) put(uid,JSONObject().put("rights",rights))})
        return ProfileState(ready=true,user=JSONObject().put("_id",actor).put("siteadmin",2),users=listOf(user),groups=listOf(mesh))
    }
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
            compose.setContent {MaterialTheme {UserMeshGrantDialog(session,current.value,uid) {}}}
        }
        override fun close() {assertNull(field(client,"webSocket").get(client));assertNull(field(session,"socket").get(session));session.close();(field(client,"scope").get(client) as CoroutineScope).cancel()}
    }
    @Test fun preparingDoesNotConfirmOrSend() {Fixture().use {
        compose.onNodeWithTag("direct-grant-send").assertDoesNotExist()
        prepare()
        compose.onNodeWithTag("direct-grant-send").assertIsNotEnabled()
        compose.onNodeWithTag("direct-grant-consent").performScrollTo().performClick()
        compose.onNodeWithTag("direct-grant-send").assertIsEnabled()
    }}
    @Test fun newAssignmentInvalidatesFrozenConfirmation() {Fixture().use {f->
        prepare()
        compose.onNodeWithTag("direct-grant-consent").performScrollTo().performClick()
        compose.onNodeWithTag("direct-grant-send").assertIsEnabled()
        compose.runOnIdle {f.current.value=state(16)}
        compose.onNodeWithTag("direct-grant-send").assertIsNotEnabled()
    }}
    @Test fun loadingReadFailureOrLostRoleBlocksPreparation() {Fixture().use {f->
        selectDraft()
        for(s in listOf(state().copy(loading=true),state().copy(readError=true),state().copy(busy=true),state().copy(detailsBusy=true),state().copy(user=JSONObject().put("_id",actor).put("siteadmin",0)))) {
            compose.runOnIdle {f.current.value=s}
            compose.onNodeWithTag("direct-grant-prepare").assertIsNotEnabled()
        }
    }}
    private fun openHex() {
        compose.onNode(hasText("Avanzado: máscara hexadecimal") or hasText("Advanced: hexadecimal mask")).performScrollTo().performClick()
    }
    private fun selectDraft() {
        compose.onNodeWithTag("direct-grant-candidate-"+mid).performScrollTo().performClick()
        openHex()
        compose.onNodeWithTag("direct-grant-hex").performScrollTo().performTextReplacement("8")
        compose.onNodeWithTag("direct-grant-prepare").assertIsEnabled()
    }
    private fun prepare() {selectDraft();compose.onNodeWithTag("direct-grant-prepare").performClick()}
    @Test fun noDefaultRightsAndAdministrativeMasksCannotPrepare() {Fixture().use {
        compose.onNodeWithTag("direct-grant-candidate-"+mid).performScrollTo().performClick()
        compose.onNodeWithTag("direct-grant-prepare").assertIsNotEnabled()
        openHex()
        for(mask in listOf("0","FFFFFFFF","80000008")) {
            compose.onNodeWithTag("direct-grant-hex").performScrollTo().performTextReplacement(mask)
            compose.onNodeWithTag("direct-grant-prepare").assertIsNotEnabled()
        }
    }}
}
