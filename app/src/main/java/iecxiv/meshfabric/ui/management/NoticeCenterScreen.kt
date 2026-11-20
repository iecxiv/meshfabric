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
    onDeviceMessage:(MeshNode)->Unit,deviceComposeOpen:Boolean=false,deviceComposer:@Composable ()->Unit={},onCloseDeviceComposer:()->Unit={},header:@Composable (@Composable ()->Unit)->Unit) {
    val actor by source.userInfo.collectAsState()
    val connected by source.connectionState.collectAsState()
    key(source,source.serverUrl,source.authCookie,source.ignoreSsl,actor?.id,connected) {
        NoticeCenterContent(source,inbox,actionsReady,nodes,modifier,onDeviceMessage,deviceComposeOpen,deviceComposer,onCloseDeviceComposer,header)
    }
}

@Composable private fun NoticeCenterContent(source:MeshCentralClient,inbox:NoticeInbox,
    actionsReady:Boolean,nodes:List<MeshNode>,modifier:Modifier,
    onDeviceMessage:(MeshNode)->Unit,deviceComposeOpen:Boolean,deviceComposer:@Composable ()->Unit,onCloseDeviceComposer:()->Unit,header:@Composable (@Composable ()->Unit)->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    var section by remember {mutableStateOf(NoticeSection.INBOX)}
    var recipient by remember {mutableStateOf<String?>(null)}
    var searchState by remember {mutableStateOf(NoticeSearchState())}
    val search=searchState.query
    var devices by remember {mutableStateOf(false)}
    val userGridState=androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val deviceGridState=androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val recipientGroups by source.groupedDevices.collectAsState()
    val groupNames=recipientGroups.flatMap { (group,members)->members.map {it.id to group.name} }.toMap()
    val session=remember(source) {ProfileSession(source)}
    val state by session.state.collectAsState()
    iecxiv.meshfabric.ui.components.ReportViewLoading(state.loading || state.busy || state.detailsBusy || state.historyBusy)
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
    LaunchedEffect(state.ready) {if(!state.ready) recipient=null}
    BackHandler(enabled=section==NoticeSection.MESSAGES && searchState.open) {searchState=searchState.cleared()}
    Column(modifier) {
        header {if(section==NoticeSection.MESSAGES) NoticeSearchAction(searchState) {searchState=searchState.toggle()}}
        NoticeCenterTabs(section) {
            onCloseDeviceComposer();recipient=null;searchState=searchState.cleared();section=it
        }
        when(section) {
            NoticeSection.INBOX->NoticeInboxScreen(inbox,actionsReady,Modifier.weight(1f).fillMaxWidth())
            NoticeSection.BROADCAST->Column(Modifier.weight(1f).fillMaxWidth()) {
                ProfileManagementConnectionStatus(state,Modifier.padding(horizontal=16.dp),session::start)
                ProfileBroadcastPane(session,state,Modifier.weight(1f).fillMaxWidth(),embedded=true) {}
                Box(Modifier.fillMaxWidth().padding(horizontal=16.dp)) {NoticeSubmissionStatus(state.notice,es)}
            }
            NoticeSection.MESSAGES->NoticeManagementBody(state,session::start,Modifier.weight(1f).fillMaxWidth(),padded=!(deviceComposeOpen || (recipient!=null && !devices))) {
                val selectedRecipient=recipient
                if(deviceComposeOpen) deviceComposer()
                else if(selectedRecipient!=null && !devices) key(selectedRecipient) {
                    CompositionLocalProvider(LocalNoticeComposeInline provides true) {UserMessageDialog(session,state,selectedRecipient) {recipient=null}}
                } else {
                    Text(t("Mensaje individual a usuario o dispositivo","Individual message to a user or device"),style=MaterialTheme.typography.titleMedium)
                    Text(t("Elige un usuario o dispositivo para enviarle un mensaje.","Choose a user or device to send a message."),style=MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected=!devices,onClick={devices=false;recipient=null;searchState=searchState.cleared()},label={Text(t("Usuarios","Users"))})
                        FilterChip(selected=devices,onClick={devices=true;recipient=null;searchState=searchState.cleared()},label={Text(t("Dispositivos","Devices"))})
                    }
                    Text(if(devices) t("Notificación a un agente conectado; el servidor valida los permisos.","Notification to a connected agent; the server validates permissions.") else t("Notificación individual; no es chat ni hay confirmación de lectura.","Individual notification; not chat and no read confirmation."),style=MaterialTheme.typography.bodySmall)
                    NoticeSearchField(searchState,{searchState=searchState.edit(it)})
                    NoticeSubmissionStatus(state.notice,es)
                    val choices=if(devices) nodes.filter {it.conn and 1!=0 && noticeDeviceRecipientLabel(it.name,groupNames[it.id].orEmpty(),es).contains(search,true)}.distinctBy {it.id}.map {NoticeNavigationChoice(it.id,noticeDeviceRecipientLabel(it.name,groupNames[it.id].orEmpty(),es),true)}
                        else state.users.filter {it.optString("_id")!=state.id && canAdministerUser(state,it) && noticeUserRecipientLabel(it.optString("name"),es).contains(search,true)}.map {NoticeNavigationChoice(it.optString("_id"),noticeUserRecipientLabel(it.optString("name"),es),false)}
                    if(choices.isEmpty()) Text(t("No hay destinatarios disponibles que coincidan.","No matching recipients available."),style=MaterialTheme.typography.bodySmall)
                    NoticeNavigationGrid(choices,if(devices) deviceGridState else userGridState,if(devices) actionsReady && !source.isUserLogout else state.canWrite,Modifier.weight(1f).fillMaxWidth()) {choice->
                        if(choice.device) nodes.singleOrNull {it.id==choice.id && it.conn and 1!=0}?.let(onDeviceMessage) else recipient=choice.id
                    }
                }
            }
        }
    }
}

@Composable private fun NoticeManagementBody(state:ProfileState,onReconnect:()->Unit,modifier:Modifier,padded:Boolean=true,content:@Composable ColumnScope.()->Unit) {
    Column(modifier) {
        ProfileManagementConnectionStatus(state,Modifier.padding(horizontal=16.dp),onReconnect)
        Column(Modifier.weight(1f).fillMaxWidth().padding(if(padded) managementConnectionContentPadding else PaddingValues(0.dp)),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
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
