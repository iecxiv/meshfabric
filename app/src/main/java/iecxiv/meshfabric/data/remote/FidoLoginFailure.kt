package iecxiv.meshfabric.data.remote

import java.io.IOException
import javax.net.ssl.SSLException

/** Fixed public codes only. Never expose exception messages, URLs, cookies or credentials. */
internal class FidoLoginFailure(val code:Code):IOException(code.tag) {
    enum class Code(val tag:String) {
        PREPARE("FIDO-001"), WEB_RESPONSE("FIDO-101"), ASSERTION("FIDO-201"),
        SUBMIT("FIDO-200"), TRANSPORT("FIDO-202"), HTTP_STATUS("FIDO-203"),
        COOKIES("FIDO-204"), TLS("FIDO-205"), ACCOUNT("FIDO-300"), SESSION("FIDO-401")
    }
    companion object {
        fun codeOf(error:Throwable,fallback:Code):Code {
            var cursor:Throwable?=error
            repeat(8) {
                val current=cursor ?: return fallback
                if(current is FidoLoginFailure) return current.code
                cursor=current.cause
            }
            return fallback
        }
        fun transportCode(error:Throwable):Code {
            var cursor:Throwable?=error
            repeat(8) {
                val current=cursor ?: return Code.TRANSPORT
                if(current is SSLException) return Code.TLS
                cursor=current.cause
            }
            return Code.TRANSPORT
        }
    }
}
