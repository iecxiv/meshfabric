package iecxiv.meshfabric.ui.management

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import iecxiv.meshfabric.data.remote.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

internal data class AgentInviteLinkOptions(val hours:Int=1,val agents:Int=0,val flags:Int=0) {
    init {require(hours in expirations && agents in agentKinds && flags in installationModes)}
    val showsType:Boolean get()=agents in setOf(0,1,2,4)
    val effectiveFlags:Int get()=if(showsType) flags else 0
    fun selectAgent(value:Int)=copy(agents=value,flags=if(value in setOf(8,16)) 0 else flags)
    fun packet(mesh:String)=AgentInvitationContract.groupLink(mesh,hours,effectiveFlags,agents,UUID.randomUUID().toString())
    companion object {val expirations=listOf(1,8,24,168,720,0);val agentKinds=listOf(0,1,2,4,8,16);val installationModes=listOf(0,2,1)}
}
private fun expiryLabel(v:Int,es:Boolean)=if(es) mapOf(1 to "1 h",8 to "8 h",24 to "1 día",168 to "1 semana",720 to "1 mes",0 to "Ilimitado").getValue(v) else mapOf(1 to "1 hour",8 to "8 hours",24 to "1 day",168 to "1 week",720 to "1 month",0 to "Unlimited").getValue(v)
private fun agentLabel(v:Int,es:Boolean)=if(es) mapOf(0 to "Todos los agentes disponibles",1 to "Agente de malla de Windows",2 to "Agente de malla de Linux",4 to "Agente de malla de MacOS",8 to "Asistente MeshCentral",16 to "Android MeshAgent").getValue(v) else mapOf(0 to "All available agents",1 to "Windows MeshAgent",2 to "Linux MeshAgent",4 to "MacOS MeshAgent",8 to "MeshCentral Assistant",16 to "Android MeshAgent").getValue(v)
internal fun modeLabel(v:Int,es:Boolean)=if(es) mapOf(0 to "Segundo plano e interactivo",2 to "Segundo plano solamente",1 to "Solo interactivo").getValue(v) else mapOf(0 to "Background and interactive",2 to "Background only",1 to "Interactive only").getValue(v)
@Composable internal fun InviteChoice(label:String,tag:String,value:Int,values:List<Int>,enabled:Boolean,text:(Int)->String,onSelect:(Int)->Unit) {
    var expanded by remember(tag) {mutableStateOf(false)}
    LaunchedEffect(enabled) {if(!enabled) expanded=false}
    Text(label,style=MaterialTheme.typography.labelMedium)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width=maxWidth
        OutlinedButton(onClick={expanded=true},enabled=enabled,modifier=Modifier.fillMaxWidth().testTag(tag)) {Text(text(value))}
        DropdownMenu(expanded=expanded && enabled,onDismissRequest={expanded=false},modifier=Modifier.width(width).testTag(tag+"-menu")) {values.forEach {v->DropdownMenuItem(text={Text(text(v))},modifier=Modifier.testTag(tag+"-"+v),onClick={expanded=false;onSelect(v)})}}
    }
}
@Composable internal fun AgentInviteLinkForm(groupName:String,options:AgentInviteLinkOptions,enabled:Boolean,busy:Boolean,es:Boolean,onChange:(AgentInviteLinkOptions)->Unit,onGenerate:()->Unit) {
    fun t(a:String,b:String)=if(es) a else b
    Column(Modifier.fillMaxWidth()) {
        Text(groupName,style=MaterialTheme.typography.titleSmall)
        Text(t("El enlace permite instalar el agente para incorporarse a este grupo.","The link allows agent installation into this device group."),style=MaterialTheme.typography.bodySmall)
        InviteChoice(t("Expiración de enlace","Link expiration"),"invite-expiry",options.hours,AgentInviteLinkOptions.expirations,enabled && !busy,{expiryLabel(it,es)}) {onChange(options.copy(hours=it))}
        if(options.hours==720) Text(t("1 mes equivale a 30 días (720 horas).","1 month means 30 days (720 hours)."),style=MaterialTheme.typography.bodySmall)
        InviteChoice(t("Agentes","Agents"),"invite-agent",options.agents,AgentInviteLinkOptions.agentKinds,enabled && !busy,{agentLabel(it,es)}) {onChange(options.selectAgent(it))}
        if(options.showsType) InviteChoice(t("Tipo de instalación","Installation type"),"invite-mode",options.flags,AgentInviteLinkOptions.installationModes,enabled && !busy,{modeLabel(it,es)}) {onChange(options.copy(flags=it))}
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        TextButton(onClick=onGenerate,enabled=enabled && !busy,modifier=Modifier.fillMaxWidth().testTag("invite-generate")) {Text(t("Generar enlace","Generate link"))}
    }
}
internal fun receivedInviteGroup(s:ProfileState,id:String,device:Boolean,invitation:Boolean=true):JSONObject? {
    if(!s.canWrite || s.detailsBusy) return null
    val mid=if(device) s.nodes?.singleOrNull {it.optString("_id")==id}?.optString("meshid") ?: return null else id
    val target=runCatching {AgentInvitationContract.installationTarget(s.id,mid)}.getOrNull() ?: return null
    val group=s.groups.singleOrNull {it.optString("_id")==target && it.optInt("mtype")==2} ?: return null
    val subjects=mutableSetOf(s.id)
    s.user?.optJSONObject("links")?.keys()?.forEach {if(it.startsWith("ugrp/")) subjects.add(it)}
    var rights=0L
    for(subject in subjects) {val raw=group.optJSONObject("links")?.optJSONObject(subject)?.opt("rights");if(raw is Number) rights=rights or runCatching {userAdvancedMask(raw)}.getOrDefault(0L)}
    return group.takeIf {AgentInvitationContract.groupAllowed(s.site,rights,2,false,invitation)}
}
@Composable internal fun AgentInviteGroupButton(source:MeshCentralClient,id:String,enabled:Boolean) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    var open by remember(id) {mutableStateOf(false)}
    ProfileGroupActionButton(if(es) "Invitar" else "Invite",Icons.Default.PersonAdd,enabled,onClick={open=true})
    if(open) AgentInviteLinkDialog(source,id,false) {open=false}
}
@Composable internal fun AgentInviteLinkDialog(source:MeshCentralClient,id:String,device:Boolean,enrollment:Boolean=false,onDismiss:()->Unit) {
    val connected by source.connectionState.collectAsState()
    val user by source.userInfo.collectAsState()
    val binding=remember(source,source.serverUrl,source.authCookie,source.ignoreSsl,user?.id) {InvitationBinding(source.serverUrl,user?.id.orEmpty(),source.authCookie,source.ignoreSsl)}
    key(binding,id,device) {
        val es=LocalConfiguration.current.locales[0].language=="es"
        fun t(a:String,b:String)=if(es) a else b
        val context=LocalContext.current;val scope=rememberCoroutineScope();val lifecycle=LocalLifecycleOwner.current.lifecycle
        val profile=remember(source) {ProfileSession(source)};val s by profile.state.collectAsState()
        var foreground by remember {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
        var options by remember {mutableStateOf(AgentInviteLinkOptions())};var busy by remember {mutableStateOf(false)}
        var channel by remember {mutableStateOf<AgentInvitationChannel?>(null)};var version by remember {mutableLongStateOf(0)}
        var link by remember {mutableStateOf<AgentInvitationOutcome.GroupLink?>(null)};var notice by remember {mutableStateOf("")}
        fun invalidate() {version++;val previous=channel;channel=null;previous?.close();busy=false;link=null}
        DisposableEffect(profile,lifecycle) {
            val observer=LifecycleEventObserver {_,_->foreground=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED);if(!foreground) invalidate()}
            lifecycle.addObserver(observer)
            onDispose {lifecycle.removeObserver(observer);invalidate();profile.close()}
        }
        LaunchedEffect(profile,lifecycle,connected) {lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {if(connected && !source.isUserLogout) {profile.start();try {awaitCancellation()} finally {profile.stop()}} else profile.stop()}}
        LaunchedEffect(profile,s.ready) {if(s.ready && device) profile.loadGroupDetails()}
        val group=receivedInviteGroup(s,id,device,invitation=!enrollment)
        LaunchedEffect(group?.toString(),s.id,connected) {invalidate()}
        val enabled=connected && foreground && binding.matches(source) && s.id==binding.actor && group!=null
        fun generate() {
            if(!enabled || busy || group==null) return
            invalidate();notice="";version++;val ticket=version;val selected=options
            val payload=selected.packet(group.optString("_id"))
            val created=AgentInvitationChannel.fromSource(source) {delivery->scope.launch {
                if(ticket!=version || !foreground || !binding.matches(source)) return@launch
                busy=false;channel=null
                if(receivedInviteGroup(profile.state.value,id,device)==null) {link=null;notice=t("El grupo o los permisos han cambiado; resultado sin confirmar.","Group or permissions changed; outcome unconfirmed.");return@launch}
                when(delivery) {
                    is InvitationDelivery.Reply->when(val outcome=delivery.outcome) {
                        is AgentInvitationOutcome.GroupLink->if(outcome.hours==selected.hours) {link=outcome;notice=t("Enlace generado.","Link generated.")} else {notice=t("Caducidad inesperada; no se confirma el enlace.","Unexpected expiration; link not confirmed.")}
                        is AgentInvitationOutcome.Rejected->notice=t("Solicitud rechazada: ","Request rejected: ")+outcome.reason
                        else->notice=t("Respuesta inesperada; resultado sin confirmar.","Unexpected response; outcome unconfirmed.")
                    }
                    InvitationDelivery.NotSent->notice=t("La solicitud no se ha enviado.","The request was not sent.")
                    InvitationDelivery.ContextChanged->notice=t("Cuenta, conexión o permisos modificados; resultado sin confirmar.","Account, connection or permissions changed; outcome unconfirmed.")
                    InvitationDelivery.Unconfirmed->notice=t("Resultado sin confirmar. No se reenviará automáticamente.","Outcome unconfirmed. No automatic resend.")
                }
            }}
            if(created==null) {notice=t("Conexión no disponible.","Connection unavailable.");return}
            channel=created;busy=true
            if(!created.start(payload)) {version++;channel=null;created.close();busy=false;notice=t("La solicitud no se ha enviado.","The request was not sent.")}
        }
        ManagementDialog(onDismissRequest={invalidate();onDismiss()},modifier=Modifier.widthIn(max=640.dp).fillMaxWidth(0.94f),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn,usePlatformDefaultWidth=false),title={Text(if(enrollment) t("Agregar agente","Add agent") else t("Invitar","Invite"))},text={
            Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*0.68f).dp).verticalScroll(rememberScrollState())) {
                if(s.loading || s.detailsBusy || !s.ready) LinearProgressIndicator(Modifier.fillMaxWidth())
                if(enrollment) AgentEnrollmentContent(source,group,s.server,enabled) else AgentInviteLinkForm(group?.optString("name").orEmpty(),options,enabled,busy,es,onChange={invalidate();options=it;notice=""},onGenerate={generate()})
                if(!enabled) Text(t("Esperando un grupo de agentes y permisos válidos. No se adivina el grupo del dispositivo.","Waiting for a valid agent group and permissions. The device group is not guessed."),style=MaterialTheme.typography.bodySmall)
                if(notice.isNotBlank()) Text(notice,style=MaterialTheme.typography.bodySmall)
                link?.let {generated->SelectionContainer {Text(generated.url,style=MaterialTheme.typography.bodySmall)};TextButton(onClick={
                    val copied=runCatching {val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: error("Clipboard unavailable");clipboard.setPrimaryClip(ClipData.newPlainText(t("Invitación de instalación","Agent installation invitation"),generated.url))}.isSuccess
                    notice=if(copied) t("Enlace copiado.","Link copied.") else t("No se ha podido copiar el enlace.","Could not copy the link.")
                }) {Text(t("Copiar enlace","Copy link"))}}
            }
        },confirmButton={},dismissButton={TextButton(onClick={invalidate();onDismiss()}) {Text(t("Cerrar","Close"))}})
    }
}
