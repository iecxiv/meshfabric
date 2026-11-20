package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

internal val LocalServerReadInline=staticCompositionLocalOf {false}

@Composable internal fun ServerReadSurface(appAlert:Boolean=false,onDismissRequest:()->Unit,title:@Composable ()->Unit,text:@Composable ()->Unit,confirmButton:@Composable ()->Unit) {
    if(!LocalServerReadInline.current) {
        if(appAlert) iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest=onDismissRequest,title=title,text=text,confirmButton=confirmButton)
        else ManagementDialog(onDismissRequest=onDismissRequest,title=title,text=text,confirmButton=confirmButton)
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
    Column(Modifier.fillMaxSize().padding(managementConnectionContentPadding),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth()) {ProvideTextStyle(MaterialTheme.typography.titleMedium) {title()}}
        Box(Modifier.weight(1f).fillMaxWidth()) {text()}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {confirmButton()}
    }
}
