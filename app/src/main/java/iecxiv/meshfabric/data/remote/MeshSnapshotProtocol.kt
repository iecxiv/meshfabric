package iecxiv.meshfabric.data.remote
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import iecxiv.meshfabric.data.model.MeshGroup
import java.util.UUID
internal class MeshSnapshotRequests {
    enum class Mode { LEGACY, COMPLETE, IGNORE }
    private var prefix="mf-mesh-snapshot-${UUID.randomUUID()}-"
    private var issued=0L
    private var accepted=0L
    @Synchronized fun request(): JsonObject = JsonObject().apply {
        addProperty("action","meshes"); addProperty("tag",prefix+(++issued))
    }
    @Synchronized fun reset() {prefix="mf-mesh-snapshot-${UUID.randomUUID()}-";issued=0L;accepted=0L}
    private fun text(packet:JsonObject,key:String):String? = packet.get(key)
        ?.takeIf {it.isJsonPrimitive && it.asJsonPrimitive.isString}?.asString
    @Synchronized fun mode(packet:JsonObject):Mode {
        if(packet.has("error")) return Mode.IGNORE
        val id=text(packet,"tag") ?: return Mode.LEGACY
        if(!id.startsWith("mf-mesh-snapshot-")) return Mode.LEGACY
        if(text(packet,"action")!="meshes" || !id.startsWith(prefix)) return Mode.IGNORE
        val sequence=id.removePrefix(prefix).toLongOrNull() ?: return Mode.IGNORE
        if(sequence<=accepted || sequence>issued || sequence<=0L) return Mode.IGNORE
        if(listOf("meshid","meshname","nodeid","partial","more","skip","limit").any {packet.has(it)}) return Mode.IGNORE
        if(packet.has("result") && !text(packet,"result").orEmpty().equals("OK",ignoreCase=true)) return Mode.IGNORE
        return Mode.COMPLETE
    }
    @Synchronized fun accept(packet:JsonObject) {
        if(mode(packet)==Mode.COMPLETE) accepted=requireNotNull(text(packet,"tag")).removePrefix(prefix).toLong()
    }
}

internal object CompleteMeshSnapshot {
    fun decode(payload:JsonElement?,gson:Gson):Map<String,MeshGroup>? {
        if(payload==null || (!payload.isJsonArray && !payload.isJsonObject)) return null
        return try {
            val result=linkedMapOf<String,MeshGroup>();val identities=mutableSetOf<String>()
            fun add(element:JsonElement,fallback:String="") {
                require(element.isJsonObject)
                val value=element.asJsonObject
                for(key in listOf("_id","name","desc")) if(value.has(key)) {
                    val field=value.get(key);require(field.isJsonPrimitive && field.asJsonPrimitive.isString)
                }
                if(value.has("mtype")) {
                    val field=value.get("mtype")
                    require(field.isJsonPrimitive && field.asJsonPrimitive.isNumber && field.asString.toIntOrNull()?.let {it>=0}==true)
                }
                val parsed=gson.fromJson(value,MeshGroup::class.java);val id=parsed.id.ifBlank {fallback}
                require(id.isNotBlank())
                val identity=when {
                    id.startsWith("mesh//") -> id.removePrefix("mesh//")
                    id.startsWith("mesh/") -> id.removePrefix("mesh/")
                    else -> id
                }
                require(identities.add(identity));result[id]=parsed.copy(id=id)
            }
            if(payload.isJsonArray) payload.asJsonArray.forEach {add(it)}
            else for((key,value) in payload.asJsonObject.entrySet()) {require(key.isNotBlank());add(value,key)}
            result
        } catch(_:Exception) {null}
    }
}
