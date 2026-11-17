package iecxiv.meshfabric.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Base64

/** Wire codec only. The caller must bind origin, account, socket and challenge lifetime.
 * Never constructs or rewrites authenticator clientDataJSON. Not a cryptographic verifier.
 */
internal object FidoRegistrationContract {
    private fun text(o:JsonObject,key:String):String {
        val e=o.get(key)
        require(e!=null && e.isJsonPrimitive && e.asJsonPrimitive.isString) { "Invalid FIDO field: $key" }
        return e.asString
    }
    private fun obj(o:JsonObject,key:String):JsonObject {
        val e=o.get(key)
        require(e!=null && e.isJsonObject) { "Invalid FIDO object: $key" }
        return e.asJsonObject
    }
    private fun bytes(value:String,max:Int=262144):ByteArray {
        require(value.isNotEmpty() && value.length<=max*2)
        val result=runCatching { Base64.getDecoder().decode(value) }
            .getOrElse { Base64.getUrlDecoder().decode(value) }
        require(result.isNotEmpty() && result.size<=max)
        return result
    }
    private fun b64(value:ByteArray)=Base64.getEncoder().encodeToString(value)
    private fun keyName(value:String):String {
        require(value.isNotBlank() && value.length<=128 && value.none { it.isISOControl() })
        return value
    }
    fun start(name:String)=JsonObject().apply {
        addProperty("action","webauthn-startregister")
        addProperty("name",keyName(name))
    }
    fun options(reply:JsonObject,name:String):JsonObject {
        require(text(reply,"action")=="webauthn-startregister")
        require(text(reply,"keyname")==keyName(name))
        val request=obj(reply,"request")
        require(bytes(text(request,"challenge"),1024).size>=16)
        val user=obj(request,"user")
        bytes(text(user,"id"),64)
        require(text(user,"name").isNotBlank())
        require(text(obj(request,"rp"),"name").isNotBlank())
        val algorithms=request.get("pubKeyCredParams")
        require(algorithms!=null && algorithms.isJsonArray && algorithms.asJsonArray.size()>0)
        return request.deepCopy()
    }
    fun finish(name:String,credential:JsonObject,challenge:String,expectedOrigin:String):JsonObject {
        require(expectedOrigin.isNotBlank())
        require(text(credential,"type")=="public-key")
        val rawId=bytes(text(credential,"rawId"),1024)
        require(bytes(text(credential,"id"),1024).contentEquals(rawId))
        val response=obj(credential,"response")
        val clientBytes=bytes(text(response,"clientDataJSON"),16384)
        val parsed=JsonParser.parseString(clientBytes.toString(Charsets.UTF_8))
        require(parsed.isJsonObject)
        val client=parsed.asJsonObject
        require(text(client,"type")=="webauthn.create")
        require(bytes(text(client,"challenge"),1024).contentEquals(bytes(challenge,1024)))
        require(text(client,"origin")==expectedOrigin)
        if(client.has("crossOrigin")) {
            val cross=client.get("crossOrigin")
            require(cross.isJsonPrimitive && cross.asJsonPrimitive.isBoolean && !cross.asBoolean)
        }
        val attestation=bytes(text(response,"attestationObject"))
        return JsonObject().apply {
            addProperty("action","webauthn-endregister")
            addProperty("name",keyName(name))
            add("response",JsonObject().apply {
                addProperty("type","public-key")
                addProperty("id",b64(rawId))
                addProperty("rawId",b64(rawId))
                add("response",JsonObject().apply {
                    addProperty("clientDataJSON",b64(clientBytes))
                    addProperty("attestationObject",b64(attestation))
                })
            })
        }
    }
    /** Acknowledgement only; the controller must subsequently verify the key-list readback. */
    fun acknowledgedIndex(reply:JsonObject):Long? {
        require(text(reply,"action")=="otp-hkey-setup-response")
        val result=reply.get("result")
        require(result!=null && result.isJsonPrimitive && result.asJsonPrimitive.isBoolean)
        if(!result.asBoolean) return null
        val index=reply.get("index")
        require(index!=null && index.isJsonPrimitive && index.asJsonPrimitive.isNumber)
        val number=index.asString.toLongOrNull()
        require(number!=null && number in 0L..4294967295L)
        return number
    }
}
