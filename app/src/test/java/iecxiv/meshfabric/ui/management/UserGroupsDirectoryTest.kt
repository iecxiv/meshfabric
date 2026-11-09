package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class UserGroupsDirectoryTest {
    @Test fun tabOrderPreservesExistingKeys() { assertEquals(listOf("account","groups","users","userGroups"),profileSectionKeys) }
    @Test fun groupTypesRemainDistinct() { assertNotEquals(profileSectionKeys[1],profileSectionKeys[3]) }
    @Test fun sameDomainIsAccepted() { assertTrue(profileUserGroupDomainAllowed("user//admin","ugrp//example"));assertTrue(profileUserGroupDomainAllowed("user/company/admin","ugrp/company/example")) }
    @Test fun otherDomainsAreNotMixed() { assertFalse(profileUserGroupDomainAllowed("user/company/admin","ugrp/other/example")) }
    @Test fun malformedIdsAreRejected() { assertFalse(profileUserGroupDomainAllowed("user//admin","ugrp/"));assertFalse(profileUserGroupDomainAllowed("user/","ugrp//example"));assertFalse(profileUserGroupDomainAllowed("user//admin","mesh//example")) }
}
