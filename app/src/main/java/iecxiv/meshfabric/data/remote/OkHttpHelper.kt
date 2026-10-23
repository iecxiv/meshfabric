package iecxiv.meshfabric.data.remote

import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object OkHttpHelper {

    fun createClient(ignoreSsl: Boolean = false): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // For WebSockets
            .writeTimeout(15, TimeUnit.SECONDS)
            .pingInterval(5, TimeUnit.SECONDS) // Automatic OkHttp WebSocket ping/pong to detect broken tunnels immediately

        if (ignoreSsl) {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val sslSocketFactory = sslContext.socketFactory

            builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier(HostnameVerifier { _, _ -> true })
        }

        return builder.build()
    }

    fun sanitizeServerUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        return url.removeSuffix("/")
    }

    fun toWebSocketUrl(httpUrl: String, path: String): String {
        val base = sanitizeServerUrl(httpUrl)
        val wsBase = if (base.startsWith("https://")) {
            base.replaceFirst("https://", "wss://")
        } else {
            base.replaceFirst("http://", "ws://")
        }
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return "$wsBase$cleanPath"
    }
}
