package iecxiv.meshfabric.ui.management
import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
@RunWith(AndroidJUnit4::class) class ReadableReceivedLinksTest {
    @get:Rule val compose=createComposeRule()
    private val gid="ugrp//team";private val uid="user//alice";private val mid="mesh//office";private val unknown="user//missing"
    private fun group()=JSONObject().put("_id",gid).put("name","Equipo").put("links",JSONObject().put(uid,JSONObject().put("rights",1)).put(mid,JSONObject().put("rights",8)).put(unknown,JSONObject().put("name","Invitado").put("rights",1)))
    private fun state(g:JSONObject)=ProfileState(ready=true,user=JSONObject().put("_id","user//admin").put("siteadmin",258L),users=listOf(JSONObject().put("_id",uid).put("name","Alicia").put("email","alice@example.invalid").put("siteadmin",0).put("links",JSONObject().put(gid,JSONObject().put("rights",1)))),groups=listOf(JSONObject().put("_id",mid).put("name","Oficina").put("desc","Madrid").put("links",JSONObject().put(gid,JSONObject().put("rights",8)))),listedUserGroups=listOf(g),userGroupPhase="ready")
    @Test fun namesHaveTypeAndContextWithoutChangingRecordIdentity() {
        val g=group();val s=state(g);val before=g.toString()
        val user=profileReceivedLinkPresentation(s,g,uid,true)
        assertEquals("Usuario: Alicia · alice@example.invalid",user.label);assertTrue(user.usable)
        assertEquals("Grupo de dispositivos: Oficina · Madrid",profileReceivedLinkPresentation(s,g,mid,true).label)
        assertEquals(before,g.toString())
    }
    @Test fun linkOnlyNameIsReadableButNeverAuthorizesAnAction() {
        val g=group();val value=profileReceivedLinkPresentation(state(g),g,unknown,true)
        assertEquals("Usuario: Invitado",value.label);assertFalse(value.usable)
        g.getJSONObject("links").getJSONObject(unknown).put("name",unknown)
        val missing=profileReceivedLinkPresentation(state(g),g,unknown,true)
        assertFalse(missing.label.contains("user/"));assertFalse(missing.usable)
    }
    @Test fun ambiguousNamesAreCheckedAgainstAllRecordsNotJustSearchMatches() {
        val g=group();val s=state(g)
        val duplicate=JSONObject(s.users.single().toString()).put("_id","user//other")
        val ambiguous=s.copy(users=s.users+duplicate)
        assertFalse(profileReceivedLinkPresentation(ambiguous,g,uid,true).usable)
        duplicate.put("email","other@example.invalid")
        assertTrue(profileReceivedLinkPresentation(ambiguous,g,uid,true).usable)
    }
    private fun field(target:Any,name:String)=target.javaClass.getDeclaredField(name).apply {isAccessible=true}
    private inner class Fixture(received:Boolean):AutoCloseable {
        val client=MeshCentralClient();val session=ProfileSession(client)
        val owner=group();val s=state(owner)
        var openedUser="";var openedMesh=""
        init {
            compose.setContent {
                val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale("es"))}
                CompositionLocalProvider(LocalConfiguration provides config) {MaterialTheme {
                    if(received) ReceivedUserGroupDialog(session,s,gid) {} else UserGroupsDirectoryPane(session,s,Modifier.fillMaxSize(),onOpenUser={openedUser=it},onOpenDeviceGroup={openedMesh=it})
                }}
            }
        }
        fun assertNoIds() {for(id in listOf(gid,uid,mid,unknown)) compose.onAllNodesWithText(id,substring=true).assertCountEquals(0)}
        override fun close() {
            assertNull(field(client,"webSocket").get(client));assertNull(field(session,"socket").get(session))
            session.close();(field(client,"scope").get(client) as CoroutineScope).cancel()
        }
    }
    @Test fun directoryShowsNamesAndNavigationRetainsExactIds() {Fixture(false).use {f->
        compose.onNodeWithText("Miembros y accesos recibidos").performScrollTo().performClick()
        f.assertNoIds()
        compose.onNodeWithText("Retirar del grupo: Usuario: Invitado").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Usuario: Alicia · alice@example.invalid").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(uid,f.openedUser)}
        compose.onNodeWithText("Grupo de dispositivos: Oficina · Madrid").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(mid,f.openedMesh)}
        f.assertNoIds()
    }}
    @Test fun receivedDetailsRemainReadOnlyAndUseReadableLinks() {Fixture(true).use {f->
        compose.onNodeWithText("Usuario: Alicia · alice@example.invalid").assertExists()
        compose.onNodeWithText("Grupo de dispositivos: Oficina · Madrid").assertExists()
        f.assertNoIds()
        compose.onAllNodes(hasText("Retirar del grupo",substring=true)).assertCountEquals(0)
        compose.onAllNodes(hasText("Editar derechos",substring=true)).assertCountEquals(0)
    }}
}
