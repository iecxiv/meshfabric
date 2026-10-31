package iecxiv.meshfabric.ui.desktop

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import iecxiv.meshfabric.R
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import iecxiv.meshfabric.data.remote.DesktopEncoding
import iecxiv.meshfabric.data.remote.FrameRateOption

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RemoteDesktopScreen(
    nodeId: String,
    nodeName: String,
    viewModel: RemoteDesktopViewModel,
    onBack: () -> Unit
) {
    val isConnected by viewModel.isConnected(nodeId).collectAsState()
    val desktopBitmap by viewModel.desktopBitmap(nodeId).collectAsState()
    val frameVersion by viewModel.frameVersion(nodeId).collectAsState()
    val drawDiagnostics = remember(nodeId) { longArrayOf(0L, 0L) }
    val settings by viewModel.settings(nodeId).collectAsState()
    val remoteClipboardText by viewModel.remoteClipboardText(nodeId).collectAsState()

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    var selectedMode by remember { mutableStateOf(MouseMode.LEFT_CLICK) }
    var isControlsBarCollapsed by rememberSaveable {
        mutableStateOf(viewModel.isDesktopBarCollapsed())
    }
    var isFullScreen by rememberSaveable { mutableStateOf(false) }
    var barRelativeY by rememberSaveable { mutableFloatStateOf(0f) }

    var isSoftKeyboardOpen by remember { mutableStateOf(false) }
    var softKeyboardTextFieldValue by remember { mutableStateOf(TextFieldValue("")) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    var showKeyboardDialog by remember { mutableStateOf(false) }
    var showClipboardDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var textInput by remember { mutableStateOf("") }
    var sendClipboardInput by remember { mutableStateOf("") }

    val density = LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val isImeVisible = WindowInsets.isImeVisible

    val desktopKeyboard = remember(nodeId, viewModel) {
        DesktopKeyboardRouter { key, down -> viewModel.sendDesktopKeyEvent(nodeId, key, down) }
    }
    val desktopLifecycle by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val desktopKeyboardActive = isConnected && !showKeyboardDialog && !showClipboardDialog && !showSettingsDialog &&
        desktopLifecycle.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    LaunchedEffect(desktopKeyboard, desktopKeyboardActive) {
        desktopKeyboard.setActive(desktopKeyboardActive)
        if (desktopKeyboardActive) focusRequester.requestFocus()
    }
    DisposableEffect(desktopKeyboard) { onDispose { desktopKeyboard.setActive(false) } }

    LaunchedEffect(isImeVisible) {
        if (!isImeVisible && isSoftKeyboardOpen) {
            isSoftKeyboardOpen = false
        }
    }

    LaunchedEffect(isSoftKeyboardOpen) {
        if (isSoftKeyboardOpen) {
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    BackHandler(enabled = isFullScreen) {
        isFullScreen = false
    }

    val view = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(isFullScreen) {
        val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
        if (isFullScreen) {
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(nodeId) {
        viewModel.startSession(nodeId)
        onDispose {
            viewModel.pauseSession(nodeId)
        }
    }

    LaunchedEffect(remoteClipboardText, settings.autoClipboard) {
        if (settings.autoClipboard && remoteClipboardText.isNotEmpty()) {
            clipboardManager.setText(AnnotatedString(remoteClipboardText))
            Toast.makeText(context, "Portapapeles copiado automáticamente a Android", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(showClipboardDialog) {
        if (showClipboardDialog) {
            viewModel.requestRemoteClipboard(nodeId)
        }
    }

    Scaffold(
        topBar = {
            if (!isFullScreen) {
                TopAppBar(
                    title = {
                        Column {
                            Text(text = stringResource(R.string.desktop_title, nodeName), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                text = if (isConnected) stringResource(R.string.status_connected) else stringResource(R.string.status_connecting),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isConnected) Color.Green else Color.Yellow
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showKeyboardDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Keyboard, contentDescription = "Teclado y Atajos")
                        }
                        IconButton(
                            onClick = { showClipboardDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Portapapeles")
                        }
                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Opciones")
                        }
                        IconButton(
                            onClick = { viewModel.refreshDisplay(nodeId) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refrescar")
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
                                contentDescription = "Desconectar Escritorio",
                                tint = Color.Red
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isFullScreen) PaddingValues(0.dp) else innerPadding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                enabled = desktopKeyboardActive,
                readOnly = !isSoftKeyboardOpen,
                value = softKeyboardTextFieldValue,
                onValueChange = { newValue ->
                    val oldText = softKeyboardTextFieldValue.text
                    val newText = newValue.text

                    if (newText.length > oldText.length) {
                        val addedText = newText.substring(oldText.length)
                        viewModel.sendUnicodeText(nodeId, addedText)
                    } else if (newText.length < oldText.length) {
                        val deleteCount = oldText.length - newText.length
                        repeat(deleteCount) {
                            viewModel.sendSingleKey(nodeId, 8)
                        }
                    }

                    softKeyboardTextFieldValue = TextFieldValue(
                        text = newText,
                        selection = TextRange(newText.length)
                    )
                },
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0.01f)
                    .desktopKeyEvents(desktopKeyboard)
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    showKeyboardOnFocus = false,
                    autoCorrectEnabled = false
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        viewModel.sendSingleKey(nodeId, 13)
                    },
                    onDone = {
                        viewModel.sendSingleKey(nodeId, 13)
                    }
                )
            )
            val maxContainerHeightPx = constraints.maxHeight.toFloat()
            val maxUpwardOffsetPx = -(maxContainerHeightPx - with(density) { 80.dp.toPx() }).coerceAtLeast(0f)
            val currentOffsetY = if (maxUpwardOffsetPx < 0f) barRelativeY * maxUpwardOffsetPx else 0f
            val bitmap = desktopBitmap
            if (bitmap != null) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    val imgWidth = bitmap.width.toFloat()
                    val imgHeight = bitmap.height.toFloat()
                    val containerWidth = constraints.maxWidth.toFloat()
                    val containerHeight = constraints.maxHeight.toFloat()

                    val scaleFit = minOf(containerWidth / imgWidth, containerHeight / imgHeight)
                    val drawWidth = imgWidth * scaleFit
                    val drawHeight = imgHeight * scaleFit

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(nodeId, selectedMode, containerWidth, containerHeight, drawWidth, drawHeight) {
                                if (showClipboardDialog || showKeyboardDialog || showSettingsDialog) return@pointerInput
                                val touchSlop = viewConfiguration.touchSlop
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val startPos = down.position
                                    val startTime = System.currentTimeMillis()
                                    var isMoved = false
                                    var isLongPressed = false
                                    var dragAmountY = 0f

                                    do {
                                        val event = awaitPointerEvent()
                                        val pointerCount = event.changes.count { it.pressed }

                                        if (pointerCount == 1) {
                                            val change = event.changes.firstOrNull { it.pressed } ?: event.changes[0]
                                            val diff = change.position - startPos
                                            if (diff.getDistance() > touchSlop) {
                                                isMoved = true
                                            }

                                            if (!isMoved && !isLongPressed && (System.currentTimeMillis() - startTime) > 400) {
                                                isLongPressed = true
                                                change.consume()
                                                val localX = (change.position.x - containerWidth / 2f - offsetX) / scale + drawWidth / 2f
                                                val localY = (change.position.y - containerHeight / 2f - offsetY) / scale + drawHeight / 2f
                                                val xPct = (localX / drawWidth).coerceIn(0f, 1f)
                                                val yPct = (localY / drawHeight).coerceIn(0f, 1f)
                                                viewModel.performMouseClick(nodeId, xPct, yPct, isRightClick = true)
                                            }

                                            if (isMoved) {
                                                val panChange = change.positionChange()
                                                if (selectedMode == MouseMode.SCROLL) {
                                                    dragAmountY += panChange.y
                                                    if (kotlin.math.abs(dragAmountY) >= touchSlop) {
                                                        val localX = (change.position.x - containerWidth / 2f - offsetX) / scale + drawWidth / 2f
                                                        val localY = (change.position.y - containerHeight / 2f - offsetY) / scale + drawHeight / 2f
                                                        val xPct = (localX / drawWidth).coerceIn(0f, 1f)
                                                        val yPct = (localY / drawHeight).coerceIn(0f, 1f)
                                                        val (resWidth, resHeight) = viewModel.screenResolution(nodeId).value
                                                        val remoteX = (xPct * resWidth).toInt().coerceIn(0, resWidth)
                                                        val remoteY = (yPct * resHeight).toInt().coerceIn(0, resHeight)
                                                        val s = settings

                                                        val direction = if (dragAmountY < 0) -1 else 1
                                                        val factor = if (s.reverseMouseWheel) -1 else 1
                                                        val delta = 120 * direction * factor

                                                        viewModel.sendMouseScroll(nodeId, remoteX, remoteY, delta)
                                                        dragAmountY = 0f
                                                    }
                                                    change.consume()
                                                } else if (scale > 1f) {
                                                    val maxOffsetX = (drawWidth * (scale - 1f) / 2f).coerceAtLeast(0f)
                                                    val maxOffsetY = (drawHeight * (scale - 1f) / 2f).coerceAtLeast(0f)
                                                    offsetX = (offsetX + panChange.x).coerceIn(-maxOffsetX, maxOffsetX)
                                                    offsetY = (offsetY + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                                                    change.consume()
                                                }
                                            }
                                        } else if (pointerCount >= 2) {
                                            isMoved = true
                                            val zoom = event.calculateZoom()
                                            val pan = event.calculatePan()
                                            scale = (scale * zoom).coerceIn(1f, 6f)
                                            val maxOffsetX = (drawWidth * (scale - 1f) / 2f).coerceAtLeast(0f)
                                            val maxOffsetY = (drawHeight * (scale - 1f) / 2f).coerceAtLeast(0f)
                                            offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                            offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                            event.changes.forEach { if (it.pressed) it.consume() }
                                        }
                                    } while (event.changes.any { it.pressed })

                                    if (!isMoved && !isLongPressed) {
                                        val localX = (startPos.x - containerWidth / 2f - offsetX) / scale + drawWidth / 2f
                                        val localY = (startPos.y - containerHeight / 2f - offsetY) / scale + drawHeight / 2f
                                        val xPct = (localX / drawWidth).coerceIn(0f, 1f)
                                        val yPct = (localY / drawHeight).coerceIn(0f, 1f)

                                        when (selectedMode) {
                                            MouseMode.RIGHT_CLICK -> {
                                                viewModel.performMouseClick(nodeId, xPct, yPct, isRightClick = true)
                                            }
                                            MouseMode.MIDDLE_CLICK -> {
                                                viewModel.performMiddleClick(nodeId, xPct, yPct)
                                            }
                                            else -> {
                                                viewModel.performMouseClick(nodeId, xPct, yPct, isRightClick = false)
                                            }
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(
                                    width = (drawWidth / density.density).dp,
                                    height = (drawHeight / density.density).dp
                                )
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                )
                        ) {
                            val currentFrameVersion = frameVersion
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                if (currentFrameVersion >= 0) {
                                    drawIntoCanvas { canvas ->
                                        canvas.nativeCanvas.drawBitmap(
                                            bitmap,
                                            null,
                                            android.graphics.RectF(0f, 0f, size.width, size.height),
                                            null
                                        )
                                        drawDiagnostics[0]++
                                        val diagnosticNow = android.os.SystemClock.elapsedRealtime()
                                        if (diagnosticNow - drawDiagnostics[1] >= 1000L) {
                                            drawDiagnostics[1] = diagnosticNow
                                            android.util.Log.d("MeshDesktopDraw", "draws=${drawDiagnostics[0]} version=$currentFrameVersion bitmap=${bitmap.width}x${bitmap.height}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Cargando escritorio remoto...",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Floating Controls Bar Overlay (Collapsible & Vertically Draggable)
            if (isControlsBarCollapsed) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .offset { IntOffset(0, currentOffsetY.roundToInt()) }
                        .pointerInput(maxUpwardOffsetPx) {
                            detectVerticalDragGestures { change, dragAmount ->
                                change.consume()
                                if (maxUpwardOffsetPx < 0f) {
                                    val currentPx = barRelativeY * maxUpwardOffsetPx
                                    val newPx = (currentPx + dragAmount).coerceIn(maxUpwardOffsetPx, 0f)
                                    barRelativeY = newPx / maxUpwardOffsetPx
                                }
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                isControlsBarCollapsed = false
                                viewModel.setDesktopBarCollapsed(false)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Desplegar controles",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (isFullScreen) {
                            IconButton(
                                onClick = { isFullScreen = false },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FullscreenExit,
                                    contentDescription = "Salir de pantalla completa",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp)
                        .offset { IntOffset(0, currentOffsetY.roundToInt()) }
                        .pointerInput(maxUpwardOffsetPx) {
                            detectVerticalDragGestures { change, dragAmount ->
                                change.consume()
                                if (maxUpwardOffsetPx < 0f) {
                                    val currentPx = barRelativeY * maxUpwardOffsetPx
                                    val newPx = (currentPx + dragAmount).coerceIn(maxUpwardOffsetPx, 0f)
                                    barRelativeY = newPx / maxUpwardOffsetPx
                                }
                            }
                        },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            IconButton(
                                onClick = {
                                    isControlsBarCollapsed = true
                                    viewModel.setDesktopBarCollapsed(true)
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Plegar controles",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        FilterChip(
                            selected = selectedMode == MouseMode.LEFT_CLICK,
                            onClick = { selectedMode = MouseMode.LEFT_CLICK },
                            label = { Text(stringResource(R.string.left_click), fontSize = 10.sp) },
                            leadingIcon = { Icon(Icons.Default.Mouse, contentDescription = null, modifier = Modifier.size(13.dp)) },
                            modifier = Modifier.height(32.dp)
                        )
                        FilterChip(
                            selected = selectedMode == MouseMode.RIGHT_CLICK,
                            onClick = { selectedMode = MouseMode.RIGHT_CLICK },
                            label = { Text(stringResource(R.string.right_click), fontSize = 10.sp) },
                            modifier = Modifier.height(32.dp)
                        )
                        FilterChip(
                            selected = selectedMode == MouseMode.SCROLL,
                            onClick = { selectedMode = MouseMode.SCROLL },
                            label = { Text(stringResource(R.string.scroll), fontSize = 10.sp) },
                            modifier = Modifier.height(32.dp)
                        )
                        IconButton(
                            onClick = {
                                isSoftKeyboardOpen = !isSoftKeyboardOpen
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = "Teclado en pantalla",
                                tint = if (isImeVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.FitScreen, contentDescription = stringResource(R.string.fit))
                        }
                        IconButton(
                            onClick = {
                                isFullScreen = !isFullScreen
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (isFullScreen) "Salir de pantalla completa" else "Pantalla completa"
                            )
                        }
                    }
                }
            }
        }

        // Combined Keyboard & Hotkeys Dialog
        if (showKeyboardDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showKeyboardDialog = false }
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
                    ) {
                        Text(stringResource(R.string.keyboard_and_shortcuts), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(10.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            // 1. Enviar Texto Directo
                            Text(stringResource(R.string.send_direct_text), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                label = { Text(stringResource(R.string.text_to_send_placeholder)) },
                                maxLines = 3,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp, max = 90.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (textInput.isNotEmpty()) {
                                        viewModel.sendUnicodeText(nodeId, textInput)
                                        textInput = ""
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                            ) {
                                Text(stringResource(R.string.send_text), fontSize = 12.sp)
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            // 2. Acciones Rápidas
                            Text(stringResource(R.string.system_quick_actions), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(selected = true, onClick = { viewModel.sendKeyCombo(nodeId, 17, 16, 27) }, label = { Text("Ctrl+Shift+Esc", fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendKeyCombo(nodeId, 91, 82) }, label = { Text("Win+R", fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendKeyCombo(nodeId, 91, 69) }, label = { Text("Win+E", fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendKeyCombo(nodeId, 91, 68) }, label = { Text("Win+D", fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendKeyCombo(nodeId, 91, 76) }, label = { Text("Win+L", fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendKeyCombo(nodeId, 18, 9) }, label = { Text("Alt+Tab", fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendKeyCombo(nodeId, 18, 115) }, label = { Text("Alt+F4", fontSize = 11.sp) })
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(stringResource(R.string.editing_and_shortcuts), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(selected = true, onClick = { viewModel.sendKeyCombo(nodeId, 17, 65) }, label = { Text("Ctrl+A", fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendKeyCombo(nodeId, 17, 67) }, label = { Text("Ctrl+C", fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendKeyCombo(nodeId, 17, 86) }, label = { Text("Ctrl+V", fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendKeyCombo(nodeId, 17, 88) }, label = { Text("Ctrl+X", fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendKeyCombo(nodeId, 17, 90) }, label = { Text("Ctrl+Z", fontSize = 11.sp) })
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(stringResource(R.string.special_keys), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(selected = true, onClick = { viewModel.sendSingleKey(nodeId, 27) }, label = { Text("Esc", fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendSingleKey(nodeId, 9) }, label = { Text("Tab ⇥", fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendSingleKey(nodeId, 13) }, label = { Text("Enter ↵", fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendSingleKey(nodeId, 32) }, label = { Text(stringResource(R.string.space_key), fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendSingleKey(nodeId, 8) }, label = { Text(stringResource(R.string.backspace_key), fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendSingleKey(nodeId, 46) }, label = { Text(stringResource(R.string.delete_key), fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendSingleKey(nodeId, 91) }, label = { Text("Win Key", fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendSingleKey(nodeId, 44) }, label = { Text(stringResource(R.string.print_screen), fontSize = 11.sp) })
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(stringResource(R.string.f_keys), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (f in 1..12) {
                                    FilterChip(
                                        selected = true,
                                        onClick = { viewModel.sendSingleKey(nodeId, 111 + f) },
                                        label = { Text("F$f", fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { showKeyboardDialog = false },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(stringResource(R.string.close))
                            }
                        }
                    }
                }
            }
        }

        // Clipboard Dialog
        if (showClipboardDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showClipboardDialog = false }
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
                        Text(stringResource(R.string.clipboard), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(10.dp))

                        Text(stringResource(R.string.send_to_remote_clipboard), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = sendClipboardInput,
                            onValueChange = { sendClipboardInput = it },
                            label = { Text(stringResource(R.string.send_remote_clipboard_placeholder)) },
                            maxLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp, max = 90.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    android.util.Log.d("DesktopScreen", "Pegar de Android clicked")
                                    val clip = clipboardManager.getText()?.text ?: ""
                                    if (clip.isNotEmpty()) {
                                        sendClipboardInput = clip
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
                            Button(
                                onClick = {
                                    android.util.Log.d("DesktopScreen", "Enviar al Remoto clicked, input='$sendClipboardInput'")
                                    if (sendClipboardInput.isNotEmpty()) {
                                        viewModel.sendClipboardText(nodeId, sendClipboardInput)
                                        Toast.makeText(context, "Enviado al portapapeles remoto", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Ingresa texto para enviar", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text(stringResource(R.string.send_to_remote), fontSize = 11.sp, maxLines = 1)
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        Text(stringResource(R.string.get_from_remote_clipboard), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = remoteClipboardText,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.received_remote_clipboard_placeholder)) },
                            maxLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp, max = 90.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    android.util.Log.d("DesktopScreen", "Solicitar del Remoto clicked")
                                    viewModel.requestRemoteClipboard(nodeId)
                                    Toast.makeText(context, "Solicitando del remoto...", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text(stringResource(R.string.request_from_remote), fontSize = 11.sp, maxLines = 1)
                            }
                            Button(
                                onClick = {
                                    android.util.Log.d("DesktopScreen", "Copiar a Android clicked, text='$remoteClipboardText'")
                                    if (remoteClipboardText.isNotEmpty()) {
                                        clipboardManager.setText(AnnotatedString(remoteClipboardText))
                                        Toast.makeText(context, "Copiado a Android", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "El portapapeles remoto está vacío", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text(stringResource(R.string.copy_to_android), fontSize = 11.sp, maxLines = 1)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { showClipboardDialog = false },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(stringResource(R.string.close))
                            }
                        }
                    }
                }
            }
        }

        // Desktop Options / Settings Dialog
        if (showSettingsDialog) {
            val qualityList = listOf(100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 5, 1)
            val scaleList = listOf(
                1024 to "100%",
                896 to "87.5%",
                768 to "75%",
                640 to "62.5%",
                512 to "50%",
                384 to "37.5%",
                256 to "25%",
                128 to "12.5%"
            )

            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showSettingsDialog = false }
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
                    ) {
                        Text(stringResource(R.string.desktop_options), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(10.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            // Quality
                            Text(stringResource(R.string.quality), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                qualityList.forEach { q ->
                                    FilterChip(
                                        selected = settings.quality == q,
                                        onClick = {
                                            viewModel.updateSettings(nodeId, settings.copy(quality = q))
                                        },
                                        label = { Text("$q%", fontSize = 11.sp) }
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                            // Scale
                            Text(stringResource(R.string.scale), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                scaleList.forEach { (scaleVal, label) ->
                                    FilterChip(
                                        selected = settings.scale == scaleVal,
                                        onClick = {
                                            viewModel.updateSettings(nodeId, settings.copy(scale = scaleVal))
                                        },
                                        label = { Text(label, fontSize = 11.sp) }
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                            // Frame Rate
                            Text(stringResource(R.string.frame_rate), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                FrameRateOption.entries.forEach { fr ->
                                    FilterChip(
                                        selected = settings.frameRate == fr,
                                        onClick = {
                                            viewModel.updateSettings(nodeId, settings.copy(frameRate = fr))
                                        },
                                        label = { Text(stringResource(fr.labelRes), fontSize = 11.sp) }
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                            // Encoding
                            Text(stringResource(R.string.encoding), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                DesktopEncoding.entries.forEach { enc ->
                                    FilterChip(
                                        selected = settings.encoding == enc,
                                        onClick = {
                                            viewModel.updateSettings(nodeId, settings.copy(encoding = enc))
                                        },
                                        label = { Text(enc.label, fontSize = 11.sp) }
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                            // Other settings
                            Text(stringResource(R.string.other_settings), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.swap_mouse_buttons), fontSize = 12.sp)
                                Switch(
                                    checked = settings.swapMouseButtons,
                                    onCheckedChange = {
                                        viewModel.updateSettings(nodeId, settings.copy(swapMouseButtons = it))
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.reverse_mouse_wheel), fontSize = 12.sp)
                                Switch(
                                    checked = settings.reverseMouseWheel,
                                    onCheckedChange = {
                                        viewModel.updateSettings(nodeId, settings.copy(reverseMouseWheel = it))
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.use_remote_keyboard_map), fontSize = 12.sp)
                                Switch(
                                    checked = settings.useRemoteKeyboardMap,
                                    onCheckedChange = {
                                        viewModel.updateSettings(nodeId, settings.copy(useRemoteKeyboardMap = it))
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.auto_clipboard), fontSize = 12.sp)
                                Switch(
                                    checked = settings.autoClipboard,
                                    onCheckedChange = {
                                        viewModel.updateSettings(nodeId, settings.copy(autoClipboard = it))
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.lock_on_disconnect), fontSize = 12.sp)
                                Switch(
                                    checked = settings.lockOnDisconnect,
                                    onCheckedChange = {
                                        viewModel.updateSettings(nodeId, settings.copy(lockOnDisconnect = it))
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { showSettingsDialog = false },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(stringResource(R.string.close))
                            }
                        }
                    }
                }
            }
        }
    }
}
