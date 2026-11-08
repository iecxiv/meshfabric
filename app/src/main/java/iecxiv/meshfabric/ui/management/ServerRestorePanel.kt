package iecxiv.meshfabric.ui.management

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okio.BufferedSink
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

private data class RestoreIdentity(val url:String,val cookie:String,val ignoreSsl:Boolean) {
    fun matches(source:MeshCentralClient)=!source.isUserLogout && url==source.serverUrl && cookie==source.authCookie && ignoreSsl==source.ignoreSsl
    override fun toString()="RestoreIdentity"
}
private data class RestoreAccess(val auth:String,val system:Boolean) { override fun toString()="RestoreAccess" }
private data class RestoreCandidate(val file:File,val name:String,val sha:String,val identity:RestoreIdentity,val system:Boolean)
private data class RestoreProposal(val candidate:RestoreCandidate,val password:String,val system:Boolean,val verified:Boolean) { override fun toString()="RestoreProposal" }

private suspend fun restoreControl(source:MeshCentralClient,target:RestoreIdentity,mutate:Boolean=false,password:String="",system:Boolean=false):RestoreAccess = withTimeout(20000) {
    require(target.matches(source))
    val result=CompletableDeferred<RestoreAccess>()
    val lock=Any()
    var token=""
    var hasSystem:Boolean?=null
    var checked=false
    var userReceived=false
    fun finish() { if(checked && token.isNotEmpty() && hasSystem!=null) result.complete(RestoreAccess(token,hasSystem==true)) }
    val socket=OkHttpHelper.createClient(target.ignoreSsl).newWebSocket(Request.Builder()
        .url(restoreEndpoint(target.url,"control.ashx").newBuilder().addQueryParameter("moreargs","1").build())
        .header("Cookie",target.cookie).build(),object:WebSocketListener() {
        override fun onOpen(webSocket:WebSocket,response:Response) {
            webSocket.send(JSONObject().put("action","urlargs").put("args",JSONObject().put("auth",source.extractRawToken(target.cookie))).toString())
            webSocket.send("{\"action\":\"userinfo\"}")
            webSocket.send("{\"action\":\"authcookie\"}")
        }
        override fun onMessage(webSocket:WebSocket,text:String) { synchronized(lock) {
            if(result.isCompleted) return
            if(text.length>2000000) { result.completeExceptionally(IOException("control")); return }
            val packet=runCatching { JSONObject(text) }.getOrNull() ?: return
            when(packet.optString("action")) {
                "ping"->webSocket.send("{\"action\":\"pong\"}")
                "userinfo"->if(!userReceived) {
                    val user=packet.optJSONObject("userinfo") ?: return
                    if(user.optString("_id").isBlank() || !restoreAllowed(user.optLong("siteadmin",0))) { result.completeExceptionally(IOException("permission")); return }
                    userReceived=true
                    webSocket.send(JSONObject().put("action","serverbackuppassword").put("check",true).put("dialog","meshfabric-restore-check").toString())
                }
                "authcookie"->{ token=packet.optString("cookie"); finish() }
                "backuprestore"->when(packet.optString("dialog")) {
                    "meshfabric-restore-check"->{
                        if(!userReceived || !packet.has("data")) return
                        hasSystem=packet.optBoolean("data")
                        if(system && hasSystem!=true) { result.completeExceptionally(IOException("system password unavailable")); return }
                        if(mutate) {
                            if(!target.matches(source)) { result.completeExceptionally(IOException("session")); return }
                            webSocket.send(JSONObject().put("action","serverbackuppassword").put("check",false).put("password",if(system) "" else password).put("override",system).toString())
                            webSocket.send(JSONObject().put("action","serverbackuppassword").put("check",true).put("dialog","meshfabric-restore-ordered").toString())
                        } else { checked=true; finish() }
                    }
                    "meshfabric-restore-ordered"->if(mutate && userReceived && hasSystem!=null) { checked=true; finish() }
                }
                "close","stopped"->result.completeExceptionally(IOException("closed"))
            }
        } }
        override fun onClosing(webSocket:WebSocket,code:Int,reason:String) { webSocket.close(code,"") }
        override fun onClosed(webSocket:WebSocket,code:Int,reason:String) { result.completeExceptionally(IOException("closed")) }
        override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) { result.completeExceptionally(IOException("connection")) }
    })
    try { result.await() } finally { socket.cancel() }
}

