package iecxiv.meshfabric.ui.devices
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalConfiguration
import iecxiv.meshfabric.data.model.MeshNode
internal enum class DeviceGeneralActionKind {ACTIONS,NOTES,EVENT,RUN,MESSAGE}
internal fun deviceGeneralActionStep(kind:DeviceGeneralActionKind)=5+kind.ordinal
internal fun deviceGeneralActionKind(step:Int)=DeviceGeneralActionKind.entries.getOrNull(step-5)
internal val LocalDeviceGeneralActionOpen=staticCompositionLocalOf<((MeshNode,DeviceGeneralActionKind)->Unit)?> {null}
@Composable internal fun DeviceGeneralActionLayers(open:Boolean,background:@Composable ()->Unit,content:@Composable ()->Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(if(open) Modifier.clearAndSetSemantics {}.pointerInput(Unit) {
            awaitPointerEventScope {while(true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach {it.consume()}}
        } else Modifier)) {background()}
        if(open) Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background,content=content)
    }
}
@Composable internal fun DeviceGeneralActionHost(node:MeshNode,action:DeviceGeneralActionKind?,onDismiss:()->Unit,onActionBack:()->Unit) {
    DeviceGeneralActionLayers(action!=null,background={DeviceGeneralDialog(node,inline=true,onDismiss=onDismiss)},content={
        action?.let {when(it) {
            DeviceGeneralActionKind.ACTIONS->DeviceGeneralActionsAction(node,embedded=true,onBack=onActionBack)
            DeviceGeneralActionKind.NOTES->DeviceGeneralNotesAction(node,embedded=true,onBack=onActionBack)
            DeviceGeneralActionKind.EVENT->DeviceManualEventAction(node,embedded=true,onBack=onActionBack)
            DeviceGeneralActionKind.RUN->DeviceGeneralRunAction(node,embedded=true,onBack=onActionBack)
            DeviceGeneralActionKind.MESSAGE->DeviceGeneralMessageAction(node,embedded=true,onBack=onActionBack)
        }}
    })
}
@Composable internal fun DeviceGeneralActionSurface(embedded:Boolean,onDismissRequest:()->Unit,title:@Composable ()->Unit,text:@Composable ()->Unit,confirmButton:@Composable ()->Unit,dismissButton:@Composable ()->Unit={}) {
    if(embedded) iecxiv.meshfabric.ui.management.UserActionSurface(inline=true,onDismissRequest=onDismissRequest,title=title,text=text,confirmButton=confirmButton,dismissButton=dismissButton)
    else iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest=onDismissRequest,title=title,text=text,confirmButton=confirmButton,dismissButton=dismissButton)
}
@Composable internal fun DeviceManualEventSurface(node:MeshNode,embedded:Boolean,onDismissRequest:()->Unit,text:@Composable ()->Unit,confirmButton:@Composable ()->Unit,dismissButton:@Composable ()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    DeviceGeneralFrame(node,onDismissRequest,inline=embedded,body={text()},footer={confirmButton();dismissButton()},subtitle=if(es) "Registrar evento" else "Log event",tagPrefix="device_manual_event")
}
