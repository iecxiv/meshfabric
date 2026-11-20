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

internal enum class DeviceBatchAgentUpdatePhase { CHECKING, READY, SKIPPED, SENDING, LOCAL_QUEUED, CONFLICT, NOT_SENT, UNCONFIRMED }
private data class AgentUpdateRow(val group: MeshGroup, val node: MeshNode,
    val draft: DeviceBatchAgentUpdateDraft? = null, val phase: DeviceBatchAgentUpdatePhase = DeviceBatchAgentUpdatePhase.CHECKING)
private data class AgentUpdateRequest(val rows: List<AgentUpdateRow>, val identity: DeviceBatchPowerIdentity)
private val agentUpdateTerminal = setOf("queued", "skipped", "error")
internal fun deviceBatchAgentUpdateResult(phase: String?, code: String, submitted: Boolean): DeviceBatchAgentUpdatePhase = when (phase) {
    "queued" -> DeviceBatchAgentUpdatePhase.LOCAL_QUEUED
    "skipped" -> DeviceBatchAgentUpdatePhase.SKIPPED
    "error" -> if (code == "unconfirmed") DeviceBatchAgentUpdatePhase.UNCONFIRMED else DeviceBatchAgentUpdatePhase.NOT_SENT
    else -> if (submitted) DeviceBatchAgentUpdatePhase.UNCONFIRMED else DeviceBatchAgentUpdatePhase.NOT_SENT
}
internal fun deviceBatchAgentUpdateStopped(phase: DeviceBatchAgentUpdatePhase): DeviceBatchAgentUpdatePhase = when (phase) {
    DeviceBatchAgentUpdatePhase.SENDING -> DeviceBatchAgentUpdatePhase.UNCONFIRMED
    DeviceBatchAgentUpdatePhase.READY -> DeviceBatchAgentUpdatePhase.NOT_SENT
    else -> phase
}

private suspend fun checkAgentUpdate(source: MeshCentralClient, request: AgentUpdateRequest, row: AgentUpdateRow): AgentUpdateRow {
    if (!deviceBatchPowerSame(source, request.identity) || row.node.meshid != row.group.id) return row.copy(phase = DeviceBatchAgentUpdatePhase.SKIPPED)
    val editor = DeviceBatchAgentUpdateEditor(source, row.node)
    try {
        editor.start()
        val ready = withTimeoutOrNull(30000L) { editor.state.first { it.phase == "ready" || it.phase in agentUpdateTerminal } }
        return if (ready?.phase == "ready" && ready.draft != null && deviceBatchPowerSame(source, request.identity)) {
            row.copy(draft = ready.draft, phase = DeviceBatchAgentUpdatePhase.READY)
        } else row.copy(phase = DeviceBatchAgentUpdatePhase.SKIPPED)
    } finally { editor.close() }
}

private suspend fun sendAgentUpdate(source: MeshCentralClient, request: AgentUpdateRequest, row: AgentUpdateRow): AgentUpdateRow {
    val draft = row.draft ?: return row.copy(phase = DeviceBatchAgentUpdatePhase.NOT_SENT)
    if (!deviceBatchPowerSame(source, request.identity)) return row.copy(phase = DeviceBatchAgentUpdatePhase.NOT_SENT)
    val editor = DeviceBatchAgentUpdateEditor(source, row.node)
    try {
        editor.start()
        val ready = withTimeoutOrNull(30000L) { editor.state.first { it.phase == "ready" || it.phase in agentUpdateTerminal } }
        if (ready?.phase != "ready" || !deviceBatchPowerSame(source, request.identity)) return row.copy(phase = DeviceBatchAgentUpdatePhase.NOT_SENT)
        if (ready.draft != draft) return row.copy(phase = DeviceBatchAgentUpdatePhase.CONFLICT)
        currentCoroutineContext().ensureActive()
        if (!editor.submit(draft)) return row.copy(phase = deviceBatchAgentUpdateResult(editor.state.value.phase, editor.state.value.code, false))
        val result = withTimeoutOrNull(30000L) { editor.state.first { it.phase in agentUpdateTerminal } }
        return row.copy(phase = deviceBatchAgentUpdateResult(result?.phase, result?.code.orEmpty(), true))
    } finally { editor.close() }
}

