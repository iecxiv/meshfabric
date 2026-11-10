package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import org.json.JSONArray
import org.json.JSONObject
internal fun previousLoginPalette(code:Any?,dark:Boolean,neutral:Color,foreground:Color):Pair<Color,Color> = when(accountLoginOutcome(code)) {
    true->(if(dark) Color(0xFF173B25) else Color(0xFFE2F4E7)) to (if(dark) Color(0xFFC5F2D1) else Color(0xFF163B24))
    false->(if(dark) Color(0xFF4A2025) else Color(0xFFFCE5E7)) to (if(dark) Color(0xFFFFDADD) else Color(0xFF64232B))
    null->neutral to foreground
}
@Composable internal fun PreviousLoginEntry(item:JSONObject,date:String,details:String,es:Boolean,modifier:Modifier=Modifier) {
    val scheme=MaterialTheme.colorScheme
    val colors=previousLoginPalette(item.opt("m"),scheme.surface.luminance()<0.5f,scheme.surfaceVariant,scheme.onSurfaceVariant)
    Column(modifier.fillMaxWidth().background(colors.first),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(date,style=MaterialTheme.typography.titleSmall,color=colors.second)
        Text(loginHistoryLabel(item.opt("m"),es)+" · "+details,style=MaterialTheme.typography.bodySmall,color=colors.second)
        HorizontalDivider(color=scheme.outlineVariant)
    }
}
@Composable internal fun OwnPreviousLoginsDialog(name:String,actor:String,events:JSONArray?,busy:Boolean,failed:Boolean,es:Boolean,onRefresh:()->Unit,onDismiss:()->Unit,error:String="") {
    fun t(a:String,b:String)=if(es) a else b
    val close={if(!busy) onDismiss()}
    ManagementDialog(onDismissRequest=close,modifier=Modifier.widthIn(max=640.dp).fillMaxWidth(0.94f).testTag("own-previous-logins-dialog"),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn,usePlatformDefaultWidth=false),title={Text(t("Inicios de sesión anteriores","Previous logins"))},text={
        Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*0.68f).dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(name,style=MaterialTheme.typography.titleSmall)
            ProfileDisclosure(t("Identificador","Identifier")) {Text(actor,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Text(t("Historial de accesos web registrado por el servidor; puede no incluir los accesos de la app.","Server-recorded web sign-ins; app sign-ins may not be included."),style=MaterialTheme.typography.bodySmall)
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(failed) Text(t("Historial no disponible o respuesta incompatible.","History unavailable or unsupported response."),color=MaterialTheme.colorScheme.error)
            if(events!=null && events.length()==0) Text(t("Sin registros.","No records."))
            if(events!=null) for(i in 0 until minOf(events.length(),200)) events.optJSONObject(i)?.let {event->
                val args=event.optJSONArray("a")
                val details=args?.let {(0 until it.length()).joinToString(" · ") {index->it.optString(index)}}.orEmpty()
                PreviousLoginEntry(event,userDate(event.opt("t")).ifBlank {t("Fecha no disponible","Date unavailable")},details,es)
            }
            TextButton(onClick=onRefresh,enabled=!busy) {Text(t("Actualizar","Refresh"))}
            if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={},dismissButton={Row {TextButton(onClick=close) {Text(t("Volver","Back"))};TextButton(onClick=close) {Text(t("Cerrar","Close"))}}})
}
