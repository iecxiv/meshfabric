package iecxiv.meshfabric.ui.management

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.awaitCancellation

internal enum class NoticeSection { INBOX, BROADCAST, MESSAGES }

@Composable internal fun NoticeCenterTabs(section:NoticeSection,onSelect:(NoticeSection)->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    ManagementSectionTabs(listOf(if(es) "Avisos" else "Notices", "Broadcast", if(es) "Mensajes" else "Messages"),section.ordinal) {
        NoticeSection.entries.getOrNull(it)?.let(onSelect)
    }
}

@Composable internal fun NoticeCenterScreen(source:MeshCentralClient,inbox:NoticeInbox,
    actionsReady:Boolean,nodes:List<MeshNode>,modifier:Modifier=Modifier,
    onDeviceMessage:(MeshNode)->Unit,header:@Composable (@Composable ()->Unit)->Unit) {
    val actor by source.userInfo.collectAsState()
    val connected by source.connectionState.collectAsState()
    key(source,source.serverUrl,source.authCookie,source.ignoreSsl,actor?.id,connected) {
        NoticeCenterContent(source,inbox,actionsReady,nodes,modifier,onDeviceMessage,header)
    }
}

@Composable private fun NoticeCenterContent(source:MeshCentralClient,inbox:NoticeInbox,
    actionsReady:Boolean,nodes:List<MeshNode>,modifier:Modifier,
    onDeviceMessage:(MeshNode)->Unit,header:@Composable (@Composable ()->Unit)->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    var section by remember {mutableStateOf(NoticeSection.INBOX)}
    var broadcast by remember {mutableStateOf(false)}
    var recipient by remember {mutableStateOf<String?>(null)}
    var searchState by remember {mutableStateOf(NoticeSearchState())}
    val search=searchState.query
    var devices by remember {mutableStateOf(false)}
    val recipientGroups by source.groupedDevices.collectAsState()
    val groupNames=recipientGroups.flatMap { (group,members)->members.map {it.id to group.name} }.toMap()
    val session=remember(source) {ProfileSession(source)}
    val state by session.state.collectAsState()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val connected by source.connectionState.collectAsState()
    DisposableEffect(session) {onDispose {session.close()}}
    LaunchedEffect(session,lifecycle,connected,section) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if(connected && !source.isUserLogout && section!=NoticeSection.INBOX) {
                session.start()
                try {awaitCancellation()} finally {session.stop()}
            } else session.stop()
        }
    }
    LaunchedEffect(state.ready) {if(!state.ready) {broadcast=false;recipient=null}}
    BackHandler(enabled=section==NoticeSection.MESSAGES && searchState.open) {searchState=searchState.cleared()}
    Column(modifier) {
        header {if(section==NoticeSection.MESSAGES) NoticeSearchAction(searchState) {searchState=searchState.toggle()}}
        NoticeCenterTabs(section) {
            broadcast=false;recipient=null;searchState=searchState.cleared();section=it
        }
        when(section) {
            NoticeSection.INBOX->NoticeInboxScreen(inbox,actionsReady,Modifier.weight(1f).fillMaxWidth())
            NoticeSection.BROADCAST->NoticeManagementBody(state,session::start,Modifier.weight(1f).fillMaxWidth()) {
                Text(t("Difusión a sesiones conectadas","Broadcast to connected sessions"),style=MaterialTheme.typography.titleMedium)
                Text(t("El diálogo de revisión muestra el servidor, dominio y alcance efectivo. No es correo ni acceso temporal.","The review dialog shows the server, domain and effective scope. This is not email or temporary access."))
                Text(t("Aceptar la solicitud no confirma recepción ni lectura. No se reenvía automáticamente.","Request acceptance does not confirm receipt or reading. No automatic resend."),style=MaterialTheme.typography.bodySmall)
                NoticeSubmissionStatus(state.notice,es)
                Button(enabled=state.canWrite && profileBroadcastRoleAllowed(state.site),onClick={broadcast=true}) {Text(t("Redactar Broadcast","Compose Broadcast"))}
                if(state.ready && !profileBroadcastRoleAllowed(state.site)) Text(t("Sin permiso para Broadcast.","No permission to broadcast."))
            }
            NoticeSection.MESSAGES->NoticeManagementBody(state,session::start,Modifier.weight(1f).fillMaxWidth()) {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=!devices,onClick={devices=false;recipient=null;searchState=searchState.cleared()},label={Text(t("Usuarios","Users"))})
                    FilterChip(selected=devices,onClick={devices=true;recipient=null;searchState=searchState.cleared()},label={Text(t("Dispositivos","Devices"))})
                }
                Text(if(devices) t("Notificación en un dispositivo con agente conectado. El servidor valida los permisos.","Notification on a device with a connected agent. The server validates permissions.") else t("Notificación individual a un usuario; no es chat. No hay confirmación de entrega ni lectura.","Individual user notification; not chat. No delivery or read confirmation."),style=MaterialTheme.typography.bodySmall)
                NoticeSearchField(searchState,{searchState=searchState.edit(it)})
                NoticeSubmissionStatus(state.notice,es)
                LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(devices) {
                        val matches=nodes.filter {it.conn and 1!=0 && noticeDeviceRecipientLabel(it.name,groupNames[it.id].orEmpty(),es).contains(search,true)}.distinctBy {it.id}
                        if(matches.isEmpty()) item {Text(t("No hay dispositivos con agente conectado que coincidan.","No matching devices with a connected agent."))}
                        items(matches,key={it.id}) {node->ManagementCard {
                            NoticeRecipientName(noticeDeviceRecipientLabel(node.name,groupNames[node.id].orEmpty(),es))
                            TextButton(enabled=actionsReady && !source.isUserLogout,onClick={onDeviceMessage(node)}) {Text(t("Redactar notificación","Compose notification"))}
                        }}
                    } else {
                        val matches=state.users.filter {it.optString("_id")!=state.id && canAdministerUser(state,it) && noticeUserRecipientLabel(it.optString("name"),es).contains(search,true)}
                        if(matches.isEmpty()) item {Text(t("No hay usuarios disponibles que coincidan con tus permisos y búsqueda.","No available users matching your permissions and search."))}
                        items(matches,key={it.optString("_id")}) {user->ManagementCard {
                            NoticeRecipientName(noticeUserRecipientLabel(user.optString("name"),es))
                            TextButton(enabled=state.canWrite,onClick={recipient=user.optString("_id")}) {Text(t("Redactar mensaje","Compose message"))}
                        }}
                    }
                }
            }
        }
    }
    if(broadcast && section==NoticeSection.BROADCAST) ProfileBroadcastDialog(session,state) {broadcast=false}
    recipient?.takeIf {section==NoticeSection.MESSAGES && !devices}?.let {id->key(id) {UserMessageDialog(session,state,id) {recipient=null}}}
}

