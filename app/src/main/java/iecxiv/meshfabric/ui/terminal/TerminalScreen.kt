package iecxiv.meshfabric.ui.terminal

import iecxiv.meshfabric.data.remote.TerminalConnectionStatus
import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.res.stringResource
import iecxiv.meshfabric.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.Gson

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TerminalScreen(
    nodeId: String,
    nodeName: String,
    viewModel: TerminalViewModel,
    onBack: () -> Unit
) {
    val revision by viewModel.manualReconnect.revision.collectAsState()
    val attempts by viewModel.manualReconnect.state.collectAsState()
    val allHistory by viewModel.manualReconnect.history.collectAsState()
    val currentSession = remember(nodeId, viewModel, revision) { viewModel.terminalClient.getSession(viewModel.meshClient, nodeId) }
    val history = allHistory[currentSession.cleanNodeId].orEmpty()
    var historyId by remember(nodeId) { mutableStateOf<String?>(null) }
    var showHistoryMenu by remember(nodeId) { mutableStateOf(false) }
    var historiesToDelete by remember(nodeId) { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(currentSession) { historyId = null }
    val selectedSession = history.firstOrNull { it.localSessionId == historyId } ?: currentSession
    val showingHistory = selectedSession !== currentSession
    val reconnectState = attempts[currentSession.cleanNodeId] ?: ManualReconnectState.IDLE
    val reconnecting = reconnectState == ManualReconnectState.CONNECTING
    val connectionStatus by selectedSession.connectionStatus.collectAsState()
    val historyTruncated by selectedSession.historyTruncated.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var rendererVersion by remember(nodeId) { mutableStateOf(0) }
    val renderer = remember(selectedSession, viewModel, rendererVersion) { viewModel.renderSession(context, selectedSession) }
    var showRecoveryDialog by remember(renderer) { mutableStateOf(false) }
    val rendererError by renderer.error.collectAsState()
    val inputEnabled = !showingHistory && !reconnecting && rendererError == null && connectionStatus == TerminalConnectionStatus.CONNECTED
    var showTerminalMenu by remember(renderer) { mutableStateOf(false) }
    var showCopyDialog by remember(renderer) { mutableStateOf(false) }
    var copySnapshot by remember(renderer) { mutableStateOf<TerminalCopySnapshot?>(null) }
    var copyLoading by remember(renderer) { mutableStateOf(false) }
    var showPasteDialog by remember(renderer) { mutableStateOf(false) }
    var pasteDraft by remember(renderer) { mutableStateOf<TerminalPasteDraft?>(null) }
    var pasteAttempted by remember(renderer) { mutableStateOf(false) }
    var pasteBusy by remember(renderer) { mutableStateOf(false) }
    var pasteResult by remember(renderer) { mutableStateOf<TerminalPasteResult?>(null) }
    var pasteEditing by remember(renderer) { mutableStateOf(false) }
    fun openPaste(editing: Boolean) {
        showTerminalMenu = false
        pasteEditing = editing
        pasteDraft = readTerminalPaste(context)
        pasteAttempted = false
        pasteBusy = false
        pasteResult = null
        showPasteDialog = true
    }
    LaunchedEffect(pasteAttempted, renderer) {
        if (pasteAttempted) {
            val text = pasteDraft?.text
            pasteResult = if (text == null) TerminalPasteResult.REJECTED else renderer.paste(text)
            pasteBusy = false
            if (pasteResult == TerminalPasteResult.DISPATCHED) {
                showPasteDialog = false
                pasteDraft = null
            }
        }
    }
    LaunchedEffect(showCopyDialog, renderer) {
        copySnapshot = null
        copyLoading = showCopyDialog
        if (showCopyDialog) {
            copySnapshot = renderer.copySnapshot()
            copyLoading = false
        }
    }
    var attachmentToken by remember(renderer) { mutableStateOf<Long?>(null) }
    var showCommandDialog by remember(renderer) { mutableStateOf(false) }
    var commandInput by remember(renderer) { mutableStateOf("") }
    var selectedCtrlChar by remember(renderer) { mutableStateOf('C') }
    var showCtrlDropdown by remember(renderer) { mutableStateOf(false) }
    val ctrlLetters = remember { ('A'..'Z').toList() }
    LaunchedEffect(renderer, connectionStatus) {
        if (connectionStatus != TerminalConnectionStatus.CONNECTED) {
            showPasteDialog = false
            pasteDraft = null
            pasteAttempted = false
            pasteBusy = false
            showCommandDialog = false
            commandInput = ""
        }
    }

    var keyMenuOpen by remember(renderer) { mutableStateOf(false) }
    var keyboardRequest by remember(renderer) { mutableStateOf(0) }
    var previousOverlay by remember(renderer) { mutableStateOf(false) }
    val inputOverlay = showTerminalMenu || showCopyDialog || showPasteDialog || showCommandDialog || showRecoveryDialog || keyMenuOpen || showHistoryMenu || historiesToDelete.isNotEmpty()
    LaunchedEffect(renderer, inputOverlay, inputEnabled) {
        renderer.setFocusBlocked(inputOverlay || !inputEnabled)
        if (inputEnabled && !inputOverlay && previousOverlay) renderer.requestTerminalFocus(false)
        previousOverlay = inputOverlay
    }
    LaunchedEffect(renderer, keyboardRequest) {
        if (keyboardRequest > 0 && inputEnabled && !inputOverlay) renderer.toggleKeyboard()
    }
    DisposableEffect(renderer) {
        onDispose { renderer.setFocusBlocked(true) }
    }

    fun changeFontSize(delta: Int) { renderer.changeFontSize(delta) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(renderer, lifecycleOwner) {
        val binding = renderer.bindVisibility()
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ ->
            renderer.setResumed(binding, lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        renderer.setResumed(binding, lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            renderer.unbindVisibility(binding)
        }
    }
    LaunchedEffect(nodeId, viewModel) { viewModel.startSession(nodeId) }
    DisposableEffect(renderer) {
        onDispose { attachmentToken?.let { viewModel.releaseRenderer(renderer, it) } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.terminal_title, nodeName),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when (connectionStatus) {
                                TerminalConnectionStatus.CONNECTED -> stringResource(R.string.status_connected)
                                TerminalConnectionStatus.CONNECTING -> stringResource(R.string.status_connecting)
                                TerminalConnectionStatus.DISCONNECTED -> stringResource(R.string.status_disconnected)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (connectionStatus == TerminalConnectionStatus.CONNECTED) Color.Green else Color.Yellow,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { changeFontSize(-1) },
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("A-", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    TextButton(
                        onClick = { changeFontSize(1) },
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("A+", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    IconButton(
                        onClick = { keyboardRequest++ },
                        enabled = inputEnabled && !inputOverlay,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Keyboard, contentDescription = stringResource(R.string.terminal_keyboard_toggle))
                    }
                    Box {
                        IconButton(onClick = { showTerminalMenu = true }, enabled = rendererError == null) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.terminal_tools))
                        }
                        DropdownMenu(expanded = showTerminalMenu, onDismissRequest = { showTerminalMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.terminal_copy_title)) },
                                onClick = { showTerminalMenu = false; showCopyDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.terminal_paste_title)) },
                                enabled = inputEnabled,
                                onClick = { openPaste(false) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.terminal_paste_edit_title)) },
                                enabled = inputEnabled,
                                onClick = { openPaste(true) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.send_command)) },
                                enabled = inputEnabled,
                                onClick = { showTerminalMenu = false; showCommandDialog = true }
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            if (!showingHistory) viewModel.stopSession(nodeId)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Desconectar Terminal",
                            tint = Color.Red
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0D1117))
        ) {
            TerminalSessionPopup(
                status = connectionStatus,
                attempt = reconnectState,
                history = history,
                showingHistory = showingHistory,
                truncated = historyTruncated,
                expanded = showHistoryMenu,
                deletionIds = historiesToDelete,
                onExpanded = { showHistoryMenu = it },
                onReconnect = { viewModel.manualReconnect.start(nodeId) },
                onCancel = { viewModel.manualReconnect.cancel(nodeId) },
                onCurrent = { historyId = null },
                onSelect = { historyId = it },
                onRequestDelete = { historiesToDelete = it },
                onDismissDelete = { historiesToDelete = emptySet() },
                onDelete = { ids ->
                    if (historyId?.let { it in ids } == true) historyId = null
                    viewModel.deleteTerminalHistories(nodeId, ids)
                }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                key(renderer) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx -> FrameLayout(ctx) },
                        onReset = null,
                        onRelease = { parent -> viewModel.releaseParent(renderer, parent) },
                        update = { parent -> attachmentToken = renderer.attach(context, parent) }
                    )
                }
                if (rendererError != null) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(when (rendererError) {
                                TerminalRendererError.RENDERER_STOPPED -> R.string.terminal_renderer_stopped
                                TerminalRendererError.ACK_TIMEOUT -> R.string.terminal_view_unresponsive
                                else -> R.string.terminal_output_continuity_lost
                            }))
                            if (rendererError != TerminalRendererError.OUTPUT_CONTINUITY) {
                                OutlinedButton(onClick = { showRecoveryDialog = true }) {
                                    Text(stringResource(R.string.terminal_recreate_view))
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().imePadding(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                key(renderer) {
                    TerminalKeyBar(
                        enabled = inputEnabled,
                        historyEnabled = rendererError == null,
                        onKey = { renderer.sendSpecialKey(it) },
                        onLatest = { renderer.scrollToLatest() },
                        onPopup = { keyMenuOpen = it }
                    )
                }
            }

        }

        if (showRecoveryDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showRecoveryDialog = false },
                title = { Text(stringResource(R.string.terminal_recreate_title)) },
                text = { Text(stringResource(R.string.terminal_recreate_warning)) },
                confirmButton = {
                    TextButton(onClick = {
                        showRecoveryDialog = false
                        if (viewModel.resetRenderer(renderer)) rendererVersion++
                    }) { Text(stringResource(R.string.terminal_recreate_view)) }
                },
                dismissButton = {
                    TextButton(onClick = { showRecoveryDialog = false }) {
                        Text(stringResource(R.string.terminal_recovery_cancel))
                    }
                }
            )
        }

        if (showCopyDialog) {
            TerminalCopyDialog(copySnapshot, copyLoading, onDismiss = { showCopyDialog = false })
        }

        if (showPasteDialog) {
            TerminalPasteDialog(
                text = pasteDraft?.text, issue = pasteDraft?.issue,
                enabled = inputEnabled,
                attempted = pasteAttempted, busy = pasteBusy, result = pasteResult,
                editing = pasteEditing,
                onEdit = { value -> if (!pasteAttempted) pasteDraft = editTerminalPaste(pasteDraft, value) },
                onConfirm = { if (!pasteAttempted) { pasteBusy = true; pasteAttempted = true } },
                onDismiss = {
                    showPasteDialog = false
                    pasteDraft = null
                    pasteAttempted = false
                    pasteResult = null
                }
            )
        }

        // Command Input Dialog
        if (showCommandDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showCommandDialog = false }
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(stringResource(R.string.send_command_to_terminal), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = commandInput,
                            onValueChange = { commandInput = it },
                            label = { Text(stringResource(R.string.command_placeholder)) },
                            maxLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp, max = 90.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text ?: ""
                                    if (clip.isNotEmpty()) {
                                        commandInput = clip
                                        Toast.makeText(context, "Pegado de Android", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "El portapapeles de Android está vacío", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text(stringResource(R.string.paste_from_android), fontSize = 11.sp, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { commandInput = "" },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text(stringResource(R.string.clear), fontSize = 11.sp, maxLines = 1)
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        Text(stringResource(R.string.control_keyboard_shortcut), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Ctrl +", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                            Box {
                                OutlinedButton(
                                    onClick = { showCtrlDropdown = true },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(text = "$selectedCtrlChar  ▼", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }

                                DropdownMenu(
                                    expanded = showCtrlDropdown,
                                    onDismissRequest = { showCtrlDropdown = false }
                                ) {
                                    ctrlLetters.forEach { char ->
                                        DropdownMenuItem(
                                            text = { Text("Ctrl + $char", fontWeight = FontWeight.Medium) },
                                            onClick = {
                                                selectedCtrlChar = char
                                                showCtrlDropdown = false
                                            }
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    renderer.session.sendControlKey(selectedCtrlChar)
                                    Toast.makeText(context, "Enviado Ctrl + $selectedCtrlChar", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            ) {
                                Text(stringResource(R.string.send_ctrl_x, selectedCtrlChar), fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(stringResource(R.string.terminal_quick_keys), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = true,
                                onClick = { renderer.session.sendInput("\t") },
                                label = { Text("Tab ⇥", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = true,
                                onClick = { renderer.session.sendInput("\u001b") },
                                label = { Text("Esc", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = true,
                                onClick = { renderer.session.sendInput("\u001b[A") },
                                label = { Text(stringResource(R.string.up_arrow), fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = true,
                                onClick = { renderer.session.sendInput("\u001b[B") },
                                label = { Text(stringResource(R.string.down_arrow), fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = true,
                                onClick = { commandInput = "sudo $commandInput" },
                                label = { Text("+ sudo", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = true,
                                onClick = { commandInput = "$commandInput | grep " },
                                label = { Text("| grep", fontSize = 11.sp) }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { showCommandDialog = false },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(stringResource(R.string.close))
                            }
                            Button(
                                onClick = {
                                    if (commandInput.isNotEmpty()) {
                                        renderer.session.sendInput(commandInput + "\r")
                                        commandInput = ""
                                    }
                                    showCommandDialog = false
                                },
                                modifier = Modifier.height(38.dp)
                            ) {
                                Text(stringResource(R.string.send) + " ↵", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
