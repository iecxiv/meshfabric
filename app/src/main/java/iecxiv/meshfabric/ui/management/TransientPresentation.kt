package iecxiv.meshfabric.ui.management
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
internal fun noticeLocalChannelLabel(es:Boolean)=if(es) "Canal local para avisos recibidos desde servidor." else "Local channel for notices received from the server."
@Composable internal fun NoticeLocalChannelRow(modifier:Modifier=Modifier) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    Box(modifier.fillMaxWidth().padding(vertical=8.dp).testTag("notice-local-channel-row")) {
        Text(noticeLocalChannelLabel(es),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
internal fun agentContextDiagnostic(connected:Boolean,foreground:Boolean,ready:Boolean,loading:Boolean,detailsBusy:Boolean,readError:Boolean,actorMatches:Boolean,eligible:Boolean):String?=when {
    !foreground->null
    !connected->"INVITE_CONTEXT_DISCONNECTED"
    readError->"INVITE_CONTEXT_READ_FAILED"
    ready && !actorMatches->"INVITE_CONTEXT_ACTOR_MISMATCH"
    !ready || loading || detailsBusy->null
    !eligible->"INVITE_CONTEXT_GROUP_OR_RIGHTS"
    else->null
}
internal fun agentContextMessage(code:String,es:Boolean)=when(code) {
    "INVITE_CONTEXT_DISCONNECTED"->if(es) "Sin conexión. Reconecta para continuar." else "Disconnected. Reconnect to continue."
    "INVITE_CONTEXT_READ_FAILED"->if(es) "No se han podido cargar los datos. Actualiza o vuelve a abrir la vista." else "Could not load the data. Refresh or reopen this view."
    "INVITE_CONTEXT_ACTOR_MISMATCH"->if(es) "La cuenta ha cambiado. Cierra y vuelve a abrir la vista." else "The account has changed. Close and reopen this view."
    else->if(es) "El grupo no está disponible o faltan permisos para esta acción." else "The group is unavailable or permissions are insufficient for this action."
}
internal fun agentNoticeParts(value:String):Pair<String,String> {
    val match=Regex("\\s+\\[([A-Z][A-Z0-9_]{0,79})\\]\\s*$").find(value) ?: return value to ""
    return value.substring(0,match.range.first).trimEnd() to match.groupValues[1]
}
@Composable internal fun AgentDiagnosticDetails(code:String,es:Boolean) {
    if(!code.matches(Regex("[A-Z][A-Z0-9_]{0,79}"))) return
    var expanded by remember(code) {mutableStateOf(false)}
    var copyFailed by remember(code) {mutableStateOf(false)}
    val context=LocalContext.current
    TextButton(onClick={expanded=!expanded},modifier=Modifier.testTag("agent-diagnostics-toggle")) {
        Text(if(expanded) {if(es) "Ocultar diagnóstico" else "Hide diagnostics"} else {if(es) "Detalles de diagnóstico" else "Diagnostic details"})
    }
    if(expanded) ManagementCard {
        SelectionContainer {Text(code,style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("agent-diagnostic-code"))}
        TextButton(onClick={copyFailed=runCatching {
            val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: error("Clipboard unavailable")
            clipboard.setPrimaryClip(ClipData.newPlainText(if(es) "Diagnóstico" else "Diagnostics",code))
        }.isFailure}) {Text(if(es) "Copiar diagnóstico" else "Copy diagnostics")}
        if(copyFailed) Text(if(es) "No se ha podido copiar." else "Could not copy.",color=MaterialTheme.colorScheme.error)
    }
}
@Composable internal fun AgentOperationNotice(value:String,es:Boolean) {
    val parts=agentNoticeParts(value)
    if(parts.first.isNotBlank()) Text(parts.first,style=MaterialTheme.typography.bodySmall)
    if(parts.second.isNotBlank()) AgentDiagnosticDetails(parts.second,es)
}
