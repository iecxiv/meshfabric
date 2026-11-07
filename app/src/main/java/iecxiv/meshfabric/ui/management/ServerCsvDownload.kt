package iecxiv.meshfabric.ui.management

import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ServerCsvDownload(enabled:Boolean,fileName:String,create:()->String) {
    val context=LocalContext.current
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun label(a:String,b:String)=if(es) a else b
    val scope=rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val currentCreate by rememberUpdatedState(create)
    val currentName by rememberUpdatedState(fileName)
    DisposableEffect(Unit) { onDispose { pending=null; job?.cancel() } }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri->
        val bytes=pending
        pending=null
        if(uri==null) busy=false
        else job=scope.launch {
            var success=false
            try {
                require(bytes!=null)
                withContext(Dispatchers.IO) {
                    currentCoroutineContext().ensureActive()
                    val output=context.contentResolver.openOutputStream(uri,"wt") ?: error("output")
                    output.use { stream->
                        var offset=0
                        while(offset<bytes.size) {
                            currentCoroutineContext().ensureActive()
                            val count=minOf(65536,bytes.size-offset)
                            stream.write(bytes,offset,count); offset+=count
                        }
                    }
                }
                success=true
                Toast.makeText(context,label("CSV guardado.","CSV saved."),Toast.LENGTH_SHORT).show()
            } catch(e:CancellationException) { throw e }
            catch(_:Exception) { Toast.makeText(context,label("No se pudo guardar el CSV.","Could not save CSV."),Toast.LENGTH_LONG).show() }
            finally {
                if(!success) withContext(NonCancellable+Dispatchers.IO) { runCatching { DocumentsContract.deleteDocument(context.contentResolver,uri) } }
                busy=false
            }
        }
    }
    TooltipBox(positionProvider=TooltipDefaults.rememberPlainTooltipPositionProvider(),tooltip={ PlainTooltip { Text(label("Descargar CSV","Download CSV")) } },state=rememberTooltipState()) {
        IconButton(enabled=enabled && !busy,onClick={
            val build=currentCreate
            val name=currentName
            busy=true
            job=scope.launch {
                try {
                    val bytes=withContext(Dispatchers.Default) { serverCsvBytes(build()) }
                    ensureActive(); pending=bytes
                    picker.launch(name)
                } catch(e:CancellationException) { pending=null; busy=false; throw e }
                catch(_:Exception) {
                    pending=null; busy=false
                    Toast.makeText(context,label("No se pudo preparar la descarga CSV.","Could not prepare CSV download."),Toast.LENGTH_LONG).show()
                }
            }
        }) { Icon(Icons.Default.Download,contentDescription=label("Descargar CSV","Download CSV")) }
    }
}
