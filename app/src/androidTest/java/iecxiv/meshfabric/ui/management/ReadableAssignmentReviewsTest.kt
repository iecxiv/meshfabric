package iecxiv.meshfabric.ui.management
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
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
import java.util.Locale
@RunWith(AndroidJUnit4::class) class ReadableAssignmentReviewsTest {
    @get:Rule val compose=createComposeRule()
    private val actor="user//admin"
    private val mid="mesh//group"
    private fun record(id:String,name:String,desc:String="")=JSONObject().put("_id",id).put("name",name).put("desc",desc)
    @Test fun duplicateAndAbsentNamesNeverFallBackToIds() {
        for(kind in listOf("mesh","ugrp","user")) {
            val a=record(kind+"//a","Equipo")
            val b=record(kind+"//b","equipo")
            assertFalse(profileReviewIdentity(a,listOf(a,b),true).usable)
            assertFalse(profileReviewIdentity(record(kind+"//missing",""),emptyList(),true).usable)
            assertFalse(profileReviewIdentity(record(kind+"//raw",kind+"//raw"),emptyList(),true).label.contains(kind+"/"))
            val key=if(kind=="user") "email" else "desc"
            a.put(key,"Madrid");b.put(key,"Sevilla")
            val before=a.toString()
            assertTrue(profileReviewIdentity(a,listOf(a,b),true).usable)
            assertEquals("Equipo · Madrid",profileReviewIdentity(a,listOf(a,b),true).label)
            assertEquals(before,a.toString())
            b.put(key,"madrid")
            assertFalse(profileReviewIdentity(a,listOf(a,b),true).usable)
        }
        val a=record("ugrp//a","Equipo")
        assertTrue(profileReviewIdentity(a,listOf(a,record("mesh//a","Equipo")),true).usable)
        assertFalse(profileReviewIdentity(a,emptyList(),false).usable)
        assertEquals("name unavailable",profileReviewIdentity(null,emptyList(),false).label)
    }
    private fun field(target:Any,name:String)=target.javaClass.getDeclaredField(name).apply {isAccessible=true}
    private inner class Fixture(val groups:Boolean):AutoCloseable {
        val uid=if(groups) "ugrp//target" else "user//target"
        val prefix=if(groups) "group-revoke-" else "direct-revoke-"
        val client=MeshCentralClient();val session=ProfileSession(client)
        val target=record(uid,"Equipo").put("siteadmin",0).put("links",JSONObject().put(mid,JSONObject().put("rights",8)))
        val mesh=record(mid,"Oficina").put("links",JSONObject().put(uid,JSONObject().put("rights",8)).put(actor,JSONObject().put("rights",if(groups) 2L else 0xffffffffL)))
        val baseline=ProfileState(ready=true,user=JSONObject().put("_id",actor).put("siteadmin",if(groups) 256L else 2L),users=if(groups) emptyList() else listOf(target),groups=listOf(mesh),listedUserGroups=if(groups) listOf(target) else emptyList(),userGroupPhase="ready")
        val state=mutableStateOf(baseline)
        init {
            field(client,"serverUrl").set(client,"https://example.invalid/");field(client,"authCookie").set(client,"offline-review-test")
            @Suppress("UNCHECKED_CAST") val connected=field(client,"_connectionState").get(client) as MutableStateFlow<Boolean>
            connected.value=true
            field(session,"binding").set(session,Triple(client.serverUrl,client.authCookie,false))
            compose.setContent {
                val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale("es"))}
                CompositionLocalProvider(LocalConfiguration provides config) {MaterialTheme {
                    if(groups) GroupMeshAssignmentReview(session,state.value,uid,mid) {} else UserMeshAssignmentReview(session,state.value,uid,mid) {}
                }}
            }
        }
        fun ambiguous():ProfileState {
            val duplicate=JSONObject(target.toString()).put("_id",if(groups) "ugrp//other" else "user//other")
            return if(groups) baseline.copy(listedUserGroups=listOf(target,duplicate)) else baseline.copy(users=listOf(target,duplicate))
        }
        override fun close() {
            assertNull(field(client,"webSocket").get(client));assertNull(field(session,"socket").get(session))
            session.close();(field(client,"scope").get(client) as CoroutineScope).cancel()
        }
    }
    private fun verifyReview(groups:Boolean) {Fixture(groups).use {f->
        compose.onAllNodesWithText(f.uid).assertCountEquals(0)
        compose.onAllNodesWithText(mid).assertCountEquals(0)
        compose.onNodeWithText("Grupo de dispositivos: Oficina").assertExists()
        compose.onNodeWithTag(f.prefix+"prepare").assertIsEnabled()
        compose.runOnIdle {f.state.value=f.ambiguous()}
        compose.onNodeWithTag(f.prefix+"prepare").assertIsNotEnabled()
        compose.runOnIdle {f.state.value=f.baseline}
        compose.onNodeWithTag(f.prefix+"prepare").assertIsEnabled().performClick()
        compose.onNodeWithTag(f.prefix+"send").assertIsNotEnabled()
        compose.onNodeWithTag(f.prefix+"consent").performScrollTo().performClick()
        compose.onNodeWithTag(f.prefix+"send").assertIsEnabled()
        compose.runOnIdle {f.state.value=f.ambiguous()}
        compose.onNodeWithTag(f.prefix+"send").assertIsNotEnabled()
        compose.runOnIdle {f.state.value=f.baseline}
        compose.onNodeWithTag(f.prefix+"send").assertIsNotEnabled()
        compose.onNodeWithTag(f.prefix+"consent").assertIsNotEnabled()
    }}
    @Test fun userReviewShowsNamesAndInvalidatesAmbiguousFrozenConfirmation()=verifyReview(false)
    @Test fun groupReviewShowsNamesAndInvalidatesAmbiguousFrozenConfirmation()=verifyReview(true)
}
