package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal enum class DeviceBatchMovePhase { CHECKING, READY, SKIPPED, MOVING, VERIFIED, CONFLICT, REJECTED, NOT_SENT, UNCONFIRMED }
private data class BatchMoveRow(val group: MeshGroup, val node: MeshNode,
    val draft: DeviceBatchMoveDraft? = null, val phase: DeviceBatchMovePhase = DeviceBatchMovePhase.CHECKING)
private data class BatchMoveRequest(val rows: List<BatchMoveRow>, val identity: DeviceBatchPowerIdentity)
internal fun deviceBatchMoveResult(phase: String?, code: String, submitted: Boolean): DeviceBatchMovePhase = when (phase) {
    "verified" -> DeviceBatchMovePhase.VERIFIED
    "rejected" -> DeviceBatchMovePhase.REJECTED
    "skipped" -> DeviceBatchMovePhase.SKIPPED
    "error" -> if (code == "unconfirmed") DeviceBatchMovePhase.UNCONFIRMED else DeviceBatchMovePhase.NOT_SENT
    else -> if (submitted) DeviceBatchMovePhase.UNCONFIRMED else DeviceBatchMovePhase.NOT_SENT
}
internal fun deviceBatchMoveStopped(phase: DeviceBatchMovePhase): DeviceBatchMovePhase = when (phase) {
    DeviceBatchMovePhase.MOVING -> DeviceBatchMovePhase.UNCONFIRMED
    DeviceBatchMovePhase.READY -> DeviceBatchMovePhase.NOT_SENT
    else -> phase
}
private val moveTerminal = setOf("verified", "rejected", "skipped", "error")

private suspend fun checkMove(source: MeshCentralClient, request: BatchMoveRequest, row: BatchMoveRow, destination: String): BatchMoveRow {
    if (!deviceBatchPowerSame(source, request.identity) || row.node.meshid != row.group.id) return row.copy(phase = DeviceBatchMovePhase.SKIPPED)
    val editor = DeviceBatchMoveEditor(source, row.node, destination)
    try {
        editor.start()
        val result = withTimeoutOrNull(30000L) { editor.state.first { it.phase == "ready" || it.phase in moveTerminal } }
        return if (result?.phase == "ready" && result.draft != null && deviceBatchPowerSame(source, request.identity)) {
            row.copy(draft = result.draft, phase = DeviceBatchMovePhase.READY)
        } else row.copy(phase = DeviceBatchMovePhase.SKIPPED)
    } finally { editor.close() }
}

private suspend fun sendMove(source: MeshCentralClient, request: BatchMoveRequest, row: BatchMoveRow): BatchMoveRow {
    val draft = row.draft ?: return row.copy(phase = DeviceBatchMovePhase.NOT_SENT)
    if (!deviceBatchPowerSame(source, request.identity)) return row.copy(phase = DeviceBatchMovePhase.NOT_SENT)
    val editor = DeviceBatchMoveEditor(source, row.node, draft.destinationGroupId)
    try {
        editor.start()
        val ready = withTimeoutOrNull(30000L) { editor.state.first { it.phase == "ready" || it.phase in moveTerminal } }
        if (ready?.phase != "ready" || !deviceBatchPowerSame(source, request.identity)) return row.copy(phase = DeviceBatchMovePhase.NOT_SENT)
        if (ready.draft != draft) return row.copy(phase = DeviceBatchMovePhase.CONFLICT)
        currentCoroutineContext().ensureActive()
        if (!editor.submit(draft)) return row.copy(phase = deviceBatchMoveResult(editor.state.value.phase, editor.state.value.code, false))
        val result = withTimeoutOrNull(55000L) { editor.state.first { it.phase in moveTerminal } }
        return row.copy(phase = deviceBatchMoveResult(result?.phase, result?.code.orEmpty(), true))
    } finally { editor.close() }
}

@Composable internal fun deviceBatchMoveTitle(): String =
    if (LocalConfiguration.current.locales[0].language == "es") "Mover de grupo" else "Move to group"

