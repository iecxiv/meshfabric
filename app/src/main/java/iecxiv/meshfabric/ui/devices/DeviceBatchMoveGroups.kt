package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.ui.management.ProfileSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect

internal data class DeviceBatchMoveChoice(val group: DeviceBatchMoveGroup, val name: String)
internal data class DeviceBatchMoveGroupsState(
    val phase: String = "loading",
    val choices: List<DeviceBatchMoveChoice> = emptyList(),
    val code: String = ""
)

private fun moveReadId(id: String, prefix: String): List<String>? = id.split('/').takeIf {
    it.size == 3 && it[0] == prefix && it[2].isNotBlank() && id.length <= 4096 && id.none { c -> c.isISOControl() }
}

internal fun deviceBatchMoveReadMask(value: JsonElement?): Long? = runCatching {
    require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
    val number = value.asBigDecimal
    require(number >= java.math.BigDecimal.valueOf(-1L) && number <= java.math.BigDecimal.valueOf(0xffffffffL))
    number.longValueExact()
}.getOrNull()

internal fun deviceBatchMoveReadGroups(
    records: List<JsonObject>,
    actor: String,
    siteRights: Long
): List<DeviceBatchMoveChoice>? = runCatching {
    val user = requireNotNull(moveReadId(actor, "user"))
    require(siteRights in -1L..0xffffffffL)
    require(records.size <= 10000)
    val result = records.map { record ->
        val rawId = record.get("_id")
        require(rawId != null && rawId.isJsonPrimitive && rawId.asJsonPrimitive.isString)
        val id = rawId.asString
        require(requireNotNull(moveReadId(id, "mesh"))[1] == user[1])
        val rawType = record.get("mtype")
        require(rawType != null && rawType.isJsonPrimitive && rawType.asJsonPrimitive.isNumber)
        val type = rawType.asBigDecimal.intValueExact()
        require(type >= 0)
        val rawName = record.get("name")
        require(rawName == null || rawName.isJsonNull || (rawName.isJsonPrimitive && rawName.asJsonPrimitive.isString))
        val name = if (rawName == null || rawName.isJsonNull) id else rawName.asString.ifBlank { id }.take(256)
        val links = record.get("links")
        require(links == null || links.isJsonNull || links.isJsonObject)
        val direct = if (links == null || links.isJsonNull) null else links.asJsonObject.get(actor)
        require(direct == null || direct.isJsonNull || direct.isJsonObject)
        val rawRights = if (direct == null || direct.isJsonNull) null else direct.asJsonObject.get("rights")
        // Do not infer inherited rights from visibility or partial user-group links.
        val rights = if (siteRights == -1L || siteRights == 0xffffffffL) 0xffffffffL else deviceBatchMoveReadMask(rawRights)
        DeviceBatchMoveChoice(DeviceBatchMoveGroup(id, type, rights), name)
    }
    require(result.map { it.group.id }.distinct().size == result.size)
    result.sortedWith(compareBy<DeviceBatchMoveChoice> { it.name }.thenBy { it.group.id })
}.getOrNull()

internal class DeviceBatchMoveGroupsReader(private val source: MeshCentralClient) {
    private val mutable = MutableStateFlow(DeviceBatchMoveGroupsState())
    val state = mutable.asStateFlow()
    private val identity = deviceBatchPowerIdentity(source)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val session = ProfileSession(source)
    private var started = false
    private var closed = false

    @Synchronized fun start() {
        if (started || closed) return
        started = true
        if (!deviceBatchPowerSame(source, identity)) { stop("identity"); return }
        session.start()
        scope.launch {
            session.state.collect { snapshot ->
                synchronized(this@DeviceBatchMoveGroupsReader) {
                    if (!closed) {
                        if (!deviceBatchPowerSame(source, identity)) {
                            stop("identity")
                        } else if (!snapshot.ready) {
                            if (snapshot.notice != "connecting") stop("connection")
                        } else if (snapshot.id != identity.actor || snapshot.readError) {
                            stop("unverified")
                        } else if (snapshot.loading) {
                            mutable.value = DeviceBatchMoveGroupsState()
                        } else {
                            val parsed = runCatching {
                                val user = JsonParser.parseString(requireNotNull(snapshot.user).toString()).asJsonObject
                                val rights = if (user.has("siteadmin")) deviceBatchMoveReadMask(user.get("siteadmin")) else 0L
                                deviceBatchMoveReadGroups(snapshot.groups.map {
                                    JsonParser.parseString(it.toString()).asJsonObject
                                }, identity.actor, requireNotNull(rights))
                            }.getOrNull()
                            if (parsed == null) stop("invalid")
                            else mutable.value = DeviceBatchMoveGroupsState("ready", parsed)
                        }
                    }
                }
            }
        }
        scope.launch {
            while (isActive) {
                delay(100L)
                synchronized(this@DeviceBatchMoveGroupsReader) {
                    if (!closed && !deviceBatchPowerSame(source, identity)) stop("identity")
                }
            }
        }
        scope.launch {
            delay(22000L)
            synchronized(this@DeviceBatchMoveGroupsReader) {
                if (!closed && mutable.value.phase == "loading") stop("timeout")
            }
        }
    }

    @Synchronized private fun stop(code: String) {
        if (closed) return
        closed = true
        session.close()
        scope.cancel()
        mutable.value = DeviceBatchMoveGroupsState("error", code = code)
    }

    @Synchronized fun close() { stop("closed") }
}
