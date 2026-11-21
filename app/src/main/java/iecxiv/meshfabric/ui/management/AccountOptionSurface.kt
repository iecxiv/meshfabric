package iecxiv.meshfabric.ui.management
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
internal fun accountInlineOption(mode:Int)=mode in 0..10 && mode!=8
@Composable internal fun AccountOptionSurface(inline:Boolean,onDismissRequest:()->Unit,properties:DialogProperties,title:@Composable ()->Unit,text:@Composable ()->Unit,confirmButton:@Composable ()->Unit) {
    if(!inline) {ManagementDialog(onDismissRequest=onDismissRequest,properties=properties,title=title,text=text,confirmButton=confirmButton);return}
    val context=LocalContext.current
    val window=remember(context) {
        var owner:android.content.Context=context
        while(owner is android.content.ContextWrapper && owner !is android.app.Activity) owner=owner.baseContext
        requireNotNull((owner as? android.app.Activity)?.window)
    }
    DisposableEffect(window) {
        val flag=android.view.WindowManager.LayoutParams.FLAG_SECURE
        val previouslySecure=window.attributes.flags and flag!=0
        window.addFlags(flag)
        onDispose {if(!previouslySecure) window.clearFlags(flag)}
    }
    BackHandler(onBack=onDismissRequest)
    Column(Modifier.fillMaxSize().imePadding().padding(managementConnectionContentPadding),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        iecxiv.meshfabric.ui.components.SubviewHeader(onBack=onDismissRequest,title=title)
        iecxiv.meshfabric.ui.components.SubviewBodyViewport(Modifier.weight(1f).fillMaxWidth()) {text()}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {confirmButton()}
    }
}