@Composable internal fun ServerRestorePanel(source:MeshCentralClient?,enabled:Boolean,onBusy:(Boolean)->Unit) {
    val context=LocalContext.current
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun label(a:String,b:String)=if(es) a else b
    val scope=rememberCoroutineScope()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var intro by remember(source,enabled) { mutableStateOf(false) }
    var candidate by remember { mutableStateOf<RestoreCandidate?>(null) }
    var proposal by remember { mutableStateOf<RestoreProposal?>(null) }
    var password by remember { mutableStateOf("") }
    var system by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var acceptUnverified by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var progress by remember { mutableLongStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    var call by remember { mutableStateOf<Call?>(null) }
    fun clearFiles() { candidate?.file?.delete(); proposal?.candidate?.file?.delete(); candidate=null; proposal=null; password=""; typed=""; acceptUnverified=false }
    LaunchedEffect(intro,selecting,busy,candidate,proposal) { onBusy(intro || selecting || busy || candidate!=null || proposal!=null) }
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,event->if(event==Lifecycle.Event.ON_STOP && !selecting) {
            intro=false
            if(busy) { note=if(attempted) "unknown" else "cancelled"; call?.cancel(); job?.cancel() }
            clearFiles()
        } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); call?.cancel(); job?.cancel(); clearFiles(); onBusy(false) }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri:Uri?->
        selecting=false
        val current=source
        if(uri!=null && current!=null) {
            val identity=RestoreIdentity(current.serverUrl,current.authCookie,current.ignoreSsl)
            busy=true; attempted=false; note="preparing"
            job=scope.launch {
                var temp:File?=null
                var keep=false
                try {
                    val access=restoreControl(current,identity)
                    val prepared=withContext(Dispatchers.IO) {
                        val file=File.createTempFile("meshfabric-restore-",".zip",context.cacheDir)
                        temp=file
                        val name=runCatching { context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cursor->if(cursor.moveToFirst()) cursor.getString(0).take(256) else "backup.zip" } }.getOrNull() ?: "backup.zip"
                        val digest=MessageDigest.getInstance("SHA-256")
                        context.contentResolver.openInputStream(uri)?.use { input->file.outputStream().use { output->
                            val buffer=ByteArray(65536); var count=0L
                            while(true) {
                                currentCoroutineContext().ensureActive()
                                val n=input.read(buffer); if(n<0) break
                                count+=n; require(count<=RESTORE_COMPRESSED_LIMIT)
                                output.write(buffer,0,n); digest.update(buffer,0,n)
                            }
                        } } ?: error("input")
                        require(file.length()>=22)
                        RestoreCandidate(file,name,digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) },identity,access.system)
                    }
                    ensureActive()
                    candidate=prepared; password=""; system=false; keep=true; note=""
                } catch(e:CancellationException) { throw e }
                catch(_:Exception) { note="prepare-failed" }
                finally { if(!keep) withContext(NonCancellable+Dispatchers.IO) { temp?.delete() }; busy=false }
            }
        }
    }
    TextButton(enabled=enabled && source!=null && !intro && !busy && !selecting && candidate==null && proposal==null,onClick={
        note=""; intro=true
    }) { Text(label("Restaurar servidor desde copia…","Restore server from backup…")) }
    if(intro) iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={ intro=false },title={ Text(label("Restaurar servidor desde copia","Restore server from backup")) },
        text={ Column(Modifier.heightIn(max=320.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(label("Restauración web de copias NeDB con config.json y meshcentral.db. No es una restauración de MongoDB/SQL. Límite: 256 MiB ZIP, 1 GiB expandido, 20 000 entradas.","Web restore of NeDB backups containing config.json and meshcentral.db. Not MongoDB/SQL restore. Limits: 256 MiB ZIP, 1 GiB expanded, 20,000 entries."))
            Text(label("Continuar abre el selector de archivos. La restauración se confirmará después de validar la copia.","Continue opens the file picker. Restore will be confirmed after validating the backup."))
        } },
        confirmButton={ TextButton(enabled=enabled && source!=null && !busy && !selecting && candidate==null && proposal==null,onClick={
            intro=false; note=""; selecting=true
            try { picker.launch(arrayOf("application/zip","application/octet-stream","application/x-zip-compressed")) } catch(_:Exception) { selecting=false; note="prepare-failed" }
        }) { Text(label("Continuar","Continue")) } },
        dismissButton={ TextButton(onClick={ intro=false }) { Text(label("Cancelar","Cancel")) } })
    val status=when(note) {
        "preparing"->label("Comprobando permisos y copiando el archivo a caché privada…","Checking permission and freezing the file in private cache…")
        "validating"->label("Validando el ZIP…","Validating ZIP…")
        "sending"->label("Preparando contraseña y autorización…","Preparing password and authorization…")
        "uploading"->label("Subiendo: $progress bytes. No repitas la operación.","Uploading: $progress bytes. Do not repeat this operation.")
        "accepted"->label("El servidor aceptó la solicitud HTTP. La restauración y el reinicio NO están confirmados. Comprueba el servidor antes de hacer nada más.","Server accepted the HTTP request. Restore and restart are NOT confirmed. Check the server before taking further action.")
        "unknown"->label("Resultado desconocido: la petición pudo llegar al servidor. No se reintentará. Comprueba el servidor antes de repetirla.","Result unknown: the request may have reached the server. No retry will occur. Check the server before repeating it.")
        "rejected"->label("El servidor rechazó la solicitud HTTP. No se reintentará automáticamente.","Server rejected the HTTP request. No automatic retry.")
        "prepare-failed"->label("No se pudo preparar la restauración. Revisa sesión, permiso de restauración, HTTPS y tamaño del archivo (máximo 256 MiB). No se subió ningún archivo.","Could not prepare restore. Check session, restore permission, HTTPS and file size (maximum 256 MiB). No file was uploaded.")
        "invalid"->label("ZIP o contraseña no válidos, archivo no compatible con NeDB o límites superados. No se subió nada.","Invalid ZIP/password, incompatible NeDB backup or limits exceeded. Nothing uploaded.")
        "cancelled"->label("Preparación cancelada; no se inició la subida.","Preparation cancelled; upload was not started.")
        else->""
    }
    if(status.isNotEmpty()) Text(status,style=MaterialTheme.typography.bodySmall,color=if(note in setOf("unknown","invalid","rejected","prepare-failed")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    candidate?.let { frozen->iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={ if(!busy) clearFiles() },title={ Text(label("Preparar restauración","Prepare restore")) },
        text={ Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(frozen.identity.url); Text(frozen.name); Text("${frozen.file.length()} bytes")
            Text(label("Sustituye datos existentes y reinicia el servidor. No hagas copias/restauraciones simultáneas desde otras sesiones: la contraseña temporal es global.","Replaces existing data and restarts the server. Do not run concurrent backup/restore from other sessions: the temporary password is global."),color=MaterialTheme.colorScheme.error)
            OutlinedTextField(value=password,onValueChange={ if(it.length<=256) password=it },enabled=!system && !busy,label={ Text(label("Contraseña de la copia","Backup password")) },visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true)
            Row { Checkbox(checked=system,enabled=frozen.system && !busy,onCheckedChange={ system=it }); Text(label("Usar contraseña del sistema","Use system password")) }
            if(system) Text(label("El móvil no conoce esta contraseña: no puede verificar las entradas cifradas antes de enviarlas.","The phone does not know this password: encrypted entries cannot be verified before upload."),style=MaterialTheme.typography.bodySmall)
        } },
        confirmButton={ TextButton(enabled=!busy,onClick={
            val secret=if(system) "" else password; val useSystem=system
            password=""; busy=true; note="validating"
            job=scope.launch {
                try {
                    val verified=withContext(Dispatchers.IO) { val ctx=currentCoroutineContext(); verifyRestoreArchive(frozen.file,secret,useSystem) { ctx.ensureActive() } }
                    ensureActive(); candidate=null; proposal=RestoreProposal(frozen,secret,useSystem,verified); typed=""; acceptUnverified=false; note=""
                } catch(e:CancellationException) { throw e }
                catch(_:Exception) { clearFiles(); note="invalid" }
                finally { busy=false }
            }
        }) { Text(label("Validar y revisar","Validate and review")) } },dismissButton={ TextButton(enabled=!busy,onClick={ clearFiles() }) { Text(label("Cancelar","Cancel")) } }) }
    proposal?.let { reviewed->iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={ if(!busy) clearFiles() },title={ Text(label("Confirmar restauración destructiva","Confirm destructive restore")) },
        text={ Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(reviewed.candidate.identity.url); Text(reviewed.candidate.name); Text("${reviewed.candidate.file.length()} bytes")
            SelectionContainer { Text("SHA-256: ${reviewed.candidate.sha}",style=MaterialTheme.typography.bodySmall) }
            Text(label("Se sustituirán datos existentes y el servidor se reiniciará. La aceptación HTTP no confirma una restauración correcta. No cierres la vista durante la subida.","Existing data will be replaced and the server will restart. HTTP acceptance does not confirm a successful restore. Do not leave during upload."),color=MaterialTheme.colorScheme.error)
            if(!reviewed.verified) Row { Checkbox(checked=acceptUnverified,onCheckedChange={ acceptUnverified=it }); Text(label("Acepto que el contenido cifrado y la contraseña del sistema no se han verificado localmente.","I accept that encrypted contents and system password were not verified locally.")) }
            OutlinedTextField(value=typed,onValueChange={ typed=it },label={ Text(label("Escribe RESTORE para continuar","Type RESTORE to continue")) },singleLine=true)
        } },
        confirmButton={ TextButton(enabled=!busy && typed=="RESTORE" && (reviewed.verified || acceptUnverified),onClick={
            val current=source
            if(current==null || !reviewed.candidate.identity.matches(current)) { clearFiles(); note="prepare-failed" }
            else {
                proposal=null; busy=true; attempted=false; progress=0; note="sending"
                job=scope.launch {
                    try {
                        val target=reviewed.candidate.identity
                        val access=restoreControl(current,target,true,reviewed.password,reviewed.system)
                        require(target.matches(current))
                        val body=object:RequestBody() {
                            override fun contentType()="application/zip".toMediaType()
                            override fun contentLength()=reviewed.candidate.file.length()
                            override fun isOneShot()=true
                            override fun writeTo(sink:BufferedSink) {
                                reviewed.candidate.file.inputStream().use { input->
                                    val buffer=ByteArray(65536); var count=0L
                                    while(true) { val n=input.read(buffer); if(n<0) break; sink.write(buffer,0,n); count+=n; progress=count }
                                }
                            }
                        }
                        val multipart=MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("auth",access.auth).addFormDataPart("datafile","backup.zip",body).build()
                        val client=OkHttpHelper.createDownloadClient(target.ignoreSsl).newBuilder().retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
                            .readTimeout(150,TimeUnit.SECONDS).writeTimeout(150,TimeUnit.SECONDS).callTimeout(5,TimeUnit.MINUTES).build()
                        val active=client.newCall(Request.Builder().url(restoreEndpoint(target.url,"restoreserver.ashx")).header("Cookie",target.cookie).post(multipart).build())
                        call=active; ensureActive(); attempted=true; note="uploading"
                        val code=withContext(Dispatchers.IO) { active.execute().use { it.code } }
                        note=if(code in 200..299) "accepted" else "rejected"
                    } catch(e:CancellationException) { note=if(attempted) "unknown" else "cancelled"; throw e }
                    catch(_:Exception) { note=if(attempted) "unknown" else "prepare-failed" }
                    finally { call?.cancel(); call=null; withContext(NonCancellable+Dispatchers.IO) { reviewed.candidate.file.delete() }; busy=false }
                }
            }
        }) { Text(label("Restaurar este servidor","Restore this server")) } },dismissButton={ TextButton(enabled=!busy,onClick={ clearFiles() }) { Text(label("Cancelar","Cancel")) } }) }
}
