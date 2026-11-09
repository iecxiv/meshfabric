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

internal fun deviceGroupAdvancedUiDraft(s:ProfileState,baseline:JSONObject?,flags:Long,consent:Long,days:String,server:String):JSONObject?=runCatching {
    require(baseline!=null && days.matches(Regex("[0-9]{1,4}")))
    val expiry=requireNotNull(days.toLongOrNull());require(expiry in 0L..2000L)
    val changes=JSONObject().put("flags",flags).put("consent",consent).put("expireDevs",expiry)
    val packet=deviceGroupAdvancedPacket(s,baseline,changes,server,true)
    require(deviceGroupAdvancedAllowed(s,packet,server))
    packet.put("_advancedConfirmed",false)
}.getOrNull()
internal fun deviceGroupAdvancedUiCurrent(s:ProfileState,review:JSONObject?,server:String)=review!=null && deviceGroupAdvancedAllowed(s,JSONObject(review.toString()).put("_advancedConfirmed",true),server)
private fun advancedHex(value:Long)="0x"+java.lang.Long.toHexString(value).padStart(8,'0')
@Composable internal fun DeviceGroupAdvancedEditor(session:ProfileSession,s:ProfileState,id:String,onDismiss:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    val actor=remember {s.id};val server=remember {session.broadcastServerLabel()}
    val baseline=remember {s.groups.singleOrNull {it.optString("_id")==id}?.let {JSONObject(it.toString())}}
    val actorRecord=remember {s.user?.let {JSONObject(it.toString())}}
    val actorIdentity=profileReviewIdentity(actorRecord,listOfNotNull(s.user)+s.users.filter {it.optString("_id")!=s.id},es)
    val groupIdentity=profileReviewIdentity(baseline,s.groups,es)
    val identityReady=actorIdentity.usable && groupIdentity.usable
    val initial=remember {runCatching {JSONObject(deviceGroupAdvancedSnapshot(requireNotNull(baseline)))}.getOrNull()}
    val mandatory=remember {runCatching {userAdvancedMask(s.server?.opt("consent")) and 511L}.getOrDefault(0L)}
    val mask=remember {runCatching {deviceGroupAdvancedFlagMask(requireNotNull(initial).getInt("mtype"),(s.server?.opt("devGroupSessionRecording") as? Number)?.toDouble()==1.0)}.getOrDefault(0L)}
    var flags by remember {mutableStateOf(initial?.optLong("flags") ?: 0L)}
    var consent by remember {mutableStateOf((initial?.optLong("consent") ?: 0L) or mandatory)}
    var days by remember {mutableStateOf(initial?.optLong("expireDevs")?.toString().orEmpty())}
    var frozen by remember {mutableStateOf<JSONObject?>(null)}
    var approved by remember {mutableStateOf(false)}
    var invalidated by remember {mutableStateOf(false)}
    var submitted by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf(false)}
    val bound=actor==s.id && server.isNotBlank() && server==session.broadcastServerLabel()
    val draft=if(bound && identityReady && initial!=null) deviceGroupAdvancedUiDraft(s,baseline,flags,consent,days,server) else null
    val review=frozen
    val current=bound && identityReady && !invalidated && deviceGroupAdvancedUiCurrent(s,review,server)
    LaunchedEffect(review,current) {if(review!=null && !current && !submitted) {invalidated=true;approved=false}}
    val packet=review?.let {JSONObject(it.toString()).put("_advancedConfirmed",approved)}
    val observed=submitted && s.notice=="deviceGroupAdvancedObserved" && s.advancedEditedDeviceGroup==id
    ManagementDialog(onDismissRequest=onDismiss,modifier=Modifier.widthIn(max=640.dp).fillMaxWidth(0.94f),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn,usePlatformDefaultWidth=false,dismissOnClickOutside=false),title={Text(t("Ajustes avanzados del grupo","Advanced group settings"))},text={
        Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*0.68f).dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(server,style=MaterialTheme.typography.bodySmall)
            Text(t("Actor: ","Actor: ")+actorIdentity.label,style=MaterialTheme.typography.bodySmall)
            Text(groupIdentity.label,style=MaterialTheme.typography.titleSmall)
            if(!identityReady && !submitted) Text(t("El actor o el grupo no se identifican inequívocamente. Cierra, actualiza y vuelve a abrir antes de revisar.","The actor or group cannot be uniquely identified. Close, refresh and reopen before reviewing."),color=MaterialTheme.colorScheme.error)
            if(initial==null) Text(t("Datos incompatibles. Cierra y actualiza.","Incompatible data. Close and refresh."),color=MaterialTheme.colorScheme.error)
            if(review==null && !submitted && initial!=null) {
                Text(t("Características","Features"),style=MaterialTheme.typography.titleSmall)
                val labels=if(es) listOf("Eliminar dispositivos al desconectarse","Sincronizar nombre del equipo","Grabar sesiones","Preferir el nombre del agente","Permitir nombre manual hasta reconexión") else listOf("Remove devices on disconnect","Synchronize host name","Record sessions","Prefer agent name","Allow manual name until reconnect")
                for(i in 0..4) {val bit=1L shl i
                    if(mask and bit!=0L) ProfileAlignedCheckboxRow {
                        Checkbox(flags and bit!=0L,{checked->
                            flags=if(checked) flags or bit else flags and bit.inv()
                            if(bit==2L && !checked && initial.getInt("mtype")==2) flags=flags and 24L.inv()
                            if(bit==1L && checked) days="0"
                        },enabled=identityReady && s.canWrite && !s.detailsBusy && bound && (i<3 || flags and 2L!=0L),modifier=Modifier.testTag("advanced-flag-"+bit))
                        Text(labels[i],Modifier.weight(1f))
                    }
                }
                Text(t("Desactivar la sincronización también desmarca sus opciones dependientes. Activar la retirada al desconectar fija la caducidad específica en cero. Revisa los valores antes de enviar.","Disabling name synchronization also clears its dependent options. Enabling removal on disconnect sets the group-specific expiry to zero. Review values before sending."),style=MaterialTheme.typography.bodySmall)
                OutlinedTextField(days,{days=it.take(16)},label={Text(t("Caducidad por inactividad: días (0–2000)","Inactivity expiry: days (0–2000)"))},singleLine=true,enabled=identityReady && s.canWrite && !s.detailsBusy && bound && flags and 1L==0L,isError=days.toLongOrNull()?.let {it !in 0L..2000L} ?: true,modifier=Modifier.fillMaxWidth().testTag("advanced-days"))
                Text(t("Consentimiento del grupo","Group consent"),style=MaterialTheme.typography.titleSmall)
                val labelsConsent=if(es) listOf("Avisar: escritorio","Avisar: terminal","Avisar: archivos","Solicitar autorización: escritorio","Solicitar autorización: terminal","Solicitar autorización: archivos","Mostrar barra de conexión","Avisar: Registro","Solicitar autorización: Registro") else listOf("Notify: desktop","Notify: terminal","Notify: files","Prompt: desktop","Prompt: terminal","Prompt: files","Show connection toolbar","Notify: Registry","Prompt: Registry")
                for(i in 0..8) {val bit=1L shl i
                    ProfileAlignedCheckboxRow {Checkbox(consent and bit!=0L,{checked->consent=if(checked) consent or bit else consent and bit.inv()},enabled=identityReady && s.canWrite && !s.detailsBusy && bound && mandatory and bit==0L,modifier=Modifier.testTag("advanced-consent-"+bit));Text(labelsConsent[i]+if(mandatory and bit!=0L) t(" (obligatorio por servidor)"," (required by server)") else "",Modifier.weight(1f))}
                }
                if(draft==null) Text(t("No hay un cambio válido en esta copia. Si cambiaron los datos, cierra y actualiza.","No valid change in this copy. If data changed, close and refresh."))
            }
            if(review!=null && initial!=null) {
                Text(t("Revisión congelada: antes → después","Frozen review: before → after"),Modifier.testTag("advanced-review"))
                for(key in listOf("flags","consent","expireDevs")) Text(key+": "+(if(key=="expireDevs") initial.getLong(key).toString()+" → "+review.getLong(key) else advancedHex(initial.getLong(key))+" → "+advancedHex(review.getLong(key))),style=MaterialTheme.typography.bodySmall)
                if(!submitted) ProfileAlignedCheckboxRow {Checkbox(approved,{approved=it},enabled=current,modifier=Modifier.testTag("advanced-confirm"));Text(t("Confirmo aplicar estos valores al grupo y servidor indicados.","I confirm applying these values to this group and server."),Modifier.weight(1f))}
            }
            Text(t("Cero elimina la caducidad específica del grupo; NO garantiza desactivar la eliminación automática global. La retirada al desconectar y la caducidad pueden eliminar dispositivos y sus datos asociados. Comprueba la política del servidor antes de confirmar.","Zero removes the group-specific expiry; it does NOT guarantee disabling global automatic removal. Removal on disconnect and inactivity expiry may delete devices and associated data. Check server policy before confirming."),style=MaterialTheme.typography.bodySmall)
            Text(t("Se conservan los bits desconocidos. El consentimiento se combina con las obligaciones del servidor y otras capas; desmarcarlo no elimina las heredadas. Las comprobaciones y el envío no son atómicos: un cambio concurrente puede ser sobrescrito. No hay reintentos automáticos y cerrar no cancela un envío.","Unknown bits are preserved. Consent combines with server requirements and other layers; clearing it does not remove inherited requirements. Checks and sending are not atomic: concurrent changes may be overwritten. No automatic retries; closing does not cancel a sent request."),style=MaterialTheme.typography.bodySmall)
            if(submitted && s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(observed) Text(t("Acuse confirmado y valores exactos observados.","Acknowledgement confirmed and exact values observed."),Modifier.testTag("advanced-observed"))
            else if(submitted) Text(if(s.notice=="rejected") t("Solicitud rechazada.","Request rejected.") else t("Enviada; resultado aún no verificado. Consulta el estado antes de repetir.","Sent; outcome not yet verified. Check state before repeating."))
            else if(error || (review!=null && !current)) Text(t("Revisión invalidada. Cierra, actualiza y vuelve a abrir.","Review invalidated. Close, refresh and reopen."),color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={if(!submitted) Button(enabled=if(review==null) draft!=null else current && approved,modifier=Modifier.testTag(if(review==null) "advanced-prepare" else "advanced-send"),onClick={
        if(review==null) {frozen=draft?.let {JSONObject(it.toString())};approved=false}
        else if(current && packet!=null && session.submitDeviceGroupAdvanced(packet)) {submitted=true;approved=false} else {error=true;invalidated=true;approved=false}
    }) {Text(if(review==null) t("Revisar cambios","Review changes") else t("Aplicar ajustes","Apply settings"))}},dismissButton={Row {
        if(!submitted) TextButton(onClick={session.refresh();onDismiss()}) {Text(t("Cerrar y actualizar","Close and refresh"))}
        TextButton(onClick=onDismiss) {Text(t("Cerrar","Close"))}
    }})
}
