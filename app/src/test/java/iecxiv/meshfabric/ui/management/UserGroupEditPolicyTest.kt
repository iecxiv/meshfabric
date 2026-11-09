package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class UserGroupEditPolicyTest {
    @Test fun groupAdministrationIsNotUserAdministration() {assertFalse(profileUserGroupEditRole(2));assertTrue(profileUserGroupEditRole(256));assertTrue(profileUserGroupEditRole(0xffffffffL))}
    @Test fun namesHaveTheServerLimit() {assertTrue(profileUserGroupNameValid("a".repeat(64)));assertFalse(profileUserGroupNameValid("a".repeat(65)));assertFalse(profileUserGroupNameValid(" "))}
    @Test fun descriptionsCanBeEmptyButNotTooLong() {assertTrue(profileUserGroupDescValid(""));assertTrue(profileUserGroupDescValid("a".repeat(1024)));assertFalse(profileUserGroupDescValid("a".repeat(1025)))}
    @Test fun nulIsRejected() {assertFalse(profileUserGroupNameValid("x\u0000"));assertFalse(profileUserGroupDescValid("x\u0000"))}
}
