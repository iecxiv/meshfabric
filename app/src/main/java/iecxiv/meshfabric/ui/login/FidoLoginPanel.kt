package iecxiv.meshfabric.ui.login

import android.content.Context
import android.content.ContextWrapper
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import iecxiv.meshfabric.MainActivity
import iecxiv.meshfabric.R
import com.google.gson.JsonObject

@Composable internal fun FidoLoginPanel(viewModel:LoginViewModel) {
    val state by viewModel.fidoLogin.state.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val server by viewModel.serverUrl.collectAsState()
    val account by viewModel.username.collectAsState()
    val ssl by viewModel.ignoreSsl.collectAsState()
    LaunchedEffect(server,account,ssl) { viewModel.fidoLogin.configurationChanged() }
    DisposableEffect(viewModel.fidoLogin) { onDispose { viewModel.fidoLogin.cancel() } }
    FidoLoginControls(state,loading,ssl,{ viewModel.startFidoLogin() },{ viewModel.fidoLogin.cancel() },
        { id,c -> viewModel.fidoLogin.submit(id,c) },{ id -> viewModel.fidoLogin.fail(id) })
}
@Composable internal fun FidoLoginControls(state:FidoLoginFlow.State,loading:Boolean,ignoreSsl:Boolean,onStart:()->Unit,onCancel:()->Unit,onCredential:(Long,JsonObject)->Unit,onFailure:(Long)->Unit) {
    OutlinedButton(onClick=onStart,enabled=!loading && !state.busy && !ignoreSsl,modifier=Modifier.fillMaxWidth().testTag("fido-start")) { Text(stringResource(R.string.fido_login_action)) }
    if(ignoreSsl) Text(stringResource(R.string.fido_login_tls),modifier=Modifier.testTag("fido-tls"))
    if(state.phase==FidoLoginFlow.Phase.FAILED) Text(stringResource(R.string.fido_login_failed) + state.failure?.let { " ["+it.tag+"]" }.orEmpty(),color=MaterialTheme.colorScheme.error)
    if(state.phase==FidoLoginFlow.Phase.EXPIRED) Text(stringResource(R.string.fido_login_expired),color=MaterialTheme.colorScheme.error)
    if(state.busy) AlertDialog(onDismissRequest=onCancel,properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn),
        title={ Text(stringResource(R.string.fido_login_title)) },confirmButton={},
        dismissButton={ TextButton(onClick=onCancel,modifier=Modifier.testTag("fido-cancel")) { Text(stringResource(R.string.fido_login_cancel)) } },
        text={ Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(state.server)
            if(state.phase in setOf(FidoLoginFlow.Phase.PENDING,FidoLoginFlow.Phase.VERIFYING)) Text(stringResource(R.string.fido_login_pending),modifier=Modifier.testTag("fido-pending"))
            else { Text(stringResource(R.string.fido_login_prompt)); iecxiv.meshfabric.ui.components.RoutedViewLoadingIndicator(Modifier.fillMaxWidth()) }
            if(state.phase==FidoLoginFlow.Phase.AUTHENTICATOR && state.options!=null) key(state.id) { AssertionHost(state,{ onCredential(state.id,it) },{ onFailure(state.id) }) }
        } })
}
private fun activity(context:Context):MainActivity? {
    var value=context
    repeat(16) { if(value is MainActivity) return value as MainActivity; if(value !is ContextWrapper) return null; value=(value as ContextWrapper).baseContext }
    return null
}
@Composable private fun AssertionHost(state:FidoLoginFlow.State,onCredential:(JsonObject)->Unit,onFailure:()->Unit) {
    val context=LocalContext.current
    val owner=remember(context) { activity(context) }
    val deliver=rememberUpdatedState(onCredential); val fail=rememberUpdatedState(onFailure)
    if(owner==null) { LaunchedEffect(state.id) { fail.value() }; return }
    val host=remember(owner,state.id) { FidoWebViewHost(owner,owner.fidoClient) }
    DisposableEffect(host) { onDispose { host.close() } }
    // Keep the WebAuthn bridge attached and sized without exposing the server login HTML.
    AndroidView(factory={ ctx ->
        runCatching { host.createAssertion(state.server,requireNotNull(state.options),{ deliver.value(it) },{ fail.value() }) }
            .getOrElse { TextView(ctx).also { view -> view.post { fail.value() } } }
    },modifier=Modifier.fillMaxWidth().height(4.dp).alpha(0f))
}
