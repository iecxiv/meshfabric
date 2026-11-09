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

internal fun profileUserMeshGrantDraft(s:ProfileState,uid:String,mid:String,rights:Long,actor:String,server:String):JSONObject?=runCatching {
    val user=requireNotNull(s.users.singleOrNull {it.optString("_id")==uid})
    val mesh=requireNotNull(s.groups.singleOrNull {it.optString("_id")==mid})
    val packet=profileUserMeshGrantPacket(actor,server,user,mesh,rights,true)
    require(profileUserMeshGrantAllowed(s,packet,server))
    packet.put("_userGrantConfirmed",false)
}.getOrNull()
@Composable internal fun UserMeshGrantDialog(session:ProfileSession,s:ProfileState,uid:String,onDismiss:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    val actor=remember {s.id};val server=remember {session.broadcastServerLabel()}
    var query by remember {mutableStateOf("")}
    var selected by remember {mutableStateOf("")}
    var selectedMesh by remember {mutableStateOf<JSONObject?>(null)}
    var hex by remember {mutableStateOf("")}
    var frozen by remember {mutableStateOf<JSONObject?>(null)}
    var approved by remember {mutableStateOf(false)}
    var submitted by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf(false)}
    val bound=actor==s.id && server.isNotBlank() && server==session.broadcastServerLabel()
    val subject=remember {s.users.singleOrNull {it.optString("_id")==uid}?.let {JSONObject(it.toString())}}
    val subjectIdentity=profileReviewIdentity(subject,s.users,es)
    val meshIdentity=profileReviewIdentity(selectedMesh,s.groups,es)
    val identityReady=subjectIdentity.usable && meshIdentity.usable
    var invalidated by remember {mutableStateOf(false)}
    val rights=profileGroupMeshGrantHex(hex)
    val draft=if(bound && identityReady && rights!=null && profileGroupChoiceUsable(s.groups.singleOrNull {it.optString("_id")==selected},s.groups)) profileUserMeshGrantDraft(s,uid,selected,rights,actor,server) else null
    val review=frozen
    val packet=review?.let {JSONObject(it.toString()).put("_userGrantConfirmed",approved)}
    val current=bound && identityReady && !invalidated && profileGroupChoiceUsable(s.groups.singleOrNull {it.optString("_id")==selected},s.groups) && review!=null && profileUserMeshGrantAllowed(s,JSONObject(review.toString()).put("_userGrantConfirmed",true),server)
    LaunchedEffect(review,current) {if(review!=null && !current && !submitted) {approved=false;invalidated=true}}
    val observed=submitted && s.notice=="userMeshGrantedObserved" && s.grantedDirectUser==uid && s.grantedDirectMesh==selected
    val candidates=if(bound && !submitted && review==null) s.groups.filter {profileUserMeshGrantDraft(s,uid,it.optString("_id"),8L,actor,server)!=null}.filter {deviceGroupMatches(query,it.optString("name"),it.optString("desc"))} else emptyList()
    ManagementDialog(onDismissRequest=onDismiss,modifier=Modifier.widthIn(max=640.dp).fillMaxWidth(0.94f),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn,usePlatformDefaultWidth=false,dismissOnClickOutside=false),title={Text(t("Nuevo acceso directo","New direct access"))},text={
        Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*0.68f).dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(server,style=MaterialTheme.typography.bodySmall);Text(t("Usuario: ","User: ")+subjectIdentity.label,style=MaterialTheme.typography.bodySmall)
            if(!subjectIdentity.usable || (selected.isNotBlank() && !meshIdentity.usable)) Text(t("No se puede confirmar: falta un nombre o los datos visibles no distinguen inequívocamente los destinos. Cierra y actualiza la ficha.","Cannot confirm: a name is missing or visible information does not uniquely identify the targets. Close and refresh the details."),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            Text(t("Solo altas directas. Se bloquea el envío si los datos revisados contienen cualquiera de los dos enlaces. Se exige autoridad directa completa sobre el grupo de dispositivos.","Direct initial grants only. Sending is blocked if reviewed data contains either link. Direct full authority over the device group is required."),style=MaterialTheme.typography.bodySmall)
            if(s.loading || s.detailsBusy || (submitted && s.busy)) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(review==null && !submitted) {
                OutlinedTextField(query,{query=it.take(256)},label={Text(t("Buscar grupo de dispositivos","Search device groups"))},singleLine=true,modifier=Modifier.fillMaxWidth())
                for(mesh in candidates.take(100)) {val mid=mesh.optString("_id");TextButton(modifier=Modifier.testTag("direct-grant-candidate-"+mid),enabled=subjectIdentity.usable && profileGroupChoiceUsable(mesh,s.groups) && profileReviewIdentity(mesh,s.groups,es).usable,onClick={selected=mid;selectedMesh=JSONObject(mesh.toString());error=false}) {Text((if(selected==mid) "● " else "○ ")+profileReviewIdentity(mesh,s.groups,es).label)}}
                if(candidates.any {!profileGroupChoiceUsable(it,s.groups) || !profileReviewIdentity(it,s.groups,es).usable}) Text(t("Hay grupos sin nombre o sin datos suficientes para distinguirlos. Actualiza o asigna un nombre o descripción inequívocos antes de seleccionarlos.","Some groups have no name or cannot be distinguished with the received data. Refresh or give them an unambiguous name or description before selecting them."),style=MaterialTheme.typography.bodySmall)
                if(candidates.isEmpty()) Text(t("No hay candidatos elegibles en los datos actuales. Actualiza o revisa permisos y enlaces recibidos.","No eligible candidates in current data. Refresh or review permissions and received links."))
                if(candidates.size>100) Text(t("Primeros 100 resultados. Acota la búsqueda.","First 100 results. Narrow the search."))
                Text(t("Selección: ","Selection: ")+(rights?.let {deviceGroupRightsLabel(it,es)} ?: if(hex.isBlank()) t("Sin permisos seleccionados","No rights selected") else t("Máscara no válida: corrígela en Avanzado","Invalid mask: correct it in Advanced")),style=MaterialTheme.typography.bodySmall)
                Text(t("Las opciones incluyen permisos y restricciones. Marcar una restricción no equivale a conceder el acceso correspondiente. No se añaden dependencias automáticamente.","Options include rights and restrictions. Selecting a restriction does not grant the corresponding access. Dependencies are not added automatically."),style=MaterialTheme.typography.bodySmall)
                ProfileDisclosure(t("Seleccionar permisos y restricciones","Select rights and restrictions")) {
                    for(index in 3..23) {
                        val bit=1L shl index
                        Row {
                            Checkbox(checked=(rights ?: 0L) and bit!=0L,enabled=bound && s.canWrite && (hex.isBlank() || rights!=null),onCheckedChange={checked->
                                profileGroupMeshToggleGrantBit(rights ?: 0L,bit,checked)?.let {value->hex=if(value==0L) "" else java.lang.Long.toHexString(value);approved=false;error=false}
                            })
                            Text(deviceGroupRightsLabel(bit,es),Modifier.weight(1f))
                        }
                    }
                }
                ProfileDisclosure(t("Avanzado: máscara hexadecimal","Advanced: hexadecimal mask")) {
                OutlinedTextField(hex,{hex=it.take(256);error=false},label={Text(t("Derechos: máscara hexadecimal","Rights: hexadecimal mask"))},supportingText={Text(t("Sin valor predeterminado. Debe ser distinta de cero y usar solo bits de 0x00FFFFF8. No admite administración completa ni los bits 1, 2 y 4.","No default. Must be nonzero and use only bits in 0x00FFFFF8. Full administration and bits 1, 2 and 4 are excluded."))},isError=hex.isNotBlank() && rights==null,singleLine=true,modifier=Modifier.fillMaxWidth().testTag("direct-grant-hex"))
                }
            }
            if(selected.isNotBlank()) Text(t("Grupo de dispositivos: ","Device group: ")+meshIdentity.label)
            if(review!=null) {
                Text(t("Derechos congelados: ","Frozen rights: ")+java.lang.String.format(java.util.Locale.ROOT,"0x%08X",review.getLong("meshadmin")))
                Text(deviceGroupRightsLabel(review.getLong("meshadmin"),es),style=MaterialTheme.typography.bodySmall)
                if(!submitted) Row {Checkbox(approved,{approved=it},enabled=current,modifier=Modifier.testTag("direct-grant-consent"));Text(t("Confirmo crear esta asignación con estos derechos.","I confirm creating this assignment with these rights."),Modifier.weight(1f))}
            }
            Text(t("Crea acceso directo para este usuario. No calcula todos sus permisos efectivos ni modifica los accesos heredados. Las comprobaciones y el envío no son atómicos: el servidor puede sobrescribir una asignación creada concurrentemente. No hay reintentos automáticos; cerrar no cancela una solicitud enviada.","Creates direct access for this user. Does not calculate all effective rights or modify inherited assignments. Checks and sending are not atomic: the server may overwrite an assignment created concurrently. No automatic retries; closing does not cancel a sent request."),style=MaterialTheme.typography.bodySmall)
            if(observed) Text(t("Respuesta confirmada y derechos solicitados observados en ambos enlaces.","Reply confirmed and requested rights observed in both links."))
            else if(submitted) Text(if(s.notice=="rejected") t("Solicitud rechazada.","Request rejected.") else t("Enviada; resultado sin verificar. Consulta el estado antes de repetir.","Sent; outcome not verified. Check state before repeating."))
            else if(error || (review!=null && !current)) Text(t("La copia ya no es válida. Vuelve atrás y actualiza.","This copy is no longer valid. Go back and refresh."),color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={if(!submitted) Button(modifier=Modifier.testTag(if(review==null) "direct-grant-prepare" else "direct-grant-send"),enabled=if(review==null) draft!=null else current && packet!=null && profileUserMeshGrantAllowed(s,packet,server),onClick={if(review==null) {frozen=draft?.let {JSONObject(it.toString())};approved=false;error=false} else if(packet!=null && current && session.submitUserMeshGrant(packet)) {submitted=true;approved=false} else error=true}) {Text(if(review==null) t("Revisar asignación","Review assignment") else t("Crear asignación","Create assignment"))}},dismissButton={Row {
        if(!submitted) TextButton(onClick={frozen=null;approved=false;invalidated=false;error=false;session.refresh()}) {Text(t("Volver y actualizar","Back and refresh"))}
        TextButton(onClick=onDismiss) {Text(t("Cerrar","Close"))}
    }})
}
