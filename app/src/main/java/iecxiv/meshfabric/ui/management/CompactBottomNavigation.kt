package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import iecxiv.meshfabric.R

internal val bottomNavigationPageOrder=listOf(3,2,0,1,4)

@Composable internal fun CompactBottomNavigation(page:Int,onSelect:(Int)->Unit) {
val request by NoticeNavigation.request.collectAsState()
val activeScope by NoticeNavigation.activeScope.collectAsState()
val select by rememberUpdatedState(onSelect)
LaunchedEffect(request,activeScope) {
val requested=request ?: return@LaunchedEffect
val current=activeScope ?: return@LaunchedEffect
if(requested==current) select(4)
NoticeNavigation.consume(requested)
}
ProfileNavigationBar(page,onSelect)
}

@Composable internal fun ProfileNavigationBar(page:Int,onSelect:(Int)->Unit) {
val scheme=MaterialTheme.colorScheme
val es=LocalConfiguration.current.locales[0].language=="es"
val unread=LocalNoticeUnread.current
Surface(color=scheme.surface,tonalElevation=0.dp,shadowElevation=0.dp) {
Column {
HorizontalDivider(color=scheme.outlineVariant)
Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal+WindowInsetsSides.Bottom)).selectableGroup(),verticalAlignment=Alignment.CenterVertically) {
val labels=listOf(if(es) "Equipos" else "Devices",stringResource(R.string.registro_title),stringResource(R.string.navigation_server),if(es) "Gestión" else "Management",if(es) "Avisos" else "Notices")
bottomNavigationPageOrder.forEach { index->
val label=labels[index]
val selected=page==index
val color=if(selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant
Column(Modifier.weight(1f).padding(horizontal=3.dp,vertical=4.dp).heightIn(min=52.dp).clip(MaterialTheme.shapes.small)
.background(if(selected) scheme.primaryContainer else scheme.surface)
.selectable(selected=selected,role=Role.Tab,onClick={ onSelect(index) }).padding(horizontal=2.dp,vertical=6.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
val icon=when(index) { 0->Icons.Default.Computer; 1->Icons.Default.History; 2->Icons.Default.Storage; 3->Icons.Default.ManageAccounts; else->Icons.Default.Notifications }
if(index==4 && unread>0) BadgedBox(badge={ Badge { Text(if(unread>99) "99+" else unread.toString()) } }) { Icon(icon,null,Modifier.size(20.dp),tint=color) } else Icon(icon,null,Modifier.size(20.dp),tint=color)
Spacer(Modifier.height(2.dp))
Text(label,color=color,style=MaterialTheme.typography.labelLarge.copy(fontSize=12.sp),fontWeight=if(selected) FontWeight.Bold else FontWeight.Normal,maxLines=1,overflow=TextOverflow.Ellipsis)
}
}
}
}
}
}
