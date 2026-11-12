package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun ManagementSectionTabs(labels:List<String>,selected:Int,onSelect:(Int)->Unit) {
    val scheme=MaterialTheme.colorScheme
    Surface(color=scheme.surface,tonalElevation=0.dp) {
        Column {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup().padding(horizontal=8.dp,vertical=6.dp),
                horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
                labels.forEachIndexed { index,label->
                    val active=selected==index
                    Box(Modifier.heightIn(min=48.dp).clip(MaterialTheme.shapes.small)
                        .background(if(active) scheme.primaryContainer else scheme.surface)
                        .selectable(selected=active,role=Role.Tab,onClick={ onSelect(index) }).padding(horizontal=12.dp,vertical=10.dp),contentAlignment=Alignment.Center) {
                        Text(label,color=if(active) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                            style=MaterialTheme.typography.labelLarge.copy(fontSize=13.sp),fontWeight=if(active) FontWeight.Bold else FontWeight.Normal,maxLines=1)
                    }
                }
            }
            HorizontalDivider(color=scheme.outlineVariant)
        }
    }
}

@Composable internal fun ManagementCard(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=MaterialTheme.shapes.medium,color= iecxiv.meshfabric.ui.theme.appCardBackground(
MaterialTheme.colorScheme.surface
),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),
    contentColor = androidx.compose.material3.contentColorFor(
MaterialTheme.colorScheme.surface
)
,
    tonalElevation = iecxiv.meshfabric.ui.theme.appCardTonalElevation(
androidx.compose.ui.unit.Dp(0f)
)
) {
        Column(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
    }
}

@Composable internal fun ManagementInfoRow(label:String,value:String) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "—" },style=MaterialTheme.typography.bodyMedium)
    }
}

@Composable internal fun ManagementActionRow(label:String,icon:ImageVector,enabled:Boolean,dangerous:Boolean=false,onClick:()->Unit) {
    TextButton(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=MaterialTheme.shapes.small,
        contentPadding=PaddingValues(horizontal=4.dp,vertical=8.dp),
        colors=ButtonDefaults.textButtonColors(contentColor=if(dangerous) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)) {
        Icon(icon,null,Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.ChevronRight,null,Modifier.size(18.dp))
    }
}
