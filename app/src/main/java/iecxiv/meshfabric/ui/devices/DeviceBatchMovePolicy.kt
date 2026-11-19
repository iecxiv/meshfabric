package iecxiv.meshfabric.ui.devices

import iecxiv.meshfabric.data.model.MeshNode

// Populate rights from an authenticated group-permissions read, never from visibility.
internal data class DeviceBatchMoveGroup(val id: String, val mtype: Int, val rights: Long?)
internal data class DeviceBatchMoveDraft(
    val actorId: String,
    val nodeId: String,
    val sourceGroupId: String,
    val destinationGroupId: String
)

private fun moveIdParts(id: String, prefix: String): List<String>? {
    if (id.length > 4096 || id.any { it.isISOControl() }) return null
    return id.split('/').takeIf { it.size == 3 && it[0] == prefix && it[2].isNotBlank() }
}

internal fun deviceBatchMoveGroupAllowed(rights: Long?): Boolean =
    rights != null && rights in -1L..0xffffffffL && (rights and 5L) == 5L

internal fun deviceBatchMoveDraft(
    node: MeshNode,
    destination: String,
    actor: String,
    groups: List<DeviceBatchMoveGroup>,
    meta: GeneralMeta
): DeviceBatchMoveDraft? {
    val user = moveIdParts(actor, "user") ?: return null
    val device = moveIdParts(node.id, "node") ?: return null
    val origin = moveIdParts(node.meshid, "mesh") ?: return null
    val target = moveIdParts(destination, "mesh") ?: return null
    if (listOf(device, origin, target).any { it[1] != user[1] }) return null
    if (node.meshid == destination || meta.mesh != node.meshid) return null
    if (meta.rights !in -1L..0xffffffffL || (meta.rights and 4L) == 0L) return null
    val sourceGroup = groups.singleOrNull { it.id == node.meshid } ?: return null
    val targetGroup = groups.singleOrNull { it.id == destination } ?: return null
    // Initial scope: agent groups only. No cross-type or AMT-only transfers.
    if (sourceGroup.mtype != 2 || targetGroup.mtype != 2) return null
    if (!deviceBatchMoveGroupAllowed(sourceGroup.rights) || !deviceBatchMoveGroupAllowed(targetGroup.rights)) return null
    return DeviceBatchMoveDraft(actor, node.id, node.meshid, destination)
}

internal fun deviceBatchMoveStillMatches(
    reviewed: DeviceBatchMoveDraft,
    node: MeshNode,
    actor: String,
    groups: List<DeviceBatchMoveGroup>,
    meta: GeneralMeta
): Boolean = deviceBatchMoveDraft(node, reviewed.destinationGroupId, actor, groups, meta) == reviewed
