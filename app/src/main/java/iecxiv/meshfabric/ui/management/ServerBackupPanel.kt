package iecxiv.meshfabric.ui.management

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.R
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

internal fun backupAllowed(rights:Long)=rights==-1L || rights==4294967295L || (rights and 1L)!=0L
internal fun validBackupHeader(head:ByteArray,length:Int,total:Long):Boolean =
    length>=4 && head.size>=4 && total>=22 && head[0]==0x50.toByte() && head[1]==0x4B.toByte() &&
        ((head[2]==3.toByte() && head[3]==4.toByte()) || (head[2]==5.toByte() && head[3]==6.toByte()))
internal fun backupFileName(now:LocalDateTime)="meshfabric-server-backup-"+now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))+".zip"

private sealed interface Control {
    data class Access(val allowed:Boolean,val systemPassword:Boolean):Control
    object Sent:Control
    data class Failed(@StringRes val message:Int):Control
}

private suspend fun controlExchange(source:MeshCentralClient,check:Boolean,password:String,override:Boolean):Control {
    if(source.isUserLogout || source.authCookie.isBlank()) return Control.Failed(R.string.my_server_backup_failed_session)
    return withTimeoutOrNull(20_000L) {
        suspendCancellableCoroutine<Control> { continuation->
            val done=AtomicBoolean(false)
            val requested=AtomicBoolean(false)
            fun finish(result:Control,socket:WebSocket?,graceful:Boolean) {
                if(!done.compareAndSet(false,true)) return
                if(graceful) socket?.close(1000,null) else socket?.cancel()
                if(continuation.isActive) continuation.resume(result)
            }
            val listener=object:WebSocketListener() {
                override fun onOpen(webSocket:WebSocket,response:Response) {
                    webSocket.send(JSONObject().put("action","urlargs").put("args",JSONObject().put("auth",source.extractRawToken(source.authCookie))).toString())
                    webSocket.send(JSONObject().put("action","userinfo").toString())
                }
                override fun onMessage(webSocket:WebSocket,text:String) {
                    if(text.length>2_000_000) { finish(Control.Failed(R.string.my_server_backup_failed_access),webSocket,false); return }
                    val packet=runCatching { JSONObject(text) }.getOrNull() ?: return
                    when(packet.optString("action")) {
                        "ping"->webSocket.send(JSONObject().put("action","pong").toString())
                        "userinfo"->{
                            val user=packet.optJSONObject("userinfo") ?: return
                            if(user.optString("_id").isBlank() || !requested.compareAndSet(false,true)) return
                            if(!backupAllowed(user.optLong("siteadmin",0L))) { finish(Control.Access(false,false),webSocket,true); return }
                            val request=JSONObject().put("action","serverbackuppassword")
                            if(check) request.put("check",true).put("dialog","backup")
                            else request.put("check",false).put("password",password).put("override",override)
                            if(!webSocket.send(request.toString())) { finish(Control.Failed(R.string.my_server_backup_failed_password),webSocket,false); return }
                            if(!check) finish(Control.Sent,webSocket,true)
                        }
                        "backuprestore"->if(check && packet.optString("dialog")=="backup") finish(Control.Access(true,packet.optBoolean("data")),webSocket,true)
                        "close","stopped"->finish(Control.Failed(R.string.my_server_backup_failed_access),webSocket,false)
                    }
                }
                override fun onClosed(webSocket:WebSocket,code:Int,reason:String) { finish(Control.Failed(R.string.my_server_backup_failed_access),webSocket,false) }
                override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) { finish(Control.Failed(R.string.my_server_backup_failed_access),webSocket,false) }
            }
            val socket=try {
                OkHttpHelper.createClient(source.ignoreSsl).newWebSocket(Request.Builder()
                    .url(OkHttpHelper.toWebSocketUrl(source.serverUrl,"/control.ashx?moreargs=1"))
                    .header("Cookie",source.authCookie).build(),listener)
            } catch(_:Exception) {
                finish(Control.Failed(R.string.my_server_backup_failed_access),null,false)
                return@suspendCancellableCoroutine
            }
            continuation.invokeOnCancellation { socket.cancel() }
        }
    } ?: Control.Failed(R.string.my_server_backup_failed_access)
}

