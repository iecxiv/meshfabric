package iecxiv.meshfabric.ui.management

import android.content.*
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import iecxiv.meshfabric.data.remote.AgentInvitationContract
import iecxiv.meshfabric.data.remote.InvitationBinding
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import kotlinx.coroutines.*
import okhttp3.Call
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicReference

internal val enrollmentFamilies=listOf(0,1,5,2,6,7,3,4,8)
internal val enrollmentArchitectures=linkedMapOf(6 to "Linux x86-64",5 to "Linux x86-32",10005 to "macOS Universal",25 to "Linux ARM-HF / Raspberry Pi",26 to "Linux ARM64-HF",28 to "Linux MIPS24KC / OpenWRT",30 to "FreeBSD x86-64",32 to "Linux ARM 64 bit",36 to "OpenWRT x86-64",37 to "OpenBSD x86-64",40 to "Linux MIPSEL24KC / OpenWRT",41 to "ARMADA / CORTEX-A53 / MUSL",45 to "RISC-V 64 bit",16 to "macOS x86-64",29 to "macOS ARM64")
internal data class AgentEnrollmentOptions(val family:Int=0,val architecture:Int=6,val mode:Int=0,val assistant:Int=2) {
    init {require(family in enrollmentFamilies && architecture in enrollmentArchitectures && mode in setOf(0,2,1) && assistant in 0..4)}
    val showsType:Boolean get()=family in setOf(0,5)
    fun selectFamily(v:Int)=copy(family=v,mode=0,assistant=2)
}
internal data class EnrollmentArtifact(val label:String,val url:String,val fileName:String,val kind:String)
internal const val enrollmentDownloadLimit=134217728L
internal fun enrollmentBase(value:String):String {val u=(value.trimEnd('/')+"/").toHttpUrl();require(u.username.isEmpty() && u.password.isEmpty() && u.query==null && u.fragment==null);return u.toString().trimEnd('/')}
private fun enrollmentMesh(value:String):String {val p=value.split('/');require(p.size==3 && p[0]=="mesh" && p[2].matches(Regex("[A-Za-z0-9_@+${'$'}=-]+")));return p[2]}
internal fun enrollmentArtifacts(base:String,mesh:String,o:AgentEnrollmentOptions):List<EnrollmentArtifact> {
    val b=enrollmentBase(base);enrollmentMesh(mesh)
    fun item(label:String,platform:String,id:Int,name:String,kind:String,mode:Int=0)=EnrollmentArtifact(label,AgentInvitationContract.download(b,mesh,platform,id,mode),name,kind)
    return when(o.family) {
        0,3->listOf(item("Windows x86-32","windows",3,"meshagent-x86.exe","pe",if(o.family==0) o.mode else 0),item("Windows x86-64","windows",4,"meshagent-x64.exe","pe",if(o.family==0) o.mode else 0),item("Windows ARM64","windows",43,"meshagent-arm64.exe","pe",if(o.family==0) o.mode else 0))
        2->listOf(item("macOS x86-64","macos",16,"meshagent-macos-x64.zip","zip"),item("macOS ARM64","macos",29,"meshagent-macos-arm64.zip","zip"),item("macOS Universal","macos",10005,"meshagent-macos-universal.zip","zip"))
        8->listOf(item("macOS Universal","macos",10005,"meshagent-macos-universal.zip","zip"))
        5->listOf(item(enrollmentArchitectures.getValue(o.architecture),"binary",o.architecture,"meshagent","bin",o.mode))
        6->listOf(item("Android APK","android",14,"meshagent.apk","zip"))
        7->listOf(item("MeshCentral Assistant","assistant",10006,"meshcentral-assistant.exe","pe",o.assistant))
        else->emptyList()
    }
}
internal fun enrollmentQuote(s:String)="'"+s.replace("'","'\"'\"'")+"'"
internal fun enrollmentLinuxCommand(base:String,mesh:String,uninstall:Boolean,ignoreSsl:Boolean):String {
    val b=enrollmentBase(base);val mid=enrollmentMesh(mesh);val url=(b+"/").toHttpUrl().newBuilder().addPathSegment("meshagents").addQueryParameter("script","1").build().toString()
    val tls=if(ignoreSsl) " --no-check-certificate" else "";val prefix=if(uninstall) "uninstall " else ""
    return "(wget "+enrollmentQuote(url)+tls+" -O ./meshinstall.sh || wget "+enrollmentQuote(url)+" --no-proxy"+tls+" -O ./meshinstall.sh) && chmod 755 ./meshinstall.sh && sudo -E ./meshinstall.sh "+prefix+enrollmentQuote(b)+" "+enrollmentQuote(mid)+" || ./meshinstall.sh "+prefix+enrollmentQuote(b)+" "+enrollmentQuote(mid)
}
internal fun enrollmentValidHeader(kind:String,bytes:ByteArray):Boolean {val h=bytes.take(4).joinToString("") {"%02x".format(it.toInt() and 255)};return when(kind) {"pe"->h.length==8 && h.startsWith("4d5a");"zip"->h=="504b0304";"bin"->h in setOf("7f454c46","cafebabe","bebafeca","feedface","cefaedfe","feedfacf","cffaedfe");else->false}}
internal fun enrollmentQr(payload:String)=QRCodeWriter().encode(payload,BarcodeFormat.QR_CODE,256,256,mapOf(EncodeHintType.CHARACTER_SET to "UTF-8",EncodeHintType.MARGIN to 2,EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
private fun familyLabel(v:Int,es:Boolean)=if(es) mapOf(0 to "Windows",1 to "Linux / BSD",5 to "Instalador binario Linux / BSD / macOS",2 to "Apple macOS",6 to "Dispositivo móvil",7 to "Asistente MeshCentral",3 to "Windows (Desinstalar)",4 to "Linux / BSD (Desinstalar)",8 to "Apple macOS (Desinstalar)").getValue(v) else mapOf(0 to "Windows",1 to "Linux / BSD",5 to "Linux / BSD / macOS binary installer",2 to "Apple macOS",6 to "Mobile device",7 to "MeshCentral Assistant",3 to "Windows (Uninstall)",4 to "Linux / BSD (Uninstall)",8 to "Apple macOS (Uninstall)").getValue(v)
private fun assistantLabel(v:Int,es:Boolean)=if(es) mapOf(2 to "Aplicación, conectar a petición del usuario",3 to "Aplicación, siempre conectada",0 to "Bandeja del sistema, conectar con consentimiento del usuario",1 to "Bandeja del sistema, siempre conectada",4 to "Bandeja del sistema, solo monitorizar").getValue(v) else mapOf(2 to "Application, connect on user request",3 to "Application, always connected",0 to "System tray, connect on user request",1 to "System tray, always connected",4 to "System tray, monitor only").getValue(v)
@Composable internal fun AgentEnrollmentSelectors(o:AgentEnrollmentOptions,enabled:Boolean,es:Boolean,onChange:(AgentEnrollmentOptions)->Unit) {
    InviteChoice(if(es) "Sistema operativo" else "Operating system","enroll-family",o.family,enrollmentFamilies,enabled,{familyLabel(it,es)}) {onChange(o.selectFamily(it))}
    if(o.family==5) InviteChoice(if(es) "Tipo de sistema" else "System type","enroll-architecture",o.architecture,enrollmentArchitectures.keys.toList(),enabled,{enrollmentArchitectures.getValue(it)}) {onChange(o.copy(architecture=it))}
    if(o.showsType) InviteChoice(if(es) "Tipo de instalación" else "Installation type","enroll-mode",o.mode,listOf(0,2,1),enabled,{modeLabel(it,es)}) {onChange(o.copy(mode=it))}
    if(o.family==7) InviteChoice(if(es) "Tipo de instalación" else "Installation type","enroll-assistant",o.assistant,listOf(2,3,0,1,4),enabled,{assistantLabel(it,es)}) {onChange(o.copy(assistant=it))}
}
@Composable private fun enrollmentDownloader(source:MeshCentralClient):Triple<Boolean,String,(EnrollmentArtifact)->Unit> {
    val context=LocalContext.current;val es=LocalConfiguration.current.locales[0].language=="es";val scope=rememberCoroutineScope()
    var busy by remember {mutableStateOf(false)};var notice by remember {mutableStateOf("")}
    var pending by remember {mutableStateOf<File?>(null)};var binding by remember {mutableStateOf<InvitationBinding?>(null)};var job by remember {mutableStateOf<Job?>(null)}
    val call=remember {AtomicReference<Call?>(null)}
    fun message(ok:Boolean)=if(es) {if(ok) "Archivo guardado." else "Descarga no completada. Comprueba conexión, permisos y disponibilidad del instalador."} else {if(ok) "File saved." else "Download not completed. Check connection, permissions and installer availability."}
    DisposableEffect(source) {onDispose {call.getAndSet(null)?.cancel();job?.cancel();pending?.delete();pending=null}}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {uri->
        val file=pending;val identity=binding;pending=null
        if(uri==null) {file?.delete();busy=false} else job=scope.launch {
            var saved=false
            try {require(file!=null && identity!=null && identity.matches(source));withContext(Dispatchers.IO) {file.inputStream().use {input->(context.contentResolver.openOutputStream(uri,"wt") ?: error("output")).use {output->val b=ByteArray(65536);while(true) {ensureActive();check(identity.matches(source));val n=input.read(b);if(n<0) break;output.write(b,0,n)}}}};saved=true;notice=message(true)}
            catch(e:CancellationException) {throw e} catch(_:Exception) {notice=message(false)}
            finally {file?.delete();if(!saved) withContext(NonCancellable+Dispatchers.IO) {runCatching {DocumentsContract.deleteDocument(context.contentResolver,uri)}};busy=false}
        }
    }
    val start:(EnrollmentArtifact)->Unit={artifact->if(!busy) {
        busy=true;notice=""
        val server=source.serverUrl;val cookie=source.authCookie;val ssl=source.ignoreSsl;val actor=source.userInfo.value?.id.orEmpty();val identity=InvitationBinding(server,actor,cookie,ssl)
        job=scope.launch {
            var file:File?=null
            try {
                check(actor.isNotBlank() && identity.matches(source))
                val target=artifact.url.toHttpUrl();val origin=enrollmentBase(server).toHttpUrl();require(target.scheme==origin.scheme && target.host==origin.host && target.port==origin.port && target.username.isEmpty() && target.password.isEmpty())
                withContext(Dispatchers.IO) {
                    val client=OkHttpHelper.createDownloadClient(ssl).newBuilder().followRedirects(false).followSslRedirects(false).build()
                    val request=client.newCall(Request.Builder().url(target).header("Cookie",cookie).build());call.set(request)
                    request.execute().use {response->require(response.code==200);val body=response.body ?: error("body");require(body.contentLength()<=enrollmentDownloadLimit);val mime=body.contentType()?.toString().orEmpty();require(!mime.startsWith("text/html") && !mime.startsWith("application/json"))
                        val f=File.createTempFile("agent-enrollment-",".part",context.cacheDir);file=f
                        body.byteStream().use {input->f.outputStream().use {output->val b=ByteArray(65536);var size=0L;while(true) {ensureActive();check(identity.matches(source));val n=input.read(b);if(n<0) break;size+=n;require(size<=enrollmentDownloadLimit);output.write(b,0,n)}}}
                        val head=ByteArray(4);require(f.inputStream().use {it.read(head)}==4 && enrollmentValidHeader(artifact.kind,head))
                    }
                }
                ensureActive();check(identity.matches(source));binding=identity;pending=file;picker.launch(artifact.fileName)
            } catch(e:CancellationException) {throw e} catch(_:Exception) {notice=message(false)}
            finally {call.getAndSet(null)?.cancel();if(pending==null) {file?.delete();busy=false}}
        }
    }}
    return Triple(busy,notice,start)
}
@Composable internal fun AgentEnrollmentGroupButton(source:MeshCentralClient,id:String,enabled:Boolean) {
    val es=LocalConfiguration.current.locales[0].language=="es";var open by remember(id) {mutableStateOf(false)}
    ProfileGroupActionButton(if(es) "Agregar agente" else "Add agent",Icons.Default.AddCircle,enabled,onClick={open=true})
    if(open) AgentInviteLinkDialog(source,id,false,enrollment=true) {open=false}
}
@Composable internal fun AgentEnrollmentContent(source:MeshCentralClient,group:JSONObject?,server:JSONObject?,enabled:Boolean) {
    val es=LocalConfiguration.current.locales[0].language=="es";val context=LocalContext.current
    fun t(a:String,b:String)=if(es) a else b
    var options by remember {mutableStateOf(AgentEnrollmentOptions())};var notice by remember {mutableStateOf("")}
    val download=enrollmentDownloader(source)
    fun copy(value:String) {notice=if(runCatching {(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("MeshCentral",value))}.isSuccess) t("Copiado.","Copied.") else t("No se ha podido copiar.","Could not copy.")}
    AgentEnrollmentSelectors(options,enabled && !download.first,es) {options=it;notice=""}
    Text(t("Los archivos y comandos incorporan este equipo al grupo seleccionado. La app no ejecuta instalaciones ni desinstalaciones.","Files and commands enroll the target computer into this group. The app does not run installation or uninstallation."),style=MaterialTheme.typography.bodySmall)
    if(options.family in setOf(3,4,8)) Text(t("Desinstalación: revisa el equipo de destino antes de ejecutar estas instrucciones.","Uninstall: verify the target computer before running these instructions."),color=MaterialTheme.colorScheme.error)
    val mesh=group?.optString("_id").orEmpty()
    val artifacts=if(enabled) runCatching {enrollmentArtifacts(source.serverUrl,mesh,options)}.getOrNull() else emptyList()
    if(enabled && artifacts==null) Text(t("No se han podido preparar enlaces seguros para este servidor.","Could not prepare safe links for this server."),color=MaterialTheme.colorScheme.error)
    artifacts.orEmpty().forEach {item->
        Text(item.label,style=MaterialTheme.typography.titleSmall)
        Row {TextButton(enabled=enabled && !download.first,onClick={download.third(item)}) {Text(t("Descargar","Download"))};TextButton(enabled=enabled,onClick={copy(item.url)}) {Text(t("Copiar enlace","Copy link"))}}
    }
    if(enabled && options.family in setOf(1,4,5)) {
        val command=runCatching {if(options.family==5) "wget -O meshagent "+enrollmentQuote(artifacts.orEmpty().single().url) else enrollmentLinuxCommand(source.serverUrl,mesh,options.family==4,source.ignoreSsl)}.getOrNull()
        command?.let {SelectionContainer {Text(it,style=MaterialTheme.typography.bodySmall)};TextButton(onClick={copy(it)}) {Text(t("Copiar comando","Copy command"))}}
        Text(if(options.family==5) t("Ejecuta chmod +x meshagent y abre el binario. En macOS puede ser necesario xattr -r -d com.apple.quarantine meshagent.","Run chmod +x meshagent and open the binary. macOS may require xattr -r -d com.apple.quarantine meshagent.") else t("Se necesitan permisos de root. En BSD instala antes wget, sudo y bash.","Root permissions are required. On BSD install wget, sudo and bash first."),style=MaterialTheme.typography.bodySmall)
        if(source.ignoreSsl) Text(t("La conexión está configurada para ignorar errores de certificado.","This connection is configured to ignore certificate errors."),color=MaterialTheme.colorScheme.error)
    }
    if(options.family==3) Text(t("Ejecuta el instalador en el equipo de destino y elige Desinstalar.","Run the installer on the target computer and choose Uninstall."))
    if(options.family==8) Text(t("Extrae el ZIP, pulsa con el botón derecho en Uninstall.command y elige Abrir.","Extract the ZIP, right-click Uninstall.command and choose Open."))
    if(options.family==6) {
        val payload=if(enabled) runCatching {AgentInvitationContract.mobileConfiguration(server?.optString("magenturl").orEmpty(),server?.optString("agentCertHash").orEmpty(),mesh)}.getOrNull() else null
        val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null,payload) {value=null;if(payload!=null) value=withContext(Dispatchers.Default) {val matrix=enrollmentQr(payload);val pixels=IntArray(256*256) {i->if(matrix[i%256,i/256]) android.graphics.Color.BLACK else android.graphics.Color.WHITE};Bitmap.createBitmap(pixels,256,256,Bitmap.Config.ARGB_8888).asImageBitmap()}}
        image?.let {Image(it,t("Código QR de conexión al grupo","Device group connection QR code"),Modifier.size(220.dp))}
        if(payload!=null) TextButton(onClick={copy(payload)}) {Text(t("Copiar configuración móvil","Copy mobile configuration"))} else Text(t("No hay configuración móvil completa recibida del servidor. No se inventa un QR.","Complete mobile configuration has not been received. No QR is fabricated."))
        Row {for((label,url) in listOf("Google Play" to "https://play.google.com/store/apps/details?id=com.meshcentral.agent2","Amazon Appstore" to "https://www.amazon.co.uk/gp/product/B097Z4Q7SK/")) TextButton(onClick={runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}}) {Text(label)}}
    }
    if(download.first) LinearProgressIndicator(Modifier.fillMaxWidth())
    if(download.second.isNotBlank()) Text(download.second,style=MaterialTheme.typography.bodySmall)
    if(notice.isNotBlank()) Text(notice,style=MaterialTheme.typography.bodySmall)
}
