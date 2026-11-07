package iecxiv.meshfabric.ui.management

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant

internal val LocalNoticeUnread=staticCompositionLocalOf { 0 }
internal data class NoticeInboxEntry(val notice:AccountNotice,val title:String="",val read:Boolean=false)
internal data class NoticeInboxState(val entries:List<NoticeInboxEntry> = emptyList(),val enabled:Boolean=false,val options:AccountNoticeOptions=AccountNoticeOptions(),val localeTag:String="",val hours24:Boolean=true) {
val unread:Int get()=entries.count { !it.read }
}
internal fun noticeInboxAppend(entries:List<NoticeInboxEntry>,entry:NoticeInboxEntry)=(listOf(entry)+entries).take(200)
internal fun noticeInboxRead(entries:List<NoticeInboxEntry>,id:Long?=null,read:Boolean=true)=entries.map { if(id==null || it.notice.serial==id) it.copy(read=read) else it }
internal fun noticeScopeValid(bound:Triple<String,String,Boolean>?,current:Triple<String,String,Boolean>,actor:String,logout:Boolean)=bound!=null && bound==current && actor.startsWith("user/") && !logout

internal class NoticeInbox(context:Context,private val source:MeshCentralClient) {
private val prefs=context.applicationContext.getSharedPreferences("native_account_view",Context.MODE_PRIVATE)
private val data=MutableStateFlow(NoticeInboxState())
val state=data.asStateFlow()
private var identity:Triple<String,String,Boolean>?=null
private var actor=""
private var serial=0L
private var closed=false
private val connectivity=mutableMapOf<String,Int>()
private val listener=SharedPreferences.OnSharedPreferenceChangeListener { _,_->reloadPreferences() }
init { prefs.registerOnSharedPreferenceChangeListener(listener) }
private fun current()=Triple(source.serverUrl,source.authCookie,source.ignoreSsl)
@Synchronized fun matchesCurrentSource()=noticeScopeValid(identity,current(),actor,source.isUserLogout) && !closed
@Synchronized fun checkIdentity() { if(!matchesCurrentSource()) clear() }
@Synchronized fun bind(userId:String,connection:Triple<String,String,Boolean>) {
if(closed) return
if(!noticeScopeValid(connection,current(),userId,source.isUserLogout)) { clear(); return }
if(identity!=connection || actor!=userId) {
identity=connection; actor=userId; data.value=NoticeInboxState(); connectivity.clear()
source.groupedDevices.value.forEach { (_,nodes)->nodes.forEach { connectivity[it.id]=it.conn } }
}
reloadPreferences()
}
@Synchronized private fun reloadPreferences() {
if(closed || actor.isBlank()) return
if(!matchesCurrentSource()) { clear(); return }
val key=MessageDigest.getInstance("SHA-256").digest((identity!!.first+"\u0000"+actor).toByteArray()).joinToString("") { "%02x".format(it) }
val options=AccountNoticeOptions(prefs.getBoolean("${key}_sound",false),prefs.getBoolean("${key}_groupname",true),prefs.getBoolean("${key}_connect",false),prefs.getBoolean("${key}_disconnect",false),prefs.getBoolean("${key}_amt",false))
data.value=data.value.copy(enabled=prefs.getBoolean("${key}_alerts",false),options=options,localeTag=prefs.getString("${key}_locale","").orEmpty(),hours24=prefs.getBoolean("${key}_24",true))
}
private fun append(kind:String,e:JSONObject,channels:Int=0) {
val s=data.value
if(!s.enabled || !accountNoticeAllowed(kind,s.options)) return
val id=e.optString("nodeid").ifBlank { e.optJSONObject("node")?.optString("_id").orEmpty() }
val match=source.groupedDevices.value.firstNotNullOfOrNull { (g,nodes)->nodes.firstOrNull { it.id==id }?.let { g to it } }
val name=match?.second?.name.orEmpty().ifBlank { e.optString("nodename").ifBlank { id } }.take(200)
val group=match?.first?.name.orEmpty().ifBlank { e.optString("meshname") }.take(200)
val body=if(kind=="general") e.optString("value").ifBlank { e.optString("msg") }.take(1000) else if(kind=="amt") e.optString("msg").take(600) else ""
if(kind=="general" && body.isBlank()) return
val entry=NoticeInboxEntry(AccountNotice(++serial,kind,body,name,group,channels),e.optString("title").take(160))
data.value=s.copy(entries=noticeInboxAppend(s.entries,entry))
}
@Synchronized fun receive(p:JSONObject) {
if(!matchesCurrentSource()) { clear(); return }
val action=p.optString("action")
if(action=="msg" && p.optString("type")=="notify") {
if(p.optInt("msgid",-1) in 17..21 && p.optString("tag")!="broadcast") return
append("general",p); return
}
val e=if(action=="event") p.optJSONObject("event") ?: return else p
val kind=e.optString("action")
if(kind=="nodeconnect" || kind=="nodedisconnect") {
val id=e.optString("nodeid").ifBlank { e.optJSONObject("node")?.optString("_id").orEmpty() }
if(id.isBlank()) return
val n=e.opt("conn") as? Number
val next=if(n!=null && n.toDouble()>=0 && n.toDouble()<=Int.MAX_VALUE && n.toDouble()%1.0==0.0) n.toInt() else if(kind=="nodedisconnect") 0 else return
val (gained,lost)=accountConnectionChange(connectivity.put(id,next),next)
if(gained!=0) append("connect",e,gained)
if(lost!=0) append("disconnect",e,lost)
} else if(kind=="relaylog" && e.optInt("protocol")==101) append("amt",e)
}
@Synchronized fun setRead(id:Long?=null,read:Boolean=true) { if(matchesCurrentSource()) data.value=data.value.copy(entries=noticeInboxRead(data.value.entries,id,read)) }
@Synchronized fun clearEntries() { if(matchesCurrentSource()) data.value=data.value.copy(entries=emptyList()) }
@Synchronized fun clear() { identity=null; actor=""; connectivity.clear(); data.value=NoticeInboxState() }
@Synchronized fun close() { closed=true; prefs.unregisterOnSharedPreferenceChangeListener(listener); clear() }
}

