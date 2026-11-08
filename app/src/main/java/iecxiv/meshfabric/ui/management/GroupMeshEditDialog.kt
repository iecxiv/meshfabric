package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import org.json.JSONObject

internal fun profileGroupMeshEditHex(text:String):Long?=runCatching {
    val raw=text.trim().removePrefix("0x").removePrefix("0X")
    require(raw.matches(Regex("[0-9a-fA-F]{1,8}")))
    raw.toLong(16).also {require(it and USER_MESH_EDITABLE_MASK==it)}
}.getOrNull()
internal fun profileGroupMeshEditBaseline(s:ProfileState,gid:String,mid:String,actor:String,server:String):JSONObject?=runCatching {
    val subject=requireNotNull(s.listedUserGroups?.singleOrNull {it.optString("_id")==gid})
    val mesh=requireNotNull(s.groups.singleOrNull {it.optString("_id")==mid})
    val a=subject.getJSONObject("links").getJSONObject(mid).get("rights")
    val b=mesh.getJSONObject("links").getJSONObject(gid).get("rights")
    require(a is Number && b is Number)
    val before=userAdvancedMask(a)
    require(before!=0xffffffffL && before==userAdvancedMask(b))
    profileGroupMeshEditPacket(actor,server,subject,mesh,before,false)
}.getOrNull()
internal fun profileGroupMeshEditDraft(s:ProfileState,baseline:JSONObject?,selected:Long?,server:String):JSONObject?=runCatching {
    require(baseline!=null && selected!=null)
    val rights=requireNotNull(profileGroupMeshEditedRights(baseline.getLong("meshadmin"),selected))
    val packet=JSONObject(baseline.toString()).put("meshadmin",rights).put("_groupEditConfirmed",true)
    require(profileGroupMeshEditAllowed(s,packet,server))
    packet.put("_groupEditConfirmed",false)
}.getOrNull()
private fun groupEditMask(value:Long)=java.lang.String.format(java.util.Locale.ROOT,"0x%08X",value)
@Composable internal fun GroupMeshEditDialog(session:ProfileSession,s:ProfileState,gid:String,mid:String,onDismiss:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    val actor=remember {s.id};val server=remember {session.broadcastServerLabel()}
    val baseline=remember {profileGroupMeshEditBaseline(s,gid,mid,actor,server)}
    val before=baseline?.getLong("meshadmin")
    var hex by remember {mutableStateOf(before?.let {java.lang.Long.toHexString(it and USER_MESH_EDITABLE_MASK)}.orEmpty())}
    var frozen by remember {mutableStateOf<JSONObject?>(null)}
    var approved by remember {mutableStateOf(false)}
    var invalidated by remember {mutableStateOf(false)}
    var submitted by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf(false)}
    val bound=actor==s.id && server.isNotBlank() && server==session.broadcastServerLabel()
    val selected=profileGroupMeshEditHex(hex)
    val after=if(before!=null && selected!=null) profileGroupMeshEditedRights(before,selected) else null
    val draft=if(bound) profileGroupMeshEditDraft(s,baseline,selected,server) else null
    val review=frozen
    val current=bound && !invalidated && review!=null && profileGroupMeshEditAllowed(s,JSONObject(review.toString()).put("_groupEditConfirmed",true),server)
    LaunchedEffect(review,current) {if(review!=null && !current && !submitted) {approved=false;invalidated=true}}
    val packet=review?.let {JSONObject(it.toString()).put("_groupEditConfirmed",approved)}
    val observed=submitted && s.notice=="groupMeshEditedObserved" && s.editedGroup==gid && s.editedDeviceGroup==mid
    ManagementDialog(onDismissRequest=onDismiss,modifier=Modifier.widthIn(max=640.dp).fillMaxWidth(0.94f),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn,usePlatformDefaultWidth=false,dismissOnClickOutside=false),title={Text(t("Editar derechos de asignación","Edit assignment rights"))},text={
        Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*0.68f).dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(server,style=MaterialTheme.typography.bodySmall)
            Text(t("Actor: ","Actor: ")+actor,style=MaterialTheme.typography.bodySmall)
            Text(t("Grupo de usuarios: ","User group: ")+gid,style=MaterialTheme.typography.bodySmall)
            Text(t("Grupo de dispositivos: ","Device group: ")+mid,style=MaterialTheme.typography.bodySmall)
            Text(t("Solo modifica una asignación existente y coherente entre este grupo de usuarios y este grupo de dispositivos. Puede cambiar el acceso heredado de sus miembros. No modifica sus enlaces directos ni calcula todos los permisos efectivos.","Only edits an existing consistent assignment between this user group and this device group. May change inherited access for its members. Does not modify their direct links or calculate all effective rights."),style=MaterialTheme.typography.bodySmall)
            if(s.loading || s.detailsBusy || s.userGroupPhase=="loading" || (submitted && s.busy)) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(before!=null) {
                Text(groupEditMask(before),Modifier.testTag("group-edit-before"))
                Text(deviceGroupRightsLabel(before,es),style=MaterialTheme.typography.bodySmall)
                Text(t("Bits conservados fuera de la parte editable: ","Preserved bits outside the editable portion: ")+groupEditMask(before and USER_MESH_EDITABLE_MASK.inv() and 0xffffffffL),style=MaterialTheme.typography.bodySmall)
            } else Text(t("Copia no editable: faltan enlaces coherentes o se trata de administración completa.","Copy cannot be edited: consistent links are missing or the assignment has full administration."),color=MaterialTheme.colorScheme.error)
            if(review==null && !submitted) {
                OutlinedTextField(hex,{hex=it.take(32);approved=false;error=false},label={Text(t("Parte editable: hexadecimal","Editable portion: hexadecimal"))},supportingText={Text(t("Se precarga la parte editable recibida; no son permisos predeterminados. Solo admite bits de 0x00FFFFF8. Los demás se conservan. Una máscara final cero requiere la acción de retirada.","Preloads the received editable portion, not default permissions. Only bits in 0x00FFFFF8 are accepted. Other bits are preserved. A zero final mask requires the revocation action."))},isError=selected==null,singleLine=true,enabled=bound && s.canWrite && !s.detailsBusy && s.userGroupPhase=="ready",modifier=Modifier.fillMaxWidth().testTag("group-edit-hex"))
                Text(t("Las opciones incluyen permisos y restricciones. No se añaden dependencias automáticamente.","Options include rights and restrictions. Dependencies are not added automatically."),style=MaterialTheme.typography.bodySmall)
                ProfileDisclosure(t("Seleccionar permisos y restricciones","Select rights and restrictions")) {
                    for(index in 3..23) {val bit=1L shl index
                        Row {
                            Checkbox(checked=(selected ?: 0L) and bit!=0L,enabled=bound && s.canWrite && !s.detailsBusy && s.userGroupPhase=="ready" && selected!=null,onCheckedChange={checked->
                                val value=selected ?: 0L
                                hex=java.lang.Long.toHexString(if(checked) value or bit else value and bit.inv());approved=false;error=false
                            },modifier=Modifier.testTag("group-edit-bit-"+bit))
                            Text(deviceGroupRightsLabel(bit,es),Modifier.weight(1f))
                        }
                    }
                }
                if(after!=null) {Text(groupEditMask(after),Modifier.testTag("group-edit-after"));Text(deviceGroupRightsLabel(after,es),style=MaterialTheme.typography.bodySmall)}
                if(draft==null) Text(t("No hay un cambio permitido en esta copia. Si cambiaron los datos o faltan permisos, cierra, actualiza y vuelve a abrir.","No permitted change in this copy. If data changed or permissions are missing, close, refresh and reopen."),style=MaterialTheme.typography.bodySmall)
            }
            if(review!=null) {
                Text(t("Máscara propuesta congelada: ","Frozen proposed mask: ")+groupEditMask(review.getLong("meshadmin")),Modifier.testTag("group-edit-frozen"))
                Text(deviceGroupRightsLabel(review.getLong("meshadmin"),es),style=MaterialTheme.typography.bodySmall)
                if(!submitted) Row {Checkbox(approved,{approved=it},enabled=current,modifier=Modifier.testTag("group-edit-consent"));Text(t("Confirmo sustituir los derechos de esta asignación entre grupos por esta máscara, conservando los bits no editables.","I confirm replacing the rights of this group assignment with this mask, preserving noneditable bits."),Modifier.weight(1f))}
            }
            Text(t("Las comprobaciones y el envío no son atómicos: una modificación concurrente puede ser sobrescrita; si se retira el acceso concurrentemente, el servidor puede volver a crearlo. No hay reintentos automáticos y cerrar no cancela una solicitud enviada.","Checks and sending are not atomic: a concurrent change may be overwritten; if access is concurrently revoked, the server may recreate it. No automatic retries; closing does not cancel a sent request."),style=MaterialTheme.typography.bodySmall)
            if(observed) Text(t("Acuse confirmado y derechos exactos observados en ambos enlaces.","Acknowledgement confirmed and exact rights observed in both links."))
            else if(submitted) Text(if(s.notice=="rejected") t("Solicitud rechazada.","Request rejected.") else t("Enviada; resultado sin verificar. Consulta el estado antes de repetir.","Sent; outcome not verified. Check state before repeating."))
            else if(error || (review!=null && !current)) Text(t("Revisión invalidada. Cierra, actualiza y abre una copia nueva.","Review invalidated. Close, refresh and open a new copy."),color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={if(!submitted) Button(modifier=Modifier.testTag(if(review==null) "group-edit-prepare" else "group-edit-send"),enabled=if(review==null) draft!=null else current && packet!=null && profileGroupMeshEditAllowed(s,packet,server),onClick={
        if(review==null) {frozen=draft?.let {JSONObject(it.toString())};approved=false;invalidated=false;error=false}
        else if(packet!=null && current && session.submitGroupMeshEdit(packet)) {submitted=true;approved=false} else error=true
    }) {Text(if(review==null) t("Revisar cambio","Review change") else t("Guardar derechos de asignación","Save assignment rights"))}},dismissButton={Row {
        if(!submitted) TextButton(onClick={session.refresh();session.loadUserGroupDirectory();onDismiss()}) {Text(t("Cerrar y actualizar","Close and refresh"))}
        TextButton(onClick=onDismiss) {Text(t("Cerrar","Close"))}
    }})
}