private fun movePhaseText(phase: DeviceBatchMovePhase, es: Boolean): String = when (phase) {
    DeviceBatchMovePhase.CHECKING -> if (es) "Comprobando" else "Checking"
    DeviceBatchMovePhase.READY -> if (es) "Elegible para el destino revisado" else "Eligible for the reviewed destination"
    DeviceBatchMovePhase.SKIPPED -> if (es) "Omitido: mismo grupo, permisos no verificados o no disponible" else "Skipped: same group, unverified permissions or unavailable"
    DeviceBatchMovePhase.MOVING -> if (es) "Revalidando, enviando o verificando" else "Rechecking, sending or verifying"
    DeviceBatchMovePhase.VERIFIED -> if (es) "Dispositivo observado en el grupo destino" else "Device observed in the destination group"
    DeviceBatchMovePhase.CONFLICT -> if (es) "La revisión ya no coincide; no enviado" else "Review no longer matches; not sent"
    DeviceBatchMovePhase.REJECTED -> if (es) "Solicitud rechazada" else "Request rejected"
    DeviceBatchMovePhase.NOT_SENT -> if (es) "No enviado" else "Not sent"
    DeviceBatchMovePhase.UNCONFIRMED -> if (es) "Resultado sin confirmar; no se reenviará" else "Unconfirmed result; will not be resent"
}

@Composable
internal fun rememberDeviceBatchMoveAction(source: MeshCentralClient, entries: List<Pair<MeshGroup, MeshNode>>): () -> Unit {
    var request by remember(source) { mutableStateOf<BatchMoveRequest?>(null) }
    request?.let { current -> BatchMoveDialog(source, current) { request = null } }
    return {
        val rows = deviceBatchPowerTargets(entries).map { BatchMoveRow(it.group, it.node) }
        if (rows.isNotEmpty() && source.connectionState.value && !source.isUserLogout) {
            request = BatchMoveRequest(rows, deviceBatchPowerIdentity(source))
        }
    }
}

