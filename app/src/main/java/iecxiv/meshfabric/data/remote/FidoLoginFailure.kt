package iecxiv.meshfabric.data.remote

import java.io.IOException
import javax.net.ssl.SSLException

/** Fixed public codes only. Never expose exception messages, URLs, cookies or credentials. */
internal class FidoLoginFailure(val code:Code):IOException(code.tag) {
    enum class Code(val tag:String) {
        PREPARE("FIDO-001"), WEB_RESPONSE("FIDO-101"), ASSERTION("FIDO-201"),
        SUBMIT("FIDO-200"), TRANSPORT("FIDO-202"), HTTP_STATUS("FIDO-203"),
        COOKIES("FIDO-204"), TLS("FIDO-205"), ACCOUNT("FIDO-300"),
        VERIFY_CONNECTION("FIDO-301"), VERIFY_HTTP("FIDO-302"), VERIFY_TIMEOUT("FIDO-303"),
        VERIFY_CLOSED("FIDO-304"), VERIFY_IDENTITY("FIDO-305"), VERIFY_PROTOCOL("FIDO-306"),
        VERIFY_TLS("FIDO-307"), VERIFY_INVALIDATED("FIDO-308"),
        VERIFY_UNAUTHORIZED("FIDO-311"), VERIFY_FORBIDDEN("FIDO-312"),
        VERIFY_REDIRECT("FIDO-313"), VERIFY_NOT_WEBSOCKET("FIDO-314"), SESSION("FIDO-401")
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
