package iecxiv.meshfabric.ui.management

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.time.Instant

private val csvJson=GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
internal fun serverCsvCell(value:String):String {
    val first=value.trimStart { it.isWhitespace() || it=='\uFEFF' }.firstOrNull()
    val safe=if(first in listOf('=','+','-','@')) "'"+value else value
    return "\""+safe.replace("\"","\"\"")+"\""
}
internal fun serverCsvRows(rows:List<List<String>>)=rows.joinToString("\r\n",postfix="\r\n") { row->row.joinToString(",") { serverCsvCell(it) } }
internal fun serverCsvBytes(csv:String)=("\uFEFF"+csv).toByteArray(Charsets.UTF_8)
internal fun serverHistoryCsv(points:List<HistoryPoint>,keys:List<String>,hours:Int):String {
    require(hours in serverHistoryPeriods)
    val columns=keys.map { key->key+when { key.startsWith("conn.")->"_count"; key.startsWith("cpu.")->"_load"; else->"_MiB" } }
    val rows=mutableListOf(listOf("timestamp_utc","server","period_hours","restart")+columns)
    points.forEach { point->
        val values=keys.map { key->point.values[key]?.takeIf { it.isFinite() && it>=0 }?.let {
            if(key.startsWith("conn.") && it<=9007199254740991.0 && it%1.0==0.0) it.toLong().toString() else it.toString()
        }.orEmpty() }
        rows.add(listOf(Instant.ofEpochMilli(point.time).toString(),point.server,hours.toString(),point.first.toString())+values)
    }
    return serverCsvRows(rows)
}
internal data class ServerTraceCsvEntry(val display:String,val timestamp:String,val source:String,val message:String) {
    val size:Int get()=display.length+timestamp.length+source.length+message.length
}
private fun clipTraceCsv(text:String):String {
    if(text.length<=16000) return text
    var part=text.take(16000)
    if(part.isNotEmpty() && Character.isHighSurrogate(part.last())) part=part.dropLast(1)
    return part+"\n[… truncated]"
}
internal fun serverTraceCsvEntry(raw:String):ServerTraceCsvEntry {
    val display=clipTraceCsv(readableServerTrace(raw))
    return runCatching {
        val packet=JsonParser.parseString(raw).asJsonObject
        val timestamp=historyTime(packet.get("time"))?.let { Instant.ofEpochMilli(it).toString() }.orEmpty()
        val source=runCatching { packet.get("source")?.asString }.getOrNull().orEmpty().take(100)
        val args=packet.get("args") ?: packet.get("message") ?: packet.get("value")
        fun argument(value:com.google.gson.JsonElement?):String=when {
            value==null || value.isJsonNull->"—"
            value.isJsonPrimitive && value.asJsonPrimitive.isString->readableServerText(value.asString)
            else->readableServerText(csvJson.toJson(value))
        }
        val message=if(args?.isJsonArray==true) args.asJsonArray.joinToString("\n") { argument(it) } else argument(args)
        ServerTraceCsvEntry(display,timestamp,source,clipTraceCsv(message))
    }.getOrElse { ServerTraceCsvEntry(display,"","",display) }
}
internal fun boundedServerTrace(previous:List<ServerTraceCsvEntry>,entry:ServerTraceCsvEntry):List<ServerTraceCsvEntry> {
    val result=(previous+entry).takeLast(200).toMutableList()
    var size=result.sumOf { it.size }
    while(size>64000 && result.size>1) size-=result.removeAt(0).size
    return result
}
internal fun serverTraceCsv(entries:List<ServerTraceCsvEntry>,sources:Set<String>):String {
    val rows=mutableListOf(listOf("timestamp_utc","source","message"))
    entries.filter { it.source in sources }.forEach { rows.add(listOf(it.timestamp,it.source,it.message)) }
    return serverCsvRows(rows)
}
