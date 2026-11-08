package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class ProfileDeviceNavigationPolicyTest {
    @Test fun sameDomainAndSameMeshAreRequired() {assertTrue(profileDeviceIdsMatch("user//admin","node//one","mesh//group","mesh//group"));assertFalse(profileDeviceIdsMatch("user//admin","node/other/one","mesh//group","mesh//group"));assertFalse(profileDeviceIdsMatch("user//admin","node//one","mesh//group","mesh//other"))}
    @Test fun malformedIdentifiersAreRejected() {assertFalse(profileDeviceIdsMatch("user//admin","node//","mesh//group","mesh//group"));assertFalse(profileDeviceIdsMatch("user//admin","user//one","mesh//group","mesh//group"));assertFalse(profileDeviceIdsMatch("user//admin","node//one","ugrp//group","ugrp//group"))}
}