@Composable internal fun NoticeInboxScreen(inbox:NoticeInbox,connected:Boolean,modifier:Modifier=Modifier) {
val s by inbox.state.collectAsState()
val es=LocalConfiguration.current.locales[0].language=="es"
fun t(a:String,b:String)=if(es) a else b
val entries=if(inbox.matchesCurrentSource()) s.entries else emptyList()
var unreadOnly by rememberSaveable { mutableStateOf(false) }
var confirmClear by remember { mutableStateOf(false) }
val visible=entries.filter { !unreadOnly || !it.read }
Column(modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
Text(t("Avisos","Notices"),style=MaterialTheme.typography.titleMedium)
Text("${entries.count { !it.read }} "+t("sin leer","unread"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
if(!connected) Text(t("Canal de avisos desconectado. Se conserva lo ya recibido en esta sesión.","Notice channel disconnected. Already received session entries are retained."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
if(!s.enabled) Text(t("La recepción está desactivada. Actívala en Perfil → Mi Usuario → Habilitar notificaciones en la app. Los avisos existentes no se borran al desactivarla.","Reception is disabled. Enable it in Profile → My user → Enable in-app notifications. Disabling it does not erase existing notices."),style=MaterialTheme.typography.bodySmall)
Text(t("Últimos 200 avisos recibidos durante esta sesión, en memoria. No es un historial del servidor ni push; no garantiza recepción con la app cerrada. Se borra al cambiar de cuenta/servidor/sesión o cerrar el proceso.","Latest 200 notices received during this session, in memory. Not server history or push; reception with the app closed is not guaranteed. Cleared on account/server/session change or process exit."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
FilterChip(selected=!unreadOnly,onClick={ unreadOnly=false },label={ Text(t("Todos","All")) })
FilterChip(selected=unreadOnly,onClick={ unreadOnly=true },label={ Text(t("No leídos","Unread")) })
}
Row(Modifier.horizontalScroll(rememberScrollState())) {
TextButton(enabled=entries.any { !it.read },onClick={ inbox.setRead() }) { Text(t("Marcar todos como leídos","Mark all read")) }
TextButton(enabled=entries.isNotEmpty(),onClick={ confirmClear=true }) { Text(t("Vaciar bandeja","Clear inbox")) }
}
LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
if(visible.isEmpty()) item { ManagementCard { Text(if(unreadOnly) t("No hay avisos sin leer.","No unread notices.") else t("No hay avisos recibidos en esta sesión.","No notices received during this session.")) } }
items(visible,key={ it.notice.serial }) { entry->
val n=entry.notice
ManagementCard {
Text(t("Recibido: ","Received: ")+accountTime(Instant.ofEpochMilli(n.time),s.localeTag,s.hours24),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
val fallback=when(n.kind) { "connect"->t("Conexión de dispositivo","Device connection"); "disconnect"->t("Desconexión de dispositivo","Device disconnection"); "amt"->t("Evento Intel(R) AMT","Intel(R) AMT event"); else->t("Notificación","Notification") }
Text(entry.title.ifBlank { fallback },style=MaterialTheme.typography.titleSmall)
val names=listOf(n.nodeName,if(s.options.groupName) n.groupName else "").filter { it.isNotBlank() }.joinToString(" · ")
if(names.isNotBlank()) Text(names,style=MaterialTheme.typography.bodyMedium)
if(n.text.isNotBlank()) Text(n.text,style=MaterialTheme.typography.bodyMedium)
val channels=listOf(1 to t("Agente","Agent"),2 to "Intel AMT CIRA",4 to "Intel AMT",16 to "MQTT").filter { n.channels and it.first != 0 }.joinToString(", ") { it.second }
if(channels.isNotBlank()) Text(channels,style=MaterialTheme.typography.bodySmall)
Text(if(entry.read) t("Leído","Read") else t("No leído","Unread"),style=MaterialTheme.typography.labelMedium,color=if(entry.read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
TextButton(onClick={ inbox.setRead(n.serial,!entry.read) }) { Text(if(entry.read) t("Marcar como no leído","Mark unread") else t("Marcar como leído","Mark read")) }
}
}
}
}
if(confirmClear) AlertDialog(onDismissRequest={ confirmClear=false },title={ Text(t("Vaciar Avisos","Clear Notices")) },text={ Text(t("Solo se vacía esta bandeja local de la sesión. No se borran eventos ni mensajes del servidor.","Only this local session inbox is cleared. Server events and messages are not deleted.")) },confirmButton={ TextButton(onClick={ inbox.clearEntries(); confirmClear=false }) { Text(t("Vaciar","Clear")) } },dismissButton={ TextButton(onClick={ confirmClear=false }) { Text(t("Cancelar","Cancel")) } })
}
