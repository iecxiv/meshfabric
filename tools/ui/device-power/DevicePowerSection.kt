package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

internal fun powerLabel(state:Int?,es:Boolean,future:Boolean=false):String {
    if(future) return if(es) "Aún no transcurrido" else "Future period"
    return when(state) {
        null->if(es) "Sin datos" else "No data"
        0->if(es) "Desconocido" else "Unknown"
        1->if(es) "Encendido (S0)" else "Powered on (S0)"
        2,3,4->(if(es) "Suspensión" else "Sleep")+" (S${state-1})"
        5->if(es) "Hibernación (S4)" else "Hibernate (S4)"
        6->if(es) "Apagado lógico (S5)" else "Soft off (S5)"
        7->if(es) "Presente" else "Present"
        8->if(es) "Apagado" else "Off"
        else->(if(es) "Estado del servidor: " else "Server state: ")+state
    }
}
@Composable internal fun DevicePowerSection(nodeId:String) {
    val source=LocalDeviceDetailsClient.current
    val locale=LocalConfiguration.current.locales[0];val es=locale.language=="es"
    fun label(a:String,b:String)=if(es) a else b
    val identity=Triple(source?.serverUrl,source?.authCookie,source?.ignoreSsl)
    val binding=remember(source,identity,nodeId) { source?.let { PowerBinding(it,nodeId) } }
    val key=remember(binding) { UUID.randomUUID().toString() }
    var retry by remember(key) { mutableIntStateOf(0) }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val result by produceState(PowerLoad(key),binding,key,retry,lifecycle) {
        value=PowerLoad(key)
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            value=PowerLoad(key)
            if(binding==null) value=PowerLoad(key,"disconnected") else devicePowerTimeline(binding,key).collect { value=it }
        }
    }
    val state=if(result.key==key && (binding==null || binding.matches())) result else PowerLoad(key,"disconnected")
    var selected by remember(key,retry) { mutableStateOf<LocalDate?>(null) }
    HorizontalDivider()
    Text(label("Energía · últimos 7 días","Power · last 7 days"),style=MaterialTheme.typography.titleSmall,modifier=Modifier.testTag("power_heading"))
    Text(label("Estados guardados por MeshCentral, no lectura física en tiempo real. Conexión no equivale a encendido.","States stored by MeshCentral, not a live physical reading. Connectivity does not imply power on."),style=MaterialTheme.typography.bodySmall)
    Row {
        TextButton(onClick={ retry++ },enabled=state.phase!="loading") { Text(label("Actualizar energía","Refresh power")) }
        DevicePowerCsv(binding,state.phase=="ready")
    }
    if(state.phase=="loading") LinearProgressIndicator(Modifier.fillMaxWidth())
    if(state.phase!="ready" && state.phase!="loading") Text(when(state.phase) {
        "timeout"->label("Sin respuesta de energía; comprueba conexión y permisos.","No power response; check connection and permissions.")
        "invalid"->label("Historial energético inválido o fecha futura del servidor.","Invalid power history or future server timestamp.")
        "denied"->label("Consulta energética rechazada.","Power query denied.")
        else->label("Energía no disponible sin conexión verificada.","Power data unavailable without a verified connection.")
    },color=MaterialTheme.colorScheme.error)
    state.history?.let { history->
        val zone=ZoneId.systemDefault()
        val dateFormat=DateTimeFormatter.ofPattern("EEE dd/MM",locale)
        val timeFormat=DateTimeFormatter.ofPattern("dd/MM HH:mm",locale).withZone(zone)
        val missing=MaterialTheme.colorScheme.outlineVariant
        val futureColor=MaterialTheme.colorScheme.surfaceVariant
        fun color(span:PowerSpan):Color=when {
            span.future->futureColor
            span.state==1->Color(0xFF22C55E)
            span.state in listOf(2,3,4)->Color(0xFFFBBF24)
            span.state==5->Color(0xFFA78BFA)
            span.state in listOf(6,8)->Color(0xFFEF4444)
            span.state==7->Color(0xFF60A5FA)
            else->missing
        }
        Text(label("Muestra: ","Sample: ")+timeFormat.format(Instant.ofEpochMilli(history.sampledAt))+" · "+zone.id,style=MaterialTheme.typography.labelSmall)
        if(history.currentOnly) Text(label("El servidor solo ha devuelto una muestra actual; no se reconstruyen días anteriores.","The server returned only a current sample; earlier days are not reconstructed."),style=MaterialTheme.typography.bodySmall)
        for(day in powerDays(history.sampledAt,zone)) {
            val start=day.atStartOfDay(zone).toInstant().toEpochMilli()
            val end=day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val spans=powerSpans(history,start,end)
            Text(day.format(dateFormat),style=MaterialTheme.typography.labelSmall)
            Canvas(Modifier.fillMaxWidth().height(18.dp).semantics { contentDescription=day.toString()+": "+spans.map { powerLabel(it.state,es,it.future) }.distinct().joinToString(", ") }
                .clickable { selected=if(selected==day) null else day }) {
                for(span in spans) {
                    val x=((span.start-start).toDouble()/(end-start)*size.width).toFloat()
                    val width=((span.end-span.start).toDouble()/(end-start)*size.width).toFloat()
                    drawRect(color(span),Offset(x,0f),Size(width,size.height))
                }
            }
            if(selected==day) {
                for(span in spans.take(200)) Text(timeFormat.format(Instant.ofEpochMilli(span.start))+" – "+timeFormat.format(Instant.ofEpochMilli(span.end))+": "+powerLabel(span.state,es,span.future),style=MaterialTheme.typography.bodySmall)
                if(spans.size>200) Text(label("Más intervalos disponibles en el CSV del servidor.","More intervals are available in the server CSV."),style=MaterialTheme.typography.bodySmall)
            }
        }
        Text(label("Toca una barra para ver sus intervalos. Gris: sin datos, desconocido o periodo futuro; nunca se interpreta como apagado.","Tap a bar to inspect intervals. Grey means no data, unknown or future period; it is never interpreted as off."),style=MaterialTheme.typography.bodySmall)
    }
    Text(label("El CSV conserva el formato e intervalo original del servidor, que puede diferir de los siete días de la vista.","CSV preserves the server's original format and interval, which may differ from the seven-day view."),style=MaterialTheme.typography.labelSmall)
}
