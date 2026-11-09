package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class UserGroupMemberRemovePolicyTest {
    @Test fun onlyFullCurrentDomainUserIdentifiersAreAccepted() {assertTrue(profileUserGroupMemberId("user//admin","user//person"));assertFalse(profileUserGroupMemberId("user//admin","person"));assertFalse(profileUserGroupMemberId("user//admin","user/other/person"))}
    @Test fun malformedOrOversizedIdentifiersAreRejected() {assertFalse(profileUserGroupMemberId("user//admin","user//"));assertFalse(profileUserGroupMemberId("user//admin","user//"+"a".repeat(256)));assertFalse(profileUserGroupMemberId("user//admin","mesh//person"))}
}
