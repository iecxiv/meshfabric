package iecxiv.meshfabric.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.yubico.yubikit.fido.android.ui.FidoClient
import com.yubico.yubikit.fido.android.ui.Origin
import com.yubico.yubikit.fido.android.ui.enableFidoWebauthn
import iecxiv.meshfabric.data.remote.FidoWebOriginPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.ByteArrayInputStream

/** Supply a client constructed during Activity.onCreate, before onStart.
 * The host itself may be created later on the main thread. Does not import or export cookies.
 * The caller must display the returned WebView and bind ceremony results to its session.
 * This adapter is not yet an account-registration or login entry point.
 */
internal class FidoWebViewHost(private val activity:ComponentActivity, private val client:FidoClient):DefaultLifecycleObserver {
    enum class Failure { NAVIGATION,TLS,HTTP }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var browser:WebView?=null
    private var closed=false
    private var failed=false
    init {
        mainThread()
        check(activity.lifecycle.currentState!=Lifecycle.State.DESTROYED)
        activity.lifecycle.addObserver(this)
    }
    private fun mainThread()=check(Looper.myLooper()==Looper.getMainLooper())
    @SuppressLint("SetJavaScriptEnabled")
    fun create(server:String,ignoreSsl:Boolean=false,onFailure:(Failure)->Unit):WebView {
        mainThread()
        check(!closed && browser==null)
        val policy=FidoWebOriginPolicy(server,ignoreSsl)
        val view=WebView(activity)
        browser=view
        view.isSaveEnabled=false
        view.settings.apply {
            allowFileAccess=false
            allowContentAccess=false
            domStorageEnabled=true
            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically=false
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(view,false)
        fun block(reason:Failure) {
            if(closed || failed) return
            failed=true
            view.stopLoading()
            view.post { close(); onFailure(reason) }
        }
        val guardedClient=object:FidoClient {
            private fun allowed(origin:Origin)=!closed && !failed && policy.allowsMessage(origin.callingApp,true) && policy.allowsOrigin(origin.resolved) && policy.allowsNavigation(view.url.orEmpty())
            override suspend fun makeCredential(origin:Origin,request:String,clientDataHash:String?,callerLabel:String?):Result<String> {
                if(!allowed(origin) || clientDataHash!=null) return Result.failure(SecurityException("FIDO origin not authorized"))
                return client.makeCredential(origin,request,null,callerLabel)
            }
            override suspend fun getAssertion(origin:Origin,request:String,clientDataHash:String?,callerLabel:String?):Result<String> {
                if(!allowed(origin) || clientDataHash!=null) return Result.failure(SecurityException("FIDO origin not authorized"))
                return client.getAssertion(origin,request,null,callerLabel)
            }
        }
        val navigation=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                if(policy.allowsNavigation(request.url.toString())) return false
                if(request.isForMainFrame) block(Failure.NAVIGATION)
                return true
            }
            override fun onPageStarted(view:WebView,url:String?,favicon:Bitmap?) {
                if(!policy.allowsNavigation(url.orEmpty())) block(Failure.NAVIGATION)
            }
            override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse? {
                if(policy.allowsNavigation(request.url.toString())) return null
                return WebResourceResponse("text/plain","UTF-8",ByteArrayInputStream(ByteArray(0)))
            }
            override fun onReceivedSslError(view:WebView,handler:SslErrorHandler,error:SslError) {
                handler.cancel()
                block(Failure.TLS)
            }
            override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError) {
                if(request.isForMainFrame) block(Failure.HTTP)
            }
            override fun onReceivedHttpError(view:WebView,request:WebResourceRequest,response:WebResourceResponse) {
                if(request.isForMainFrame) block(Failure.HTTP)
            }
        }
        if(!view.enableFidoWebauthn(scope,guardedClient,navigation)) {
            close()
            error("Secure WebView messaging is unavailable")
        }
        view.webChromeClient=object:WebChromeClient() {
            override fun onConsoleMessage(message:ConsoleMessage?):Boolean=true
        }
        view.loadUrl(policy.entryUrl)
        return view
    }
    fun close() {
        mainThread()
        if(closed) return
        closed=true
        scope.cancel()
        activity.lifecycle.removeObserver(this)
        browser?.apply { stopLoading(); clearHistory(); removeAllViews(); destroy() }
        browser=null
    }
    override fun onDestroy(owner:LifecycleOwner)=close()
}
