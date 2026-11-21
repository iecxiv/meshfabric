package iecxiv.meshfabric.ui.management
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.dp
import org.json.JSONObject
internal enum class ProfileSearchTarget {NONE,DIRECTORY,GROUP_DEVICES}
internal fun profileSearchTarget(tab:Int,group:Boolean,groupTab:Int,covered:Boolean,advanced:Boolean)=when {
    tab !in 1..3->ProfileSearchTarget.NONE
    group && !covered && !advanced && groupTab==1->ProfileSearchTarget.GROUP_DEVICES
    group || covered->ProfileSearchTarget.NONE
    else->ProfileSearchTarget.DIRECTORY
}
internal class DeviceGroupSearchState(query:String="",open:Boolean=false) {
    var query by mutableStateOf(query)
    var open by mutableStateOf(open)
    var advanced by mutableStateOf(false)
    fun close() {open=false;query=""}
    fun toggle() {if(open) close() else open=true}
    companion object {
        val Saver=listSaver<DeviceGroupSearchState,String>(save={listOf(it.query,it.open.toString())},restore={DeviceGroupSearchState(it[0],it[1].toBoolean())})
    }
}
internal val DeviceGroupSearchMapSaver=listSaver<MutableMap<String,DeviceGroupSearchState>,String>(
    save={map->map.flatMap {(id,state)->listOf(id,state.query,state.open.toString())}},
    restore={rows->rows.chunked(3).associate {it[0] to DeviceGroupSearchState(it[1],it[2].toBoolean())}.toMutableMap()})
internal val LocalDeviceGroupSearch=staticCompositionLocalOf<DeviceGroupSearchState?> {null}
internal fun deviceGroupSearchNodes(nodes:List<JSONObject>?,id:String,query:String)=nodes?.filter {it.optString("meshid")==id && deviceGroupMatches(query,it.optString("name"),it.optString("desc"))}
@Composable internal fun ProfileSearchAction(target:ProfileSearchTarget,open:Boolean,onToggle:()->Unit) {
    if(target==ProfileSearchTarget.NONE) return
    val es=LocalConfiguration.current.locales[0].language=="es"
    IconButton(onClick=onToggle,modifier=Modifier.size(38.dp).testTag("profile-directory-search-toggle")) {
        Icon(if(open) Icons.Default.Close else Icons.Default.Search,if(open) {if(es) "Cerrar búsqueda" else "Close search"} else {if(es) "Buscar" else "Search"},Modifier.size(20.dp),tint=if(open) androidx.compose.ui.graphics.Color(0xFF2196F3) else LocalContentColor.current)
    }
}
@Composable internal fun DeviceGroupSearchField(state:DeviceGroupSearchState,collapsible:Boolean=true) {
    if(!state.open) return
    val es=LocalConfiguration.current.locales[0].language=="es"
    val focus=remember {FocusRequester()};val manager=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
    BackHandler(enabled=collapsible) {state.close();manager.clearFocus(force=true);keyboard?.hide()}
    LaunchedEffect(state,collapsible) {if(collapsible) {withFrameNanos {};focus.requestFocus();keyboard?.show()}}
    OutlinedTextField(state.query,{state.query=it.take(256)},label={Text(if(es) "Buscar dispositivo" else "Search device")},singleLine=true,modifier=Modifier.fillMaxWidth().focusRequester(focus).testTag("device-group-search-input"))
}
