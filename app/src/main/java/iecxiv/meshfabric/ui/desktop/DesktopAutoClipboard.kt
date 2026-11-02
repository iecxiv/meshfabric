package iecxiv.meshfabric.ui.desktop

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import iecxiv.meshfabric.data.remote.ClipboardTransferState
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private data class LocalClipboardSnapshot(val text: String?, val canApplyRemote: Boolean)

private fun readLocalClipboard(manager: ClipboardManager): LocalClipboardSnapshot = try {
    val clip = manager.primaryClip
    if (clip == null) LocalClipboardSnapshot(null, true)
    else {
        val text = if (clip.itemCount == 1) clip.getItemAt(0).text?.toString() else null
        LocalClipboardSnapshot(text, text != null)
    }
} catch (_: SecurityException) { LocalClipboardSnapshot(null, false) }

@Composable
internal fun DesktopAutoClipboard(
    nodeId: String,
    enabled: Boolean,
    connected: Boolean,
    viewModel: RemoteDesktopViewModel
) {
    val context = LocalContext.current
    val view = LocalView.current
    val manager = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    val policy = remember(nodeId, viewModel) { DesktopClipboardAutoPolicy() }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    LaunchedEffect(nodeId, enabled, connected, resumed, manager, view, viewModel) {
        if (!enabled) { policy.reset(); return@LaunchedEffect }
        if (!connected || !resumed) return@LaunchedEffect
        val session = viewModel.desktopClient.getSession(viewModel.meshClient, nodeId)
        val signals = Channel<Unit>(Channel.CONFLATED)
        val localDirty = AtomicBoolean(true)
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            localDirty.set(true)
            signals.trySend(Unit)
        }
        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener {
            localDirty.set(true)
            signals.trySend(Unit)
        }
        val observer = view.viewTreeObserver
        var clipboardRegistered = false
        var focusRegistered = false
        var updates: Job? = null
        var remoteReady = false
        var wasFocused = false
        var localReadable = false
        var nextPollAt = 0L
        var previousWrite = session.clipboardWriteState.value
        try {
            manager.addPrimaryClipChangedListener(listener)
            clipboardRegistered = true
            if (observer.isAlive) {
                observer.addOnWindowFocusChangeListener(focusListener)
                focusRegistered = true
            }
            updates = launch {
                combine(session.remoteClipboardText, session.clipboardReadState, session.clipboardWriteState) {
                    _, _, _ -> Unit
                }.collect { signals.trySend(Unit) }
            }
            while (isActive) {
                if (view.hasWindowFocus()) {
                    if (!wasFocused) { localDirty.set(true); nextPollAt = 0L }
                    wasFocused = true
                    if (localDirty.getAndSet(false)) {
                        val snapshot = readLocalClipboard(manager)
                        localReadable = snapshot.canApplyRemote
                        if (snapshot.text != null) policy.observeLocal(snapshot.text)
                        else {
                            // No clip/unsupported data is not interpreted as a request to clear the remote.
                            policy.seedLocalIfUnknown("")
                            policy.discardPendingLocal()
                        }
                    }
                    val write = session.clipboardWriteState.value
                    if (previousWrite == ClipboardTransferState.WAITING &&
                        (write == ClipboardTransferState.FAILED || write == ClipboardTransferState.TIMED_OUT))
                        policy.writeFailed()
                    previousWrite = write
                    if (localReadable && write != ClipboardTransferState.WAITING) {
                        val value = policy.takeLocalToSend()
                        if (value != null) {
                            viewModel.sendClipboardText(nodeId, value)
                            previousWrite = session.clipboardWriteState.value
                            if (previousWrite == ClipboardTransferState.FAILED || previousWrite == ClipboardTransferState.TIMED_OUT)
                                policy.writeFailed()
                        }
                    }
                    if (remoteReady && localReadable && session.clipboardWriteState.value != ClipboardTransferState.WAITING &&
                        session.clipboardReadState.value == ClipboardTransferState.RECEIVED) {
                        val value = policy.observeRemote(session.remoteClipboardText.value)
                        if (value != null) {
                            try { manager.setPrimaryClip(ClipData.newPlainText("", value)) }
                            catch (_: SecurityException) { /* Device policy may reject clipboard writes. */ }
                        }
                    }
                    val now = SystemClock.elapsedRealtime()
                    val read = session.clipboardReadState.value
                    if (now >= nextPollAt && read != ClipboardTransferState.WAITING &&
                        session.clipboardWriteState.value != ClipboardTransferState.WAITING) {
                        remoteReady = true
                        viewModel.requestRemoteClipboard(nodeId)
                        nextPollAt = now + if (read == ClipboardTransferState.FAILED || read == ClipboardTransferState.TIMED_OUT)
                            10_000L else 2_000L
                    }
                } else {
                    wasFocused = false
                    remoteReady = false
                    localDirty.set(true)
                }
                withTimeoutOrNull(1_000L) { signals.receive() }
            }
        } finally {
            updates?.cancel()
            if (clipboardRegistered) manager.removePrimaryClipChangedListener(listener)
            if (focusRegistered && observer.isAlive) observer.removeOnWindowFocusChangeListener(focusListener)
            signals.close()
        }
    }
}
