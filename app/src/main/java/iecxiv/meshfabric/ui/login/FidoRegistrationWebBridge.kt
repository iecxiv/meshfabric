package iecxiv.meshfabric.ui.login

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.gson.JsonObject
import iecxiv.meshfabric.data.remote.FidoWebOriginPolicy
import java.util.UUID

internal class FidoRegistrationWebBridge(
    private val view: WebView,
    private val policy: FidoWebOriginPolicy,
    options: JsonObject,
    private val onCredential: (JsonObject) -> Unit,
    private val onFailure: () -> Unit
) {
    private val page = FidoRegistrationPage(policy, options, UUID.randomUUID().toString())
    private var closed = false
    val hasStarted: Boolean get() = page.hasStarted
    init {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            error("Secure WebView messaging is unavailable")
        }
        WebViewCompat.addWebMessageListener(view, "meshFabricFidoRegistration", setOf(policy.origin),
            WebViewCompat.WebMessageListener { _, message, origin, mainFrame, _ ->
                if (!closed) {
                    val data = runCatching { message.data }.getOrNull()
                    if (data != null) {
                        when (val result = page.receive(origin.toString(), mainFrame, view.url.orEmpty(), data)) {
                            is FidoRegistrationPage.Delivery.Credential -> onCredential(result.value)
                            FidoRegistrationPage.Delivery.Failed -> onFailure()
                            null -> Unit
                        }
                    }
                }
            })
    }
    fun onPageFinished(url: String) {
        if (closed) return
        if (!policy.allowsNavigation(url) || !policy.allowsNavigation(view.url.orEmpty())) {
            close(); onFailure(); return
        }
        page.begin()?.let { view.evaluateJavascript(it, null) }
    }
    fun close() {
        if (closed) return
        closed = true
        page.invalidate()
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.removeWebMessageListener(view, "meshFabricFidoRegistration")
        }
    }
}