@Composable internal fun deviceBatchAgentUpdateTitle(): String =
    agentActionLabel(AgentConsoleAction.FORCE_UPDATE, LocalConfiguration.current.locales[0].language == "es")

private fun agentUpdatePhaseText(phase: DeviceBatchAgentUpdatePhase, es: Boolean): String = when (phase) {
    DeviceBatchAgentUpdatePhase.CHECKING -> if (es) "Comprobando" else "Checking"
    DeviceBatchAgentUpdatePhase.READY -> if (es) "Elegible" else "Eligible"
    DeviceBatchAgentUpdatePhase.SKIPPED -> if (es) "Omitido: no disponible o sin derechos completos verificables" else "Skipped: unavailable or full rights not verified"
    DeviceBatchAgentUpdatePhase.SENDING -> if (es) "Revalidando y solicitando" else "Rechecking and requesting"
    DeviceBatchAgentUpdatePhase.LOCAL_QUEUED -> if (es) "Encolada localmente; aceptación del servidor e instalación no verificadas" else "Queued locally; server acceptance and installation not verified"
    DeviceBatchAgentUpdatePhase.CONFLICT -> if (es) "Grupo, sistema o versión cambiados; no enviada" else "Group, system or version changed; not sent"
    DeviceBatchAgentUpdatePhase.NOT_SENT -> if (es) "No enviada" else "Not sent"
    DeviceBatchAgentUpdatePhase.UNCONFIRMED -> if (es) "Resultado sin confirmar; sin reenvío automático" else "Unconfirmed result; no automatic resend"
}

@Composable
internal fun rememberDeviceBatchAgentUpdateAction(source: MeshCentralClient, entries: List<Pair<MeshGroup, MeshNode>>): () -> Unit {
    var request by remember(source) { mutableStateOf<AgentUpdateRequest?>(null) }
    request?.let { current -> AgentUpdateDialog(source, current) { request = null } }
    return {
        val rows = deviceBatchPowerTargets(entries).map { AgentUpdateRow(it.group, it.node) }
        if (rows.isNotEmpty() && source.connectionState.value && !source.isUserLogout) request = AgentUpdateRequest(rows, deviceBatchPowerIdentity(source))
    }
}

