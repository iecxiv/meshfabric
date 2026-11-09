package iecxiv.meshfabric.ui.management
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
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
@RunWith(AndroidJUnit4::class) class UserDetailPresentationTest {
    @get:Rule val compose=createComposeRule()
    private val uid="user//alice";private val gid="ugrp//team";private val missing="ugrp//missing";private val mid="mesh//office";private val nid="node//laptop"
    private fun field(target:Any,name:String)=target.javaClass.getDeclaredField(name).apply {isAccessible=true}
    private inner class Fixture:AutoCloseable {
        val client=MeshCentralClient();val session=ProfileSession(client)
        val actor=JSONObject().put("_id","user//admin").put("siteadmin",2L).put("links",JSONObject().put(mid,JSONObject().put("rights",8)))
        val user=JSONObject().put("_id",uid).put("name","Alicia").put("email","alice@example.invalid").put("siteadmin",0).put("links",JSONObject().put(gid,JSONObject().put("rights",1)).put(missing,JSONObject().put("rights",1)).put(mid,JSONObject().put("rights",8)).put(nid,JSONObject().put("rights",8)))
        val team=JSONObject().put("_id",gid).put("name","Equipo").put("desc","Madrid").put("links",JSONObject().put(uid,JSONObject().put("rights",1)))
        val mesh=JSONObject().put("_id",mid).put("name","Oficina").put("desc","Madrid")
        val node=JSONObject().put("_id",nid).put("name","Portátil").put("desc","Personal").put("meshid",mid)
        val state=ProfileState(ready=true,user=actor,users=listOf(user),groups=listOf(mesh),nodes=listOf(node),userGroups=listOf(team),listedUserGroups=listOf(team),userGroupPhase="ready")
        init {
            compose.setContent {
                val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale("es"))}
                CompositionLocalProvider(LocalConfiguration provides config) {MaterialTheme {
                    UserAdministrationDialog(session,state,ProfileForm(true,"edit",uid,"Alicia","alice@example.invalid"),onDismiss={})
                }}
            }
        }
        fun assertNoIds() {for(id in listOf(uid,gid,missing,mid,nid)) compose.onAllNodesWithText(id,substring=true).assertCountEquals(0)}
        override fun close() {
            assertNull(field(client,"webSocket").get(client));assertNull(field(session,"socket").get(session))
            session.close();(field(client,"scope").get(client) as CoroutineScope).cancel()
        }
    }
    @Test fun mainAndSecurityActionsHaveIconsWithoutChangingTheirText() {Fixture().use {f->
        compose.onAllNodesWithTag("profile-group-action-icon",useUnmergedTree=true).assertCountEquals(2)
        compose.onNodeWithText("Acciones y seguridad").performScrollTo().performClick()
        compose.onAllNodesWithTag("profile-group-action-icon",useUnmergedTree=true).assertCountEquals(5)
        for(label in listOf("Agregar dispositivo","Cambiar contraseña","Inicios de sesión anteriores")) compose.onNodeWithText(label).assertExists()
        f.assertNoIds()
    }}
    @Test fun membershipsUseNamesAndNeverFallBackToLongIds() {Fixture().use {f->
        compose.onNodeWithText("Grupos de usuarios recibidos").performScrollTo().performClick()
        compose.onNodeWithText("Grupo de usuarios: Equipo · Madrid").assertExists()
        compose.onNodeWithText("Grupo de usuarios: nombre no disponible").assertExists()
        f.assertNoIds()
    }}
    @Test fun commonDevicesUseNamesAndParentGroupContext() {Fixture().use {f->
        compose.onNodeWithText("Dispositivos en común").performScrollTo().performClick()
        compose.onNodeWithText("Dispositivo: Portátil · Personal").assertExists()
        compose.onNodeWithText("Grupo de dispositivos: Oficina · Madrid").assertExists()
        f.assertNoIds()
    }}
    @Test fun passwordModeStillStatesThatMultifactorMethodsArePreserved() {Fixture().use {f->
        compose.onNodeWithText("Acciones y seguridad").performScrollTo().performClick()
        compose.onNodeWithText("Cambiar contraseña").performScrollTo().performClick()
        compose.onNodeWithText("Se conservan sus métodos de autenticación multifactor.",substring=true).assertExists()
        f.assertNoIds()
    }}
}
