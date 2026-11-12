package iecxiv.meshfabric.ui.devices

import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import iecxiv.meshfabric.data.remote.OkHttpHelper
import kotlinx.coroutines.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.time.*
import java.util.concurrent.TimeUnit

internal fun powerCsvUrl(server:String,node:String,language:String,zone:ZoneId,now:Long):HttpUrl =
    (OkHttpHelper.sanitizeServerUrl(server).trimEnd('/')+"/devicepowerevents.ashx").toHttpUrl().newBuilder()
        .addQueryParameter("id",node).addQueryParameter("l",language).addQueryParameter("tz",zone.id)
        .addQueryParameter("tf",(-zone.rules.getOffset(Instant.ofEpochMilli(now)).totalSeconds/60).toString()).build()
internal fun powerCsvValid(bytes:ByteArray,type:String):Boolean {
    if(bytes.isEmpty()) return false
    val prefix=bytes.take(1024).toByteArray().toString(Charsets.UTF_8).trimStart('\uFEFF',' ','\r','\n','\t').lowercase()
    if(prefix.startsWith("<") || prefix.startsWith("{") || prefix.startsWith("access denied")) return false
    return type.contains("csv",true) || ((type.contains("text/plain",true) || type.contains("octet-stream",true) || type.contains("vnd.ms-excel",true)) && prefix.lineSequence().first().let { ',' in it || ';' in it })
}
private suspend fun fetchPowerCsv(b:PowerBinding,language:String):ByteArray=withContext(Dispatchers.IO) {
    require(b.matches())
    val client=OkHttpHelper.createClient(b.tls).newBuilder().followRedirects(false).followSslRedirects(false).callTimeout(45,TimeUnit.SECONDS).build()
    val request=Request.Builder().url(powerCsvUrl(b.url,b.node,language,ZoneId.systemDefault(),System.currentTimeMillis())).header("Cookie",b.cookie).build()
    client.newCall(request).execute().use { response->
        require(response.code==200 && b.matches())
        val body=response.body ?: error("body")
        val max=16*1024*1024
        require(body.contentLength()<=max)
        val out=ByteArrayOutputStream()
        body.byteStream().use { input->
            val chunk=ByteArray(65536)
            while(true) {
                currentCoroutineContext().ensureActive();require(b.matches())
                val n=input.read(chunk);if(n<0) break
                require(out.size()+n<=max);out.write(chunk,0,n)
            }
        }
        out.toByteArray().also { require(b.matches() && powerCsvValid(it,response.header("Content-Type").orEmpty())) }
    }
}
@Composable internal fun DevicePowerCsv(b:PowerBinding?,enabled:Boolean) {
    val context=LocalContext.current;val language=LocalConfiguration.current.locales[0].language;val es=language=="es"
    val scope=rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PowerBinding?>(null) }
    DisposableEffect(Unit) { onDispose { pending=null } }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri->
        val binding=pending;pending=null
        if(uri==null) busy=false else scope.launch {
            var success=false
            try {
                require(binding!=null && binding.matches())
                val bytes=fetchPowerCsv(binding,language)
                withContext(Dispatchers.IO) {
                    ensureActive();require(binding.matches())
                    (context.contentResolver.openOutputStream(uri,"wt") ?: error("output")).use { output->
                        var at=0
                        while(at<bytes.size) { ensureActive();require(binding.matches());val n=minOf(65536,bytes.size-at);output.write(bytes,at,n);at+=n }
                    }
                    require(binding.matches())
                }
                success=true
                Toast.makeText(context,if(es) "CSV guardado." else "CSV saved.",Toast.LENGTH_SHORT).show()
            } catch(e:CancellationException) { throw e }
            catch(_:Exception) { Toast.makeText(context,if(es) "No se pudo descargar o guardar el CSV." else "Could not download or save CSV.",Toast.LENGTH_LONG).show() }
            finally {
                if(!success) withContext(NonCancellable+Dispatchers.IO) { runCatching { DocumentsContract.deleteDocument(context.contentResolver,uri) } }
                busy=false
            }
        }
    }
    TextButton(enabled=enabled && b!=null && b.matches() && !busy,modifier=Modifier.testTag("power_csv"),onClick={
        pending=b;busy=true
        runCatching { picker.launch("powerevents.csv") }.onFailure { pending=null;busy=false }
    }) { Text(if(busy) { if(es) "Preparando CSV…" else "Preparing CSV…" } else "powerevents.csv") }
}