private sealed interface Download {
    data class Done(val bytes:Long):Download
    data class Failed(@StringRes val message:Int,val code:Int=0):Download
}

private suspend fun downloadBackup(source:MeshCentralClient,cache:File,resolver:ContentResolver,output:Uri,onCall:(Call)->Unit,onProgress:(Long)->Unit):Download = withContext(Dispatchers.IO) {
    val temp=File(cache,"server-backup.part")
    var call:Call?=null
    try {
        temp.delete()
        val client=OkHttpHelper.createDownloadClient(source.ignoreSsl).newBuilder().readTimeout(150,TimeUnit.SECONDS).build()
        val request=Request.Builder().url(source.serverUrl.trimEnd('/')+"/backup.zip").header("Cookie",source.authCookie).build()
        val active=client.newCall(request)
        call=active
        onCall(active)
        active.execute().use { response->
            if(response.code==403) return@withContext Download.Failed(R.string.my_server_backup_failed_disabled,403)
            if(response.code==401) return@withContext Download.Failed(R.string.my_server_backup_failed_unauthorized,401)
            if(!response.isSuccessful) return@withContext Download.Failed(R.string.my_server_backup_failed_http,response.code)
            val body=response.body ?: return@withContext Download.Failed(R.string.my_server_backup_failed_invalid)
            var total=0L
            var reported=0L
            body.byteStream().use { input->
                temp.outputStream().use { out->
                    val buffer=ByteArray(65536)
                    while(true) {
                        val count=input.read(buffer)
                        if(count<0) break
                        out.write(buffer,0,count)
                        total+=count
                        if(total-reported>=262144) { reported=total; onProgress(total) }
                    }
                }
            }
        }
        val head=ByteArray(4)
        val read=temp.inputStream().use { stream->
            var count=0
            while(count<4) {
                val value=stream.read(head,count,4-count)
                if(value<0) break
                count+=value
            }
            count
        }
        val size=temp.length()
        if(!validBackupHeader(head,read,size)) return@withContext Download.Failed(R.string.my_server_backup_failed_invalid)
        val stream=resolver.openOutputStream(output,"wt") ?: return@withContext Download.Failed(R.string.my_server_backup_failed_write)
        val written=try {
            stream.use { destination-> temp.inputStream().use { it.copyTo(destination) } }
            true
        } catch(_:IOException) { false }
        if(!written) Download.Failed(R.string.my_server_backup_failed_write) else Download.Done(size)
    } catch(_:SecurityException) {
        Download.Failed(R.string.my_server_backup_failed_write)
    } catch(_:IOException) {
        if(call?.isCanceled()==true) Download.Failed(R.string.my_server_backup_cancelled) else Download.Failed(R.string.my_server_backup_failed_network)
    } finally {
        temp.delete()
    }
}

private sealed interface BackupPhase {
    object Idle:BackupPhase
    object Checking:BackupPhase
    object Sending:BackupPhase
    data class Working(val bytes:Long):BackupPhase
    data class Done(val bytes:Long):BackupPhase
    data class Failed(@StringRes val message:Int,val code:Int=0):BackupPhase
}
private data class BackupRequest(val password:String,val override:Boolean) { override fun toString()="BackupRequest" }

