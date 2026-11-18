package iecxiv.meshfabric.ui.desktop

import android.content.ClipData
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import iecxiv.meshfabric.R
import iecxiv.meshfabric.data.remote.ClipboardTransferState
import iecxiv.meshfabric.data.remote.DesktopEncoding
import iecxiv.meshfabric.data.remote.FrameRateOption
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RemoteDesktopScreen(nodeId: String, nodeName: String, viewModel: RemoteDesktopViewModel, onBack: () -> Unit) {
    val isConnected by viewModel.isConnected(nodeId).collectAsState()
    val desktopBitmap by viewModel.desktopBitmap(nodeId).collectAsState()
    val frameVersion by viewModel.frameVersion(nodeId).collectAsState()
    val drawDiagnostics = remember(nodeId) { longArrayOf(0L, 0L) }
    val settings by viewModel.settings(nodeId).collectAsState()
    val desktopMouseCapture = remember(nodeId) { DesktopMouseCapture() }
    val rightClickAssist = rememberDesktopRightClickAssist(nodeId)
    val desktopDialogConfiguration = androidx.compose.ui.platform.LocalConfiguration.current
    val desktopDialogsLandscape = desktopDialogConfiguration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val desktopLandscapeDialogWidth = (desktopDialogConfiguration.screenWidthDp - 48).coerceIn(1, 600).dp
    val remoteClipboardText by viewModel.remoteClipboardText(nodeId).collectAsState()
    val clipboardReadState by viewModel.clipboardReadState(nodeId).collectAsState()
    val clipboardWriteState by viewModel.clipboardWriteState(nodeId).collectAsState()
    val viewport = rememberDesktopViewport(viewModel, nodeId, isConnected)
    var scale by viewport.scale
    var offsetX by viewport.offsetX
    var offsetY by viewport.offsetY
    var selectedMode by remember { mutableStateOf(MouseMode.LEFT_CLICK) }
    var isControlsBarCollapsed by rememberSaveable { mutableStateOf(viewModel.isDesktopBarCollapsed()) }
    var isFullScreen by rememberSaveable { mutableStateOf(false) }
    var barRelativeY by viewport.barRelativeY
    var controlsBarHeightPx by remember { mutableIntStateOf(0) }
    var isSoftKeyboardOpen by remember { mutableStateOf(false) }
    var softKeyboardTextFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var showKeyboardDialog by remember { mutableStateOf(false) }
    var showClipboardDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showGestureHelp by rememberSaveable { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    var sendClipboardInput by remember { mutableStateOf("") }
    val density = LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    val pastedFromAndroidText = stringResource(R.string.pasted_from_android)
    val androidClipboardEmptyText = stringResource(R.string.android_clipboard_empty)
    val copiedToAndroidText = stringResource(R.string.copied_to_android)
    val remoteClipboardEmptyText = stringResource(R.string.remote_clipboard_empty)
    val clipboardEpoch = remember(nodeId, showClipboardDialog) { Any() }
    val currentClipboardEpoch = rememberUpdatedState(clipboardEpoch)
    val isImeVisible = WindowInsets.isImeVisible
    val desktopKeyboard = remember(nodeId, viewModel) {
        DesktopKeyboardRouter { key, down -> viewModel.sendDesktopKeyEvent(nodeId, key, down) }
    }
    val desktopLifecycle by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val desktopKeyboardActive = isConnected && !showKeyboardDialog && !showClipboardDialog && !showSettingsDialog && !showGestureHelp &&
        desktopLifecycle.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    DesktopRightClickAssistBinding(rightClickAssist, desktopMouseCapture, desktopKeyboardActive)
    LaunchedEffect(desktopKeyboard, desktopKeyboardActive) {
        desktopKeyboard.setActive(desktopKeyboardActive)
        if (desktopKeyboardActive) focusRequester.requestFocus()
    }
    DisposableEffect(desktopKeyboard) { onDispose { desktopKeyboard.setActive(false) } }
    LaunchedEffect(nodeId, isConnected, desktopLifecycle) {
        if (!isConnected || !desktopLifecycle.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) viewModel.releaseDesktopKeys(nodeId)
    }
    LaunchedEffect(isImeVisible) {
        if (!isImeVisible && isSoftKeyboardOpen) isSoftKeyboardOpen = false
    }
    LaunchedEffect(isSoftKeyboardOpen) {
        if (isSoftKeyboardOpen) {
            focusRequester.requestFocus()
            keyboardController?.show()
        } else keyboardController?.hide()
    }
    BackHandler(enabled = isFullScreen) { isFullScreen = false }
    val view = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(isFullScreen) {
        val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
        if (isFullScreen) {
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }
    DisposableEffect(nodeId) {
        viewModel.startSession(nodeId)
        onDispose { viewModel.pauseSession(nodeId) }
    }
    DesktopAutoClipboard(nodeId = nodeId, enabled = settings.autoClipboard, connected = isConnected, viewModel = viewModel)
    LaunchedEffect(showClipboardDialog) {
        if (showClipboardDialog) viewModel.requestRemoteClipboard(nodeId)
    }
    Scaffold(topBar = {
        if (!isFullScreen) {
            TopAppBar(
                title = {
                    Column {
                        Text(text = stringResource(R.string.desktop_title, nodeName), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(text = desktopConnectionStatusText(viewModel, nodeId, isConnected), style = MaterialTheme.typography.labelSmall, color = if (isConnected) Color.Green else Color.Yellow)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                actions = {
                    DesktopRightClickAssistHeader(rightClickAssist)
                    IconButton(onClick = { showKeyboardDialog = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Keyboard, contentDescription = stringResource(R.string.keyboard_and_shortcuts))
                    }
                    IconButton(onClick = { showClipboardDialog = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ContentPaste, contentDescription = stringResource(R.string.clipboard))
                    }
                    IconButton(onClick = { showSettingsDialog = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.options))
                    }
                    IconButton(onClick = { viewModel.refreshDisplay(nodeId) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                    IconButton(onClick = { viewModel.stopSession(nodeId); onBack() }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.disconnect_desktop), tint = Color.Red)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    }) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(if (isFullScreen) PaddingValues(0.dp) else innerPadding).background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                enabled = desktopKeyboardActive, readOnly = !isSoftKeyboardOpen, value = softKeyboardTextFieldValue,
                onValueChange = { newValue ->
                    val oldText = softKeyboardTextFieldValue.text
                    val newText = newValue.text
                    if (newText.length > oldText.length) {
                        val addedText = newText.substring(oldText.length)
                        viewModel.sendUnicodeText(nodeId, addedText)
                    } else if (newText.length < oldText.length) {
                        val deleteCount = oldText.length - newText.length
                        repeat(deleteCount) { viewModel.sendSingleKey(nodeId, 8) }
                    }
                    softKeyboardTextFieldValue = TextFieldValue(text = newText, selection = TextRange(newText.length))
                },
                modifier = Modifier.size(1.dp).alpha(0.01f).desktopKeyEvents(desktopKeyboard).focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, showKeyboardOnFocus = false, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onSend = { viewModel.sendSingleKey(nodeId, 13) }, onDone = { viewModel.sendSingleKey(nodeId, 13) })
            )
            val maxContainerHeightPx = constraints.maxHeight.toFloat()
            val maxUpwardOffsetPx = desktopControlsBarLimit(maxContainerHeightPx, controlsBarHeightPx, 0f)
            val bitmap = desktopBitmap
            if (bitmap != null) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    val imgWidth = bitmap.width.toFloat()
                    val imgHeight = bitmap.height.toFloat()
                    val containerWidth = constraints.maxWidth.toFloat()
                    val containerHeight = constraints.maxHeight.toFloat()
                    val scaleFit = minOf(containerWidth / imgWidth, containerHeight / imgHeight)
                    val drawWidth = imgWidth * scaleFit
                    val drawHeight = imgHeight * scaleFit
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .desktopRightClickCanvas(
                                capture = desktopMouseCapture, enabled = desktopKeyboardActive,
                                geometry = DesktopWheelGeometry(containerWidth, containerHeight, drawWidth, drawHeight, scale, offsetX, offsetY),
                                onRightClick = { point ->
                                    rightClickAssist.latch.cancel()
                                    val (remoteWidth, remoteHeight) = viewModel.screenResolution(nodeId).value
                                    if (remoteWidth <= 0 || remoteHeight <= 0) false else {
                                        viewModel.performMouseClick(nodeId, point.xFraction, point.yFraction, isRightClick = true)
                                        true
                                    }
                                }
                            )
                            .desktopVerticalWheel(
                                enabled = desktopKeyboardActive,
                                geometry = DesktopWheelGeometry(containerWidth, containerHeight, drawWidth, drawHeight, scale, offsetX, offsetY),
                                reverse = settings.reverseMouseWheel,
                                onWheel = { point, delta ->
                                    val (remoteWidth, remoteHeight) = viewModel.screenResolution(nodeId).value
                                    val position = point.remote(remoteWidth, remoteHeight)
                                    if (position == null) false else {
                                        viewModel.sendMouseScroll(nodeId, position.first, position.second, delta)
                                        true
                                    }
                                }
                            )
                            .pointerInput(nodeId, selectedMode, desktopKeyboardActive, containerWidth, containerHeight, drawWidth, drawHeight) {
                                if (!desktopKeyboardActive) return@pointerInput
                                val touchSlop = viewConfiguration.touchSlop
                                fun point(position: androidx.compose.ui.geometry.Offset): DesktopWheelPoint? =
                                    DesktopWheelGeometry(containerWidth, containerHeight, drawWidth, drawHeight, scale, offsetX, offsetY).project(position.x, position.y)
                                var lastTapUp = -1L
                                var lastTapPosition = androidx.compose.ui.geometry.Offset.Zero
                                val clickScope = kotlinx.coroutines.CoroutineScope(kotlin.coroutines.coroutineContext)
                                val tapSequence = createDesktopTapSequence(clickScope, viewModel, nodeId) { desktopMouseCapture.ready() }
                                try {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = true)
                                        if (down.type == androidx.compose.ui.input.pointer.PointerType.Mouse) {
                                            tapSequence.cancel()
                                            return@awaitEachGesture
                                        }
                                        val initial = point(down.position) ?: run {
                                            tapSequence.flush()
                                            return@awaitEachGesture
                                        }
                                        val gestureOrigin = tapSequence.currentBinding()
                                        val tapInterval = down.uptimeMillis - lastTapUp
                                        val doubleTapDrag = lastTapUp >= 0L &&
                                            tapInterval in viewConfiguration.doubleTapMinTimeMillis..viewConfiguration.doubleTapTimeoutMillis &&
                                            (down.position - lastTapPosition).getDistance() <= touchSlop * 2f &&
                                            selectedMode == MouseMode.LEFT_CLICK && !rightClickAssist.latch.armed
                                        if (doubleTapDrag) tapSequence.reserveSecond() else tapSequence.flush()
                                        lastTapUp = -1L
                                        var tapUpTime = down.uptimeMillis
                                        val drag = DesktopTouchDragGesture(initial, touchSlop,
                                            press = {
                                                tapSequence.flush()
                                                desktopMouseCapture.ready() && desktopMouseCapture.primaryInput(it, DesktopPrimaryPhase.DOWN)
                                            },
                                            move = { desktopMouseCapture.ready() && desktopMouseCapture.primaryInput(it, DesktopPrimaryPhase.MOVE) },
                                            release = { desktopMouseCapture.primaryInput(null, DesktopPrimaryPhase.UP); Unit })
                                        var moved = false
                                        var transformed = false
                                        var cancelled = false
                                        var dragAmountY = 0f
                                        try {
                                            do {
                                                val event = awaitPointerEvent()
                                                if (!desktopMouseCapture.ready() || event.changes.any { it.isConsumed }) {
                                                    cancelled = true
                                                    break
                                                }
                                                val pointerCount = event.changes.count { it.pressed }
                                                if (pointerCount >= 2) {
                                                    tapSequence.flush()
                                                    rightClickAssist.latch.cancel()
                                                    drag.cancel()
                                                    transformed = true
                                                    moved = true
                                                    val zoom = event.calculateZoom()
                                                    val pan = event.calculatePan()
                                                    scale = (scale * zoom).coerceIn(1f, 6f)
                                                    val maxOffsetX = (drawWidth * (scale - 1f) / 2f).coerceAtLeast(0f)
                                                    val maxOffsetY = (drawHeight * (scale - 1f) / 2f).coerceAtLeast(0f)
                                                    offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                                    offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                                    event.changes.forEach { it.consume() }
                                                } else if (!transformed) {
                                                    val change = event.changes.firstOrNull { it.id == down.id }
                                                    if (change == null) {
                                                        cancelled = true
                                                        break
                                                    }
                                                    if (!change.pressed) tapUpTime = change.uptimeMillis
                                                    val distance = (change.position - down.position).getDistance()
                                                    if (distance > touchSlop) { moved = true; rightClickAssist.latch.cancel() }
                                                    when (selectedMode) {
                                                        MouseMode.LEFT_CLICK, MouseMode.DRAG_MOVE -> {
                                                            if (doubleTapDrag || selectedMode == MouseMode.DRAG_MOVE) {
                                                                drag.move(point(change.position), distance)
                                                                if (drag.isCancelled) {
                                                                    cancelled = true
                                                                    break
                                                                }
                                                            } else if (moved) {
                                                                val pan = change.positionChange()
                                                                val maxOffsetX = (drawWidth * (scale - 1f) / 2f).coerceAtLeast(0f)
                                                                val maxOffsetY = (drawHeight * (scale - 1f) / 2f).coerceAtLeast(0f)
                                                                offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                                                offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                                            }
                                                            if (moved) change.consume()
                                                        }
                                                        MouseMode.SCROLL -> if (moved) {
                                                            dragAmountY += change.positionChange().y
                                                            if (kotlin.math.abs(dragAmountY) >= touchSlop) {
                                                                val projected = point(change.position)
                                                                val (width, height) = viewModel.screenResolution(nodeId).value
                                                                val remote = projected?.remote(width, height)
                                                                if (remote != null) {
                                                                    val direction = if (dragAmountY < 0) -1 else 1
                                                                    val factor = if (settings.reverseMouseWheel) -1 else 1
                                                                    viewModel.sendMouseScroll(nodeId, remote.first, remote.second, 120 * direction * factor)
                                                                }
                                                                dragAmountY = 0f
                                                            }
                                                            change.consume()
                                                        }
                                                        else -> if (moved) change.consume()
                                                    }
                                                } else event.changes.forEach { it.consume() }
                                            } while (event.changes.any { it.pressed })
                                            val tap = drag.finish() && !moved && !transformed && !cancelled
                                            if (tap && desktopMouseCapture.ready()) {
                                                if (rightClickAssist.latch.consume()) {
                                                    viewModel.performMouseClick(nodeId, initial.xFraction, initial.yFraction, isRightClick = true)
                                                } else when (selectedMode) {
                                                    MouseMode.RIGHT_CLICK -> viewModel.performMouseClick(nodeId, initial.xFraction, initial.yFraction, isRightClick = true)
                                                    MouseMode.MIDDLE_CLICK -> viewModel.performMiddleClick(nodeId, initial.xFraction, initial.yFraction)
                                                    MouseMode.LEFT_CLICK -> {
                                                        val (width, height) = viewModel.screenResolution(nodeId).value
                                                        val remote = initial.remote(width, height)
                                                        if (remote != null && tapSequence.tap(
                                                            DesktopTapTarget(remote.first, remote.second, viewModel.settings(nodeId).value.swapMouseButtons),
                                                            viewConfiguration.doubleTapTimeoutMillis,
                                                            gestureOrigin
                                                        ) && !doubleTapDrag) {
                                                            lastTapUp = tapUpTime
                                                            lastTapPosition = down.position
                                                        }
                                                    }
                                                    else -> {
                                                        if (desktopMouseCapture.primaryInput(initial, DesktopPrimaryPhase.DOWN)) {
                                                            desktopMouseCapture.primaryInput(null, DesktopPrimaryPhase.UP)
                                                        }
                                                    }
                                                }
                                            }
                                        } finally {
                                            drag.cancel()
                                            if (doubleTapDrag && clickScope.coroutineContext[kotlinx.coroutines.Job]?.isActive == true) {
                                                tapSequence.flush()
                                            }
                                        }
                                    }
                                } finally {
                                    tapSequence.cancel()
                                    if (viewModel.desktopTapSequences[nodeId] === tapSequence) {
                                        viewModel.desktopTapSequences.remove(nodeId)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.size(width = (drawWidth / density.density).dp, height = (drawHeight / density.density).dp)
                            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)) {
                            val currentFrameVersion = frameVersion
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                if (currentFrameVersion >= 0) {
                                    drawIntoCanvas { canvas ->
                                        canvas.nativeCanvas.drawBitmap(bitmap, null, android.graphics.RectF(0f, 0f, size.width, size.height), null)
                                        if (iecxiv.meshfabric.diagnostics.AppDiagnostics.enabled) {
                                            drawDiagnostics[0]++
                                            val diagnosticNow = android.os.SystemClock.elapsedRealtime()
                                            if (diagnosticNow - drawDiagnostics[1] >= 1000L) {
                                                drawDiagnostics[1] = diagnosticNow
                                                iecxiv.meshfabric.diagnostics.AppDiagnostics.trace(3, "MeshDesktopDraw", "RemoteDesktopScreen.kt:473 draws=") { "draws=${drawDiagnostics[0]} version=$currentFrameVersion bitmap=${bitmap.width}x${bitmap.height}" }
                                            }
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
                    Text(text = stringResource(R.string.desktop_loading_remote), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            }
            DesktopRightAssistLayout(rightClickAssist, desktopMouseCapture, barRelativeY, isControlsBarCollapsed, desktopKeyboardActive) {
                if (isControlsBarCollapsed) {
                    Surface(
                        modifier = Modifier.onSizeChanged { controlsBarHeightPx = it.height }.desktopMouseLocalControls(desktopMouseCapture)
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
                        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f), shadowElevation = 6.dp
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(2.dp)) {
                            IconButton(onClick = { isControlsBarCollapsed = false; viewModel.setDesktopBarCollapsed(false) }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.expand_controls), tint = MaterialTheme.colorScheme.onSurface)
                            }
                            if (isFullScreen) {
                                IconButton(onClick = { isFullScreen = false }, modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.Default.FullscreenExit, contentDescription = stringResource(R.string.exit_fullscreen), tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth().onSizeChanged { controlsBarHeightPx = it.height }.desktopMouseLocalControls(desktopMouseCapture)
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
                        color = iecxiv.meshfabric.ui.theme.appCardBackground(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                        shadowElevation = 8.dp,
                        contentColor = contentColorFor(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                        tonalElevation = iecxiv.meshfabric.ui.theme.appCardTonalElevation(androidx.compose.ui.unit.Dp(0f))
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                                IconButton(onClick = { isControlsBarCollapsed = true; viewModel.setDesktopBarCollapsed(true) }, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.collapse_controls), modifier = Modifier.size(20.dp))
                                }
                            }
                            FilterChip(selected = selectedMode == MouseMode.LEFT_CLICK, onClick = { selectedMode = MouseMode.LEFT_CLICK },
                                label = { Text(stringResource(R.string.left_click), fontSize = 10.sp) },
                                leadingIcon = { Icon(Icons.Default.Mouse, contentDescription = null, modifier = Modifier.size(13.dp)) }, modifier = Modifier.height(32.dp))
                            FilterChip(selected = selectedMode == MouseMode.RIGHT_CLICK, onClick = { selectedMode = MouseMode.RIGHT_CLICK },
                                label = { Text(stringResource(R.string.right_click), fontSize = 10.sp) }, modifier = Modifier.height(32.dp))
                            FilterChip(selected = selectedMode == MouseMode.SCROLL, onClick = { selectedMode = MouseMode.SCROLL },
                                label = { Text(stringResource(R.string.scroll), fontSize = 10.sp) }, modifier = Modifier.height(32.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                IconButton(onClick = { showGestureHelp = true }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Info, contentDescription = stringResource(R.string.desktop_gestures_title), modifier = Modifier.size(18.dp), tint = Color(0xFF9E9E9E))
                                }
                                IconButton(onClick = { isSoftKeyboardOpen = !isSoftKeyboardOpen }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Keyboard, contentDescription = stringResource(R.string.on_screen_keyboard), tint = if (isImeVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                }
                                IconButton(onClick = { scale = 1f; offsetX = 0f; offsetY = 0f }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.FitScreen, contentDescription = stringResource(R.string.fit))
                                }
                                IconButton(onClick = { isFullScreen = !isFullScreen }, modifier = Modifier.size(32.dp)) {
                                    Icon(if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, contentDescription = if (isFullScreen) stringResource(R.string.exit_fullscreen) else stringResource(R.string.fullscreen))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showGestureHelp) DesktopGestureHelpDialog(onDismiss = { showGestureHelp = false })
        if (showKeyboardDialog) {
            androidx.compose.ui.window.Dialog(properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = !desktopDialogsLandscape), onDismissRequest = { showKeyboardDialog = false }) {
                iecxiv.meshfabric.ui.components.AppDialogSurface(
                    shape = iecxiv.meshfabric.ui.components.AppDialogAppearance.shape,
                    color = iecxiv.meshfabric.ui.components.AppDialogAppearance.containerColor(),
                    tonalElevation = iecxiv.meshfabric.ui.components.AppDialogAppearance.tonalElevation,
                    modifier = Modifier.then(if (desktopDialogsLandscape) Modifier.width(desktopLandscapeDialogWidth) else Modifier.fillMaxWidth()).padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(stringResource(R.string.keyboard_and_shortcuts), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(modifier = Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())) {
                            Text(stringResource(R.string.send_direct_text), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(value = textInput, onValueChange = { textInput = it }, label = { Text(stringResource(R.string.text_to_send_placeholder)) }, maxLines = 3, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp, max = 90.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { if (textInput.isNotEmpty()) { viewModel.sendUnicodeText(nodeId, textInput); textInput = "" } }, modifier = Modifier.fillMaxWidth().height(38.dp)) { Text(stringResource(R.string.send_text), fontSize = 12.sp) }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            Text(stringResource(R.string.system_quick_actions), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(selected = true, onClick = { viewModel.sendKeyCombo(nodeId, 17, 65) }, label = { Text("Ctrl+A", fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendKeyCombo(nodeId, 17, 67) }, label = { Text("Ctrl+C", fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendKeyCombo(nodeId, 17, 86) }, label = { Text("Ctrl+V", fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendKeyCombo(nodeId, 17, 88) }, label = { Text("Ctrl+X", fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendKeyCombo(nodeId, 17, 90) }, label = { Text("Ctrl+Z", fontSize = 11.sp) })
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(stringResource(R.string.special_keys), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(selected = true, onClick = { viewModel.sendSingleKey(nodeId, 27) }, label = { Text("Esc", fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendSingleKey(nodeId, 9) }, label = { Text("Tab ⇥", fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendSingleKey(nodeId, 13) }, label = { Text(stringResource(R.string.desktop_key_enter) + " ↵", fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendSingleKey(nodeId, 32) }, label = { Text(stringResource(R.string.space_key), fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendSingleKey(nodeId, 8) }, label = { Text(stringResource(R.string.backspace_key), fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendSingleKey(nodeId, 46) }, label = { Text(stringResource(R.string.delete_key), fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendSingleKey(nodeId, 91) }, label = { Text(stringResource(R.string.desktop_win_key), fontSize = 11.sp) })
                                FilterChip(selected = true, onClick = { viewModel.sendSingleKey(nodeId, 44) }, label = { Text(stringResource(R.string.print_screen), fontSize = 11.sp) })
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(stringResource(R.string.f_keys), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (f in 1..12) FilterChip(selected = true, onClick = { viewModel.sendSingleKey(nodeId, 111 + f) }, label = { Text("F$f", fontSize = 11.sp) })
                            }
                            DesktopShortcutSections(nodeId, viewModel, isConnected)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showKeyboardDialog = false }, modifier = Modifier.height(36.dp)) { Text(stringResource(R.string.close)) }
                        }
                    }
                }
            }
        }
        if (showClipboardDialog) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showClipboardDialog = false }) {
                iecxiv.meshfabric.ui.components.AppDialogSurface(
                    shape = iecxiv.meshfabric.ui.components.AppDialogAppearance.shape,
                    color = iecxiv.meshfabric.ui.components.AppDialogAppearance.containerColor(),
                    tonalElevation = iecxiv.meshfabric.ui.components.AppDialogAppearance.tonalElevation,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState())) {
                        Text(stringResource(R.string.clipboard), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(stringResource(R.string.send_to_remote_clipboard), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        DesktopClipboardStatus(clipboardWriteState)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(value = sendClipboardInput, onValueChange = { sendClipboardInput = it }, label = { Text(stringResource(R.string.send_remote_clipboard_placeholder)) }, maxLines = 3, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp, max = 90.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    iecxiv.meshfabric.diagnostics.AppDiagnostics.trace(3, "DesktopScreen", "RemoteDesktopScreen.kt:828 Pegar de Android clicked") { "Pegar de Android clicked" }
                                    val draft = sendClipboardInput
                                    clipboardScope.launch {
                                        if (currentClipboardEpoch.value !== clipboardEpoch) return@launch
                                        val data = clipboard.getClipEntry()?.clipData
                                        val clip = data?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString().orEmpty()
                                        if (currentClipboardEpoch.value !== clipboardEpoch || sendClipboardInput != draft) return@launch
                                        if (clip.isNotEmpty()) {
                                            sendClipboardInput = clip
                                            Toast.makeText(context, pastedFromAndroidText, Toast.LENGTH_SHORT).show()
                                        } else Toast.makeText(context, androidClipboardEmptyText, Toast.LENGTH_SHORT).show()
                                    }
                                }, modifier = Modifier.weight(1f).height(38.dp), contentPadding = PaddingValues(horizontal = 4.dp)
                            ) { Text(stringResource(R.string.paste_from_android), fontSize = 11.sp, maxLines = 1) }
                            Button(enabled = isConnected && clipboardWriteState != ClipboardTransferState.WAITING, onClick = { viewModel.sendClipboardText(nodeId, sendClipboardInput) }, modifier = Modifier.weight(1f).height(38.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                                Text(stringResource(R.string.send_to_remote), fontSize = 11.sp, maxLines = 1)
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        Text(stringResource(R.string.get_from_remote_clipboard), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        DesktopClipboardStatus(clipboardReadState)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(value = remoteClipboardText, onValueChange = {}, readOnly = true, label = { Text(stringResource(R.string.received_remote_clipboard_placeholder)) }, maxLines = 3, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp, max = 90.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(enabled = isConnected && clipboardReadState != ClipboardTransferState.WAITING,
                                onClick = {
                                    iecxiv.meshfabric.diagnostics.AppDiagnostics.trace(3, "DesktopScreen", "RemoteDesktopScreen.kt:884 Solicitar del Remoto clicked") { "Solicitar del Remoto clicked" }
                                    viewModel.requestRemoteClipboard(nodeId)
                                }, modifier = Modifier.weight(1f).height(38.dp), contentPadding = PaddingValues(horizontal = 4.dp)
                            ) { Text(stringResource(R.string.request_from_remote), fontSize = 11.sp, maxLines = 1) }
                            Button(onClick = {
                                if (clipboardReadState == ClipboardTransferState.RECEIVED) {
                                    clipboardScope.launch {
                                        if (currentClipboardEpoch.value !== clipboardEpoch) return@launch
                                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("plain text", remoteClipboardText)))
                                        if (currentClipboardEpoch.value === clipboardEpoch) Toast.makeText(context, copiedToAndroidText, Toast.LENGTH_SHORT).show()
                                    }
                                } else Toast.makeText(context, remoteClipboardEmptyText, Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.weight(1f).height(38.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Text(stringResource(R.string.copy_to_android), fontSize = 11.sp, maxLines = 1) }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showClipboardDialog = false }, modifier = Modifier.height(36.dp)) { Text(stringResource(R.string.close)) }
                        }
                    }
                }
            }
        }
        if (showSettingsDialog) {
            val qualityList = listOf(100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 5, 1)
            val scaleList = listOf(1024 to "100%", 896 to "87.5%", 768 to "75%", 640 to "62.5%", 512 to "50%", 384 to "37.5%", 256 to "25%", 128 to "12.5%")
            androidx.compose.ui.window.Dialog(properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = !desktopDialogsLandscape), onDismissRequest = { showSettingsDialog = false }) {
                iecxiv.meshfabric.ui.components.AppDialogSurface(
                    shape = iecxiv.meshfabric.ui.components.AppDialogAppearance.shape,
                    color = iecxiv.meshfabric.ui.components.AppDialogAppearance.containerColor(),
                    tonalElevation = iecxiv.meshfabric.ui.components.AppDialogAppearance.tonalElevation,
                    modifier = Modifier.then(if (desktopDialogsLandscape) Modifier.width(desktopLandscapeDialogWidth) else Modifier.fillMaxWidth()).padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(stringResource(R.string.desktop_options), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(modifier = Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())) {
                            Text(stringResource(R.string.quality), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                qualityList.forEach { q -> FilterChip(selected = settings.quality == q, onClick = { viewModel.updateSettings(nodeId, settings.copy(quality = q)) }, label = { Text("$q%", fontSize = 11.sp) }) }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                            Text(stringResource(R.string.scale), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                scaleList.forEach { (scaleVal, label) -> FilterChip(selected = settings.scale == scaleVal, onClick = { viewModel.updateSettings(nodeId, settings.copy(scale = scaleVal)) }, label = { Text(label, fontSize = 11.sp) }) }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                            Text(stringResource(R.string.frame_rate), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                FrameRateOption.entries.forEach { fr -> FilterChip(selected = settings.frameRate == fr, onClick = { viewModel.updateSettings(nodeId, settings.copy(frameRate = fr)) }, label = { Text(stringResource(fr.labelRes), fontSize = 11.sp) }) }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                            Text(stringResource(R.string.encoding), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                DesktopEncoding.entries.forEach { enc -> FilterChip(selected = settings.encoding == enc, onClick = { viewModel.updateSettings(nodeId, settings.copy(encoding = enc)) }, label = { Text(enc.label, fontSize = 11.sp) }) }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                            Text(stringResource(R.string.other_settings), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.swap_mouse_buttons), fontSize = 12.sp)
                                Switch(checked = settings.swapMouseButtons, onCheckedChange = { viewModel.updateSettings(nodeId, settings.copy(swapMouseButtons = it)) })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.reverse_mouse_wheel), fontSize = 12.sp)
                                Switch(checked = settings.reverseMouseWheel, onCheckedChange = { viewModel.updateSettings(nodeId, settings.copy(reverseMouseWheel = it)) })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.use_remote_keyboard_map), fontSize = 12.sp)
                                Switch(checked = settings.useRemoteKeyboardMap, onCheckedChange = { viewModel.updateSettings(nodeId, settings.copy(useRemoteKeyboardMap = it)) })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.auto_clipboard), fontSize = 12.sp)
                                Switch(checked = settings.autoClipboard, onCheckedChange = { viewModel.updateSettings(nodeId, settings.copy(autoClipboard = it)) })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.lock_on_disconnect), fontSize = 12.sp)
                                Switch(checked = settings.lockOnDisconnect, onCheckedChange = { viewModel.updateSettings(nodeId, settings.copy(lockOnDisconnect = it)) })
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showSettingsDialog = false }, modifier = Modifier.height(36.dp)) { Text(stringResource(R.string.close)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopClipboardStatus(state: ClipboardTransferState) {
    val message = when (state) {
        ClipboardTransferState.IDLE -> return
        ClipboardTransferState.WAITING -> R.string.desktop_clipboard_waiting
        ClipboardTransferState.RECEIVED -> R.string.desktop_clipboard_received
        ClipboardTransferState.CONFIRMED -> R.string.desktop_clipboard_confirmed
        ClipboardTransferState.FAILED -> R.string.desktop_clipboard_failed
        ClipboardTransferState.TIMED_OUT -> R.string.desktop_clipboard_timeout
    }
    Text(stringResource(message), style = MaterialTheme.typography.bodySmall)
}
