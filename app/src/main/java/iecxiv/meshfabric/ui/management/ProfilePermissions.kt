package iecxiv.meshfabric.ui.management

internal object ProfilePermissions {
private const val FULL = 0xffffffffL
fun normalized(value: Long): Long = value and FULL
fun manageUsers(site: Long): Boolean = normalized(site) and 2L != 0L
fun createGroup(site: Long): Boolean = normalized(site) == FULL || normalized(site) and 64L == 0L
fun editGroup(site: Long, rights: Long): Boolean = normalized(site) == FULL || normalized(rights) and 1L != 0L
fun deleteGroup(site: Long, rights: Long): Boolean = normalized(site) == FULL || normalized(rights) == FULL
}
