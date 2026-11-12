package iecxiv.meshfabric.ui.management

import android.Manifest
import android.os.Build
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
internal data class NoticeInboxState(val entries:List<NoticeInboxEntry> = emptyList(),val enabled:Boolean=false,val options:AccountNoticeOptions=AccountNoticeOptions(),val localeTag:String="",val hours24:Boolean=true,val storageLoading:Boolean=false,val storageSaving:Boolean=false,val storageError:Boolean=false) {
val unread:Int get()=entries.count { !it.read }
}
internal fun noticeInboxRetain(entries:List<NoticeInboxEntry>):List<NoticeInboxEntry> {
    var read=0
    return entries.filter { !it.read || ++read<=200 }
}
internal fun noticeInboxAppend(entries:List<NoticeInboxEntry>,entry:NoticeInboxEntry)=noticeInboxRetain(listOf(entry)+entries)
internal fun noticeInboxRead(entries:List<NoticeInboxEntry>,id:Long?=null,read:Boolean=true)=entries.map { if(id==null || it.notice.serial==id) it.copy(read=read) else it }
internal fun noticeInboxRemove(entries:List<NoticeInboxEntry>,id:Long)=entries.filterNot { it.notice.serial==id }
internal fun noticeScopeValid(bound:Triple<String,String,Boolean>?,current:Triple<String,String,Boolean>,actor:String,logout:Boolean)=bound!=null && bound==current && actor.startsWith("user/") && !logout

