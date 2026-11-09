package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class UserMeshRevokePolicyTest {
    @Test fun sameDomainOtherUserOnly() {
        assertTrue(profileUserMeshRevokeSubject("user//admin","user//target"))
        assertTrue(profileUserMeshRevokeSubject("user/domain/admin","user/domain/target"))
        for(uid in listOf("user//admin","user/other/target","ugrp//target","node//target","user//","target","user//a/b")) assertFalse(profileUserMeshRevokeSubject("user//admin",uid))
        assertFalse(profileUserMeshRevokeSubject("user//","user//target"))
    }
    @Test fun nonAdministratorCannotRemoveAFullGroupAdministrator() {
        assertTrue(profileGroupMeshRevokeAuthority(2,8))
        assertFalse(profileGroupMeshRevokeAuthority(2,0xffffffffL))
        assertTrue(profileGroupMeshRevokeAuthority(0xffffffffL,0xffffffffL))
        assertFalse(profileGroupMeshRevokeAuthority(8,8))
    }
}
