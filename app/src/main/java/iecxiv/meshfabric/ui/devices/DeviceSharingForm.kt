package iecxiv.meshfabric.ui.devices
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
internal val shareDateFormat:DateTimeFormatter=DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT)
internal fun shareDateSeconds(text:String,zone:ZoneId):Long {
    val date=LocalDateTime.parse(text,shareDateFormat);val offsets=zone.rules.getValidOffsets(date)
    require(offsets.size==1);return date.toEpochSecond(offsets.single())
}
internal data class ShareForm(val guest:String="",val access:ShareAccess=ShareAccess.VIEW_ONLY,val validity:ShareValidity=ShareValidity.NOW,val expiry:Int=15,val duration:Int=15,val start:String="",val end:String="",val consent:ShareConsent=ShareConsent.PROMPT)
internal fun shareInitialForm(zone:ZoneId):ShareForm {
    val start=LocalDateTime.now(zone).plusMinutes(5).withSecond(0).withNano(0)
    return ShareForm(start=start.format(shareDateFormat),end=start.plusHours(1).format(shareDateFormat))
}
internal fun shareFormDraft(f:ShareForm,mesh:String,now:Long,zone:ZoneId)=generalShareOptionsDraft(f.guest,mesh,f.access,f.validity,
    if(f.validity.recurring>0) f.duration else f.expiry,if(f.validity==ShareValidity.NOW) now else shareDateSeconds(f.start,zone),
    if(f.validity==ShareValidity.RANGE) shareDateSeconds(f.end,zone) else 0L,f.consent,now)