@Composable
private fun BatchMoveDialog(source: MeshCentralClient, request: BatchMoveRequest, onClose: () -> Unit) {
    val es = LocalConfiguration.current.locales[0].language == "es"
    fun t(a: String, b: String) = if (es) a else b
    val scope = rememberCoroutineScope()
    val reader = remember(request) { DeviceBatchMoveGroupsReader(source) }
    val read by reader.state.collectAsState()
    val connected by source.connectionState.collectAsState()
    var selected by remember(request) { mutableStateOf("") }
    var destination by remember(request) { mutableStateOf<DeviceBatchMoveChoice?>(null) }
    var menu by remember { mutableStateOf(false) }
    var rows by remember(request) { mutableStateOf(request.rows) }
    var checking by remember(request) { mutableStateOf(false) }
    var running by remember(request) { mutableStateOf(false) }
    var finished by remember(request) { mutableStateOf(false) }
    var job by remember(request) { mutableStateOf<Job?>(null) }
    DisposableEffect(reader) { reader.start(); onDispose { reader.close() } }
    LaunchedEffect(request) {
        while (isActive) {
            if (!deviceBatchPowerSame(source, request.identity)) { job?.cancel(); onClose(); break }
            delay(100L)
        }
    }
    val choices = read.choices.filter { it.group.mtype == 2 && deviceBatchMoveGroupAllowed(it.group.rights) }
    val choice = choices.singleOrNull { it.group.id == selected }
    val eligible = rows.count { it.phase == DeviceBatchMovePhase.READY }
    fun update(row: BatchMoveRow) { rows = rows.map { if (it.node.id == row.node.id) row else it } }
    DeviceBatchAlertDialog(
        onDismissRequest = { if (!running) onClose() },
        title = { DeviceBatchDialogTitle(deviceBatchMoveTitle() + " (${rows.size})") },
        text = {
            Column(Modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DeviceBatchNotice(t(
                    "Mover cambia el grupo y puede cambiar permisos y políticas. Solo grupos de agentes del mismo dominio. Los permisos heredados no verificables se omiten. Se relee antes de enviar; no es una transacción atómica frente a cambios simultáneos. Un intento por dispositivo, sin reenvío automático.",
                    "Moving changes the group and may change permissions and policies. Agent groups in the same domain only. Unverifiable inherited rights are skipped. Data is reread before sending; this is not an atomic transaction against concurrent changes. One attempt per device, no automatic resend."
                ))
                if (read.phase == "loading" || checking || running) iecxiv.meshfabric.ui.components.RoutedViewLoadingIndicator(Modifier.fillMaxWidth())
                if (read.phase == "error") Text(t("No se pudo verificar la lectura de grupos. Cierra y vuelve a abrir.", "Group read could not be verified. Close and reopen."))
                if (destination == null) {
                    Box {
                        OutlinedButton(enabled = read.phase == "ready" && !checking, onClick = { menu = true }) {
                            Text(choice?.name ?: t("Elegir grupo destino", "Choose destination group"))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            choices.forEach { item ->
                                DropdownMenuItem(text = { Text(item.name) },
                                    onClick = { selected = item.group.id; menu = false })
                            }
                        }
                    }
                    if (read.phase == "ready" && choices.isEmpty()) Text(t("No hay destinos con permisos verificables.", "No destinations with verifiable permissions."))
                } else {
                    destination?.let { Text(t("Destino: ", "Destination: ") + it.name) }
                    rows.forEach { row ->
                        HorizontalDivider()
                        Text(row.node.name.ifBlank { t("Dispositivo sin nombre", "Unnamed device") }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Text(t("Origen: ", "Source: ") + row.group.name)
                        DeviceBatchStatus(movePhaseText(row.phase, es), deviceBatchVisualKind("move", row.phase.name))
                    }
                }
                if (finished) {
                    DeviceBatchSummary(t("Verificados: ", "Verified: ") + rows.count { it.phase == DeviceBatchMovePhase.VERIFIED })
                    DeviceBatchSummary(t("Omitidos/no enviados: ", "Skipped/not sent: ") + rows.count { it.phase in setOf(DeviceBatchMovePhase.SKIPPED, DeviceBatchMovePhase.NOT_SENT, DeviceBatchMovePhase.CONFLICT) })
                    DeviceBatchSummary(t("Rechazados: ", "Rejected: ") + rows.count { it.phase == DeviceBatchMovePhase.REJECTED })
                    DeviceBatchSummary(t("Sin confirmar: ", "Unconfirmed: ") + rows.count { it.phase == DeviceBatchMovePhase.UNCONFIRMED })
                }
                Text(t("Detener pendientes o cerrar el diálogo no revierte solicitudes ya enviadas.", "Stopping pending requests or closing the dialog does not undo requests already sent."))
            }
        },
        confirmButton = {
            if (!finished) TextButton(enabled = connected && read.phase == "ready" && !checking && !running &&
                (if (destination == null) choice != null else eligible > 0 && choices.any { it.group.id == destination?.group?.id }), onClick = {
                if (!checking && !running && !finished && deviceBatchPowerSame(source, request.identity)) {
                    if (destination == null) {
                        val target = choice
                        if (target != null) {
                            destination = target; checking = true
                            job = scope.launch {
                                try {
                                    val gate = Semaphore(4)
                                    coroutineScope { request.rows.map { row -> async {
                                        gate.withPermit { update(checkMove(source, request, row, target.group.id)) }
                                    } }.awaitAll() }
                                } finally { checking = false }
                            }
                        }
                    } else {
                        val approved = rows.filter { it.phase == DeviceBatchMovePhase.READY }
                        running = true
                        job = scope.launch {
                            try {
                                val gate = Semaphore(4)
                                coroutineScope { approved.map { row -> async {
                                    gate.withPermit {
                                        currentCoroutineContext().ensureActive()
                                        update(row.copy(phase = DeviceBatchMovePhase.MOVING))
                                        update(sendMove(source, request, row))
                                    }
                                } }.awaitAll() }
                                finished = true
                            } finally { running = false }
                        }
                    }
                }
            }) { Text(if (destination == null) t("Revisar dispositivos", "Review devices") else t("Mover una vez: ", "Move once: ") + eligible) }
        },
        dismissButton = {
            Row {
                if (destination != null && !checking && !running && !finished) TextButton(onClick = {
                    destination = null; rows = request.rows
                }) { Text(t("Volver", "Back")) }
                TextButton(onClick = {
                    if (running) {
                        job?.cancel()
                        rows = rows.map { it.copy(phase = deviceBatchMoveStopped(it.phase)) }
                        running = false; finished = true
                    } else onClose()
                }) { Text(if (running) t("Detener pendientes", "Stop pending requests") else t("Cerrar", "Close")) }
            }
        }
    )
}
