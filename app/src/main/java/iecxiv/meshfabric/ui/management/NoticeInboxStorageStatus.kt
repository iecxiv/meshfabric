package iecxiv.meshfabric.ui.management
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
@Composable internal fun NoticeInboxStorageStatus(state:NoticeInboxState,es:Boolean,onRetry:()->Unit) {
    if(state.storageLoading) {LinearProgressIndicator();Text(if(es) "Restaurando avisos guardados…" else "Restoring saved notices…")}
    if(state.storageSaving) Text(if(es) "Guardando avisos en el dispositivo…" else "Saving notices on this device…",style=MaterialTheme.typography.bodySmall)
    if(state.storageError) {
        Text(if(es) "No se ha confirmado el almacenamiento local (error, clave no disponible o límite de 16 MiB). El archivo anterior se conserva. Puede haber cambios solo en memoria: no cierres la app hasta resolverlo." else "Local storage is unconfirmed (error, unavailable key or 16 MiB limit). The previous file is preserved. Some changes may exist only in memory: resolve this before closing the app.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        TextButton(enabled=!state.storageLoading && !state.storageSaving,onClick=onRetry) {Text(if(es) "Reintentar almacenamiento" else "Retry storage")}
    }
}
