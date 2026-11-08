package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class GroupMeshRevokePolicyTest {
    @Test fun directManagementIsRequired() {assertFalse(profileGroupMeshRevokeAuthority(1,8));assertTrue(profileGroupMeshRevokeAuthority(2,8))}
    @Test fun fullAdminGrantRequiresFullAdminAuthority() {assertFalse(profileGroupMeshRevokeAuthority(2,0xffffffffL));assertTrue(profileGroupMeshRevokeAuthority(0xffffffffL,0xffffffffL));assertTrue(profileGroupMeshRevokeAuthority(-1,-1))}
    @Test fun meshIdentifiersStayInCurrentDomain() {assertTrue(profileGroupMeshId("user//admin","mesh//one"));assertFalse(profileGroupMeshId("user//admin","mesh/other/one"));assertFalse(profileGroupMeshId("user//admin","ugrp//one"))}
}
