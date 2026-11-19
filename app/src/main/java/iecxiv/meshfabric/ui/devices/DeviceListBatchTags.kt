package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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

internal enum class DeviceBatchTagMode { ADD, REMOVE, REPLACE }
internal enum class DeviceBatchTagPhase { CHECKING, READY, UNCHANGED, SKIPPED, SAVING, VERIFIED, CONFLICT, REJECTED, NOT_SENT, UNCONFIRMED }
internal data class DeviceBatchTagTarget(val group: MeshGroup, val node: MeshNode,
    val before: GeneralMeta? = null, val after: GeneralDraft? = null,
    val phase: DeviceBatchTagPhase = DeviceBatchTagPhase.CHECKING)
private data class DeviceBatchTagRequest(val targets: List<DeviceBatchTagTarget>, val identity: DeviceBatchPowerIdentity)

internal fun deviceBatchTagInput(text: String, mode: DeviceBatchTagMode): List<String> {
    require(text.length <= 4096 && Charsets.UTF_8.newEncoder().canEncode(text))
    val tags = generalDraft("Device", text.replace("\r\n", "\n")).tags
    require(mode == DeviceBatchTagMode.REPLACE || tags.isNotEmpty())
    return tags
}
internal fun deviceBatchTagDraft(before: GeneralMeta, input: List<String>, mode: DeviceBatchTagMode): GeneralDraft {
    val desired = when (mode) {
        DeviceBatchTagMode.ADD -> before.tags + input
        DeviceBatchTagMode.REMOVE -> before.tags.filterNot { it in input.toSet() }
        DeviceBatchTagMode.REPLACE -> input
    }.distinct().sorted()
    val draft = generalDraft(before.name, desired.joinToString("\n"))
    require(draft.tags == desired)
    return draft
}
internal fun deviceBatchTagAvailable(node: String, group: String, meta: GeneralMeta, inventory: List<Pair<MeshGroup, List<MeshNode>>>): Boolean {
    if (meta.rights and 4L == 0L || meta.mesh != group) return false
    val mesh = inventory.singleOrNull { it.first.id == group } ?: return false
    val current = inventory.flatMap { it.second }.singleOrNull { it.id == node } ?: return false
    return current.meshid == group && mesh.second.count { it.id == node } == 1
}
internal fun deviceBatchTagResult(state: GeneralEditorState): DeviceBatchTagPhase = when {
    state.phase == "confirmed" -> DeviceBatchTagPhase.VERIFIED
    state.code == "conflict" -> DeviceBatchTagPhase.CONFLICT
    state.code == "denied" -> DeviceBatchTagPhase.REJECTED
    else -> DeviceBatchTagPhase.UNCONFIRMED
}

private suspend fun checkBatchTags(source: MeshCentralClient, request: DeviceBatchTagRequest,
    target: DeviceBatchTagTarget, input: List<String>, mode: DeviceBatchTagMode): DeviceBatchTagTarget = coroutineScope {
    if (!deviceBatchPowerSame(source, request.identity)) return@coroutineScope target.copy(phase = DeviceBatchTagPhase.SKIPPED)
    val editor = DeviceGeneralEditor(source, target.node, this)
    val guard = launch { while (isActive) { delay(100L); if (!deviceBatchPowerSame(source, request.identity)) editor.close() } }
    try {
        editor.start()
        val ready = withTimeoutOrNull(27000L) { editor.state.first { it.phase in setOf("ready", "error") } }
        val meta = ready?.meta
        if (meta == null || ready?.canEdit != true || !deviceBatchPowerSame(source, request.identity) ||
            !deviceBatchTagAvailable(target.node.id, target.group.id, meta, source.groupedDevices.value)) {
            return@coroutineScope target.copy(phase = DeviceBatchTagPhase.SKIPPED)
        }
        val draft = runCatching { deviceBatchTagDraft(meta, input, mode) }.getOrNull()
            ?: return@coroutineScope target.copy(phase = DeviceBatchTagPhase.SKIPPED)
        target.copy(before = meta, after = draft, phase = if (generalMatches(meta, draft)) DeviceBatchTagPhase.UNCHANGED else DeviceBatchTagPhase.READY)
    } finally { guard.cancel(); editor.close() }
}