@Composable internal fun ServerBackupPanel(source:MeshCentralClient?) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var phase by remember { mutableStateOf<BackupPhase>(BackupPhase.Idle) }
    var systemPassword by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var override by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<BackupRequest?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var call by remember { mutableStateOf<Call?>(null) }
    DisposableEffect(Unit) { onDispose { call?.cancel(); job?.cancel() } }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri->
        val request=pending
        pending=null
        if(uri==null) { phase=BackupPhase.Idle; return@rememberLauncherForActivityResult }
        val current=source
        if(request==null || current==null) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver,uri) }
            phase=BackupPhase.Failed(R.string.my_server_backup_failed_lost)
            return@rememberLauncherForActivityResult
        }
        phase=BackupPhase.Sending
        job=scope.launch {
            var success=false
            try {
                val control=controlExchange(current,false,request.password,request.override)
                if(control !is Control.Sent) {
                    phase=BackupPhase.Failed(if(control is Control.Failed) control.message else R.string.my_server_backup_failed_password)
                    return@launch
                }
                delay(700)
                phase=BackupPhase.Working(0)
                val result=downloadBackup(current,context.cacheDir,context.contentResolver,uri,{ call=it },{ phase=BackupPhase.Working(it) })
                call=null
                phase=when(result) {
                    is Download.Done->{ success=true; BackupPhase.Done(result.bytes) }
                    is Download.Failed->BackupPhase.Failed(result.message,result.code)
                }
            } finally {
                if(!success) withContext(NonCancellable) { runCatching { DocumentsContract.deleteDocument(context.contentResolver,uri) } }
            }
        }
    }
    val busy=phase is BackupPhase.Checking || phase is BackupPhase.Sending || phase is BackupPhase.Working
    Text(stringResource(R.string.my_server_backup_title),style=MaterialTheme.typography.titleSmall)
    TextButton(enabled=source!=null && !busy,onClick={
        val current=source
        if(current!=null) {
            phase=BackupPhase.Checking
            job=scope.launch {
                phase=when(val access=controlExchange(current,true,"",false)) {
                    is Control.Access->if(access.allowed) {
                        systemPassword=access.systemPassword; password=""; override=false; dialog=true
                        BackupPhase.Idle
                    } else BackupPhase.Failed(R.string.my_server_backup_failed_denied)
                    is Control.Failed->BackupPhase.Failed(access.message)
                    Control.Sent->BackupPhase.Failed(R.string.my_server_backup_failed_access)
                }
            }
        }
    }) { Text(stringResource(R.string.my_server_backup_start)) }
    when(val state=phase) {
        BackupPhase.Idle->Unit
        BackupPhase.Checking->Text(stringResource(R.string.my_server_backup_checking),style=MaterialTheme.typography.bodySmall)
        BackupPhase.Sending->{ Text(stringResource(R.string.my_server_backup_sending),style=MaterialTheme.typography.bodySmall); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        is BackupPhase.Working->{
            Text(stringResource(R.string.my_server_backup_working,Formatter.formatShortFileSize(context,state.bytes)),style=MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick={ call?.cancel() }) { Text(stringResource(R.string.my_server_backup_cancel_download)) }
        }
        is BackupPhase.Done->Text(stringResource(R.string.my_server_backup_done,Formatter.formatShortFileSize(context,state.bytes)),style=MaterialTheme.typography.bodySmall)
        is BackupPhase.Failed->Text(if(state.message==R.string.my_server_backup_failed_http) stringResource(state.message,state.code) else stringResource(state.message),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
    }
    Text(stringResource(R.string.my_server_backup_restore_pending),style=MaterialTheme.typography.bodySmall)
    if(dialog) AlertDialog(onDismissRequest={ dialog=false; password="" },title={ Text(stringResource(R.string.my_server_backup_dialog_title)) },
        text={ Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.my_server_backup_dialog_text))
            OutlinedTextField(value=password,onValueChange={ if(it.length<=256) password=it },label={ Text(stringResource(R.string.my_server_backup_password)) },singleLine=true,enabled=!override,
                visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth())
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(checked=override,enabled=systemPassword,onCheckedChange={ override=it })
                Text(stringResource(R.string.my_server_backup_system_password))
            }
            if(!override && password.isEmpty()) Text(stringResource(R.string.my_server_backup_no_encryption),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.my_server_backup_other_sessions),style=MaterialTheme.typography.bodySmall)
        } },
        confirmButton={ TextButton(onClick={
            pending=BackupRequest(if(override) "" else password,override)
            dialog=false; password=""; phase=BackupPhase.Idle
            launcher.launch(backupFileName(LocalDateTime.now()))
        }) { Text(stringResource(R.string.my_server_backup_choose)) } },
        dismissButton={ TextButton(onClick={ dialog=false; password="" }) { Text(stringResource(R.string.my_server_cancel)) } })
}
