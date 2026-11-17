package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun DeviceGeneralActionButton(label:String,enabled:Boolean,onClick:()->Unit,modifier:Modifier=Modifier) {
    FilterChip(selected=true,onClick=onClick,enabled=enabled,
        modifier=modifier.fillMaxWidth().heightIn(min=32.dp).semantics {role=Role.Button},
        label={Text(label,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center,
            fontSize=11.sp,maxLines=2,overflow=TextOverflow.Ellipsis)})
}
