package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.R
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal enum class DeviceBatchPowerPhase { CHECKING, ELIGIBLE, SKIPPED, SENDING, ACCEPTED, REJECTED, NOT_SENT, UNCONFIRMED }
internal data class DeviceBatchPowerTarget(val group: MeshGroup, val node: MeshNode,
    val draft: GeneralDeviceActionDraft? = null, val phase: DeviceBatchPowerPhase = DeviceBatchPowerPhase.CHECKING)
internal data class DeviceBatchPowerIdentity(val url: String, val actor: String, val cookie: String, val tls: Boolean)
internal fun deviceBatchPowerIdentity(source: MeshCentralClient) = DeviceBatchPowerIdentity(
    source.serverUrl, source.userInfo.value?.id.orEmpty(), source.authCookie, source.ignoreSsl)
internal fun deviceBatchPowerSame(source: MeshCentralClient, expected: DeviceBatchPowerIdentity) =
    !source.isUserLogout && source.connectionState.value && expected.actor.isNotBlank() && deviceBatchPowerIdentity(source) == expected
internal fun deviceBatchPowerTargets(entries: List<Pair<MeshGroup, MeshNode>>): List<DeviceBatchPowerTarget> =
    entries.groupBy { it.second.id }.values.mapNotNull { copies ->
        copies.first().takeIf { it.second.id.isNotBlank() && copies.map { row -> row.first.id }.distinct().size == 1 }
            ?.let { DeviceBatchPowerTarget(it.first, it.second) }
    }
internal fun deviceBatchPowerResult(state: GeneralDeviceActionsState, submitted: Boolean): DeviceBatchPowerPhase = when (state.phase) {
    "accepted" -> DeviceBatchPowerPhase.ACCEPTED
    "rejected" -> DeviceBatchPowerPhase.REJECTED
    "error" -> if (state.code == "unconfirmed") DeviceBatchPowerPhase.UNCONFIRMED else DeviceBatchPowerPhase.NOT_SENT
    else -> if (submitted) DeviceBatchPowerPhase.UNCONFIRMED else DeviceBatchPowerPhase.NOT_SENT
}
private val batchPowerActions = listOf(GeneralDeviceAction.WAKE, GeneralDeviceAction.RESTART, GeneralDeviceAction.SHUTDOWN, GeneralDeviceAction.SLEEP)

private suspend fun deviceBatchPowerCheck(source: MeshCentralClient, identity: DeviceBatchPowerIdentity,
    target: DeviceBatchPowerTarget, action: GeneralDeviceAction): DeviceBatchPowerTarget {
    require(action in batchPowerActions)
    if (!deviceBatchPowerSame(source, identity)) return target.copy(phase = DeviceBatchPowerPhase.SKIPPED)
    val editor = DeviceGeneralActionsEditor(source, target.node.id)
    try {
        editor.start()
        val state = withTimeoutOrNull(22_000L) { editor.state.first { it.phase == "ready" || it.phase == "error" } }
        val meta = state?.meta
        return if (meta != null && state.phase == "ready" && meta.mesh == target.group.id && deviceBatchPowerSame(source, identity) &&
            generalDeviceActionAvailable(target.node.id, meta, source.groupedDevices.value, action)) {
            target.copy(draft = GeneralDeviceActionDraft(action, meta.mesh, meta.os), phase = DeviceBatchPowerPhase.ELIGIBLE)
        } else target.copy(phase = DeviceBatchPowerPhase.SKIPPED)
    } finally { editor.close() }
}

private suspend fun deviceBatchPowerSend(source: MeshCentralClient, identity: DeviceBatchPowerIdentity,
    target: DeviceBatchPowerTarget): DeviceBatchPowerTarget {
    val draft = target.draft ?: return target.copy(phase = DeviceBatchPowerPhase.NOT_SENT)
    if (!deviceBatchPowerSame(source, identity)) return target.copy(phase = DeviceBatchPowerPhase.NOT_SENT)
    val editor = DeviceGeneralActionsEditor(source, target.node.id)
    try {
        editor.start()
        val ready = withTimeoutOrNull(22_000L) { editor.state.first { it.phase == "ready" || it.phase == "error" } }
        if (ready?.phase != "ready" || !deviceBatchPowerSame(source, identity)) return target.copy(phase = DeviceBatchPowerPhase.NOT_SENT)
        currentCoroutineContext().ensureActive()
        val submitted = editor.submit(draft)
        if (!submitted) return target.copy(phase = deviceBatchPowerResult(editor.state.value, false))
        val result = withTimeoutOrNull(45_000L) {
            editor.state.first { it.phase in setOf("accepted", "rejected", "error") }
        }
        return target.copy(phase = if (result == null) DeviceBatchPowerPhase.UNCONFIRMED else deviceBatchPowerResult(result, true))
    } finally { editor.close() }
}

