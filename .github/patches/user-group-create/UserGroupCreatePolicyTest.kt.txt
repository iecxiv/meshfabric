package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class UserGroupCreatePolicyTest {
    @Test fun returnedIdentifierMustBelongToCurrentDomain() {assertTrue(profileCreatedUserGroupId("user//admin","ugrp//new"));assertFalse(profileCreatedUserGroupId("user//admin","ugrp/other/new"))}
    @Test fun malformedIdentifiersAreRejected() {assertFalse(profileCreatedUserGroupId("user//admin",""));assertFalse(profileCreatedUserGroupId("user//admin","ugrp//"));assertFalse(profileCreatedUserGroupId("user//admin","mesh//new"))}
}
