package iecxiv.meshfabric.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import iecxiv.meshfabric.R
import iecxiv.meshfabric.data.remote.ConnectionStatus
import iecxiv.meshfabric.ui.devices.DeviceListViewModel
import iecxiv.meshfabric.ui.management.ActivityJournal
import iecxiv.meshfabric.ui.management.ActivityTimeline
import iecxiv.meshfabric.ui.management.RemoteActionFeedback
import iecxiv.meshfabric.ui.theme.OnlineGreen
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun MeshFabricAppBar(viewModel:DeviceListViewModel,onOpenSettings:()->Unit,
    actions:@Composable RowScope.()->Unit={}) {
    val appName by (viewModel.sessionManager?.appNameFlow ?: remember { MutableStateFlow("MeshFabric") }).collectAsState()
    val customIcon by (viewModel.sessionManager?.customIconPathFlow ?: remember { MutableStateFlow<String?>(null) }).collectAsState()
    val user by viewModel.userInfo.collectAsState()
    val connection by viewModel.connectionStatus.collectAsState()
    val dot=when(connection) { ConnectionStatus.CONNECTED->OnlineGreen; ConnectionStatus.CONNECTING->Color(0xFFFFC107); ConnectionStatus.DISCONNECTED->Color.Red }
    TopAppBar(title={ Row(verticalAlignment=Alignment.CenterVertically) {
        AppIconImage(customIconPath=customIcon,modifier=Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),onClick=onOpenSettings)
        Spacer(Modifier.width(10.dp))
        Column {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(appName,fontWeight=FontWeight.Bold,fontSize=18.sp)
                Spacer(Modifier.width(8.dp)); Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
            }
            Text(user?.name?.let { stringResource(R.string.user_label,it) } ?: viewModel.meshClient.serverUrl,
                style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
    } },actions=actions,colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.surface))
}

@Composable internal fun MeshFabricActivityScreen(viewModel:DeviceListViewModel,journal:ActivityJournal,feedback:RemoteActionFeedback,
    actionsReady:Boolean,actionsStatus:String,onReconnect:()->Unit,onOpenSettings:()->Unit) {
    val connected by viewModel.isConnected.collectAsState()
    Scaffold(topBar={ MeshFabricAppBar(viewModel,onOpenSettings) }) { padding->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).padding(horizontal=12.dp,vertical=8.dp),
            verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("Actividad",modifier=Modifier.padding(vertical=6.dp),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            if(!actionsReady) {
                Text(actionsStatus,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick=onReconnect,enabled=connected) { Text("Reconectar acciones") }
            }
            ActivityTimeline(journal,feedback,Modifier.weight(1f))
        }
    }
}
