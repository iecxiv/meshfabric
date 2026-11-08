package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class UserGroupMemberAddPolicyTest {
    @Test fun lowercaseAsciiIdentifierHasAnUnambiguousToken() {assertEquals("alice",profileUserGroupAddToken("user//alice"))}
    @Test fun uppercaseAndUnicodeAreNotSilentlyNormalized() {assertNull(profileUserGroupAddToken("user//Alice"));assertNull(profileUserGroupAddToken("user//álîce"))}
    @Test fun malformedEmptyAndOversizedNamesAreRejected() {assertNull(profileUserGroupAddToken("alice"));assertNull(profileUserGroupAddToken("user//"));assertNull(profileUserGroupAddToken("user//a/b"));assertNull(profileUserGroupAddToken("user//"+"a".repeat(129)));assertNull(profileUserGroupAddToken("user//a b"))}
}