@Composable
private fun AgentUpdateDialog(source: MeshCentralClient, request: AgentUpdateRequest, onClose: () -> Unit) {
    val es = LocalConfiguration.current.locales[0].language == "es"
    fun t(a: String, b: String) = if (es) a else b
    val scope = rememberCoroutineScope()
    val connected by source.connectionState.collectAsState()
    var rows by remember(request) { mutableStateOf(request.rows) }
    var checking by remember(request) { mutableStateOf(true) }
    var running by remember(request) { mutableStateOf(false) }
    var finished by remember(request) { mutableStateOf(false) }
    var acknowledged by remember(request) { mutableStateOf(false) }
    var job by remember(request) { mutableStateOf<Job?>(null) }
    fun update(row: AgentUpdateRow) { rows = rows.map { if (it.node.id == row.node.id) row else it } }
    LaunchedEffect(request) {
        try {
            val gate = Semaphore(4)
            coroutineScope { request.rows.map { row -> async {
                gate.withPermit { update(checkAgentUpdate(source, request, row)) }
            } }.awaitAll() }
        } finally { checking = false }
    }
    LaunchedEffect(request) {
        while (isActive) {
            if (!deviceBatchPowerSame(source, request.identity)) { job?.cancel(); onClose(); break }
            delay(100L)
        }
    }
    val eligible = rows.count { it.phase == DeviceBatchAgentUpdatePhase.READY }
    DeviceBatchAlertDialog(onDismissRequest = { if (!running) onClose() },
        title = { DeviceBatchDialogTitle(deviceBatchAgentUpdateTitle() + " (${rows.size})") },
        text = {
            Column(Modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DeviceBatchNotice(agentActionDetail(AgentConsoleAction.FORCE_UPDATE, es))
                Text(t("Puede interrumpir sesiones remotas. No comprueba la instalación ni la versión final. Un intento por dispositivo, sin reenvío automático. Detener pendientes no revierte solicitudes ya encoladas.",
                    "May interrupt remote sessions. Installation and final version are not checked. One attempt per device, no automatic resend. Stopping pending requests does not undo requests already queued."))
                if (checking || running) iecxiv.meshfabric.ui.components.RoutedViewLoadingIndicator(Modifier.fillMaxWidth())
                rows.forEach { row ->
                    HorizontalDivider()
                    Text(row.node.name.ifBlank { row.node.id }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    DeviceBatchSecondary(row.node.id)
                    DeviceBatchSecondary(row.group.name + "\n" + row.group.id)
                    row.draft?.let { draft ->
                        Text(draft.os)
                        Text(t("Versión observada: ", "Observed version: ") + (draft.version ?: t("desconocida", "unknown")))
                    }
                    DeviceBatchStatus(agentUpdatePhaseText(row.phase, es), deviceBatchVisualKind("agent", row.phase.name))
                }
                if (!finished && !running) Row {
                    Checkbox(checked = acknowledged, enabled = !checking, onCheckedChange = { acknowledged = it })
                    Text(t("Entiendo que solicitar la actualización puede interrumpir el acceso y no garantiza su instalación.",
                        "I understand that requesting an update may interrupt access and does not guarantee installation."), Modifier.weight(1f))
                }
                if (finished) {
                    DeviceBatchSummary(t("Encoladas localmente (no instaladas/verificadas): ", "Locally queued (not installed/verified): ") + rows.count { it.phase == DeviceBatchAgentUpdatePhase.LOCAL_QUEUED })
                    DeviceBatchSummary(t("Omitidas/no enviadas: ", "Skipped/not sent: ") + rows.count { it.phase in setOf(DeviceBatchAgentUpdatePhase.SKIPPED, DeviceBatchAgentUpdatePhase.NOT_SENT, DeviceBatchAgentUpdatePhase.CONFLICT) })
                    DeviceBatchSummary(t("Sin confirmar: ", "Unconfirmed: ") + rows.count { it.phase == DeviceBatchAgentUpdatePhase.UNCONFIRMED })
                }
            }
        },
        confirmButton = {
            if (!finished) TextButton(enabled = connected && !checking && !running && acknowledged && eligible > 0, onClick = {
                if (!checking && !running && !finished && acknowledged && deviceBatchPowerSame(source, request.identity)) {
                    val approved = rows.filter { it.phase == DeviceBatchAgentUpdatePhase.READY }
                    running = true
                    job = scope.launch {
                        try {
                            val gate = Semaphore(4)
                            coroutineScope { approved.map { row -> async {
                                gate.withPermit {
                                    currentCoroutineContext().ensureActive()
                                    update(row.copy(phase = DeviceBatchAgentUpdatePhase.SENDING))
                                    update(sendAgentUpdate(source, request, row))
                                }
                            } }.awaitAll() }
                            finished = true
                        } finally { running = false }
                    }
                }
            }) { Text(t("Solicitar una vez: ", "Request once: ") + eligible) }
        },
        dismissButton = {
            TextButton(onClick = {
                if (running) {
                    job?.cancel()
                    rows = rows.map { it.copy(phase = deviceBatchAgentUpdateStopped(it.phase)) }
                    running = false; finished = true
                } else onClose()
            }) { Text(if (running) t("Detener pendientes", "Stop pending requests") else t("Cerrar", "Close")) }
        }
    )
}
