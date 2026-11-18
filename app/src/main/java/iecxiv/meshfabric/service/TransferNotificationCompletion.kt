package iecxiv.meshfabric.service

internal fun transferNotificationsCompleted(successIcons: List<Boolean>, hasActiveTransfers: Boolean): Boolean =
    successIcons.isNotEmpty() && !hasActiveTransfers && successIcons.all { it }