private data class DeviceBatchPowerRequest(val action: GeneralDeviceAction, val targets: List<DeviceBatchPowerTarget>, val identity: DeviceBatchPowerIdentity)
private fun batchPowerActionLabel(action: GeneralDeviceAction): Int = when (action) {
    GeneralDeviceAction.WAKE -> R.string.device_batch_wake
    GeneralDeviceAction.RESTART -> R.string.device_batch_restart
    GeneralDeviceAction.SHUTDOWN -> R.string.device_batch_shutdown
    GeneralDeviceAction.SLEEP -> R.string.device_batch_sleep
    GeneralDeviceAction.UNINSTALL_AGENT -> error("Unsupported batch action")
}
private fun batchPowerPhaseLabel(phase: DeviceBatchPowerPhase): Int = when (phase) {
    DeviceBatchPowerPhase.CHECKING -> R.string.device_batch_checking
    DeviceBatchPowerPhase.ELIGIBLE -> R.string.device_batch_eligible
    DeviceBatchPowerPhase.SKIPPED -> R.string.device_batch_skipped
    DeviceBatchPowerPhase.SENDING -> R.string.device_batch_sending
    DeviceBatchPowerPhase.ACCEPTED -> R.string.device_batch_accepted
    DeviceBatchPowerPhase.REJECTED -> R.string.device_batch_rejected
    DeviceBatchPowerPhase.NOT_SENT -> R.string.device_batch_not_sent
    DeviceBatchPowerPhase.UNCONFIRMED -> R.string.device_batch_unconfirmed
}

@Composable
internal fun DeviceListBatchActionsButton(source: MeshCentralClient, entries: List<Pair<MeshGroup, MeshNode>>, enabled: Boolean, onExport: () -> Unit) {
    val connected by source.connectionState.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var request by remember(source) { mutableStateOf<DeviceBatchPowerRequest?>(null) }
    Box {
        OutlinedButton(enabled = enabled, onClick = { expanded = true }) { Text(stringResource(R.string.device_batch_menu, entries.size)) }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.device_list_export_json)) }, onClick = { expanded = false; onExport() })
            HorizontalDivider()
            batchPowerActions.forEach { action ->
                DropdownMenuItem(enabled = connected, text = { Text(stringResource(batchPowerActionLabel(action))) }, onClick = {
                    expanded = false
                    request = DeviceBatchPowerRequest(action, deviceBatchPowerTargets(entries), deviceBatchPowerIdentity(source))
                })
            }
        }
    }
    request?.let { current -> DeviceBatchPowerDialog(source, current) { request = null } }
}

@Composable
private fun DeviceBatchPowerDialog(source: MeshCentralClient, request: DeviceBatchPowerRequest, onClose: () -> Unit) {
    val workScope = rememberCoroutineScope()
    val connected by source.connectionState.collectAsState()
    val userInfo by source.userInfo.collectAsState()
    var items by remember(request) { mutableStateOf(request.targets) }
    var checking by remember(request) { mutableStateOf(true) }
    var running by remember(request) { mutableStateOf(false) }
    var finished by remember(request) { mutableStateOf(false) }
    var sendJob by remember(request) { mutableStateOf<Job?>(null) }
    fun update(target: DeviceBatchPowerTarget) { items = items.map { if (it.node.id == target.node.id) target else it } }
    LaunchedEffect(userInfo?.id) { if (userInfo?.id != request.identity.actor) onClose() }
    LaunchedEffect(request) {
        val gate = Semaphore(4)
        coroutineScope { request.targets.map { target -> async {
            gate.withPermit { update(deviceBatchPowerCheck(source, request.identity, target, request.action)) }
        } }.awaitAll() }
        checking = false
    }
    val eligibleCount = items.count { it.phase == DeviceBatchPowerPhase.ELIGIBLE }
    AlertDialog(onDismissRequest = { if (!running) onClose() },
        title = { Text(stringResource(R.string.device_batch_review, stringResource(batchPowerActionLabel(request.action)), items.size)) },
        text = {
            Column(Modifier.heightIn(max = 320.dp)) {
                Text(stringResource(R.string.device_batch_notice))
                if (checking || running) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    items.forEach { target ->
                        Text(target.node.name.ifBlank { target.node.id }, style = MaterialTheme.typography.titleSmall)
                        Text(target.group.name, style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(batchPowerPhaseLabel(target.phase)), style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    }
                }
                if (finished) Text(stringResource(R.string.device_batch_summary,
                    items.count { it.phase == DeviceBatchPowerPhase.ACCEPTED },
                    items.count { it.phase in setOf(DeviceBatchPowerPhase.SKIPPED, DeviceBatchPowerPhase.NOT_SENT) },
                    items.count { it.phase == DeviceBatchPowerPhase.REJECTED },
                    items.count { it.phase == DeviceBatchPowerPhase.UNCONFIRMED }))
            }
        },
        confirmButton = {
            if (!finished) TextButton(enabled = !checking && !running && connected && eligibleCount > 0, onClick = {
                if (!running && !finished && !checking) {
                    val approved = items.filter { it.phase == DeviceBatchPowerPhase.ELIGIBLE }
                    running = true
                    sendJob = workScope.launch {
                        try {
                            val gate = Semaphore(4)
                            coroutineScope { approved.map { target -> async {
                                gate.withPermit {
                                    currentCoroutineContext().ensureActive()
                                    update(target.copy(phase = DeviceBatchPowerPhase.SENDING))
                                    update(deviceBatchPowerSend(source, request.identity, target))
                                }
                            } }.awaitAll() }
                            finished = true
                        } finally { running = false }
                    }
                }
            }) { Text(stringResource(R.string.device_batch_send, eligibleCount)) }
        },
        dismissButton = {
            TextButton(onClick = {
                if (running) {
                    sendJob?.cancel()
                    items = items.map { target -> when (target.phase) {
                        DeviceBatchPowerPhase.SENDING -> target.copy(phase = DeviceBatchPowerPhase.UNCONFIRMED)
                        DeviceBatchPowerPhase.ELIGIBLE -> target.copy(phase = DeviceBatchPowerPhase.NOT_SENT)
                        else -> target
                    } }
                    running = false
                    finished = true
                } else onClose()
            }) { Text(stringResource(if (running) R.string.device_batch_stop else R.string.close)) }
        })
}
