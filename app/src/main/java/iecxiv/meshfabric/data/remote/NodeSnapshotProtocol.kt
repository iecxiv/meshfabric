package iecxiv.meshfabric.data.remote
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import iecxiv.meshfabric.data.model.MeshNode
import java.util.UUID
internal class NodeSnapshotRequests {
    enum class Mode { LEGACY, COMPLETE, IGNORE }
    private var prefix="mf-node-snapshot-${UUID.randomUUID()}-"
    private var issued=0L
    private var accepted=0L
    @Synchronized fun request(): JsonObject = JsonObject().apply {
        addProperty("action","nodes"); addProperty("responseid",prefix+(++issued))
    }
    @Synchronized fun reset() {prefix="mf-node-snapshot-${UUID.randomUUID()}-";issued=0L;accepted=0L}
    private fun text(packet:JsonObject,key:String):String? = packet.get(key)
        ?.takeIf {it.isJsonPrimitive && it.asJsonPrimitive.isString}?.asString
    @Synchronized fun mode(packet:JsonObject):Mode {
        val id=text(packet,"responseid") ?: return Mode.LEGACY
        if(!id.startsWith("mf-node-snapshot-")) return Mode.LEGACY
        if(text(packet,"action")!="nodes" || !id.startsWith(prefix)) return Mode.IGNORE
        val sequence=id.removePrefix(prefix).toLongOrNull() ?: return Mode.IGNORE
        if(sequence<=accepted || sequence>issued || sequence<=0L) return Mode.IGNORE
        if(listOf("meshid","meshname","nodeid","partial","more","skip","limit").any {packet.has(it)}) return Mode.IGNORE
        if(packet.has("result") && !text(packet,"result").orEmpty().equals("OK",ignoreCase=true)) return Mode.IGNORE
        return Mode.COMPLETE
    }
    @Synchronized fun accept(packet:JsonObject) {
        if(mode(packet)==Mode.COMPLETE) accepted=requireNotNull(text(packet,"responseid")).removePrefix(prefix).toLong()
    }
}
internal object CompleteNodeSnapshot {
    fun decode(payload:JsonElement?,gson:Gson):Map<String,MeshNode>? {
        if(payload==null || (!payload.isJsonObject && !payload.isJsonArray)) return null
        return try {
            val result=linkedMapOf<String,MeshNode>();val identities=mutableSetOf<String>()
            fun add(element:JsonElement,fallbackId:String="",fallbackMesh:String="") {
                require(element.isJsonObject)
                val value=element.asJsonObject
                for(key in listOf("_id","name","meshid")) if(value.has(key)) {
                    val field=value.get(key)
                    require(field.isJsonPrimitive && field.asJsonPrimitive.isString)
                }
                if(value.has("conn")) {
                    val field=value.get("conn")
                    require(field.isJsonPrimitive && field.asJsonPrimitive.isNumber)
                    require(field.asString.toIntOrNull()?.let {it>=0}==true)
                }
                val parsed=gson.fromJson(value,MeshNode::class.java)
                val id=parsed.id.ifBlank {fallbackId}
                require(id.isNotBlank())
                val identity=when {
                    id.startsWith("node//") -> id.removePrefix("node//")
                    id.startsWith("node/") -> id.removePrefix("node/")
                    else -> id
                }
                require(identities.add(identity))
                result[id]=parsed.copy(id=id,meshid=parsed.meshid.ifBlank {fallbackMesh})
            }
            if(payload.isJsonArray) payload.asJsonArray.forEach {add(it)}
            else for((key,value) in payload.asJsonObject.entrySet()) {
                require(key.isNotBlank())
                if(value.isJsonArray) value.asJsonArray.forEach {add(it,fallbackMesh=key)}
                else add(value,fallbackId=key)
            }
            result
        } catch(_:Exception) {null}
    }
}
