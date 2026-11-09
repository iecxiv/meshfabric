package iecxiv.meshfabric.ui.management
import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
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
@RunWith(AndroidJUnit4::class) class DeviceGroupDetailPresentationTest {
    @get:Rule val compose=createComposeRule()
    @Test fun actionsKeepTextCallbacksAndDisabledStateWithDiscreetIcons() {
        var edits=0;var advanced=0;var deletes=0
        compose.setContent {MaterialTheme {Column {
            ProfileGroupActionButton("Editar nombre y descripción",Icons.Default.Edit,true,onClick={edits++})
            ProfileGroupActionButton("Ajustes avanzados",Icons.Default.Tune,true,onClick={advanced++})
            ProfileGroupActionButton("Eliminar grupo",Icons.Default.Delete,false,dangerous=true,onClick={deletes++})
        }}}
        compose.onAllNodesWithTag("profile-group-action-icon",useUnmergedTree=true).assertCountEquals(3)
        compose.onAllNodesWithTag("profile-group-action").assertCountEquals(3)
        compose.onNodeWithText("Editar nombre y descripción").assertIsEnabled().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Ajustes avanzados").assertIsEnabled().performClick()
        compose.onNodeWithText("Eliminar grupo").assertIsNotEnabled()
        compose.runOnIdle {assertEquals(1,edits);assertEquals(1,advanced);assertEquals(0,deletes)}
    }
    @Test fun accessDetailsUseNamesAndNeverExposeUnresolvedMemberIds() {
        val mid="mesh//office";val uid="user//alice";val gid="ugrp//team";val unknown="user//missing"
        val group=JSONObject().put("_id",mid).put("name","Oficina").put("links",JSONObject().put(uid,JSONObject().put("rights",8)).put(gid,JSONObject().put("rights",16)).put(unknown,JSONObject().put("rights",8)))
        val user=JSONObject().put("_id",uid).put("name","Alicia").put("email","alice@example.invalid")
        val team=JSONObject().put("_id",gid).put("name","Equipo").put("desc","Madrid")
        val state=ProfileState(ready=false,users=listOf(user),groups=listOf(group),userGroups=listOf(team))
        val client=MeshCentralClient();val session=ProfileSession(client)
        fun field(target:Any,name:String)=target.javaClass.getDeclaredField(name).apply {isAccessible=true}
        try {
            compose.setContent {
                val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale("es"))}
                CompositionLocalProvider(LocalConfiguration provides config) {MaterialTheme {
                    DeviceGroupDetailsDialog(session,state,mid,false,onDismiss={},onEdit={},onDelete={},initialTab=2)
                }}
            }
            compose.onNodeWithText("Usuario: Alicia · alice@example.invalid").assertExists()
            compose.onNodeWithText("Grupo de usuarios: Equipo · Madrid").assertExists()
            compose.onNodeWithText("Usuario: nombre no disponible").assertExists()
            for(id in listOf(mid,uid,gid,unknown)) compose.onAllNodesWithText(id,substring=true).assertCountEquals(0)
            compose.onAllNodesWithText("Control remoto").assertCountEquals(2)
            compose.onNodeWithText("Consola").assertExists()
            assertNull(field(client,"webSocket").get(client));assertNull(field(session,"socket").get(session))
        } finally {session.close();(field(client,"scope").get(client) as CoroutineScope).cancel()}
    }
}
