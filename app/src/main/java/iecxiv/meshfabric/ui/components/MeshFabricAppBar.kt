package iecxiv.meshfabric.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
    var showSearch by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    val requester=remember { FocusRequester() }
    val keyboard=LocalSoftwareKeyboardController.current
    val focusManager=LocalFocusManager.current
    Scaffold(topBar={ Column {
        MeshFabricAppBar(viewModel,onOpenSettings,actions={ Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy((-6).dp)) {
            if(!actionsReady) IconButton(onClick=onReconnect,enabled=connected,modifier=Modifier.size(38.dp)) {
                Icon(Icons.Default.Refresh,contentDescription="Reconectar actividad",modifier=Modifier.size(20.dp))
            }
            IconButton(onClick={
                if(showSearch) { search=""; focusManager.clearFocus(force=true); keyboard?.hide() }
                showSearch=!showSearch
            },modifier=Modifier.size(38.dp)) {
                Icon(if(showSearch) Icons.Default.Close else Icons.Default.Search,contentDescription=stringResource(R.string.search),modifier=Modifier.size(20.dp))
            }
        } })
        AnimatedVisibility(showSearch) {
            LaunchedEffect(showSearch) { if(showSearch) { withFrameNanos { }; requester.requestFocus(); keyboard?.show() } }
            OutlinedTextField(value=search,onValueChange={ search=it },singleLine=true,
                placeholder={ Text("Buscar en actividad…") },modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp).focusRequester(requester),
                trailingIcon={ if(search.isNotEmpty()) IconButton(onClick={ search="" }) { Icon(Icons.Default.Close,contentDescription=stringResource(R.string.clear_search)) } })
        }
    } }) { padding->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).padding(horizontal=12.dp,vertical=8.dp),
            verticalArrangement=Arrangement.spacedBy(4.dp)) {
            if(!actionsReady) Text(actionsStatus,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            ActivityTimeline(journal,feedback,Modifier.weight(1f),query=search)
        }
    }
}
