package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import iecxiv.meshfabric.data.model.MeshNode

internal fun deviceNotifyLabel(value:Long,es:Boolean):String {
    val labels=mutableListOf<String>()
    if(value and 2L!=0L) labels.add(if(es) "Conexión web" else "Web connect")
    if(value and 4L!=0L) labels.add(if(es) "Desconexión web" else "Web disconnect")
    if(value and DEVICE_NOTIFY_WEB_MASK.inv()!=0L) labels.add(if(es) "Otros ajustes conservados" else "Other settings preserved")
    return labels.joinToString(", ").ifBlank {if(es) "Avisos web de conexión y desconexión desactivados" else "Web connect and disconnect notifications disabled"}
}
@Composable internal fun DeviceNotificationSection(node:MeshNode) {
    val source=LocalDeviceDetailsClient.current
    val actor=source?.userInfo?.collectAsState()?.value?.id
    val identity=Triple(source?.serverUrl,source?.authCookie,source?.ignoreSsl)
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    key(source,identity,node.id,actor) {
        var retry by remember {mutableIntStateOf(0)}
        val holder=remember {mutableStateOf<DeviceNotificationEditor?>(null)}
        var editing by remember {mutableStateOf<Long?>(null)}
        val state by produceState(DeviceNotifyState(),retry,lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val editor=DeviceNotificationEditor(source,node.id);holder.value=editor;value=DeviceNotifyState()
                try {editor.start();editor.state.collect {value=it}} finally {
                    editor.close();if(holder.value===editor) holder.value=null
                    value=editor.state.value
                }
            }
        }
        GeneralValueRow(t("Notificaciones","Notifications"),state.value?.let {deviceNotifyLabel(it,es)} ?: t("Sin verificar","Unverified"),state.canEdit,"general_notifications",t("Editar notificaciones","Edit notifications")) {editing=state.value}
        if(state.phase in setOf("loading","saving")) iecxiv.meshfabric.ui.components.RoutedViewLoadingIndicator(Modifier.fillMaxWidth())
        val status=when {
            state.phase=="confirmed"->t("Preferencia confirmada por relectura de la cuenta.","Preference confirmed by re-reading the account.")
            state.code=="conflict"->t("La preferencia cambió. Recarga y vuelve a revisar. No se ha enviado el cambio.","The preference changed. Reload and review again. No change was sent.")
            state.code=="unconfirmed"->t("Guardado sin confirmar. Comprueba la preferencia antes de repetir; no se reenvía automáticamente.","Save unconfirmed. Check the preference before repeating; no automatic replay.")
            state.phase=="error"->t("No se pueden verificar las notificaciones con la conexión y los permisos actuales.","Notifications cannot be verified with current connection and permissions.")
            else->""
        }
        if(status.isNotEmpty()) Text(status,style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("general_notifications_status"))
        TextButton(onClick={retry++},enabled=state.phase!="saving") {Text(t("Recargar notificaciones","Reload notifications"))}
        editing?.let {baseline->key(baseline) {
            var selected by remember {mutableLongStateOf(baseline and DEVICE_NOTIFY_WEB_MASK)}
            var submitted by remember {mutableStateOf(false)}
            iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={editing=null},
                title={Text(t("Notificaciones personales","Personal notifications"))},
                text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(node.name)
                    Text(t("Preferencia de la cuenta conectada para este dispositivo. Avisos en la web del servidor, no notificaciones push de Android.","Connected account preference for this device. Notifications in the server web page, not Android push notifications."),style=MaterialTheme.typography.bodySmall)
                    for(bit in listOf(2L,4L)) Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(selected and bit!=0L,{checked->selected=if(checked) selected or bit else selected and bit.inv()},enabled=state.canEdit && !submitted,modifier=Modifier.testTag("general_notify_bit_$bit"))
                        Text(if(bit==2L) t("Avisar al conectarse","Notify on connect") else t("Avisar al desconectarse","Notify on disconnect"),Modifier.weight(1f))
                    }
                    Text(t("Se conservan Intel AMT, correo, mensajería y ajustes desconocidos; no se editan en este bloque. Guardado independiente del borrador de General. Cerrar no revierte un envío.","Intel AMT, email, messaging and unknown settings are preserved, not edited in this block. Saved independently of the General draft. Closing does not undo a send."),style=MaterialTheme.typography.bodySmall)
                    if(status.isNotEmpty()) Text(status,style=MaterialTheme.typography.bodySmall)
                }},
                confirmButton={if(!submitted) TextButton(enabled=state.canEdit && selected!=(baseline and DEVICE_NOTIFY_WEB_MASK),onClick={submitted=holder.value?.save(baseline,selected)==true},modifier=Modifier.testTag("general_notify_save")) {Text(t("Guardar notificaciones","Save notifications"))}},
                dismissButton={TextButton(onClick={editing=null}) {Text(t("Cerrar","Close"))}})
        }}
    }
}
