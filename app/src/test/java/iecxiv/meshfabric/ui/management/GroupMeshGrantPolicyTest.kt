package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class GroupMeshGrantPolicyTest {
    @Test fun initialRightsAreNonzeroKnownNonManagementBits() {assertTrue(profileGroupMeshInitialGrantRights(8));assertTrue(profileGroupMeshInitialGrantRights(264));assertFalse(profileGroupMeshInitialGrantRights(0));assertFalse(profileGroupMeshInitialGrantRights(1));assertFalse(profileGroupMeshInitialGrantRights(2));assertFalse(profileGroupMeshInitialGrantRights(4))}
    @Test fun fullAdminAndUnknownBitsAreExcludedFromInitialGrants() {assertFalse(profileGroupMeshInitialGrantRights(0xffffffffL));assertFalse(profileGroupMeshInitialGrantRights(0x80000008L));assertFalse(profileGroupMeshInitialGrantRights(-1))}
    @Test fun replyCountsAreNotStringsMasksOrMissingValues() {assertTrue(profileGroupMeshGrantCount(1,1));assertTrue(profileGroupMeshGrantCount(0,0));assertFalse(profileGroupMeshGrantCount("1",1));assertFalse(profileGroupMeshGrantCount(null,0));assertFalse(profileGroupMeshGrantCount(-1,1));assertFalse(profileGroupMeshGrantCount(Double.NaN,0))}
}
