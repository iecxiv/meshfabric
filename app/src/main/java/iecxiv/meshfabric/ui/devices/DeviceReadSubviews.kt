package iecxiv.meshfabric.ui.devices
import androidx.compose.runtime.*
import iecxiv.meshfabric.data.model.MeshNode
internal val LocalDeviceEventsSubviewInline=staticCompositionLocalOf {false}
internal val LocalDeviceEventsSubviewOpen=staticCompositionLocalOf<((MeshNode)->Unit)?> {null}
internal val LocalDeviceDetailsSubviewOpen=staticCompositionLocalOf<((MeshNode)->Unit)?> {null}
@Composable internal fun DeviceReadSubview(node:MeshNode,events:Boolean,onDismiss:()->Unit) {
    key(node.id,events) {
        if(events) CompositionLocalProvider(LocalDeviceEventsSubviewInline provides true) {DeviceEventsDialog(node,onDismiss=onDismiss)}
        else CompositionLocalProvider(LocalDeviceDetailsInline provides true) {DeviceDetailsDialog(node,onDismiss=onDismiss)}
    }
}
