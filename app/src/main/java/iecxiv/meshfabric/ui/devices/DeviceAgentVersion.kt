package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun agentConsoleServerVersion(packet:JsonObject):String?=runCatching {
    val raw=packet.getAsJsonObject("node")?.getAsJsonObject("agent")?.get("ver") ?: return null
    if(!raw.isJsonPrimitive || !raw.asJsonPrimitive.isNumber || raw.asString.length>32) return null
    raw.asBigDecimal.longValueExact().takeIf {it in 1L..0xffffffffL}?.toString()
}.getOrNull()

internal fun agentConsoleBuildVersion(text:String):String?=runCatching {
    val json=JsonParser.parseString(text.trim()).asJsonObject
    fun field(key:String)=json.get(key)?.takeIf {it.isJsonPrimitive && it.asJsonPrimitive.isString}?.asString?.takeIf {it.isNotBlank() && it.length<=128 && it.none {c->c.isISOControl()}}
    val format=DateTimeFormatter.ofPattern("MMM d yyyy",Locale.US)
    val date=field("compileTime")?.substringAfter(", ","")?.let {runCatching {LocalDate.parse(it,format)}.getOrNull()}
        ?: field("commitDate")?.let {runCatching {Instant.parse(it).atZone(ZoneOffset.UTC).toLocalDate()}.getOrNull()}
    date?.format(format)
}.getOrNull()

internal suspend fun readAgentConsoleVersion(source:MeshCentralClient,nodeId:String,fallbackVersion:String?):String? {
    val reader=DeviceAgentConsoleSession(source,nodeId)
    return try {
        withTimeoutOrNull(15_000L) {
            reader.connect()
            val ready=reader.state.first {it.phase=="ready" || it.phase=="closed"}
            if(ready.phase!="ready" || !reader.submit("versions")) return@withTimeoutOrNull null
            val reply=reader.state.first {snapshot->snapshot.phase=="closed" || snapshot.lines.any {it.kind=="output" && agentConsoleBuildVersion(it.text)!=null}}
            if(reply.phase=="closed") return@withTimeoutOrNull null
            val date=reply.lines.asReversed().firstNotNullOfOrNull {if(it.kind=="output") agentConsoleBuildVersion(it.text) else null}
                ?: return@withTimeoutOrNull null
            val version=reader.agentVersion.value ?: fallbackVersion
            if(version.isNullOrBlank()) date else "$date, $version"
        }
    } finally {reader.close()}
}