@Composable private fun NoticeManagementBody(state:ProfileState,onReconnect:()->Unit,modifier:Modifier,content:@Composable ColumnScope.()->Unit) {
    Column(modifier) {
        ProfileManagementConnectionStatus(state,Modifier.padding(horizontal=16.dp),onReconnect)
        Column(Modifier.weight(1f).fillMaxWidth().padding(managementConnectionContentPadding),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}

@Composable private fun NoticeSubmissionStatus(notice:String,es:Boolean) {
    val text=when(notice) {
        "broadcastAccepted"->if(es) "Difusión aceptada por el servidor; recepción y lectura sin confirmar." else "Broadcast accepted by the server; receipt and reading unconfirmed."
        "sent"->if(es) "Solicitud enviada; resultado sin confirmar." else "Request sent; outcome unconfirmed."
        "unknown"->if(es) "Resultado sin confirmar. Comprueba antes de repetir; no se reenvía automáticamente." else "Outcome unconfirmed. Check before repeating; no automatic resend."
        "rejected"->if(es) "El servidor rechazó la solicitud." else "The server rejected the request."
        "readFailed"->if(es) "Listado incompleto; actualiza antes de enviar." else "Incomplete list; refresh before sending."
        else->""
    }
    if(text.isNotEmpty()) Text(text,style=MaterialTheme.typography.bodySmall)
}