private suspend fun saveBatchTags(source: MeshCentralClient, request: DeviceBatchTagRequest,
    target: DeviceBatchTagTarget): DeviceBatchTagTarget = coroutineScope {
    val before = target.before ?: return@coroutineScope target.copy(phase = DeviceBatchTagPhase.NOT_SENT)
    val after = target.after ?: return@coroutineScope target.copy(phase = DeviceBatchTagPhase.NOT_SENT)
    if (!deviceBatchPowerSame(source, request.identity)) return@coroutineScope target.copy(phase = DeviceBatchTagPhase.NOT_SENT)
    val editor = DeviceGeneralEditor(source, target.node, this)
    val guard = launch { while (isActive) { delay(100L); if (!deviceBatchPowerSame(source, request.identity)) editor.close() } }
    try {
        editor.start()
        val ready = withTimeoutOrNull(27000L) { editor.state.first { it.phase in setOf("ready", "error") } }
        val meta = ready?.meta
        if (meta == null || ready?.canEdit != true || !deviceBatchPowerSame(source, request.identity) ||
            !deviceBatchTagAvailable(target.node.id, target.group.id, meta, source.groupedDevices.value)) {
            return@coroutineScope target.copy(phase = DeviceBatchTagPhase.NOT_SENT)
        }
        if (!generalBaselineMatches(meta, before, after)) return@coroutineScope target.copy(phase = DeviceBatchTagPhase.CONFLICT)
        currentCoroutineContext().ensureActive()
        editor.save(after)
        val result = withTimeoutOrNull(30000L) { editor.state.first { it.phase in setOf("confirmed", "error") } }
        target.copy(phase = if (result == null) DeviceBatchTagPhase.UNCONFIRMED else deviceBatchTagResult(result))
    } finally { guard.cancel(); editor.close() }
}

private fun batchTagModeLabel(mode: DeviceBatchTagMode): Int = when (mode) {
    DeviceBatchTagMode.ADD -> R.string.device_batch_tags_add
    DeviceBatchTagMode.REMOVE -> R.string.device_batch_tags_remove
    DeviceBatchTagMode.REPLACE -> R.string.device_batch_tags_replace
}
private fun batchTagPhaseLabel(phase: DeviceBatchTagPhase): Int = when (phase) {
    DeviceBatchTagPhase.CHECKING -> R.string.device_batch_checking
    DeviceBatchTagPhase.READY -> R.string.device_batch_eligible
    DeviceBatchTagPhase.UNCHANGED -> R.string.device_batch_tags_unchanged
    DeviceBatchTagPhase.SKIPPED -> R.string.device_batch_skipped
    DeviceBatchTagPhase.SAVING -> R.string.device_batch_tags_saving
    DeviceBatchTagPhase.VERIFIED -> R.string.device_batch_tags_verified
    DeviceBatchTagPhase.CONFLICT -> R.string.device_batch_tags_conflict
    DeviceBatchTagPhase.REJECTED -> R.string.device_batch_rejected
    DeviceBatchTagPhase.NOT_SENT -> R.string.device_batch_not_sent
    DeviceBatchTagPhase.UNCONFIRMED -> R.string.device_batch_unconfirmed
}

@Composable
internal fun rememberDeviceBatchTagsAction(source: MeshCentralClient, entries: List<Pair<MeshGroup, MeshNode>>): () -> Unit {
    var request by remember(source) { mutableStateOf<DeviceBatchTagRequest?>(null) }
    request?.let { current -> DeviceBatchTagsDialog(source, current) { request = null } }
    return {
        val targets = deviceBatchPowerTargets(entries).map { DeviceBatchTagTarget(it.group, it.node) }
        if (targets.isNotEmpty()) request = DeviceBatchTagRequest(targets, deviceBatchPowerIdentity(source))
    }
}

