package iecxiv.meshfabric.ui.devices
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
internal val LocalDeviceDetailsInline=staticCompositionLocalOf {false}
@Composable internal fun DeviceDetailsFrame(inline:Boolean,onDismiss:()->Unit,content:@Composable ()->Unit) {
    if(inline) {
        BackHandler(onBack=onDismiss)
        Box(Modifier.fillMaxSize().imePadding()) {content()}
    } else Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false),content=content)
}
@Composable internal fun DeviceDetailsContentSurface(inline:Boolean,modifier:Modifier,content:@Composable ()->Unit) {
    if(inline) Surface(modifier=modifier,color=MaterialTheme.colorScheme.background,contentColor=MaterialTheme.colorScheme.onBackground,content=content)
    else iecxiv.meshfabric.ui.components.AppDialogSurface(
        shape=iecxiv.meshfabric.ui.components.AppDialogAppearance.shape,
        color=iecxiv.meshfabric.ui.components.AppDialogAppearance.containerColor(),
        tonalElevation=iecxiv.meshfabric.ui.components.AppDialogAppearance.tonalElevation,
        modifier=modifier,content=content)
}
