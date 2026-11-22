package iecxiv.meshfabric.ui.devices
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.R
import iecxiv.meshfabric.data.model.MeshNode
@Composable internal fun DeviceStatusDecoration(node:MeshNode?,modifier:Modifier=Modifier,tagPrefix:String="device_status") {
    val es=LocalConfiguration.current.locales[0].language=="es"
    Row(modifier,verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Surface(shape=RoundedCornerShape(6.dp),color=if(node?.isOnline==true) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
            Text(if(node==null) {if(es) "Estado no informado" else "Status not reported"} else stringResource(if(node.isOnline) R.string.device_details_online else R.string.device_details_offline),
                Modifier.padding(horizontal=8.dp,vertical=3.dp).testTag("${tagPrefix}_badge"),style=MaterialTheme.typography.labelSmall)
        }
        if(!node?.osdesc.isNullOrBlank()) Text(node?.osdesc.orEmpty(),Modifier.weight(1f).testTag("${tagPrefix}_os"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