internal class NoticeInbox(context:Context,private val source:MeshCentralClient) {
private val publisher=AndroidNoticePublisher(context)
private var notificationScope=java.util.UUID.randomUUID().toString()
private val prefs=context.applicationContext.getSharedPreferences("native_account_view",Context.MODE_PRIVATE)
private val data=MutableStateFlow(NoticeInboxState())
val state=data.asStateFlow()
private var identity:Triple<String,String,Boolean>?=null
private var actor=""
private var account:JSONObject?=null
private var webMask:Long?=null
private val webStateReader=AccountWebStateReader()
private val disk=NoticeInboxDisk(context.applicationContext)
private var persistentScope=""
private var storageEpoch=0L
private var saveVersion=0L
private var storageReady=false
private fun persistInbox() {
    if(!storageReady || !matchesCurrentSource()) return
    val key=persistentScope;val epoch=storageEpoch;val ticket=++saveVersion
    val rows=noticeInboxRetain(data.value.entries)
    data.value=data.value.copy(entries=rows,storageSaving=true,storageError=false)
    NoticeInboxDisk.execute {
        val result=runCatching {disk.save(key,rows)}
        synchronized(this@NoticeInbox) {
            if(!closed && persistentScope==key && storageEpoch==epoch && saveVersion==ticket)
                data.value=data.value.copy(storageSaving=false,storageError=result.isFailure)
        }
    }
}
private fun restoreInbox() {
    if(!matchesCurrentSource()) return
    val key=persistentScope;val epoch=++storageEpoch
    storageReady=false
    data.value=data.value.copy(storageLoading=true,storageSaving=false,storageError=false)
    NoticeInboxDisk.execute {
        val result=runCatching {disk.load(key)}
        synchronized(this@NoticeInbox) {
            if(!closed && persistentScope==key && storageEpoch==epoch && matchesCurrentSource()) {
                result.onSuccess {saved->
                    val incoming=data.value.entries
                    val merged=noticeInboxRetain((incoming+saved).distinctBy {it.notice.serial}.sortedByDescending {it.notice.time})
                    storageReady=true
                    data.value=data.value.copy(entries=merged,storageLoading=false,storageError=false)
                    if(incoming.isNotEmpty()) persistInbox()
                }.onFailure {data.value=data.value.copy(storageLoading=false,storageError=true)}
            }
        }
    }
}
@Synchronized fun retryStorage() {if(!data.value.storageLoading && !data.value.storageSaving) restoreInbox()}
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
webStateReader.cancel()
NoticeNavigation.release(notificationScope); publisher.clear(); notificationScope=java.util.UUID.randomUUID().toString()
identity=connection; actor=userId; account=null; webMask=null; data.value=NoticeInboxState(); connectivity.clear()
persistentScope=noticeInboxStorageKey(connection.first,userId);notificationScope=persistentScope
restoreInbox()
source.groupedDevices.value.forEach { (_,nodes)->nodes.forEach { connectivity[noticeNodeKey(it.id)]=it.conn } }
}
NoticeNavigation.bind(notificationScope)
reloadPreferences()
if(webMask==null) refreshWebState()
}
@Synchronized private fun refreshWebState() {
if(!matchesCurrentSource() || webMask!=null || webStateReader.isRunning()) return
val bound=identity ?: return
val boundScope=notificationScope
webStateReader.load(bound) { snapshot,_->
synchronized(this@NoticeInbox) {
if(snapshot!=null && matchesCurrentSource() && identity==bound && notificationScope==boundScope && webMask==null) {
webMask=webNoticeMask(snapshot); reloadPreferences()
}
}
}
}
@Synchronized private fun reloadPreferences() {
if(closed || actor.isBlank()) return
if(!matchesCurrentSource()) { clear(); return }
val key=MessageDigest.getInstance("SHA-256").digest((identity!!.first+"\u0000"+actor).toByteArray()).joinToString("") { "%02x".format(it) }
val options=webNoticeOptions(webMask ?: 0L)
data.value=data.value.copy(enabled=prefs.getBoolean("${key}_alerts",false),options=options,localeTag=prefs.getString("${key}_locale","").orEmpty(),hours24=prefs.getBoolean("${key}_24",true))
}
private fun append(kind:String,e:JSONObject,channels:Int=0) {
val s=data.value
val id=e.optString("nodeid").ifBlank { e.optJSONObject("node")?.optString("_id").orEmpty() }.ifBlank { (e.opt("node") as? String).orEmpty() }
val nodeMatch=source.groupedDevices.value.firstNotNullOfOrNull { (g,nodes)->nodes.firstOrNull { noticeNodeKey(it.id)==noticeNodeKey(id) }?.let { g to it } }
val meshId=e.optString("meshid").ifBlank { e.optJSONObject("node")?.optString("meshid").orEmpty() }.ifBlank { nodeMatch?.first?.id.orEmpty() }
val groupFlags=webNoticeNumber(account?.optJSONObject("links")?.optJSONObject(meshId)?.opt("notify")) ?: 0L
val effectiveOptions=webNoticeOptions((webMask ?: 0L) or (groupFlags and 14L))
if(!s.enabled || !accountNoticeAllowed(kind,effectiveOptions)) return
val match=nodeMatch
val name=match?.second?.name.orEmpty().ifBlank { e.optString("nodename").ifBlank { id } }.take(200)
val group=match?.first?.name.orEmpty().ifBlank { e.optString("meshname") }.take(200)
val body=if(kind=="general") e.optString("value").ifBlank { e.optString("msg") }.take(1000) else if(kind=="amt") e.optString("value").ifBlank { e.optString("msg") }.take(1000) else ""
if(kind=="general" && body.isBlank()) return
val entry=NoticeInboxEntry(AccountNotice(java.util.UUID.randomUUID().mostSignificantBits.and(Long.MAX_VALUE).coerceAtLeast(1L),kind,body,name,group,channels),e.optString("title").take(160))
val updated=noticeInboxAppend(s.entries,entry)
val retained=updated.map { it.notice.serial }.toSet()
s.entries.filterNot { it.notice.serial in retained }.forEach { publisher.cancel(notificationScope,it.notice.serial) }
data.value=s.copy(entries=updated)
persistInbox()
publisher.post(notificationScope,entry,effectiveOptions)
}
@Synchronized fun receive(p:JSONObject) {
val action=p.optString("action")
if(action=="userinfo") {
val info=p.optJSONObject("userinfo") ?: return
val userId=info.optString("_id")
if(!userId.startsWith("user/")) return
bind(userId,current())
if(matchesCurrentSource()) account=JSONObject(info.toString())
return
}
if(!matchesCurrentSource()) { clear(); return }
webNoticeState(p)?.let { snapshot->webMask=webNoticeMask(snapshot); reloadPreferences(); return }
if(action=="nodes") {
noticeSnapshotNodes(p).forEach { node->noticeConnectivity(JSONObject().put("action","changenode").put("node",node))?.let { (id,conn)->connectivity[id]=conn } }
return
}
if(action=="msg" && p.optString("type")=="notify") {
if(p.optInt("msgid",-1) in 17..21 && p.optString("tag")!="broadcast") return
append(webNoticeKind(p),p); return
}
val e=if(action=="event") p.optJSONObject("event") ?: return else p
if(e.optString("action")=="accountchange") {
val info=e.optJSONObject("account")
if(info?.optString("_id")==actor) account=JSONObject(info.toString())
}
val (id,next)=noticeConnectivity(e) ?: return
val (gained,lost)=accountConnectionChange(connectivity.put(id,next),next)
if(gained!=0) append("connect",e,gained)
if(lost!=0) append("disconnect",e,lost)
}
@Synchronized fun setRead(id:Long?=null,read:Boolean=true) {
if(!matchesCurrentSource() || !storageReady) return
if(read) data.value.entries.filter { id==null || it.notice.serial==id }.forEach { publisher.cancel(notificationScope,it.notice.serial) }
data.value=data.value.copy(entries=noticeInboxRead(data.value.entries,id,read))
persistInbox()
}
@Synchronized fun removeEntry(id:Long) {
if(!matchesCurrentSource() || !storageReady) return
publisher.cancel(notificationScope,id)
data.value=data.value.copy(entries=noticeInboxRemove(data.value.entries,id))
persistInbox()
}
@Synchronized fun clearEntries() { if(matchesCurrentSource()) { storageEpoch++;storageReady=true;publisher.clear();data.value=data.value.copy(entries=emptyList(),storageLoading=false);persistInbox() } }
@Synchronized fun clear() { storageEpoch++;saveVersion++;storageReady=false;persistentScope="";webStateReader.cancel(); NoticeNavigation.release(notificationScope); publisher.clear(); notificationScope=java.util.UUID.randomUUID().toString(); identity=null; actor=""; account=null; webMask=null; connectivity.clear(); data.value=NoticeInboxState() }
@Synchronized fun close() { closed=true; prefs.unregisterOnSharedPreferenceChangeListener(listener); clear() }
}

