package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.R
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.ui.management.CommandPhase
import iecxiv.meshfabric.ui.management.commandInterpreters
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal enum class DeviceBatchRunPhase { CHECKING, READY, SKIPPED, RUNNING, OUTPUT, REJECTED, NOT_SENT, UNCONFIRMED }
internal data class DeviceBatchRunCompletion(val phase: DeviceBatchRunPhase, val output: String = "", val detail: String = "")
private data class BatchRunTarget(val group: MeshGroup, val node: MeshNode, val draft: GeneralRunDraft? = null,
    val phase: DeviceBatchRunPhase = DeviceBatchRunPhase.CHECKING, val output: String = "", val detail: String = "", val truncated: Boolean = false)
private data class BatchRunRequest(val targets: List<BatchRunTarget>, val identity: DeviceBatchPowerIdentity)
private data class BatchRunText(val command: String, val interpreter: String)
internal fun deviceBatchRunInterpreterAllowed(os: String?, interpreter: String) =
    interpreter.isNotBlank() && commandInterpreters(os).any { it.key == interpreter }
internal fun deviceBatchRunOutput(value: String, budget: Int) = value.take(minOf(16000, budget.coerceAtLeast(0)))
internal fun deviceBatchRunCompletion(runPhase: String, code: String, feedbackPhase: CommandPhase?, output: String = "", detail: String = ""): DeviceBatchRunCompletion? = when {
    feedbackPhase == CommandPhase.OUTPUT -> DeviceBatchRunCompletion(DeviceBatchRunPhase.OUTPUT, output, detail)
    runPhase == "error" -> DeviceBatchRunCompletion(if (code == "unconfirmed") DeviceBatchRunPhase.UNCONFIRMED else DeviceBatchRunPhase.NOT_SENT, detail = detail)
    feedbackPhase == CommandPhase.ERROR -> DeviceBatchRunCompletion(DeviceBatchRunPhase.REJECTED, detail = detail)
    feedbackPhase == CommandPhase.UNKNOWN -> DeviceBatchRunCompletion(DeviceBatchRunPhase.UNCONFIRMED, detail = detail)
    else -> null
}
private suspend fun checkBatchRun(source: MeshCentralClient, request: BatchRunRequest, target: BatchRunTarget, text: BatchRunText): BatchRunTarget {
    if (!deviceBatchPowerSame(source, request.identity)) return target.copy(phase = DeviceBatchRunPhase.SKIPPED)
    val editor = DeviceGeneralRunEditor(source, target.node.id, target.node.name)
    try {
        editor.start()
        val ready = withTimeoutOrNull(22000L) { editor.state.first { it.phase in setOf("ready", "error") } }
        val meta = ready?.meta
        return if (ready?.phase == "ready" && meta != null && meta.mesh == target.group.id && deviceBatchPowerSame(source, request.identity) &&
            generalRunAvailable(target.node.id, meta, source.groupedDevices.value) && deviceBatchRunInterpreterAllowed(meta.os, text.interpreter)) {
            target.copy(draft = GeneralRunDraft(text.command, text.interpreter, meta.os, meta.mesh), phase = DeviceBatchRunPhase.READY)
        } else target.copy(phase = DeviceBatchRunPhase.SKIPPED)
    } finally { editor.close() }
}
private suspend fun sendBatchRun(source: MeshCentralClient, request: BatchRunRequest, target: BatchRunTarget): BatchRunTarget {
    val draft = target.draft ?: return target.copy(phase = DeviceBatchRunPhase.NOT_SENT)
    if (!deviceBatchPowerSame(source, request.identity)) return target.copy(phase = DeviceBatchRunPhase.NOT_SENT)
    val editor = DeviceGeneralRunEditor(source, target.node.id, target.node.name)
    try {
        editor.start()
        val ready = withTimeoutOrNull(22000L) { editor.state.first { it.phase in setOf("ready", "error") } }
        val meta = ready?.meta
        if (ready?.phase != "ready" || meta == null || !deviceBatchPowerSame(source, request.identity) ||
            !generalRunContextMatches(draft, meta)) return target.copy(phase = DeviceBatchRunPhase.NOT_SENT)
        currentCoroutineContext().ensureActive()
        if (!editor.submit(draft)) return target.copy(phase = DeviceBatchRunPhase.NOT_SENT)
        val result = withTimeoutOrNull(120000L) {
            combine(editor.state, editor.feedback.state) { run, feedback ->
                val command = feedback.commands.firstOrNull()
                deviceBatchRunCompletion(run.phase, run.code, command?.phase, command?.output.orEmpty(), command?.message.orEmpty())
            }.first { it != null }
        }
        return if (result == null) target.copy(phase = DeviceBatchRunPhase.UNCONFIRMED)
            else target.copy(phase = result.phase, output = result.output, detail = result.detail)
    } finally { editor.close() }
}
private fun batchRunPhaseLabel(phase: DeviceBatchRunPhase): Int = when (phase) {
    DeviceBatchRunPhase.CHECKING -> R.string.device_batch_checking
    DeviceBatchRunPhase.READY -> R.string.device_batch_eligible
    DeviceBatchRunPhase.SKIPPED -> R.string.device_batch_skipped
    DeviceBatchRunPhase.RUNNING -> R.string.device_batch_run_running
    DeviceBatchRunPhase.OUTPUT -> R.string.device_batch_run_received
    DeviceBatchRunPhase.REJECTED -> R.string.device_batch_run_error
    DeviceBatchRunPhase.NOT_SENT -> R.string.device_batch_not_sent
    DeviceBatchRunPhase.UNCONFIRMED -> R.string.device_batch_unconfirmed
}
@Composable
internal fun rememberDeviceBatchRunAction(source: MeshCentralClient, entries: List<Pair<MeshGroup, MeshNode>>): () -> Unit {
    var request by remember(source) { mutableStateOf<BatchRunRequest?>(null) }
    request?.let { current -> BatchRunDialog(source, current) { request = null } }
    return {
        val targets = deviceBatchPowerTargets(entries).map { BatchRunTarget(it.group, it.node) }
        if (targets.isNotEmpty()) request = BatchRunRequest(targets, deviceBatchPowerIdentity(source))
    }
}
@Composable
private fun BatchRunDialog(source: MeshCentralClient, request: BatchRunRequest, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val connected by source.connectionState.collectAsState()
    val userInfo by source.userInfo.collectAsState()
    val choices = remember { commandInterpreters(null) }
    var command by remember(request) { mutableStateOf("") }
    var interpreter by remember(request) { mutableStateOf("") }
    var interpreterMenu by remember { mutableStateOf(false) }
    var reviewed by remember(request) { mutableStateOf<BatchRunText?>(null) }
    var items by remember(request) { mutableStateOf(request.targets) }
    var checking by remember(request) { mutableStateOf(false) }
    var running by remember(request) { mutableStateOf(false) }
    var finished by remember(request) { mutableStateOf(false) }
    var job by remember(request) { mutableStateOf<Job?>(null) }
    var expandedOutputs by remember(request) { mutableStateOf(emptySet<String>()) }
    val readyCount = items.count { it.phase == DeviceBatchRunPhase.READY }
    fun update(target: BatchRunTarget) {
        val used = items.filter { it.node.id != target.node.id }.sumOf { it.output.length }
        val bounded = deviceBatchRunOutput(target.output, 262144 - used)
        val value = target.copy(output = bounded, detail = target.detail.take(1000), truncated = target.truncated || bounded.length < target.output.length)
        items = items.map { if (it.node.id == value.node.id) value else it }
    }
    LaunchedEffect(userInfo?.id) { if (userInfo?.id != request.identity.actor) onClose() }
    AlertDialog(onDismissRequest = { if (!running) onClose() },
        title = { Text(stringResource(R.string.device_batch_review, stringResource(R.string.device_batch_run_action), items.size)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.device_batch_run_notice))
                val text = reviewed
                if (text == null) {
                    Box {
                        OutlinedButton(onClick = { interpreterMenu = true }) { Text(choices.firstOrNull { it.key == interpreter }?.label ?: stringResource(R.string.device_batch_run_interpreter)) }
                        DropdownMenu(expanded = interpreterMenu, onDismissRequest = { interpreterMenu = false }) {
                            choices.forEach { choice -> DropdownMenuItem(text = { Text(choice.label) }, onClick = { interpreter = choice.key; interpreterMenu = false }) }
                        }
                    }
                    OutlinedTextField(value = command, onValueChange = { command = it }, minLines = 4, maxLines = 10,
                        label = { Text(stringResource(R.string.device_batch_run_command)) }, supportingText = { Text("${command.length}/64000") },
                        isError = command.isNotEmpty() && !generalRunTextValid(command), modifier = Modifier.fillMaxWidth())
                } else {
                    Text(choices.firstOrNull { it.key == text.interpreter }?.label.orEmpty(), style = MaterialTheme.typography.titleSmall)
                    SelectionContainer { Text(text.command, fontFamily = FontFamily.Monospace) }
                }
                if (checking || running) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (text != null) items.forEach { target ->
                    Text(target.node.name.ifBlank { target.node.id }, style = MaterialTheme.typography.titleSmall)
                    Text(target.group.name, style = MaterialTheme.typography.bodySmall)
                    target.draft?.let { Text(it.os, style = MaterialTheme.typography.bodySmall) }
                    Text(stringResource(batchRunPhaseLabel(target.phase)), style = MaterialTheme.typography.bodySmall)
                    if (target.detail.isNotEmpty()) Text(target.detail, style = MaterialTheme.typography.bodySmall)
                    if (target.truncated) Text(stringResource(R.string.device_batch_run_truncated), style = MaterialTheme.typography.bodySmall)
                    if (target.output.isNotEmpty()) {
                        TextButton(onClick = { expandedOutputs = if (target.node.id in expandedOutputs) expandedOutputs - target.node.id else expandedOutputs + target.node.id }) {
                            Text(stringResource(if (target.node.id in expandedOutputs) R.string.device_batch_run_hide_output else R.string.device_batch_run_show_output))
                        }
                        if (target.node.id in expandedOutputs) SelectionContainer { Text(target.output, fontFamily = FontFamily.Monospace) }
                    }
                    HorizontalDivider()
                }
                if (finished) Text(stringResource(R.string.device_batch_run_summary,
                    items.count { it.phase == DeviceBatchRunPhase.OUTPUT },
                    items.count { it.phase in setOf(DeviceBatchRunPhase.SKIPPED, DeviceBatchRunPhase.NOT_SENT) },
                    items.count { it.phase == DeviceBatchRunPhase.REJECTED }, items.count { it.phase == DeviceBatchRunPhase.UNCONFIRMED }))
            }
        },
        confirmButton = {
            if (!finished) TextButton(enabled = connected && !checking && !running &&
                (if (reviewed == null) generalRunTextValid(command) && choices.any { it.key == interpreter } else readyCount > 0), onClick = {
                if (!checking && !running && !finished && deviceBatchPowerSame(source, request.identity)) {
                    val text = reviewed
                    if (text == null) {
                        if (generalRunTextValid(command) && choices.any { it.key == interpreter }) {
                            val approvedText = BatchRunText(command, interpreter)
                            reviewed = approvedText; checking = true
                            job = scope.launch {
                                try {
                                    val gate = Semaphore(4)
                                    coroutineScope { request.targets.map { target -> async {
                                        gate.withPermit { update(checkBatchRun(source, request, target, approvedText)) }
                                    } }.awaitAll() }
                                } finally { checking = false }
                            }
                        }
                    } else {
                        val approved = items.filter { it.phase == DeviceBatchRunPhase.READY }
                        running = true
                        job = scope.launch {
                            try {
                                val gate = Semaphore(4)
                                coroutineScope { approved.map { target -> async {
                                    gate.withPermit {
                                        currentCoroutineContext().ensureActive()
                                        update(target.copy(phase = DeviceBatchRunPhase.RUNNING))
                                        update(sendBatchRun(source, request, target))
                                    }
                                } }.awaitAll() }
                                finished = true
                            } finally { running = false }
                        }
                    }
                }
            }) { Text(if (reviewed == null) stringResource(R.string.device_batch_run_review) else stringResource(R.string.device_batch_run_send, readyCount)) }
        },
        dismissButton = {
            Row {
                if (reviewed != null && !checking && !running && !finished) TextButton(onClick = { reviewed = null; items = request.targets; expandedOutputs = emptySet() }) { Text(stringResource(R.string.back)) }
                TextButton(onClick = {
                    if (running) {
                        job?.cancel()
                        items = items.map { target -> when (target.phase) {
                            DeviceBatchRunPhase.RUNNING -> target.copy(phase = DeviceBatchRunPhase.UNCONFIRMED)
                            DeviceBatchRunPhase.READY -> target.copy(phase = DeviceBatchRunPhase.NOT_SENT)
                            else -> target
                        } }
                        running = false; finished = true
                    } else onClose()
                }) { Text(stringResource(if (running) R.string.device_batch_run_stop else R.string.close)) }
            }
        })
}
