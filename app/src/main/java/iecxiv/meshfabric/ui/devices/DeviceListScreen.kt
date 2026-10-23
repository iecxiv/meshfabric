package iecxiv.meshfabric.ui.devices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import iecxiv.meshfabric.R
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import iecxiv.meshfabric.ui.components.AppIconImage
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.data.remote.ConnectionStatus
import iecxiv.meshfabric.ui.theme.OfflineGray
import iecxiv.meshfabric.ui.theme.OnlineGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    viewModel: DeviceListViewModel,
    onLogout: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDesktop: (String, String) -> Unit,
    onOpenTerminal: (String, String) -> Unit,
    onOpenFiles: (String, String) -> Unit
) {
    val groupedDevices by viewModel.groupedDevices.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val userInfo by viewModel.userInfo.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val terminalSessionsStatus by viewModel.terminalSessionsStatus.collectAsState()
    val desktopSessionsStatus by viewModel.desktopSessionsStatus.collectAsState()
    val fileSessionsStatus by viewModel.fileSessionsStatus.collectAsState()

    val collapsedGroups by viewModel.collapsedGroups.collectAsState()
    val appName by (viewModel.sessionManager?.appNameFlow ?: remember { kotlinx.coroutines.flow.MutableStateFlow("MeshFabric") }).collectAsState()
    val customIconPath by (viewModel.sessionManager?.customIconPathFlow ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()
    val hideSettingsIcon by (viewModel.sessionManager?.hideSettingsIconFlow ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsState()

    val isAnyGroupExpanded = remember(groupedDevices, collapsedGroups) {
        val allIds = groupedDevices.map { it.first.id }.toSet()
        if (allIds.isEmpty()) true else allIds.any { it !in collapsedGroups }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        viewModel.refreshActiveSessions()
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshActiveSessions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val dotColor = when (connectionStatus) {
        ConnectionStatus.CONNECTED -> OnlineGreen
        ConnectionStatus.CONNECTING -> Color(0xFFFFC107)
        ConnectionStatus.DISCONNECTED -> Color.Red
    }

    var showSearch by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showCloseRelaysDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.confirm_logout_title), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = { Text(stringResource(R.string.confirm_logout_msg_custom, appName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.closeAllActiveSessions()
                        onLogout()
                    }
                ) {
                    Text(stringResource(R.string.logout), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showCloseRelaysDialog) {
        AlertDialog(
            onDismissRequest = { showCloseRelaysDialog = false },
            title = { Text(stringResource(R.string.confirm_disconnect_all_title), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = { Text(stringResource(R.string.confirm_disconnect_all_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCloseRelaysDialog = false
                        viewModel.closeAllActiveSessions()
                    }
                ) {
                    Text(stringResource(R.string.disconnect_all), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseRelaysDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            AppIconImage(
                                customIconPath = customIconPath,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                onClick = onOpenSettings
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = appName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                }
                                Text(
                                    text = userInfo?.name?.let { stringResource(R.string.user_label, it) } ?: viewModel.meshClient.serverUrl,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy((-6).dp)
                        ) {
                            if (!hideSettingsIcon) {
                                IconButton(
                                    onClick = onOpenSettings,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = stringResource(R.string.settings),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.refresh() },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refrescar",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    viewModel.toggleAllGroups()
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = if (isAnyGroupExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                    contentDescription = if (isAnyGroupExpanded) "Plegar todos los grupos" else "Desplegar todos los grupos",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (showSearch) {
                                        viewModel.searchQuery.value = ""
                                    }
                                    showSearch = !showSearch
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = if (showSearch) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "Buscar",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { showLogoutDialog = true },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = stringResource(R.string.logout),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { showCloseRelaysDialog = true },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PowerSettingsNew,
                                    contentDescription = "Cerrar Conexiones Activas",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                AnimatedVisibility(visible = showSearch) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.searchQuery.value = it },
                            placeholder = { Text(stringResource(R.string.search_devices_placeholder)) },
                            singleLine = true,
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.clear_search)
                                        )
                                    }
                                }
                            },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        if (groupedDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (connectionStatus == ConnectionStatus.CONNECTING) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.connecting_server),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.no_devices_found),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.refresh() }) {
                            Text(stringResource(R.string.reload_devices))
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                groupedDevices.forEach { (group, nodes) ->
                    val isExpanded = if (searchQuery.isNotBlank()) true else (group.id !in collapsedGroups)
                    val onlineCount = nodes.count { it.isOnline }
                    val offlineCount = nodes.size - onlineCount

                    item(key = "group_${group.id}") {
                        GroupHeaderCard(
                            group = group,
                            onlineCount = onlineCount,
                            offlineCount = offlineCount,
                            isExpanded = isExpanded,
                            onToggleExpand = {
                                viewModel.toggleGroupExpanded(group.id)
                            }
                        )
                    }

                    if (isExpanded) {
                        items(
                            items = nodes,
                            key = { "node_${it.id}" }
                        ) { node ->
                            val cleanId = node.id.removePrefix("node//").removePrefix("node/").trim()
                            DeviceCard(
                                node = node,
                                desktopStatus = desktopSessionsStatus[cleanId] ?: iecxiv.meshfabric.data.model.SessionStatus.NONE,
                                terminalStatus = terminalSessionsStatus[cleanId] ?: iecxiv.meshfabric.data.model.SessionStatus.NONE,
                                fileStatus = fileSessionsStatus[cleanId] ?: iecxiv.meshfabric.data.model.SessionStatus.NONE,
                                onOpenDesktop = { onOpenDesktop(node.id, node.name) },
                                onOpenTerminal = { onOpenTerminal(node.id, node.name) },
                                onOpenFiles = { onOpenFiles(node.id, node.name) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupHeaderCard(
    group: MeshGroup,
    onlineCount: Int,
    offlineCount: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onToggleExpand() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name.ifEmpty { stringResource(R.string.unnamed_group) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                val offlineText = when {
                    offlineCount == 1 -> stringResource(R.string.offline_count_one)
                    offlineCount > 1 -> stringResource(R.string.offline_count_many, offlineCount)
                    else -> ""
                }
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.online_count, onlineCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = OnlineGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (offlineText.isNotEmpty()) {
                        Text(
                            text = offlineText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun DeviceCard(
    node: MeshNode,
    desktopStatus: iecxiv.meshfabric.data.model.SessionStatus = iecxiv.meshfabric.data.model.SessionStatus.NONE,
    terminalStatus: iecxiv.meshfabric.data.model.SessionStatus = iecxiv.meshfabric.data.model.SessionStatus.NONE,
    fileStatus: iecxiv.meshfabric.data.model.SessionStatus = iecxiv.meshfabric.data.model.SessionStatus.NONE,
    onOpenDesktop: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenFiles: () -> Unit
) {
    val osDesc = node.osdesc ?: ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (node.isOnline) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = if (node.isOnline) OnlineGreen.copy(alpha = 0.15f) else OfflineGray.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                osDesc.contains("Windows", ignoreCase = true) -> Icons.Default.DesktopWindows
                                osDesc.contains("Linux", ignoreCase = true) -> Icons.Default.Computer
                                osDesc.contains("Android", ignoreCase = true) -> Icons.Default.Android
                                else -> Icons.Default.Computer
                            },
                            contentDescription = null,
                            tint = if (node.isOnline) OnlineGreen else OfflineGray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = node.name.ifEmpty { stringResource(R.string.unknown_device) },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (node.isOnline) OnlineGreen else OfflineGray
                        ) {
                            Text(
                                text = if (node.isOnline) "ONLINE" else "OFFLINE",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (osDesc.isNotEmpty()) {
                        Text(
                            text = osDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (node.isOnline) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (node.supportsDesktop) {
                        SessionActionButton(
                            title = stringResource(R.string.desktop),
                            icon = Icons.Default.DesktopWindows,
                            status = desktopStatus,
                            onOpen = onOpenDesktop,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    if (node.supportsTerminal) {
                        SessionActionButton(
                            title = stringResource(R.string.terminal),
                            icon = Icons.Default.Terminal,
                            status = terminalStatus,
                            onOpen = onOpenTerminal,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    if (node.supportsFiles) {
                        SessionActionButton(
                            title = stringResource(R.string.files),
                            icon = Icons.Default.Folder,
                            status = fileStatus,
                            onOpen = onOpenFiles,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun SessionActionButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    status: iecxiv.meshfabric.data.model.SessionStatus,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = status == iecxiv.meshfabric.data.model.SessionStatus.CONNECTED
    val isDisconnectedWithBuffer = status == iecxiv.meshfabric.data.model.SessionStatus.DISCONNECTED_WITH_BUFFER
    val isActiveOrBuffered = isConnected || isDisconnectedWithBuffer

    val borderColor = when {
        isDisconnectedWithBuffer -> MaterialTheme.colorScheme.error
        isConnected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    val containerColor = when {
        isDisconnectedWithBuffer -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        isConnected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else -> Color.Unspecified
    }

    val contentColor = when {
        isDisconnectedWithBuffer -> MaterialTheme.colorScheme.error
        isConnected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    OutlinedButton(
        onClick = onOpen,
        modifier = modifier,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isActiveOrBuffered) 2.dp else 1.dp,
            color = borderColor
        ),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor
        ),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = contentColor
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = if (isActiveOrBuffered) FontWeight.Bold else FontWeight.Normal,
            color = contentColor
        )
    }
}