@Composable internal fun NoticeInboxScreen(inbox:NoticeInbox,connected:Boolean,modifier:Modifier=Modifier) {
val s by inbox.state.collectAsState()
val context=LocalContext.current
var permissionDenied by remember { mutableStateOf(false) }
val permissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed->permissionDenied=!allowed }
val es=LocalConfiguration.current.locales[0].language=="es"
fun t(a:String,b:String)=if(es) a else b
val entries=if(inbox.matchesCurrentSource()) s.entries else emptyList()
var unreadOnly by rememberSaveable { mutableStateOf(false) }
var confirmClear by remember { mutableStateOf(false) }
val visible=entries.filter { !unreadOnly || !it.read }
Column(modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
Text(t("Avisos","Notices"),style=MaterialTheme.typography.titleMedium)
Text(t("Si llega un aviso con la app en segundo plano, se mostrará una notificación Android si el sistema lo permite. No se garantiza recepción con el proceso detenido. El permiso Android no activa la recepción de la cuenta.","If a notice arrives while the app is backgrounded, an Android notification is shown if allowed by the system. Delivery is not guaranteed when the process is stopped. Android permission does not enable account reception."),style=MaterialTheme.typography.bodySmall)
TextButton(onClick={
if(Build.VERSION.SDK_INT>=33 && androidx.core.content.ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED && !permissionDenied) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
else runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName)) }
}) { Text(t("Permitir / configurar notificaciones Android","Allow / configure Android notifications")) }
if(permissionDenied) Text(t("Permiso denegado. Avisos sigue funcionando; puedes cambiar el permiso en Ajustes.","Permission denied. The inbox still works; permission can be changed in Settings."),style=MaterialTheme.typography.bodySmall)
NoticeInboxStorageStatus(s,es,inbox::retryStorage)
Text("${entries.count { !it.read }} "+t("sin leer","unread"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
if(!connected) Text(t("Canal de avisos desconectado. Se conserva lo ya recibido en esta sesión.","Notice channel disconnected. Already received session entries are retained."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
if(!s.enabled) Text(t("La recepción está desactivada. Actívala en Perfil → Mi Usuario → Habilitar notificaciones en la app. Los avisos existentes no se borran al desactivarla.","Reception is disabled. Enable it in Profile → My user → Enable in-app notifications. Disabling it does not erase existing notices."),style=MaterialTheme.typography.bodySmall)
Text(t("Bandeja local cifrada por cuenta y servidor. Conserva todos los no leídos y hasta 200 leídos; solo los leídos antiguos se limpian automáticamente. No es historial del servidor ni garantiza recepción con el proceso detenido.","Encrypted local inbox per account and server. Keeps all unread and up to 200 read notices; only old read notices are removed automatically. Not server history; reception with the process stopped is not guaranteed."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
FilterChip(selected=!unreadOnly,onClick={ unreadOnly=false },label={ Text(t("Todos","All")) })
FilterChip(selected=unreadOnly,onClick={ unreadOnly=true },label={ Text(t("No leídos","Unread")) })
}
Row(Modifier.horizontalScroll(rememberScrollState())) {
TextButton(enabled=!s.storageLoading && !s.storageError && entries.any { !it.read },onClick={ inbox.setRead() }) { Text(t("Marcar todos como leídos","Mark all read")) }
TextButton(enabled=!s.storageLoading && (entries.isNotEmpty() || s.storageError),onClick={ confirmClear=true }) { Text(t("Borrar todos","Clear all")) }
}
LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
if(visible.isEmpty()) item { ManagementCard { Text(if(unreadOnly) t("No hay avisos sin leer.","No unread notices.") else t("No hay avisos guardados para esta cuenta y servidor.","No saved notices for this account and server.")) } }
items(visible,key={ it.notice.serial }) { entry->
val n=entry.notice
ManagementCard {
Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
Text(t("Recibido: ","Received: ")+accountTime(Instant.ofEpochMilli(n.time),s.localeTag,s.hours24),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
IconButton(enabled=!s.storageLoading && !s.storageError,onClick={ inbox.removeEntry(n.serial) }) { Icon(Icons.Default.Close,contentDescription=t("Borrar aviso","Remove notice")) }
}
val fallback=when(n.kind) { "connect"->t("Conexión de dispositivo","Device connection"); "disconnect"->t("Desconexión de dispositivo","Device disconnection"); "amt"->t("Evento Intel(R) AMT","Intel(R) AMT event"); else->t("Notificación","Notification") }
Text(entry.title.ifBlank { fallback },style=MaterialTheme.typography.titleSmall)
val names=listOf(n.nodeName,if(s.options.groupName) n.groupName else "").filter { it.isNotBlank() }.joinToString(" · ")
if(names.isNotBlank()) Text(names,style=MaterialTheme.typography.bodyMedium)
if(n.text.isNotBlank()) Text(n.text,style=MaterialTheme.typography.bodyMedium)
val channels=listOf(1 to t("Agente","Agent"),2 to "Intel AMT",4 to "Intel AMT CIRA",16 to "MQTT").filter { n.channels and it.first != 0 }.joinToString(", ") { it.second }
if(channels.isNotBlank()) Text(channels,style=MaterialTheme.typography.bodySmall)
Text(if(entry.read) t("Leído","Read") else t("No leído","Unread"),style=MaterialTheme.typography.labelMedium,color=if(entry.read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
TextButton(enabled=!s.storageLoading && !s.storageError,onClick={ inbox.setRead(n.serial,!entry.read) }) { Text(if(entry.read) t("Marcar como no leído","Mark unread") else t("Marcar como leído","Mark read")) }
}
}
}
}
if(confirmClear) ManagementDialog(onDismissRequest={ confirmClear=false },title={ Text(t("Vaciar Avisos","Clear Notices")) },text={ Text(t("Se borrarán todos los avisos locales de esta cuenta y servidor, incluidos los no leídos. No se puede deshacer. No se borran eventos ni mensajes del servidor.","All local notices for this account and server, including unread notices, will be deleted. This cannot be undone. Server events and messages are not deleted.")) },confirmButton={ TextButton(onClick={ inbox.clearEntries(); confirmClear=false }) { Text(t("Vaciar","Clear")) } },dismissButton={ TextButton(onClick={ confirmClear=false }) { Text(t("Cancelar","Cancel")) } })
}
