package iecxiv.meshfabric.ui.management

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

internal val LocalProfileUserInline=staticCompositionLocalOf {false}
internal val LocalCenteredDeviceActionBody=staticCompositionLocalOf {false}

@Composable internal fun ProfileUserDetailSurface(inline:Boolean,onDismissRequest:()->Unit,modifier:Modifier=Modifier,properties:DialogProperties=DialogProperties(),title:@Composable ()->Unit,text:@Composable ()->Unit,confirmButton:@Composable ()->Unit,dismissButton:@Composable ()->Unit) {
    if(!inline) {
        ManagementDialog(onDismissRequest=onDismissRequest,modifier=modifier,properties=properties,title=title,text=text,confirmButton=confirmButton,dismissButton=dismissButton)
        return
    }
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
    Surface(modifier=Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background,contentColor=MaterialTheme.colorScheme.onBackground) {
        Column(Modifier.fillMaxSize().imePadding().padding(if(LocalNoticeComposeInline.current) managementBottomFlushPadding else managementConnectionContentPadding),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            iecxiv.meshfabric.ui.components.SubviewHeader(onBack=onDismissRequest,title=title,backTag="user-action-back")
            if(LocalCenteredDeviceActionBody.current) iecxiv.meshfabric.ui.components.CenteredSubviewBody(Modifier.weight(1f).fillMaxWidth()) {text()}
            else iecxiv.meshfabric.ui.components.SubviewBodyViewport(Modifier.weight(1f).fillMaxWidth()) {text()}
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.End),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                dismissButton()
                confirmButton()
            }
        }
    }
}
