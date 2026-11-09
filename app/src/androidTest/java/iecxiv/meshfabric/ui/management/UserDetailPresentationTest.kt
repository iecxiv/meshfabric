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
    private inner class Fixture(val groupMode:String="",val deviceCanOpen:Boolean=false,val ambiguousDevice:Boolean=false,val duplicateDeviceId:Boolean=false):AutoCloseable {
        var openedGroup:String?=null
        var openedDevice:String?=null
        val client=MeshCentralClient();val session=ProfileSession(client)
        val actor=JSONObject().put("_id","user//admin").put("siteadmin",2L).put("links",JSONObject().put(mid,JSONObject().put("rights",8)))
        val user=JSONObject().put("_id",uid).put("name","Alicia").put("email","alice@example.invalid").put("siteadmin",0).put("links",JSONObject().put(gid,JSONObject().put("rights",1)).put(missing,JSONObject().put("rights",1)).put(mid,JSONObject().put("rights",8)).put(nid,JSONObject().put("rights",8)))
        val team=JSONObject().put("_id",gid).put("name","Equipo").put("desc","Madrid").put("links",JSONObject().put(uid,JSONObject().put("rights",1)))
        val mesh=JSONObject().put("_id",mid).put("name",if(groupMode=="missing") "" else "Oficina").put("desc","Madrid")
        val node=JSONObject().put("_id",nid).put("name","Portátil").put("desc","Personal").put("meshid",mid)
        val state=ProfileState(ready=true,user=actor,users=listOf(user),groups=if(groupMode=="ambiguous") listOf(mesh,JSONObject(mesh.toString()).put("_id","mesh//other")) else listOf(mesh),nodes=if(duplicateDeviceId) listOf(node,JSONObject(node.toString())) else if(ambiguousDevice) listOf(node,JSONObject(node.toString()).put("_id","node//other")) else listOf(node),userGroups=listOf(team),listedUserGroups=listOf(team),userGroupPhase="ready")
        init {
            compose.setContent {
                val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale("es"))}
                CompositionLocalProvider(LocalConfiguration provides config) {MaterialTheme {
                    UserAdministrationDialog(session,state,ProfileForm(true,"edit",uid,"Alicia","alice@example.invalid"),onOpenGroup={openedGroup=it},onOpenDevice={openedDevice=it},canOpenDevice={deviceCanOpen && (it==nid || (ambiguousDevice && it=="node//other"))},onDismiss={})
                }}
            }
        }
        fun assertNoIds() {for(id in listOf(uid,gid,missing,mid,nid,"node//other")) compose.onAllNodesWithText(id,substring=true).assertCountEquals(0)}
        override fun close() {
            assertNull(field(client,"webSocket").get(client));assertNull(field(session,"socket").get(session))
            session.close();(field(client,"scope").get(client) as CoroutineScope).cancel()
        }
    }
    @Test fun directGroupsUseContextAndSharedActionIcons() {Fixture().use {f->
        compose.onNodeWithText("Accesos directos a grupos de dispositivos").performScrollTo().performClick()
        compose.onNodeWithText("Grupo de dispositivos: Oficina · Madrid").assertExists()
        compose.onNodeWithText("Revisar acceso directo").assertIsEnabled()
        compose.onNodeWithText("Editar derechos directos").assertIsEnabled()
        compose.onAllNodesWithTag("profile-group-action-icon",useUnmergedTree=true).assertCountEquals(5)
        f.assertNoIds()
    }}
    @Test fun commonGroupsKeepInternalNavigationAndReadableContext() {Fixture().use {f->
        compose.onNodeWithText("Grupos de dispositivos en común").performScrollTo().performClick()
        compose.onNodeWithText("Grupo de dispositivos: Oficina · Madrid").assertExists()
        compose.onNodeWithText("Abrir grupo").performScrollTo().performClick()
        assertEquals(mid,f.openedGroup)
        f.assertNoIds()
    }}
    @Test fun ambiguousDirectGroupBlocksBothActions()=assertDirectGroupBlocked("ambiguous")
    @Test fun unnamedDirectGroupBlocksBothActions()=assertDirectGroupBlocked("missing")
    private fun assertDirectGroupBlocked(mode:String) {Fixture(mode).use {f->
        compose.onNodeWithText("Accesos directos a grupos de dispositivos").performScrollTo().performClick()
        if(mode=="missing") compose.onNodeWithText("Grupo de dispositivos: nombre no disponible").assertExists()
        else compose.onNodeWithText("Grupo de dispositivos: Oficina · Madrid").assertExists()
        compose.onNodeWithText("Revisar acceso directo").assertIsNotEnabled()
        compose.onNodeWithText("Editar derechos directos").assertIsNotEnabled()
        compose.onNodeWithText("Ficha incompleta o ambigua.",substring=true).assertExists()
        f.assertNoIds()
    }}
    @Test fun ambiguousCommonGroupBlocksNavigation()=assertCommonGroupBlocked("ambiguous")
    @Test fun unnamedCommonGroupBlocksNavigation()=assertCommonGroupBlocked("missing")
    private fun assertCommonGroupBlocked(mode:String) {Fixture(mode).use {f->
        compose.onNodeWithText("Grupos de dispositivos en común").performScrollTo().performClick()
        if(mode=="missing") compose.onNodeWithText("Grupo de dispositivos: nombre no disponible").assertExists()
        else compose.onNodeWithText("Grupo de dispositivos: Oficina · Madrid").assertExists()
        compose.onNodeWithText("Abrir grupo").assertIsNotEnabled()
        assertNull(f.openedGroup)
        f.assertNoIds()
    }}
    @Test fun mainAndSecurityActionsHaveIconsWithoutChangingTheirText() {Fixture().use {f->
        compose.onAllNodesWithTag("profile-group-action-icon",useUnmergedTree=true).assertCountEquals(2)
        compose.onNodeWithText("Acciones y seguridad").performScrollTo().performClick()
        compose.onAllNodesWithTag("profile-group-action-icon",useUnmergedTree=true).assertCountEquals(6)
        for(label in listOf("Agregar dispositivo","Cambiar contraseña","Restablecer MFA","Inicios de sesión anteriores")) compose.onNodeWithText(label).assertExists()
        f.assertNoIds()
    }}
    @Test fun membershipsUseNamesAndNeverFallBackToLongIds() {Fixture().use {f->
        compose.onNodeWithText("Grupos de usuarios recibidos").performScrollTo().performClick()
        compose.onNodeWithText("Grupo de usuarios: Equipo · Madrid").assertExists()
        compose.onNodeWithText("Grupo de usuarios: nombre no disponible").assertExists()
        f.assertNoIds()
    }}
    @Test fun commonDeviceUnavailableShowsDisabledButtonAndReason() {Fixture().use {f->
        compose.onNodeWithText("Dispositivos en común").performScrollTo().performClick()
        compose.onAllNodesWithTag("profile-group-action-icon",useUnmergedTree=true).assertCountEquals(3)
        compose.onNodeWithText("Abrir ficha").assertIsNotEnabled()
        compose.onNodeWithText("Ficha no disponible con los datos y la conexión actuales.").assertExists()
        assertNull(f.openedDevice);f.assertNoIds()
    }}
    @Test fun commonDeviceAvailableStillNavigatesByInternalId() {Fixture(deviceCanOpen=true).use {f->
        compose.onNodeWithText("Dispositivos en común").performScrollTo().performClick()
        compose.onAllNodesWithTag("profile-group-action-icon",useUnmergedTree=true).assertCountEquals(3)
        compose.onNodeWithText("Abrir ficha").performScrollTo().assertIsEnabled().performClick()
        assertEquals(nid,f.openedDevice);f.assertNoIds()
    }}
    @Test fun homonymousCommonDevicesOpenTheirOwnExactIds() {Fixture(deviceCanOpen=true,ambiguousDevice=true).use {f->
        compose.onNodeWithText("Dispositivos en común").performScrollTo().performClick()
        compose.onAllNodesWithTag("profile-group-action-icon",useUnmergedTree=true).assertCountEquals(4)
        val buttons=compose.onAllNodesWithText("Abrir ficha")
        buttons.assertCountEquals(2)
        buttons[0].performScrollTo().assertIsEnabled().performClick()
        assertEquals(nid,f.openedDevice)
        buttons[1].performScrollTo().assertIsEnabled().performClick()
        assertEquals("node//other",f.openedDevice)
        compose.onAllNodesWithText("Ficha no disponible:",substring=true).assertCountEquals(0)
        f.assertNoIds()
    }}
    @Test fun duplicateExactDeviceIdStillBlocksBothEntries() {Fixture(deviceCanOpen=true,duplicateDeviceId=true).use {f->
        compose.onNodeWithText("Dispositivos en común").performScrollTo().performClick()
        compose.onAllNodesWithTag("profile-group-action-icon",useUnmergedTree=true).assertCountEquals(4)
        val buttons=compose.onAllNodesWithText("Abrir ficha")
        buttons.assertCountEquals(2)
        buttons[0].assertIsNotEnabled();buttons[1].assertIsNotEnabled()
        compose.onAllNodesWithText("Ficha no disponible: identificación incompleta o ambigua.").assertCountEquals(2)
        assertNull(f.openedDevice);f.assertNoIds()
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
