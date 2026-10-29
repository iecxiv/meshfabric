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

class AndroidTerminalBridge(
    private val onInput: (String) -> Unit,
    private val onResize: (Int, Int) -> Unit,
    private val onReady: () -> Unit
) {
    @JavascriptInterface
    fun sendInput(data: String) {
        onInput(data)
    }

    @JavascriptInterface
    fun onTerminalResize(cols: Int, rows: Int) {
        onResize(cols, rows)
    }

    @JavascriptInterface
    fun onHtmlReady() {
        onReady()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TerminalScreen(
    nodeId: String,
    nodeName: String,
    viewModel: TerminalViewModel,
    onBack: () -> Unit
) {
    val connectionStatus by viewModel.connectionStatus(nodeId).collectAsState()
    val gson = remember { Gson() }
    val readyGate = remember(nodeId) { TerminalReadyGate() }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var currentFontSize by remember { mutableStateOf(11) }
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    var isHtmlReady by remember { mutableStateOf(false) }
    var isHistoryLoaded by remember { mutableStateOf(false) }
    var showCommandDialog by remember { mutableStateOf(false) }
    var commandInput by remember { mutableStateOf("") }
    var selectedCtrlChar by remember { mutableStateOf('C') }
    var showCtrlDropdown by remember { mutableStateOf(false) }
    val ctrlLetters = remember { ('A'..'Z').toList() }

    fun writeToTerminal(data: String) {
        if (data.isEmpty()) return
        val jsonStr = gson.toJson(data)
        activeWebView?.post {
            activeWebView?.evaluateJavascript("window.writeTerminalData($jsonStr);", null)
        }
    }

    fun focusTerminal() {
        activeWebView?.post {
            activeWebView?.requestFocus()
            activeWebView?.evaluateJavascript("window.focusTerminal();", null)
        }
    }

    fun loadHistoryOnce() {
        if (!isHistoryLoaded) {
            isHistoryLoaded = true
            isHtmlReady = true
            val fullHistory = viewModel.getFullHistory(nodeId)
            if (fullHistory.isNotEmpty()) {
                writeToTerminal(fullHistory)
            }
            focusTerminal()
        }
    }

    fun changeFontSize(delta: Int) {
        val newSize = (currentFontSize + delta).coerceIn(8, 20)
        currentFontSize = newSize
        activeWebView?.post {
            activeWebView?.evaluateJavascript("window.setFontSize($newSize);", null)
        }
    }


    fun clearTerminal() {
        activeWebView?.post {
            activeWebView?.evaluateJavascript("window.clearTerminal();", null)
        }
    }

    DisposableEffect(nodeId) {
        viewModel.startSession(nodeId)
        onDispose {
            readyGate.invalidate()
            // Keep terminal session alive on simple back navigation as requested in A1
            activeWebView?.stopLoading()
            activeWebView = null
        }
    }

    LaunchedEffect(isHtmlReady, nodeId) {
        if (isHtmlReady) {
            viewModel.incomingDataFlow(nodeId).collect { data ->
                writeToTerminal(data)
            }
        }
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
                        onClick = { showCommandDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Keyboard, contentDescription = "Enviar Comando")
                    }
                    IconButton(
                        onClick = {
                            viewModel.stopSession(nodeId)
                            onBack()
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(0xFF0D1117.toInt())
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = true
                            settings.allowContentAccess = true
                            settings.useWideViewPort = false
                            settings.loadWithOverviewMode = false
                            settings.setSupportZoom(false)

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    Log.d("TerminalScreen", "onPageFinished: $url")
                                    // Page loading is not terminal readiness; wait for onHtmlReady.
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                    Log.d("WebConsole", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                                    return true
                                }
                            }

                            val terminalWebView = this
                            val pageGeneration = readyGate.begin()
                            val bridge = AndroidTerminalBridge(
                                onInput = { inputData ->
                                    viewModel.sendInput(nodeId, inputData)
                                },
                                onResize = { cols, rows ->
                                    viewModel.sendResize(nodeId, cols, rows)
                                },
                                onReady = {
                                    Log.d("TerminalScreen", "onHtmlReady triggered from JS!")
                                    terminalWebView.post {
                                        if (activeWebView === terminalWebView && readyGate.accept(pageGeneration)) {
                                            loadHistoryOnce()
                                        }
                                    }
                                }
                            )
                            addJavascriptInterface(bridge, "AndroidTerminal")
                            loadUrl("file:///android_asset/terminal/terminal.html")
                            activeWebView = this
                        }
                    },
                    update = { view ->
                        activeWebView = view
                    }
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.sendInput(nodeId, "\r") },
                        label = { Text("Enter ↵", fontWeight = FontWeight.Bold) }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.sendInput(nodeId, "\b") },
                        label = { Text(stringResource(R.string.backspace_key)) }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.sendInput(nodeId, "\t") },
                        label = { Text("Tab") }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.sendControlKey(nodeId, 'C') },
                        label = { Text("Ctrl+C") }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.sendControlKey(nodeId, 'Z') },
                        label = { Text("Ctrl+Z") }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.sendControlKey(nodeId, 'X') },
                        label = { Text("Ctrl+X") }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.sendControlKey(nodeId, 'O') },
                        label = { Text("Ctrl+O") }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.sendControlKey(nodeId, 'W') },
                        label = { Text("Ctrl+W") }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.sendInput(nodeId, "\u001b") },
                        label = { Text("Esc") }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.sendInput(nodeId, "\u001b[A") },
                        label = { Text("↑") }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.sendInput(nodeId, "\u001b[B") },
                        label = { Text("↓") }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.sendInput(nodeId, "\u001b[D") },
                        label = { Text("←") }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.sendInput(nodeId, "\u001b[C") },
                        label = { Text("→") }
                    )
                }
            }
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
                                    viewModel.sendControlKey(nodeId, selectedCtrlChar)
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
                                onClick = { viewModel.sendInput(nodeId, "\t") },
                                label = { Text("Tab ⇥", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = true,
                                onClick = { viewModel.sendInput(nodeId, "\u001b") },
                                label = { Text("Esc", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = true,
                                onClick = { viewModel.sendInput(nodeId, "\u001b[A") },
                                label = { Text(stringResource(R.string.up_arrow), fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = true,
                                onClick = { viewModel.sendInput(nodeId, "\u001b[B") },
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
                                        viewModel.sendInput(nodeId, commandInput + "\r")
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
