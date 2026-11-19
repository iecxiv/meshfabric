package iecxiv.meshfabric.ui.devices

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import iecxiv.meshfabric.R
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun deviceListInventoryJson(entries: List<Pair<MeshGroup, MeshNode>>): String {
    val devices = JsonArray()
    entries.filter { it.second.id.isNotBlank() }.distinctBy { it.second.id }.forEach { (group, node) ->
        devices.add(JsonObject().apply {
            addProperty("id", node.id)
            addProperty("name", node.name)
            addProperty("groupId", group.id)
            addProperty("group", group.name)
            addProperty("os", node.osdesc)
            addProperty("connected", node.isOnline)
            add("ipv4", JsonArray().apply {
                node.networkIpv4.orEmpty().map { it.address.trim() }.filter { it.isNotEmpty() }.distinct().sorted().forEach { add(it) }
            })
        })
    }
    return JsonObject().apply { addProperty("formatVersion", 1); add("devices", devices) }.toString()
}

private data class DeviceListExportSnapshot(val identity: Triple<String, String, String>, val content: String)

@Composable
internal fun DeviceListExportButton(source: MeshCentralClient, entries: List<Pair<MeshGroup, MeshNode>>) {
    val context = LocalContext.current
    val userInfo by source.userInfo.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var pending by remember(source) { mutableStateOf<DeviceListExportSnapshot?>(null) }
    var writing by remember(source) { mutableStateOf(false) }
    val successText = stringResource(R.string.device_list_export_done)
    val failureText = stringResource(R.string.device_list_export_failed)
    fun identity() = Triple(source.serverUrl, source.userInfo.value?.id.orEmpty(), source.authCookie)
    fun same(snapshot: DeviceListExportSnapshot) = !source.isUserLogout && snapshot.identity == identity()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val snapshot = pending
        pending = null
        if (uri != null && snapshot != null) {
            if (!same(snapshot)) {
                Toast.makeText(context, failureText, Toast.LENGTH_LONG).show()
            } else coroutineScope.launch {
                writing = true
                try {
                    val saved = withContext(Dispatchers.IO) {
                        runCatching {
                            check(same(snapshot))
                            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("No output stream")
                            stream.bufferedWriter(Charsets.UTF_8).use { it.write(snapshot.content) }
                        }.isSuccess
                    }
                    Toast.makeText(context, if (saved) successText else failureText, Toast.LENGTH_LONG).show()
                } finally { writing = false }
            }
        }
    }
    DeviceListBatchActionsButton(source = source, entries = entries,
        enabled = entries.isNotEmpty() && pending == null && !writing && !source.isUserLogout && userInfo?.id?.isNotBlank() == true,
        onExport = {
            pending = DeviceListExportSnapshot(identity(), deviceListInventoryJson(entries))
            try { launcher.launch("devices.json") } catch (_: ActivityNotFoundException) {
                pending = null
                Toast.makeText(context, failureText, Toast.LENGTH_LONG).show()
            }
        })
}
