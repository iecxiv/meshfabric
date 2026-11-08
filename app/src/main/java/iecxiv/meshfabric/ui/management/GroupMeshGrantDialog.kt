package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import org.json.JSONArray
import org.json.JSONObject

internal fun profileGroupMeshGrantHex(text:String):Long? {
    val match=Regex("(?:0[xX])?([0-9a-fA-F]{1,8})").matchEntire(text.trim()) ?: return null
    val digits=match.groupValues[1]
    return digits.toLongOrNull(16)?.takeIf(::profileGroupMeshInitialGrantRights)
}
internal fun profileGroupMeshGrantDraft(s:ProfileState,gid:String,mid:String,rights:Long,actor:String,server:String):JSONObject?=runCatching {
    val group=requireNotNull(s.listedUserGroups?.singleOrNull {it.optString("_id")==gid})
    val mesh=requireNotNull(s.groups.singleOrNull {it.optString("_id")==mid})
    val p=JSONObject().put("action","addmeshuser").put("meshid",mid).put("userids",JSONArray().put(gid)).put("meshadmin",rights).put("_grantActor",actor).put("_grantServer",server).put("_grantGroupBefore",profileUserGroupDeleteSnapshot(group)).put("_grantDeviceBefore",profileUserGroupDeleteSnapshot(mesh)).put("_grantConfirmed",true)
    require(profileGroupMeshGrantAllowed(s,p,server))
    p.put("_grantConfirmed",false)
}.getOrNull()
@Composable internal fun GroupMeshGrantDialog(session:ProfileSession,s:ProfileState,gid:String,onDismiss:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    val actor=remember {s.id};val server=remember {session.broadcastServerLabel()}
    var query by remember {mutableStateOf("")}
    var selected by remember {mutableStateOf("")}
    var hex by remember {mutableStateOf("")}
    var frozen by remember {mutableStateOf<JSONObject?>(null)}
    var approved by remember {mutableStateOf(false)}
    var submitted by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf(false)}
    val bound=actor==s.id && server.isNotBlank() && server==session.broadcastServerLabel()
    val rights=profileGroupMeshGrantHex(hex)
    val draft=if(bound && rights!=null) profileGroupMeshGrantDraft(s,gid,selected,rights,actor,server) else null
    val review=frozen
    val packet=review?.let {JSONObject(it.toString()).put("_grantConfirmed",approved)}
    val current=bound && review!=null && profileGroupMeshGrantAllowed(s,JSONObject(review.toString()).put("_grantConfirmed",true),server)
    val observed=submitted && s.notice=="groupMeshGrantedObserved" && s.grantedGroup==gid && s.grantedDeviceGroup==selected
    val candidates=if(bound && !submitted && review==null) s.groups.filter {profileGroupMeshGrantDraft(s,gid,it.optString("_id"),8L,actor,server)!=null}.filter {deviceGroupMatches(query,it.optString("name"),it.optString("desc"))} else emptyList()
    ManagementDialog(onDismissRequest=onDismiss,modifier=Modifier.widthIn(max=640.dp).fillMaxWidth(0.94f),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn,usePlatformDefaultWidth=false,dismissOnClickOutside=false),title={Text(t("Nueva asignación","New assignment"))},text={
        Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*0.68f).dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(server,style=MaterialTheme.typography.bodySmall);Text(gid,style=MaterialTheme.typography.bodySmall)
            Text(t("Solo asignaciones nuevas. No se sobrescriben enlaces existentes. Se exige autoridad directa completa sobre el grupo de dispositivos.","New assignments only. Existing links are not overwritten. Direct full authority over the device group is required."),style=MaterialTheme.typography.bodySmall)
            if(s.loading || s.userGroupPhase=="loading" || (submitted && s.busy)) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(review==null && !submitted) {
                OutlinedTextField(query,{query=it.take(256)},label={Text(t("Buscar grupo de dispositivos","Search device groups"))},singleLine=true,modifier=Modifier.fillMaxWidth())
                for(mesh in candidates.take(100)) {val mid=mesh.optString("_id");TextButton(onClick={selected=mid;error=false}) {Text((if(selected==mid) "[x] " else "[ ] ")+mesh.optString("name").ifBlank {mid}+" — "+mid)}}
                if(candidates.isEmpty()) Text(t("No hay candidatos elegibles en los datos actuales. Actualiza o revisa permisos y enlaces recibidos.","No eligible candidates in current data. Refresh or review permissions and received links."))
                if(candidates.size>100) Text(t("Primeros 100 resultados. Acota la búsqueda.","First 100 results. Narrow the search."))
                OutlinedTextField(hex,{hex=it.take(256);error=false},label={Text(t("Derechos: máscara hexadecimal","Rights: hexadecimal mask"))},supportingText={Text(t("Sin valor predeterminado. Debe ser distinta de cero y usar solo bits de 0x00FFFFF8. No admite administración completa ni los bits 1, 2 y 4.","No default. Must be nonzero and use only bits in 0x00FFFFF8. Full administration and bits 1, 2 and 4 are excluded."))},isError=hex.isNotBlank() && rights==null,singleLine=true,modifier=Modifier.fillMaxWidth())
            }
            if(selected.isNotBlank()) Text(t("Grupo de dispositivos: ","Device group: ")+selected)
            if(review!=null) {
                Text(t("Derechos congelados: ","Frozen rights: ")+java.lang.String.format(java.util.Locale.ROOT,"0x%08X",review.getLong("meshadmin")))
                if(!submitted) Row {Checkbox(approved,{approved=it},enabled=current);Text(t("Confirmo crear esta asignación con estos derechos.","I confirm creating this assignment with these rights."),Modifier.weight(1f))}
            }
            Text(t("Puede conceder acceso heredado a los miembros. Las consultas no son atómicas y no calculan todos los permisos efectivos. No hay reintentos automáticos; cerrar no cancela una solicitud enviada.","May grant inherited access to members. Reads are not atomic and do not calculate all effective rights. No automatic retries; closing does not cancel a sent request."),style=MaterialTheme.typography.bodySmall)
            if(observed) Text(t("Respuesta confirmada y derechos solicitados observados en ambos enlaces.","Reply confirmed and requested rights observed in both links."))
            else if(submitted) Text(if(s.notice=="rejected") t("Solicitud rechazada.","Request rejected.") else t("Enviada; resultado sin verificar. Consulta el estado antes de repetir.","Sent; outcome not verified. Check state before repeating."))
            else if(error || (review!=null && !current)) Text(t("La copia ya no es válida. Vuelve atrás y actualiza.","This copy is no longer valid. Go back and refresh."),color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={if(!submitted) Button(enabled=if(review==null) draft!=null else bound && packet!=null && profileGroupMeshGrantAllowed(s,packet,server),onClick={if(review==null) {frozen=draft?.let {JSONObject(it.toString())};approved=false;error=false} else if(packet!=null && session.submit(packet)) {submitted=true;approved=false} else error=true}) {Text(if(review==null) t("Revisar asignación","Review assignment") else t("Crear asignación","Create assignment"))}},dismissButton={Row {
        if(!submitted) TextButton(onClick={frozen=null;approved=false;error=false;session.refresh();session.loadUserGroupDirectory()}) {Text(t("Volver y actualizar","Back and refresh"))}
        TextButton(onClick=onDismiss) {Text(t("Cerrar","Close"))}
    }})
}
