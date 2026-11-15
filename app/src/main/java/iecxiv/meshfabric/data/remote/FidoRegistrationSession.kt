package iecxiv.meshfabric.data.remote

import com.google.gson.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal data class FidoRegistrationBinding(val server:String,val account:String,val generation:Long)

/** Protocol lifecycle only; transport must supply the live binding for every callback.
 * This HTTPS-origin flow does not authorize an Android app for a web domain.
 * A send failure or disconnect after submission must call cancel(), never resend.
 */
internal class FidoRegistrationSession(
    private val binding:FidoRegistrationBinding,
    private val name:String,
    existingIndices:Set<Long>,
    ignoreSsl:Boolean,
    private val clock:()->Long={System.nanoTime()/1_000_000L}
) {
    enum class Phase { NEW,OPTIONS,AUTHENTICATOR,ACK,READBACK,COMPLETE,REJECTED,CANCELLED,EXPIRED,INVALID,UNKNOWN }
    @Volatile var phase=Phase.NEW
        private set
    private val previous=existingIndices.toSet()
    private val origin:String
    private var started=0L
    private var budget=60_000L
    private var challenge=""
    private var index:Long?=null
    init {
        val url=requireNotNull(binding.server.toHttpUrlOrNull())
        require(url.scheme=="https" && !ignoreSsl && url.username.isEmpty() && url.password.isEmpty() && url.query==null && url.fragment==null)
        require(binding.account.isNotBlank() && binding.generation>=0)
        require(previous.all { it in 0L..4294967295L })
        origin=url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString().removeSuffix("/")
        FidoRegistrationContract.start(name)
    }
    private fun terminal()=phase in setOf(Phase.COMPLETE,Phase.REJECTED,Phase.CANCELLED,Phase.EXPIRED,Phase.INVALID,Phase.UNKNOWN)
    private fun stop(next:Phase) { phase=next; challenge=""; index=null }
    private fun uncertain()=phase==Phase.ACK || phase==Phase.READBACK
    private fun valid(current:FidoRegistrationBinding):Boolean {
        if(terminal()) return false
        if(current!=binding) { stop(if(uncertain()) Phase.UNKNOWN else Phase.CANCELLED); return false }
        if(phase!=Phase.NEW && clock()-started>=budget) { stop(if(uncertain()) Phase.UNKNOWN else Phase.EXPIRED); return false }
        return true
    }
    @Synchronized fun start(current:FidoRegistrationBinding):JsonObject? {
        if(!valid(current) || phase!=Phase.NEW) return null
        started=clock(); phase=Phase.OPTIONS
        return FidoRegistrationContract.start(name)
    }
    @Synchronized fun acceptOptions(current:FidoRegistrationBinding,reply:JsonObject):JsonObject? {
        if(!valid(current) || phase!=Phase.OPTIONS) return null
        return runCatching {
            val options=FidoRegistrationContract.options(reply,name)
            require(options.getAsJsonObject("user").get("name").asString==binding.account)
            if(options.has("timeout")) {
                val timeout=options.get("timeout")
                require(timeout.isJsonPrimitive && timeout.asJsonPrimitive.isNumber)
                val ms=timeout.asString.toLongOrNull()
                require(ms!=null && ms>0)
                budget=minOf(budget,(clock()-started)+minOf(ms,60_000L))
            }
            challenge=options.get("challenge").asString
            phase=Phase.AUTHENTICATOR
            options
        }.getOrElse { stop(Phase.INVALID); null }
    }
    @Synchronized fun submit(current:FidoRegistrationBinding,credential:JsonObject):JsonObject? {
        if(!valid(current) || phase!=Phase.AUTHENTICATOR) return null
        return runCatching {
            val packet=FidoRegistrationContract.finish(name,credential,challenge,origin)
            challenge=""; phase=Phase.ACK
            packet
        }.getOrElse { stop(Phase.INVALID); null }
    }
    @Synchronized fun acknowledge(current:FidoRegistrationBinding,reply:JsonObject):JsonObject? {
        if(!valid(current) || phase!=Phase.ACK) return null
        return runCatching {
            val received=FidoRegistrationContract.acknowledgedIndex(reply)
            if(received==null) { stop(Phase.REJECTED); null } else {
                require(received !in previous)
                index=received; phase=Phase.READBACK
                JsonObject().apply { addProperty("action","otp-hkey-get") }
            }
        }.getOrElse { stop(Phase.UNKNOWN); null }
    }
    @Synchronized fun readback(current:FidoRegistrationBinding,reply:JsonObject):Boolean {
        if(!valid(current) || phase!=Phase.READBACK) return false
        return runCatching {
            require(reply.get("action")?.asString=="otp-hkey-get")
            val list=reply.get("keys")
            require(list!=null && list.isJsonArray)
            val ids=mutableSetOf<Long>()
            var observed=false
            for(value in list.asJsonArray) {
                require(value.isJsonObject)
                val key=value.asJsonObject
                val raw=key.get("i")
                require(raw!=null && raw.isJsonPrimitive && raw.asJsonPrimitive.isNumber)
                val id=raw.asString.toLongOrNull()
                require(id!=null && id in 0L..4294967295L && ids.add(id))
                val keyName=key.get("name")
                val type=key.get("type")
                require(keyName!=null && keyName.isJsonPrimitive && keyName.asJsonPrimitive.isString)
                require(type!=null && type.isJsonPrimitive && type.asJsonPrimitive.isNumber && type.asString.toIntOrNull()!=null)
                if(id==index && keyName.asString==name && type.asInt==3) observed=true
            }
            if(observed) stop(Phase.COMPLETE)
            observed
        }.getOrElse { stop(Phase.UNKNOWN); false }
    }
    @Synchronized fun poll(current:FidoRegistrationBinding):Phase { valid(current); return phase }
    @Synchronized fun cancel() { if(!terminal()) stop(if(uncertain()) Phase.UNKNOWN else Phase.CANCELLED) }
}
