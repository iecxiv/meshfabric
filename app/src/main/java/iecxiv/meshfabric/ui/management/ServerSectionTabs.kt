package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import iecxiv.meshfabric.R

@Composable internal fun ServerSectionTabs(selected:Int,onSelect:(Int)->Unit) {
    val scheme=MaterialTheme.colorScheme
    Surface(color=scheme.surface,tonalElevation=0.dp) {
        Column {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup().padding(horizontal=8.dp,vertical=6.dp),
                horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
                listOf(R.string.my_server_general,R.string.my_server_statistics,R.string.my_server_console,R.string.my_server_trace).forEachIndexed { index,resource->
                    val active=selected==index
                    Box(Modifier.heightIn(min=48.dp).clip(MaterialTheme.shapes.small)
                        .background(if(active) scheme.primaryContainer else scheme.surface)
                        .selectable(selected=active,role=Role.Tab,onClick={ onSelect(index) }).padding(horizontal=12.dp,vertical=10.dp),contentAlignment=Alignment.Center) {
                        Text(stringResource(resource),color=if(active) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                            style=MaterialTheme.typography.labelLarge.copy(fontSize=13.sp),fontWeight=if(active) FontWeight.Bold else FontWeight.Normal,maxLines=1)
                    }
                }
            }
            HorizontalDivider(color=scheme.outlineVariant)
        }
    }
}