@Composable
private fun DeviceBatchTagsDialog(source: MeshCentralClient, request: DeviceBatchTagRequest, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val connected by source.connectionState.collectAsState()
    val userInfo by source.userInfo.collectAsState()
    var mode by remember(request) { mutableStateOf(DeviceBatchTagMode.ADD) }
    var input by remember(request) { mutableStateOf("") }
    var modeMenu by remember { mutableStateOf(false) }
    var clearConfirmed by remember(request) { mutableStateOf(false) }
    var reviewed by remember(request) { mutableStateOf(false) }
    var checking by remember(request) { mutableStateOf(false) }
    var running by remember(request) { mutableStateOf(false) }
    var finished by remember(request) { mutableStateOf(false) }
    var items by remember(request) { mutableStateOf(request.targets) }
    var job by remember(request) { mutableStateOf<Job?>(null) }
    val parsed = runCatching { deviceBatchTagInput(input, mode) }.getOrNull()
    val clearing = mode == DeviceBatchTagMode.REPLACE && parsed?.isEmpty() == true
    val readyCount = items.count { it.phase == DeviceBatchTagPhase.READY }
    fun update(target: DeviceBatchTagTarget) { items = items.map { if (it.node.id == target.node.id) target else it } }
    LaunchedEffect(userInfo?.id) { if (userInfo?.id != request.identity.actor) onClose() }
    DeviceBatchAlertDialog(onDismissRequest = { if (!running) onClose() },
        title = { DeviceBatchDialogTitle(stringResource(R.string.device_batch_review, stringResource(R.string.device_batch_tags_action), items.size)) },
        text = {
            Column(Modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DeviceBatchNotice(stringResource(R.string.device_batch_tags_notice))
                if (!reviewed) {
                    Box {
                        OutlinedButton(onClick = { modeMenu = true }) { Text(stringResource(batchTagModeLabel(mode))) }
                        DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                            DeviceBatchTagMode.entries.forEach { option ->
                                DropdownMenuItem(text = { Text(stringResource(batchTagModeLabel(option))) }, onClick = { mode = option; modeMenu = false; clearConfirmed = false })
                            }
                        }
                    }
                    OutlinedTextField(value = input, onValueChange = { input = it; clearConfirmed = false },
                        label = { Text(stringResource(R.string.device_batch_tags_input)) }, minLines = 2, maxLines = 4,
                        isError = parsed == null && input.isNotBlank(), modifier = Modifier.fillMaxWidth())
                    if (clearing) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = clearConfirmed, onCheckedChange = { clearConfirmed = it })
                        Text(stringResource(R.string.device_batch_tags_clear), modifier = Modifier.weight(1f))
                    }
                } else Text(stringResource(batchTagModeLabel(mode)), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                if (checking || running) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (reviewed) items.forEach { target ->
                    Text(target.node.name.ifBlank { target.node.id }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    DeviceBatchSecondary(target.group.name)
                    target.before?.let { Text(stringResource(R.string.device_batch_tags_before, it.tags.joinToString(", ").ifEmpty { "—" })) }
                    target.after?.let { Text(stringResource(R.string.device_batch_tags_after, it.tags.joinToString(", ").ifEmpty { "—" })) }
                    DeviceBatchStatus(stringResource(batchTagPhaseLabel(target.phase)), deviceBatchVisualKind("tags", target.phase.name))
                    HorizontalDivider()
                }
                if (finished) DeviceBatchSummary(stringResource(R.string.device_batch_tags_summary,
                    items.count { it.phase == DeviceBatchTagPhase.VERIFIED }, items.count { it.phase == DeviceBatchTagPhase.UNCHANGED },
                    items.count { it.phase in setOf(DeviceBatchTagPhase.SKIPPED, DeviceBatchTagPhase.NOT_SENT) },
                    items.count { it.phase == DeviceBatchTagPhase.CONFLICT }, items.count { it.phase == DeviceBatchTagPhase.REJECTED },
                    items.count { it.phase == DeviceBatchTagPhase.UNCONFIRMED }))
            }
        },
        confirmButton = {
            if (!finished) TextButton(enabled = connected && !checking && !running &&
                (if (reviewed) readyCount > 0 else parsed != null && (!clearing || clearConfirmed)), onClick = {
                if (!checking && !running && !finished && deviceBatchPowerSame(source, request.identity)) {
                    if (!reviewed) {
                        val values = parsed
                        if (values != null && (!clearing || clearConfirmed)) {
                            val operation = mode
                            reviewed = true; checking = true
                            job = scope.launch {
                                try {
                                    val gate = Semaphore(4)
                                    coroutineScope { request.targets.map { target -> async {
                                        gate.withPermit { update(checkBatchTags(source, request, target, values, operation)) }
                                    } }.awaitAll() }
                                } finally { checking = false }
                            }
                        }
                    } else {
                        val approved = items.filter { it.phase == DeviceBatchTagPhase.READY }
                        running = true
                        job = scope.launch {
                            try {
                                val gate = Semaphore(4)
                                coroutineScope { approved.map { target -> async {
                                    gate.withPermit {
                                        currentCoroutineContext().ensureActive()
                                        update(target.copy(phase = DeviceBatchTagPhase.SAVING))
                                        update(saveBatchTags(source, request, target))
                                    }
                                } }.awaitAll() }
                                finished = true
                            } finally { running = false }
                        }
                    }
                }
            }) { Text(if (reviewed) stringResource(R.string.device_batch_tags_apply, readyCount) else stringResource(R.string.device_batch_tags_review)) }
        },
        dismissButton = {
            Row {
                if (reviewed && !checking && !running && !finished) TextButton(onClick = { reviewed = false; items = request.targets; clearConfirmed = false }) { Text(stringResource(R.string.device_batch_tags_back)) }
                TextButton(onClick = {
                    if (running) {
                        job?.cancel()
                        items = items.map { target -> when (target.phase) {
                            DeviceBatchTagPhase.SAVING -> target.copy(phase = DeviceBatchTagPhase.UNCONFIRMED)
                            DeviceBatchTagPhase.READY -> target.copy(phase = DeviceBatchTagPhase.NOT_SENT)
                            else -> target
                        } }
                        running = false; finished = true
                    } else onClose()
                }) { Text(stringResource(if (running) R.string.device_batch_stop else R.string.close)) }
            }
        })
}
