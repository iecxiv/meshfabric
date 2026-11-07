package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class ProfilePermissionsTest {
@Test fun userAdministrationRequiresItsBit() {
assertFalse(ProfilePermissions.manageUsers(0))
assertTrue(ProfilePermissions.manageUsers(2))
assertTrue(ProfilePermissions.manageUsers(-1))
}
@Test fun noNewGroupsIsRespected() {
assertTrue(ProfilePermissions.createGroup(0))
assertFalse(ProfilePermissions.createGroup(64))
assertTrue(ProfilePermissions.createGroup(-1))
}
@Test fun groupEditingRequiresEditRights() {
assertFalse(ProfilePermissions.editGroup(0, 8))
assertTrue(ProfilePermissions.editGroup(0, 1))
assertTrue(ProfilePermissions.editGroup(-1, 0))
}
@Test fun deletionIsConservativelyOwnerOrFullAdministratorOnly() {
assertFalse(ProfilePermissions.deleteGroup(0, 1))
assertFalse(ProfilePermissions.deleteGroup(2, 8))
assertTrue(ProfilePermissions.deleteGroup(0, -1))
assertTrue(ProfilePermissions.deleteGroup(-1, 0))
}
}
