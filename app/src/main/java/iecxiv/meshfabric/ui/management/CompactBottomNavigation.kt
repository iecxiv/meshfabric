package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun CompactBottomNavigation(page:Int,onSelect:(Int)->Unit) {
    val scheme=MaterialTheme.colorScheme
    Surface(color=scheme.surface,tonalElevation=0.dp,shadowElevation=0.dp) {
        Column {
            HorizontalDivider(color=scheme.outlineVariant)
            Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal+WindowInsetsSides.Bottom)).selectableGroup(),
                verticalAlignment=Alignment.CenterVertically) {
                listOf("Equipos","Actividad").forEachIndexed { index,label->
                    val selected=page==index
                    val color=if(selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant
                    Row(Modifier.weight(1f).padding(horizontal=6.dp,vertical=4.dp).heightIn(min=48.dp).clip(MaterialTheme.shapes.small)
                        .background(if(selected) scheme.primaryContainer else scheme.surface)
                        .selectable(selected=selected,role=Role.Tab,onClick={ onSelect(index) }).padding(horizontal=12.dp,vertical=8.dp),
                        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
                        Icon(if(index==0) Icons.Default.Computer else Icons.Default.History,null,Modifier.size(20.dp),tint=color)
                        Spacer(Modifier.width(8.dp))
                        Text(label,color=color,style=MaterialTheme.typography.labelLarge.copy(fontSize=13.sp),
                            fontWeight=if(selected) FontWeight.Bold else FontWeight.Normal,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