internal fun shareAccessLabel(a:ShareAccess,es:Boolean)=if(es) listOf("Escritorio","Escritorio, solo vista","Terminal","Archivos","Escritorio + Archivos","Terminal + Archivos","Escritorio + Terminal + Archivos")[a.ordinal] else listOf("Desktop","Desktop, view only","Terminal","Files","Desktop + Files","Terminal + Files","Desktop + Terminal + Files")[a.ordinal]
internal fun shareValidityLabel(v:ShareValidity,es:Boolean)=if(es) listOf("Comenzando ahora","Intervalo de tiempo","Recurrente diariamente","Semanal recurrente")[v.ordinal] else listOf("Starting now","Time range","Recurring daily","Recurring weekly")[v.ordinal]
internal fun shareConsentLabel(c:ShareConsent,es:Boolean)=if(es) listOf("Solo notificar","Solicitar consentimiento","Sin consentimiento")[c.ordinal] else listOf("Notify only","Prompt for consent","No consent")[c.ordinal]
internal fun shareMinutesLabel(m:Int,es:Boolean):String=when {
    m==0->if(es) "Ilimitado" else "Unlimited"
    m<=60->if(es) "$m minuto"+(if(m==1) "" else "s") else "$m minute"+(if(m==1) "" else "s")
    m<=1440->if(es) "${m/60} horas" else "${m/60} hours"
    else->if(es) "${m/1440} días" else "${m/1440} days"
}
@Composable private fun <T> ShareChoice(label:String,value:T,values:List<T>,tag:String,labelOf:(T)->String,enabled:(T)->Boolean={true},onChange:(T)->Unit) {
    var expanded by remember(label) {mutableStateOf(false)}
    Text(label,style=MaterialTheme.typography.labelLarge)
    OutlinedButton(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth().testTag(tag)) {Text(labelOf(value))}
    if(expanded) values.forEach {option->
        val allowed=enabled(option)
        Row(Modifier.fillMaxWidth().testTag("${tag}_${values.indexOf(option)}").clickable(enabled=allowed) {onChange(option);expanded=false},verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            RadioButton(selected=value==option,onClick=null,enabled=allowed)
            Text(labelOf(option),Modifier.padding(8.dp),color=if(allowed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha=0.38f))
        }
    }
}
@Composable internal fun DeviceSharingForm(f:ShareForm,rights:Long,zone:ZoneId,es:Boolean,onChange:(ShareForm)->Unit) {
    fun t(a:String,b:String)=if(es) a else b
    OutlinedTextField(f.guest,{onChange(f.copy(guest=it))},singleLine=true,label={Text(t("Nombre de invitado","Guest name"))},modifier=Modifier.fillMaxWidth().testTag("share_guest"))
    ShareChoice(t("Tipo","Type"),f.access,ShareAccess.entries.toList(),"share_access",{shareAccessLabel(it,es)},{generalShareAccessAllowed(rights,it)}) {onChange(f.copy(access=it))}
    ShareChoice(t("Validez","Validity"),f.validity,ShareValidity.entries.toList(),"share_validity",{shareValidityLabel(it,es)}) {onChange(f.copy(validity=it))}
    if(f.validity!=ShareValidity.NOW) {
        Text(t("Zona horaria: ","Time zone: ")+zone.id,style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(f.start,{onChange(f.copy(start=it))},singleLine=true,label={Text(t("Inicio · aaaa-MM-dd HH:mm","Start · yyyy-MM-dd HH:mm"))},modifier=Modifier.fillMaxWidth().testTag("share_start"))
        if(f.validity==ShareValidity.RANGE) OutlinedTextField(f.end,{onChange(f.copy(end=it))},singleLine=true,label={Text(t("Final · aaaa-MM-dd HH:mm","End · yyyy-MM-dd HH:mm"))},modifier=Modifier.fillMaxWidth().testTag("share_end"))
        else Text(t("El servidor calcula las ventanas recurrentes. La hora local puede cambiar al cambiar el horario de verano.","The server calculates recurring windows. Local time may change with daylight saving time."),style=MaterialTheme.typography.bodySmall)
    }
    if(f.validity==ShareValidity.NOW) ShareChoice(t("Tiempo de expiración","Expiry time"),f.expiry,SHARE_EXPIRY_MINUTES,"share_expiry",{shareMinutesLabel(it,es)}) {onChange(f.copy(expiry=it))}
    if(f.validity.recurring>0) ShareChoice(t("Duración","Duration"),f.duration,SHARE_DURATION_MINUTES,"share_duration",{shareMinutesLabel(it,es)}) {onChange(f.copy(duration=it))}
    ShareChoice(t("Consentimiento del usuario","User consent"),f.consent,ShareConsent.entries.toList(),"share_consent",{shareConsentLabel(it,es)}) {onChange(f.copy(consent=it))}
    Text(t("La opción elegida no anula las políticas del servidor o del agente. El nombre del invitado es una etiqueta, no una autenticación.","Your choice does not override server or agent policies. The guest name is a label, not authentication."),style=MaterialTheme.typography.bodySmall)
}
@Composable internal fun DeviceSharingReview(d:GeneralShareCreateDraft,consent:ShareConsent,zone:ZoneId,es:Boolean) {
    fun t(a:String,b:String)=if(es) a else b
    fun date(s:Long)=Instant.ofEpochSecond(s).atZone(zone).format(shareDateFormat)
    Text(t("Invitado: ","Guest: ")+d.guest);Text(shareAccessLabel(d.access,es));Text(shareValidityLabel(d.validity,es));Text(shareConsentLabel(consent,es))
    Text(t("Inicio: ","Start: ")+date(d.start)+" · "+zone.id)
    if(d.end>0) Text(t("Final: ","End: ")+date(d.end)) else if(d.validity.recurring>0) Text(shareMinutesLabel(requireNotNull(d.durationMinutes),es)) else Text(t("Sin vencimiento. Revoca el acceso cuando deje de ser necesario.","No expiry. Revoke access when no longer needed."))
    Text(t("El enlace es una credencial: quien lo posea podrá usar el acceso permitido.","The link is a credential: anyone holding it can use its permitted access."),style=MaterialTheme.typography.bodySmall)
    Text(t("Horario fijado al revisar; vuelve a revisar si pasan dos minutos o vence el acceso. El enlace no se envía ni copia automáticamente.","Times fixed at review; review again after two minutes or when access expires. The link is not sent or copied automatically."),style=MaterialTheme.typography.bodySmall)
}
internal val LocalDeviceGeneralOpen=staticCompositionLocalOf<((iecxiv.meshfabric.data.model.MeshNode)->Unit)?> {null}
internal val LocalDeviceSharingOpen=staticCompositionLocalOf<((iecxiv.meshfabric.data.model.MeshNode)->Unit)?> {null}
