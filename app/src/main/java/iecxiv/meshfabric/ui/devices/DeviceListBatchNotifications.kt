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

private data class BatchNotificationTarget(val group: MeshGroup, val node: MeshNode, val mesh: String? = null,
    val phase: DeviceBatchPowerPhase = DeviceBatchPowerPhase.CHECKING)
private data class BatchNotificationRequest(val targets: List<BatchNotificationTarget>, val identity: DeviceBatchPowerIdentity)
private data class ReviewedNotification(val title: String, val message: String)
internal fun deviceBatchNotificationResult(state: GeneralMessageState, submitted: Boolean) =
    deviceBatchPowerResult(GeneralDeviceActionsState(phase = state.phase, code = state.code), submitted)

private suspend fun checkNotification(source: MeshCentralClient, request: BatchNotificationRequest, target: BatchNotificationTarget): BatchNotificationTarget {
    if (!deviceBatchPowerSame(source, request.identity)) return target.copy(phase = DeviceBatchPowerPhase.SKIPPED)
    val editor = DeviceToastEditor(source, target.node.id)
    try {
        editor.start()
        val ready = withTimeoutOrNull(22000L) { editor.state.first { it.phase in setOf("ready", "error") } }
        val meta = ready?.meta
        return if (ready?.phase == "ready" && meta != null && meta.mesh == target.group.id && deviceBatchPowerSame(source, request.identity) &&
            generalMessageAvailable(target.node.id, meta, source.groupedDevices.value)) {
            target.copy(mesh = meta.mesh, phase = DeviceBatchPowerPhase.ELIGIBLE)
        } else target.copy(phase = DeviceBatchPowerPhase.SKIPPED)
    } finally { editor.close() }
}
private suspend fun sendNotification(source: MeshCentralClient, request: BatchNotificationRequest,
    target: BatchNotificationTarget, text: ReviewedNotification): BatchNotificationTarget {
    val mesh = target.mesh ?: return target.copy(phase = DeviceBatchPowerPhase.NOT_SENT)
    if (!deviceBatchPowerSame(source, request.identity)) return target.copy(phase = DeviceBatchPowerPhase.NOT_SENT)
    val editor = DeviceToastEditor(source, target.node.id)
    try {
        editor.start()
        val ready = withTimeoutOrNull(22000L) { editor.state.first { it.phase in setOf("ready", "error") } }
        if (ready?.phase != "ready" || !deviceBatchPowerSame(source, request.identity)) return target.copy(phase = DeviceBatchPowerPhase.NOT_SENT)
        currentCoroutineContext().ensureActive()
        val submitted = editor.submit(GeneralMessageDraft(text.title, text.message, mesh))
        if (!submitted) return target.copy(phase = deviceBatchNotificationResult(editor.state.value, false))
        val result = withTimeoutOrNull(45000L) { editor.state.first { it.phase in setOf("accepted", "rejected", "error") } }
        return target.copy(phase = if (result == null) DeviceBatchPowerPhase.UNCONFIRMED else deviceBatchNotificationResult(result, true))
    } finally { editor.close() }
}
private fun notificationPhaseLabel(phase: DeviceBatchPowerPhase): Int = when (phase) {
    DeviceBatchPowerPhase.CHECKING -> R.string.device_batch_checking
    DeviceBatchPowerPhase.ELIGIBLE -> R.string.device_batch_eligible
    DeviceBatchPowerPhase.SKIPPED -> R.string.device_batch_skipped
    DeviceBatchPowerPhase.SENDING -> R.string.device_batch_sending
    DeviceBatchPowerPhase.ACCEPTED -> R.string.device_batch_notify_accepted
    DeviceBatchPowerPhase.REJECTED -> R.string.device_batch_rejected
    DeviceBatchPowerPhase.NOT_SENT -> R.string.device_batch_not_sent
    DeviceBatchPowerPhase.UNCONFIRMED -> R.string.device_batch_unconfirmed
}
@Composable
internal fun rememberDeviceBatchNotificationAction(source: MeshCentralClient, entries: List<Pair<MeshGroup, MeshNode>>): () -> Unit {
    var request by remember(source) { mutableStateOf<BatchNotificationRequest?>(null) }
    request?.let { current -> BatchNotificationDialog(source, current) { request = null } }
    return {
        val targets = deviceBatchPowerTargets(entries).map { BatchNotificationTarget(it.group, it.node) }
        if (targets.isNotEmpty()) request = BatchNotificationRequest(targets, deviceBatchPowerIdentity(source))
    }
}
@Composable
private fun BatchNotificationDialog(source: MeshCentralClient, request: BatchNotificationRequest, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val connected by source.connectionState.collectAsState()
    val userInfo by source.userInfo.collectAsState()
    val defaultTitle = stringResource(R.string.device_batch_notify_action)
    var title by remember(request) { mutableStateOf(defaultTitle) }
    var message by remember(request) { mutableStateOf("") }
    var reviewed by remember(request) { mutableStateOf<ReviewedNotification?>(null) }
    var items by remember(request) { mutableStateOf(request.targets) }
    var checking by remember(request) { mutableStateOf(false) }
    var running by remember(request) { mutableStateOf(false) }
    var finished by remember(request) { mutableStateOf(false) }
    var job by remember(request) { mutableStateOf<Job?>(null) }
    val readyCount = items.count { it.phase == DeviceBatchPowerPhase.ELIGIBLE }
    fun update(target: BatchNotificationTarget) { items = items.map { if (it.node.id == target.node.id) target else it } }
    LaunchedEffect(userInfo?.id) { if (userInfo?.id != request.identity.actor) onClose() }
    AlertDialog(onDismissRequest = { if (!running) onClose() },
        title = { Text(stringResource(R.string.device_batch_review, stringResource(R.string.device_batch_notify_action), items.size)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.device_batch_notify_notice))
                val text = reviewed
                if (text == null) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true,
                        label = { Text(stringResource(R.string.device_batch_notify_title)) }, supportingText = { Text("${title.length}/160") },
                        isError = title.length > 160, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = message, onValueChange = { message = it }, minLines = 2, maxLines = 5,
                        label = { Text(stringResource(R.string.device_batch_notify_body)) }, supportingText = { Text("${message.length}/512") },
                        isError = message.length > 512, modifier = Modifier.fillMaxWidth())
                } else {
                    Text(text.title, style = MaterialTheme.typography.titleSmall)
                    Text(text.message)
                }
                if (checking || running) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (text != null) items.forEach { target ->
                    Text(target.node.name.ifBlank { target.node.id }, style = MaterialTheme.typography.titleSmall)
                    Text(target.group.name, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(notificationPhaseLabel(target.phase)), style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                }
                if (finished) Text(stringResource(R.string.device_batch_summary,
                    items.count { it.phase == DeviceBatchPowerPhase.ACCEPTED },
                    items.count { it.phase in setOf(DeviceBatchPowerPhase.SKIPPED, DeviceBatchPowerPhase.NOT_SENT) },
                    items.count { it.phase == DeviceBatchPowerPhase.REJECTED }, items.count { it.phase == DeviceBatchPowerPhase.UNCONFIRMED }))
            }
        },
        confirmButton = {
            if (!finished) TextButton(enabled = connected && !checking && !running &&
                (if (reviewed == null) generalToastTextValid(title, message) else readyCount > 0), onClick = {
                if (!checking && !running && !finished && deviceBatchPowerSame(source, request.identity)) {
                    val text = reviewed
                    if (text == null) {
                        if (generalToastTextValid(title, message)) {
                            reviewed = ReviewedNotification(title, message); checking = true
                            job = scope.launch {
                                try {
                                    val gate = Semaphore(4)
                                    coroutineScope { request.targets.map { target -> async {
                                        gate.withPermit { update(checkNotification(source, request, target)) }
                                    } }.awaitAll() }
                                } finally { checking = false }
                            }
                        }
                    } else {
                        val approved = items.filter { it.phase == DeviceBatchPowerPhase.ELIGIBLE }
                        running = true
                        job = scope.launch {
                            try {
                                val gate = Semaphore(4)
                                coroutineScope { approved.map { target -> async {
                                    gate.withPermit {
                                        currentCoroutineContext().ensureActive()
                                        update(target.copy(phase = DeviceBatchPowerPhase.SENDING))
                                        update(sendNotification(source, request, target, text))
                                    }
                                } }.awaitAll() }
                                finished = true
                            } finally { running = false }
                        }
                    }
                }
            }) { Text(if (reviewed == null) stringResource(R.string.device_batch_notify_review) else stringResource(R.string.device_batch_notify_send, readyCount)) }
        },
        dismissButton = {
            Row {
                if (reviewed != null && !checking && !running && !finished) TextButton(onClick = { reviewed = null; items = request.targets }) { Text(stringResource(R.string.back)) }
                TextButton(onClick = {
                    if (running) {
                        job?.cancel()
                        items = items.map { target -> when (target.phase) {
                            DeviceBatchPowerPhase.SENDING -> target.copy(phase = DeviceBatchPowerPhase.UNCONFIRMED)
                            DeviceBatchPowerPhase.ELIGIBLE -> target.copy(phase = DeviceBatchPowerPhase.NOT_SENT)
                            else -> target
                        } }
                        running = false; finished = true
                    } else onClose()
                }) { Text(stringResource(if (running) R.string.device_batch_stop else R.string.close)) }
            }
        })
}
