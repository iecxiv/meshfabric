package iecxiv.meshfabric.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Base64

/** Codec only: it does not verify signatures or establish an authenticated session. */
internal object FidoLoginContract {
    data class Options(val publicKey:JsonObject,val challenge:String,val allowedIds:Set<String>)
    private fun text(o:JsonObject,k:String):String {
        val v=o.get(k)
        require(v!=null && v.isJsonPrimitive && v.asJsonPrimitive.isString)
        return v.asString
    }
    private fun obj(o:JsonObject,k:String):JsonObject {
        val v=o.get(k); require(v!=null && v.isJsonObject); return v.asJsonObject
    }
    private fun bytes(s:String,min:Int,max:Int):ByteArray {
        require(s.length<=max*2+4)
        val b=runCatching { Base64.getDecoder().decode(s) }.getOrElse { Base64.getUrlDecoder().decode(s) }
        require(b.size in min..max); return b
    }
    private fun b64(b:ByteArray)=Base64.getEncoder().encodeToString(b)
    fun options(challenge:JsonObject):Options {
        require(text(challenge,"type")=="webAuthn")
        val raw=text(challenge,"challenge")
        bytes(raw,16,1024)
        val keys=challenge.get("keyIds")
        require(keys!=null && keys.isJsonArray && keys.asJsonArray.size() in 1..256)
        val ids=linkedSetOf<String>()
        val allowed=com.google.gson.JsonArray()
        for(key in keys.asJsonArray) {
            require(key.isJsonPrimitive && key.asJsonPrimitive.isString)
            val id=b64(bytes(key.asString,1,1024)); require(ids.add(id))
            allowed.add(JsonObject().apply { addProperty("type","public-key"); addProperty("id",id) })
        }
        val result=JsonObject().apply { addProperty("challenge",raw); add("allowCredentials",allowed) }
        if(challenge.has("timeout")) {
            val timeout=challenge.get("timeout")
            require(timeout.isJsonPrimitive && timeout.asJsonPrimitive.isNumber)
            val ms=timeout.asString.toLongOrNull(); require(ms!=null && ms>0)
            result.addProperty("timeout",minOf(ms,60_000L))
        }
        return Options(result,raw,ids.toSet())
    }
    fun form(options:Options,credential:JsonObject,expectedOrigin:String):Map<String,String> {
        require(FidoWebOriginPolicy(expectedOrigin).origin==expectedOrigin)
        require(text(credential,"type")=="public-key")
        val id=bytes(text(credential,"rawId"),1,1024)
        require(bytes(text(credential,"id"),1,1024).contentEquals(id))
        require(b64(id) in options.allowedIds)
        val response=obj(credential,"response")
        val clientBytes=bytes(text(response,"clientDataJSON"),1,16384)
        val client=JsonParser.parseString(clientBytes.toString(Charsets.UTF_8)).asJsonObject
        require(text(client,"type")=="webauthn.get")
        require(text(client,"origin")==expectedOrigin)
        require(bytes(text(client,"challenge"),16,1024).contentEquals(bytes(options.challenge,16,1024)))
        if(client.has("crossOrigin")) {
            val cross=client.get("crossOrigin")
            require(cross.isJsonPrimitive && cross.asJsonPrimitive.isBoolean && !cross.asBoolean)
        }
        val auth=bytes(text(response,"authenticatorData"),37,4096)
        val signature=bytes(text(response,"signature"),1,16384)
        val handle=response.get("userHandle")
        val handleBytes=if(handle==null || handle.isJsonNull) ByteArray(0) else bytes(text(response,"userHandle"),0,1024)
        val assertion=JsonObject().apply {
            addProperty("id",b64(id))
            addProperty("clientDataJSON",b64(clientBytes))
            addProperty("userHandle",b64(handleBytes))
            addProperty("signature",b64(signature))
            addProperty("authenticatorData",b64(auth))
        }
        return mapOf("hwtoken" to assertion.toString())
    }
}
