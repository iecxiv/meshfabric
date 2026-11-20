package iecxiv.meshfabric.ui.devices
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import iecxiv.meshfabric.R
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

@Composable internal fun DeviceInventoryHeader(inline:Boolean,onBack:()->Unit,title:@Composable ()->Unit,actions:@Composable RowScope.()->Unit) {
    if(inline) iecxiv.meshfabric.ui.components.SubviewHeader(onBack=onBack,title=title,actions=actions)
    else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Computer,null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary)
        Box(Modifier.weight(1f)) {title()}
        actions()
        IconButton(onClick=onBack) {Icon(Icons.Default.Close,stringResource(R.string.device_details_close))}
    }
}
