package iecxiv.meshfabric.ui.login

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.gson.JsonParser
import iecxiv.meshfabric.MainActivity
import iecxiv.meshfabric.data.remote.FidoWebOriginPolicy
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class FidoAssertionScriptTest {
    @Test fun assertionScriptPreservesBinaryResponseInWebView() {
        val result=AtomicReference<String>(); val done=CountDownLatch(1)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var view:WebView
            scenario.onActivity { activity ->
                assertTrue(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))
                view=WebView(activity); view.settings.javaScriptEnabled=true; activity.setContentView(view)
                val options=JsonParser.parseString("""{"challenge":"AQID","allowCredentials":[{"type":"public-key","id":"AQID"}]}""").asJsonObject
                val page=FidoRegistrationPage(FidoWebOriginPolicy("https://server.example"),options,"ceremony_0123456789")
                WebViewCompat.addWebMessageListener(view,"meshFabricFidoRegistration",setOf("https://server.example"),WebViewCompat.WebMessageListener { _,m,_,_,_->result.set(m.data);done.countDown() })
                view.webViewClient=object:WebViewClient() { override fun onPageFinished(v:WebView,url:String?) { page.begin(true)?.let { v.evaluateJavascript(it,null) } } }
                view.loadDataWithBaseURL("https://server.example/", """<script>Object.defineProperty(navigator,'credentials',{value:{get:async function(o){if(o.publicKey.challenge[0]!==1||o.publicKey.allowCredentials[0].id[2]!==3)throw Error('bytes');return {id:'AQID',rawId:new Uint8Array([1,2,3]).buffer,type:'public-key',response:{clientDataJSON:new Uint8Array([32,123,125,32]).buffer,authenticatorData:new Uint8Array([0,128,255]).buffer,signature:new Uint8Array([255,1]).buffer,userHandle:null}};}}});</script>""", "text/html","UTF-8",null)
            }
            try {
                assertTrue(done.await(15,TimeUnit.SECONDS))
                val packet=JsonParser.parseString(result.get()).asJsonObject
                assertFalse(packet.has("error"))
                val response=packet.getAsJsonObject("credential").getAsJsonObject("response")
                assertArrayEquals(byteArrayOf(32,123,125,32),Base64.getUrlDecoder().decode(response.get("clientDataJSON").asString))
                assertArrayEquals(byteArrayOf(0,128.toByte(),255.toByte()),Base64.getUrlDecoder().decode(response.get("authenticatorData").asString))
                assertTrue(response.get("userHandle").isJsonNull)
            } finally { scenario.onActivity { WebViewCompat.removeWebMessageListener(view,"meshFabricFidoRegistration");view.destroy() } }
        }
    }
}
