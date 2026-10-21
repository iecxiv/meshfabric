package iecxiv.meshfabric.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import iecxiv.meshfabric.data.local.SessionManager
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.data.remote.DesktopRelayClient
import iecxiv.meshfabric.data.remote.FileRelayClient
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.TerminalRelayClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class DeviceListViewModel(
    val meshClient: MeshCentralClient,
    val desktopClient: DesktopRelayClient,
    val terminalClient: TerminalRelayClient,
    val fileClient: FileRelayClient,
    val sessionManager: SessionManager? = null
) : ViewModel() {

    val searchQuery = MutableStateFlow("")

    val userInfo = meshClient.userInfo
    val isConnected = meshClient.connectionState
    val connectionStatus = meshClient.connectionStatus

    private val _collapsedGroups = MutableStateFlow<Set<String>>(emptySet())
    val collapsedGroups: StateFlow<Set<String>> = _collapsedGroups.asStateFlow()

    private val _isAllExpanded = MutableStateFlow(true)
    val isAllExpanded: StateFlow<Boolean> = _isAllExpanded.asStateFlow()

    init {
        sessionManager?.let { sm ->
            _collapsedGroups.value = sm.getCollapsedGroups()
            _isAllExpanded.value = sm.isAllGroupsExpanded()
        }
    }

    fun toggleGroupExpanded(groupId: String) {
        val current = _collapsedGroups.value.toMutableSet()
        if (current.contains(groupId)) {
            current.remove(groupId)
        } else {
            current.add(groupId)
        }
        _collapsedGroups.value = current
        sessionManager?.saveCollapsedGroups(current)
    }

    fun toggleAllGroups() {
        val allGroupIds = groupedDevices.value.map { it.first.id }.toSet()
        val currentCollapsed = _collapsedGroups.value
        val isAnyExpanded = allGroupIds.any { it !in currentCollapsed }

        if (isAnyExpanded) {
            _collapsedGroups.value = allGroupIds
            _isAllExpanded.value = false
            sessionManager?.saveCollapsedGroups(allGroupIds)
            sessionManager?.saveAllGroupsExpandedState(false)
        } else {
            _collapsedGroups.value = emptySet()
            _isAllExpanded.value = true
            sessionManager?.saveCollapsedGroups(emptySet())
            sessionManager?.saveAllGroupsExpandedState(true)
        }
    }

    val groupedDevices: StateFlow<List<Pair<MeshGroup, List<MeshNode>>>> = combine(
        meshClient.groupedDevices,
        searchQuery
    ) { groups, query ->
        if (query.isBlank()) {
            groups
        } else {
            val q = query.trim().lowercase()
            groups.mapNotNull { (group, nodes) ->
                val filteredNodes = nodes.filter { node ->
                    node.name.lowercase().contains(q) ||
                            (node.osdesc?.lowercase()?.contains(q) == true)
                }
                if (filteredNodes.isNotEmpty() || group.name.lowercase().contains(q)) {
                    Pair(group, filteredNodes)
                } else {
                    null
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val activeDesktopNodes = desktopClient.activeNodesFlow
    val activeTerminalNodes = terminalClient.activeNodesFlow
    val activeFileNodes = fileClient.activeNodesFlow

    fun refreshActiveSessions() {
        desktopClient.updateActiveNodes()
        terminalClient.updateActiveNodes()
        fileClient.updateActiveNodes()
    }

    fun isDesktopActive(nodeId: String): Boolean = desktopClient.isConnectedToNode(nodeId)
    fun isTerminalActive(nodeId: String): Boolean = terminalClient.isConnectedToNode(nodeId)
    fun isFilesActive(nodeId: String): Boolean = fileClient.isConnectedToNode(nodeId)

    fun closeAllActiveSessions() {
        desktopClient.disconnect()
        terminalClient.disconnectAll()
        fileClient.disconnect()
    }

    fun refresh() {
        meshClient.requestServerData()
    }
}