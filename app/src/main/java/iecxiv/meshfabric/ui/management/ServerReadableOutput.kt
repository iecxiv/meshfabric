package iecxiv.meshfabric.ui.management

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val readableServerJson=GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
private fun cleanServerText(text:String)=text.replace("\r\n","\n").replace('\r','\n').filter { it=='\n' || it=='\t' || !it.isISOControl() }
internal fun readableServerText(text:String):String {
    if(text.length>64000) return cleanServerText(text.take(16000))+"\n[… truncated]"
    var value=text
    repeat(3) {
        val parsed=runCatching { JsonParser.parseString(value) }.getOrNull() ?: return cleanServerText(value)
        if(parsed.isJsonPrimitive && parsed.asJsonPrimitive.isString) {
            val decoded=parsed.asString
            if(decoded==value) return cleanServerText(value)
            value=decoded
        } else return cleanServerText(readableServerJson.toJson(parsed))
    }
    return cleanServerText(value)
}
private fun readableTraceArgument(value:JsonElement?):String = when {
    value==null || value.isJsonNull->"—"
    value.isJsonPrimitive && value.asJsonPrimitive.isString->readableServerText(value.asString)
    else->readableServerText(readableServerJson.toJson(value))
}
internal fun readableServerTrace(raw:String,zone:ZoneId=ZoneId.systemDefault(),locale:Locale=Locale.getDefault()):String = runCatching {
    if(raw.length>64000) return readableServerText(raw)
    val packet=JsonParser.parseString(raw).asJsonObject
    val time=packet.get("time")
    val stamp=historyTime(time)?.let { DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss z",locale).withZone(zone).format(Instant.ofEpochMilli(it)) }
        ?: runCatching { time?.asString }.getOrNull().orEmpty().ifBlank { "—" }
    val source=runCatching { packet.get("source")?.asString }.getOrNull().orEmpty().ifBlank { "—" }
    val args=packet.get("args") ?: packet.get("message") ?: packet.get("value")
    val message=if(args?.isJsonArray==true) args.asJsonArray.joinToString("\n") { readableTraceArgument(it) } else readableTraceArgument(args)
    cleanServerText("$stamp · $source\n$message")
}.getOrElse { readableServerText(raw) }
internal fun availableTraceOptions(current:Set<String>)=(serverTraceSources+current.sorted()).distinct()
internal fun traceSourceName(name:String,es:Boolean):String {
    val names=when(name) {
        "cookie"->"Cookies" to "Cookies"
        "dispatch"->"Distribución de eventos" to "Event dispatch"
        "main"->"Servidor principal" to "Main server"
        "peer"->"Servidores pares" to "Peer servers"
        "agent"->"Agentes" to "Agents"
        "agentupdate"->"Actualización de agentes" to "Agent updates"
        "cert"->"Certificados" to "Certificates"
        "db"->"Base de datos" to "Database"
        "email"->"Correo electrónico" to "Email"
        "web"->"Servidor web" to "Web server"
        "webrequest"->"Peticiones web" to "Web requests"
        "relay"->"Relés" to "Relays"
        "httpheaders"->"Cabeceras HTTP" to "HTTP headers"
        "authlog"->"Autenticación" to "Authentication"
        "amt"->"Intel AMT" to "Intel AMT"
        "webrelay"->"Relé web" to "Web relay"
        "mps"->"Conexiones MPS/CIRA" to "MPS/CIRA connections"
        "mpscmd"->"Comandos MPS" to "MPS commands"
        else->name to name
    }
    return if(es) names.first else names.second
}
