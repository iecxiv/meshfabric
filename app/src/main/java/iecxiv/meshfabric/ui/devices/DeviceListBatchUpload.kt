package iecxiv.meshfabric.ui.devices

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.data.model.*
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

internal enum class BatchUploadPhase { CHECKING, READY, SKIPPED, SENDING, OBSERVED, NOT_SENT, UNKNOWN }
private data class UploadRow(val group: MeshGroup, val node: MeshNode, val draft: DeviceBatchUploadDraft? = null,
    val phase: BatchUploadPhase = BatchUploadPhase.CHECKING, val bytes: Long = 0L, val detail: String = "")
private data class UploadRequest(val targets: List<DeviceBatchPowerTarget>, val identity: DeviceBatchPowerIdentity,
    val id: String = UUID.randomUUID().toString())
@Composable internal fun deviceBatchUploadTitle(): String =
    if (LocalConfiguration.current.locales[0].language == "es") "Subir archivo por lote" else "Upload file to devices"
@Composable internal fun rememberDeviceBatchUploadAction(source: MeshCentralClient, entries: List<Pair<MeshGroup, MeshNode>>): () -> Unit {
    var request by remember(source) { mutableStateOf<UploadRequest?>(null) }
    request?.let { current -> key(current.id) { BatchUploadDialog(source, current) { request = null } } }
    return {
        val targets = deviceBatchPowerTargets(entries)
        if (targets.isNotEmpty() && source.connectionState.value && !source.isUserLogout)
            request = UploadRequest(targets, deviceBatchPowerIdentity(source))
    }
}
@Composable private fun BatchUploadDialog(source: MeshCentralClient, request: UploadRequest, onClose: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val es = LocalConfiguration.current.locales[0].language == "es"
    fun t(a: String, b: String) = if (es) a else b
    val scope = rememberCoroutineScope()
    val rowsFlow = remember(request) { MutableStateFlow(request.targets.map { UploadRow(it.group, it.node) }) }
    val rows by rowsFlow.collectAsState()
    var options by remember { mutableStateOf(DeviceBatchUploadOptions()) }
    var file by remember { mutableStateOf<PreparedDeviceBatchUpload?>(null) }
    var busy by remember { mutableStateOf(false) }
    var reviewed by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var work by remember { mutableStateOf<Job?>(null) }
    val alive = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    fun change(id: String, transform: (UploadRow) -> UploadRow) { rowsFlow.update { all -> all.map { if (it.node.id == id) transform(it) else it } } }
    DisposableEffect(Unit) {
        onDispose {
            alive.set(false); work?.cancel(); scope.coroutineContext.cancelChildren()
            runCatching { file?.close() }
        }
    }
    LaunchedEffect(request) {
        while (isActive) {
            if (!deviceBatchPowerSame(source, request.identity)) { work?.cancel(); onClose(); break }
            delay(100L)
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && alive.get() && !busy && !reviewed) {
            busy = true; message = ""
            work = scope.launch {
                try {
                    val prepared = prepareDeviceBatchUploadFile(context, uri)
                    if (!alive.get()) prepared.close() else {
                        val previous = file
                        file = prepared
                        if (runCatching { previous?.close() }.isFailure) message = t("No se pudo eliminar el temporal anterior.", "Could not remove the previous temporary file.")
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { message = t("No se pudo preparar el archivo local.", "Could not prepare the local file.") }
                finally { busy = false }
            }
        }
    }
    val eligible = rows.count { it.phase == BatchUploadPhase.READY }
    val routesValid = (options.windowsDirectory.isBlank() || deviceBatchUploadRoute(options, DevicePlatform.WINDOWS) != null) &&
        (options.linuxDirectory.isBlank() || deviceBatchUploadRoute(options, DevicePlatform.LINUX) != null) &&
        (deviceBatchUploadRoute(options, DevicePlatform.WINDOWS) != null || deviceBatchUploadRoute(options, DevicePlatform.LINUX) != null)
    DeviceBatchAlertDialog(onDismissRequest = { if (!sending) onClose() }, title = { DeviceBatchDialogTitle(deviceBatchUploadTitle()) }, text = {
        Column(Modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DeviceBatchNotice(t("Un archivo para los dispositivos revisados. Sin reintentos automáticos. Se omiten las colisiones detectadas si no autorizas sobrescribir, sin garantía atómica frente a otros procesos. Algunos agentes no distinguen errores de listado de listas vacías. La sobrescritura conserva una copia .meshfabric-backup-. Una interrupción puede dejar temporales o copias.",
                "One file for the reviewed devices. No automatic retries. Detected collisions are skipped unless overwriting is authorized; there is no atomic guarantee against other processes. Some agents do not distinguish listing errors from empty lists. Overwriting retains a .meshfabric-backup- copy. Interrupted operations may leave temporary files or backups."))
            if (!reviewed) {
                OutlinedButton(enabled = !busy, onClick = { picker.launch(arrayOf("*/*")) }) { Text(t("Elegir archivo", "Choose file")) }
                DeviceBatchUploadOptionsForm(options, !busy) { options = it }
            }
            file?.metadata?.let { metadata ->
                Text(metadata.name)
                Text("${metadata.size} bytes")
                Text("SHA-256 local: ${metadata.sha256}", style = MaterialTheme.typography.bodySmall)
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (message.isNotBlank()) Text(message)
            if (reviewed) rows.forEach { row ->
                HorizontalDivider()
                Text(row.node.name.ifBlank { row.node.id }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                DeviceBatchSecondary(row.node.id)
                DeviceBatchSecondary(row.group.name)
                row.draft?.let { Text(it.destination) }
                val status = when (row.phase) {
                    BatchUploadPhase.CHECKING -> t("Comprobando", "Checking")
                    BatchUploadPhase.READY -> t("Elegible", "Eligible")
                    BatchUploadPhase.SKIPPED -> t("Omitido o no verificable", "Skipped or unverifiable")
                    BatchUploadPhase.SENDING -> t("En curso; bytes reconocidos: ", "In progress; acknowledged bytes: ") + row.bytes
                    BatchUploadPhase.OBSERVED -> t("Confirmación del protocolo y tipo/tamaño observados; no hash remoto", "Protocol acknowledged and type/size observed; not a remote hash check")
                    BatchUploadPhase.NOT_SENT -> t("No enviado", "Not sent")
                    BatchUploadPhase.UNKNOWN -> t("Sin confirmar; puede haber cambios remotos", "Unconfirmed; remote changes may exist")
                }
                DeviceBatchStatus(status, deviceBatchVisualKind("upload", row.phase.name))
                if (row.detail.isNotBlank()) Text(row.detail, style = MaterialTheme.typography.bodySmall)
            }
            if (reviewed && !busy && !finished) Row {
                Checkbox(checked = consent, onCheckedChange = { consent = it })
                Text(t("He revisado destinos, opciones y limitaciones y autorizo los cambios indicados.", "I reviewed the destinations, options and limitations and authorize the indicated changes."), Modifier.weight(1f))
            }
            if (finished) DeviceBatchSummary(t("Observados por tamaño: ", "Size observed: ") + rows.count { it.phase == BatchUploadPhase.OBSERVED } +
                t(" · Sin confirmar: ", " · Unconfirmed: ") + rows.count { it.phase == BatchUploadPhase.UNKNOWN })
            Text(t("Detener no deshace lo ya enviado. No se ejecutan los archivos subidos.", "Stopping does not undo data already sent. Uploaded files are not executed."))
        }
    }, confirmButton = {
        if (!finished) TextButton(enabled = !busy && file != null && (if (reviewed) eligible > 0 && consent else routesValid), onClick = {
            val prepared = file
            if (!busy && prepared != null && deviceBatchPowerSame(source, request.identity)) {
                if (!reviewed) {
                    val selectedOptions = options
                    rowsFlow.value = request.targets.map { UploadRow(it.group, it.node) }
                    reviewed = true; busy = true
                    work = scope.launch {
                        try {
                            val gate = Semaphore(4)
                            coroutineScope { request.targets.map { target -> async {
                                gate.withPermit {
                                    try {
                                        require(target.group.id == target.node.meshid)
                                        val draft = reviewBatchUpload(source, target.node, request.identity, selectedOptions, prepared.metadata)
                                        change(target.node.id) { it.copy(draft = draft, phase = BatchUploadPhase.READY) }
                                    } catch (cancelled: CancellationException) { throw cancelled }
                                    catch (_: Exception) { change(target.node.id) { it.copy(phase = BatchUploadPhase.SKIPPED) } }
                                }
                            } }.awaitAll() }
                        } finally { busy = false }
                    }
                } else if (consent) {
                    val approved = rows.filter { it.phase == BatchUploadPhase.READY }
                    busy = true; sending = true
                    work = scope.launch {
                        try {
                            val gate = Semaphore(4)
                            coroutineScope { approved.map { row -> async {
                                gate.withPermit {
                                    var mutated = false
                                    change(row.node.id) { it.copy(phase = BatchUploadPhase.SENDING) }
                                    try {
                                        val result = transferBatchUpload(source, row.node, request.identity, requireNotNull(row.draft), prepared,
                                            mutation = { mutated = true },
                                            progress = { count -> change(row.node.id) { it.copy(bytes = count) } },
                                            journal = { detail -> change(row.node.id) { it.copy(detail = detail) } })
                                        change(row.node.id) { it.copy(phase = BatchUploadPhase.OBSERVED, detail = result.detail) }
                                    } catch (cancelled: CancellationException) { throw cancelled }
                                    catch (_: Exception) { change(row.node.id) { it.copy(phase = if (mutated) BatchUploadPhase.UNKNOWN else BatchUploadPhase.NOT_SENT) } }
                                }
                            } }.awaitAll() }
                            finished = true
                        } finally { busy = false; sending = false }
                    }
                }
            }
        }) { Text(if (reviewed) t("Subir una vez: ", "Upload once: ") + eligible else t("Revisar lote", "Review devices")) }
    }, dismissButton = {
        Row {
            if (reviewed && !busy && !finished) TextButton(onClick = { reviewed = false; consent = false }) { Text(t("Volver", "Back")) }
            TextButton(onClick = {
                if (sending) {
                    work?.cancel()
                    rowsFlow.update { all -> all.map { row -> row.copy(phase = when (row.phase) {
                        BatchUploadPhase.SENDING -> BatchUploadPhase.UNKNOWN
                        BatchUploadPhase.READY -> BatchUploadPhase.NOT_SENT
                        else -> row.phase
                    }) } }
                    finished = true; busy = false; sending = false
                } else onClose()
            }) { Text(if (sending) t("Detener pendientes", "Stop pending requests") else t("Cerrar", "Close")) }
        }
    })
}
