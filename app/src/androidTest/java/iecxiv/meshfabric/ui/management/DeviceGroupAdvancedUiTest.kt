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
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class DeviceGroupAdvancedUiTest {
    @get:Rule val compose=createComposeRule()
    private val actor="user//admin";private val mid="mesh//one";private val label="offline-server"
    private fun mesh(days:Int=0)=JSONObject().put("_id",mid).put("mtype",2).put("flags",0x80000000L).put("consent",0x80000000L).put("expireDevs",days).put("links",JSONObject().put(actor,JSONObject().put("rights",1)))
    private fun state(days:Int=0)=ProfileState(ready=true,user=JSONObject().put("_id",actor).put("siteadmin",0),server=JSONObject().put("consent",0),groups=listOf(mesh(days)))
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).apply {isAccessible=true}
    @Test fun draftPreservesUnknownBitsAndZeroIsARealChange() {
        val s=state(30);val p=requireNotNull(deviceGroupAdvancedUiDraft(s,mesh(30),0x80000000L,0x80000000L,"0",label))
        assertEquals(0x80000000L,p.getLong("flags"));assertEquals(0x80000000L,p.getLong("consent"));assertEquals(0,p.getInt("expireDevs"));assertFalse(p.getBoolean("_advancedConfirmed"))
        assertTrue(deviceGroupAdvancedUiCurrent(s,p,label));assertFalse(deviceGroupAdvancedUiCurrent(state(29),p,label));assertFalse(deviceGroupAdvancedUiCurrent(s,p,"other"))
    }
    @Test fun invalidExpiryUnknownBitsAndMandatoryConsentAreRejected() {
        val s=state()
        for(days in listOf("","-1","2001","1.5"," 30","30 ")) assertNull(deviceGroupAdvancedUiDraft(s,mesh(),0x80000000L,0x80000000L,days,label))
        assertNull(deviceGroupAdvancedUiDraft(s,mesh(),0L,0x80000000L,"30",label))
        assertNull(deviceGroupAdvancedUiDraft(s.copy(server=JSONObject().put("consent",8)),mesh(),0x80000000L,0x80000000L,"30",label))
    }
    @Test fun reviewRequiresSeparateConfirmationAndStaysInvalidAfterRestoration() {
        val client=MeshCentralClient();val session=ProfileSession(client)
        try {
            field(client,"serverUrl").set(client,"https://example.invalid/");field(client,"authCookie").set(client,"offline-test")
            @Suppress("UNCHECKED_CAST")
            val connected=field(client,"_connectionState").get(client) as MutableStateFlow<Boolean>;connected.value=true
            field(session,"binding").set(session,Triple(client.serverUrl,client.authCookie,false))
            val current=mutableStateOf(state())
            compose.setContent {MaterialTheme {DeviceGroupAdvancedEditor(session,current.value,mid) {}}}
            compose.onNodeWithTag("advanced-days").performScrollTo().performTextReplacement("30")
            compose.onNodeWithTag("advanced-prepare").assertIsEnabled().performClick()
            compose.onNodeWithTag("advanced-send").assertIsNotEnabled()
            compose.onNodeWithTag("advanced-confirm").performScrollTo().performClick()
            compose.onNodeWithTag("advanced-send").assertIsEnabled()
            compose.runOnIdle {current.value=state(5)}
            compose.waitForIdle()
            compose.onNodeWithTag("advanced-send").assertIsNotEnabled()
            compose.runOnIdle {current.value=state()}
            compose.waitForIdle()
            compose.onNodeWithTag("advanced-send").assertIsNotEnabled()
            assertNull(field(session,"socket").get(session));assertNull(field(client,"webSocket").get(client))
        } finally {session.close();(field(client,"scope").get(client) as CoroutineScope).cancel()}
    }
}
