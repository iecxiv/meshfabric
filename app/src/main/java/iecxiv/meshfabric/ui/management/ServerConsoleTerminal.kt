package iecxiv.meshfabric.ui.management

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

private class ServerConsoleRenderer(context:Context,private val status:(Boolean)->Unit) {
    private var destroyed=false
    private var ready=false
    private var latest=""
    private var sent=""
    val view=WebView(context)
    @SuppressLint("SetJavaScriptEnabled","ClickableViewAccessibility")
    fun initialize() {
        view.setBackgroundColor(android.graphics.Color.rgb(13,17,23))
        view.settings.apply {
            javaScriptEnabled=true
            allowFileAccess=false
            allowContentAccess=false
            domStorageEnabled=false
            javaScriptCanOpenWindowsAutomatically=false
            setSupportMultipleWindows(false)
            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            blockNetworkLoads=true
            cacheMode=WebSettings.LOAD_NO_CACHE
            safeBrowsingEnabled=true
        }
        view.isVerticalScrollBarEnabled=false
        view.setDownloadListener { _,_,_,_,_-> }
        view.setOnTouchListener { target,event->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE->target.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->target.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
        view.webChromeClient=object:WebChromeClient() {
            override fun onConsoleMessage(consoleMessage:ConsoleMessage)=true
        }
        view.webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(webView:WebView,request:WebResourceRequest)=request.url.toString()!=SERVER_CONSOLE_URL
            override fun shouldInterceptRequest(webView:WebView,request:WebResourceRequest):WebResourceResponse {
                val path=serverConsoleAsset(request.url.toString()) ?: return blocked()
                return try {
                    val mime=when { path.endsWith(".html")->"text/html"; path.endsWith(".css")->"text/css"; else->"application/javascript" }
                    WebResourceResponse(mime,"utf-8",200,"OK",mapOf("Cache-Control" to "no-store","X-Content-Type-Options" to "nosniff"),context.assets.open(path))
                } catch(_:Exception) { blocked() }
            }
            override fun onPageFinished(webView:WebView,url:String) {
                if(destroyed || url!=SERVER_CONSOLE_URL) return
                webView.evaluateJavascript("Boolean(window.serverConsoleReady && window.serverConsoleRender)") { value->
                    if(!destroyed) { ready=value=="true"; status(ready); if(ready) { sent=""; deliver() } }
                }
            }
            override fun onReceivedError(webView:WebView,request:WebResourceRequest,error:WebResourceError) { if(request.isForMainFrame && !destroyed) { ready=false; status(false) } }
            override fun onReceivedHttpError(webView:WebView,request:WebResourceRequest,response:WebResourceResponse) { if(request.isForMainFrame && !destroyed) { ready=false; status(false) } }
            override fun onRenderProcessGone(webView:WebView,detail:RenderProcessGoneDetail):Boolean { if(!destroyed) { ready=false; status(false) }; return true }
        }
        view.loadUrl(SERVER_CONSOLE_URL)
    }
    private fun blocked()=WebResourceResponse("text/plain","utf-8",403,"Blocked",emptyMap(),ByteArrayInputStream(ByteArray(0)))
    fun submit(payload:String) { latest=payload; deliver() }
    private fun deliver() {
        if(destroyed || !ready || latest.isBlank() || latest==sent) return
        val payload=latest
        sent=payload
        view.evaluateJavascript("window.serverConsoleRender && window.serverConsoleRender($payload)") { result->
            if(!destroyed && result!="true") { ready=false; status(false) }
        }
    }
    fun close() {
        if(destroyed) return
        destroyed=true; ready=false; latest=""; sent=""
        (view.parent as? ViewGroup)?.removeView(view)
        view.stopLoading(); view.clearHistory(); view.removeAllViews(); view.destroy()
    }
}

@Composable internal fun ServerConsoleTerminal(output:List<String>,onClear:()->Unit) {
    val context=LocalContext.current
    val clipboard=LocalClipboardManager.current
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun label(a:String,b:String)=if(es) a else b
    val snapshot=remember(output) { serverConsoleSnapshot(output) }
    var font by rememberSaveable { mutableIntStateOf(13) }
    var revision by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    val renderer=remember(context,revision) { ServerConsoleRenderer(context.applicationContext) { ok->loaded=ok; failed=!ok } }
    DisposableEffect(renderer) { onDispose { renderer.close() } }
    val payload=remember(snapshot,font,es) { serverConsolePayload(snapshot,label("Consola administrativa MeshCentral","MeshCentral administrative console"),label("Sin salida recibida.","No output received."),font,if(es) "es" else "en") }
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            TextButton(enabled=font>10,onClick={ font-- },contentPadding=PaddingValues(6.dp)) { Text("A−") }
            TextButton(enabled=font<20,onClick={ font++ },contentPadding=PaddingValues(6.dp)) { Text("A+") }
            TextButton(enabled=snapshot.isNotEmpty(),onClick={ clipboard.setText(AnnotatedString(snapshot)) }) { Text(label("Copiar salida","Copy output")) }
            TextButton(onClick=onClear) { Text(label("Limpiar","Clear")) }
        }
        Surface(modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.medium,color=Color(0xFF0D1117),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
            if(failed) Column(Modifier.fillMaxWidth().heightIn(min=160.dp).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(label("No se pudo iniciar o mantener la vista de consola. El canal del servidor no se ha reenviado.","Could not start or maintain the console view. Server requests have not been resent."),color=Color(0xFFFF7B72))
                TextButton(onClick={ loaded=false; failed=false; revision++ }) { Text(label("Recrear solo la vista local","Recreate local view only")) }
            } else key(renderer) {
                AndroidView(modifier=Modifier.fillMaxWidth().height(320.dp),factory={ renderer.initialize(); renderer.view },onReset=null,onRelease={ renderer.close() },update={ renderer.submit(payload) })
            }
        }
        if(!loaded && !failed) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(label("Salida xterm local de solo lectura. Escribe y revisa el comando debajo; no es una shell del dispositivo.","Read-only local xterm output. Enter and review the command below; this is not a device shell."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
