package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class UserGroupDeletePolicyTest {
    @Test fun exactNonBlankNameIsRequired() {assertTrue(profileUserGroupDeleteTyped("Group","Group"));assertFalse(profileUserGroupDeleteTyped("Group","group"));assertFalse(profileUserGroupDeleteTyped(" "," "))}
    @Test fun acknowledgementMustHaveTheSameIdentifier() {assertTrue(profileUserGroupDeleteAck("ugrp//one","ugrp//one"));assertFalse(profileUserGroupDeleteAck("ugrp//one","ugrp//two"));assertFalse(profileUserGroupDeleteAck("ugrp//one",null));assertFalse(profileUserGroupDeleteAck("",""))}
}
