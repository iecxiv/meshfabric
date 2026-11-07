package iecxiv.meshfabric.ui.management

import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.json.JSONArray
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

internal data class AccountNoticeOptions(val sound:Boolean=false,val groupName:Boolean=true,val connections:Boolean=false,val disconnections:Boolean=false,val amt:Boolean=false)
internal data class AccountNotice(val serial:Long,val kind:String,val text:String,val nodeName:String,val groupName:String,val channels:Int=0,val time:Long=System.currentTimeMillis())
internal fun accountNoticeAllowed(kind:String,o:AccountNoticeOptions)=when(kind) { "general"->true; "connect"->o.connections; "disconnect"->o.disconnections; "amt"->o.amt; else->false }
internal fun accountConnectionChange(before:Int?,after:Int):Pair<Int,Int> = if(before==null || before<0 || after<0) 0 to 0 else (after and before.inv() and 23) to (before and after.inv() and 23)
internal fun accountLanguageValid(tag:String)=tag.matches(Regex("[A-Za-z]{2,8}(-[A-Za-z0-9]{2,8})*"))
internal fun accountDefaultLanguage(tag:String)=tag.ifBlank { "*" }
internal fun accountLanguageTags(a:JSONArray?):List<String> = a?.let { (0 until it.length()).mapNotNull { i->(it.opt(i) as? String)?.takeIf(::accountLanguageValid) }.distinct() }.orEmpty()
internal fun accountLocaleLabel(tag:String,es:Boolean)=Locale.forLanguageTag(tag).getDisplayName(if(es) Locale("es") else Locale.ENGLISH).ifBlank { tag }+" ($tag)"
internal fun accountLocaleChoices(es:Boolean):List<Pair<String,String>> = listOf("" to if(es) "Predeterminado del sistema" else "System default")+Locale.getAvailableLocales().filter { it.language.isNotBlank() }.map { it.toLanguageTag() }.filter { it!="und" }.distinct().map { it to accountLocaleLabel(it,es) }.sortedBy { it.second.lowercase() }
internal fun accountTime(instant:Instant,tag:String,hours24:Boolean):String=runCatching {
val locale=if(tag.isBlank()) Locale.getDefault() else Locale.forLanguageTag(tag)
val date=Date.from(instant)
DateFormat.getDateInstance(DateFormat.MEDIUM,locale).format(date)+" "+SimpleDateFormat(if(hours24) "HH:mm:ss" else "hh:mm:ss a",locale).format(date)
}.getOrDefault("")

@Composable internal fun AccountChoiceDropdown(label:String,selected:String,choices:List<Pair<String,String>>,enabled:Boolean=true,es:Boolean,onSelect:(String)->Unit) {
var open by remember { mutableStateOf(false) }
var query by remember { mutableStateOf("") }
Text(label,style=MaterialTheme.typography.labelMedium)
Box(Modifier.fillMaxWidth()) {
OutlinedButton(enabled=enabled,onClick={ query=""; open=true },modifier=Modifier.fillMaxWidth()) {
Text(choices.firstOrNull { it.first==selected }?.second ?: selected,Modifier.weight(1f))
Icon(Icons.Default.ArrowDropDown,null)
}
DropdownMenu(expanded=open && enabled,onDismissRequest={ open=false },modifier=Modifier.heightIn(max=300.dp).widthIn(min=240.dp,max=320.dp)) {
OutlinedTextField(query,{ query=it },label={ Text(if(es) "Buscar" else "Search") },singleLine=true,modifier=Modifier.padding(8.dp))
val matches=choices.filter { it.first.contains(query,true) || it.second.contains(query,true) }
for((id,text) in matches.take(80)) DropdownMenuItem(text={ Text(text) },onClick={ onSelect(id); open=false })
if(matches.isEmpty()) Text(if(es) "Sin resultados." else "No results.",Modifier.padding(12.dp))
if(matches.size>80) Text(if(es) "Afina la búsqueda para ver más opciones." else "Refine the search to see more choices.",Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)
}
}
}

@Composable internal fun AccountNoticeBanner(incoming:AccountNotice?,options:AccountNoticeOptions,enabled:Boolean,es:Boolean,localeTag:String,hours24:Boolean) {
val context=LocalContext.current
val lifecycle=LocalLifecycleOwner.current.lifecycle
var shown by remember { mutableStateOf<AccountNotice?>(null) }
val ringtone=remember(context) { runCatching { RingtoneManager.getRingtone(context,RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.apply { audioAttributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build() } }.getOrNull() }
DisposableEffect(lifecycle,ringtone) {
val observer=LifecycleEventObserver { _,event->if(event==Lifecycle.Event.ON_PAUSE || event==Lifecycle.Event.ON_STOP) runCatching { ringtone?.stop() } }
lifecycle.addObserver(observer)
onDispose { lifecycle.removeObserver(observer); runCatching { ringtone?.stop() } }
}
LaunchedEffect(incoming?.serial) {
if(incoming==null) shown=null else if(enabled && accountNoticeAllowed(incoming.kind,options) && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
shown=incoming
if(options.sound) runCatching { ringtone?.stop(); ringtone?.play() }
}
}
val note=shown
if(enabled && note!=null && accountNoticeAllowed(note.kind,options)) ManagementCard {
Text(accountTime(Instant.ofEpochMilli(note.time),localeTag,hours24),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
val title=when(note.kind) { "connect"->if(es) "Conexión de dispositivo" else "Device connection"; "disconnect"->if(es) "Desconexión de dispositivo" else "Device disconnection"; "amt"->if(es) "Evento de redirección Intel(R) AMT" else "Intel(R) AMT redirection event"; else->if(es) "Notificación" else "Notification" }
Text(title,style=MaterialTheme.typography.titleSmall)
val names=listOf(note.nodeName,if(options.groupName) note.groupName else "").filter { it.isNotBlank() }.joinToString(" · ")
if(names.isNotBlank()) Text(names,style=MaterialTheme.typography.bodyMedium)
if(note.text.isNotBlank()) Text(note.text,style=MaterialTheme.typography.bodySmall)
val channels=listOf(1 to if(es) "Agente" else "Agent",2 to "Intel AMT",4 to "Intel AMT CIRA",16 to "MQTT").filter { note.channels and it.first != 0 }.joinToString(", ") { it.second }
if(channels.isNotBlank()) Text(channels,style=MaterialTheme.typography.bodySmall)
}
}
