package iecxiv.meshfabric.ui.management

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceivedUserGroupNavigationTest {
    private val id="ugrp//group"
    private fun record()=JSONObject().put("_id",id).put("name","Group")
    private fun state()=ProfileState(ready=true,user=JSONObject().put("_id","user//admin"),userGroups=listOf(record()))
    @Test fun onlyAnActuallyReceivedSameDomainGroupCanBeOpened() {assertNotNull(profileReceivedUserGroup(state(),id));assertNull(profileReceivedUserGroup(state(),"ugrp/other/group"));assertNull(profileReceivedUserGroup(state(),"ugrp//missing"))}
    @Test fun freshDirectoryOverridesOldDetailCache() {assertNull(profileReceivedUserGroup(state().copy(listedUserGroups=emptyList(),userGroupPhase="ready"),id))}
    @Test fun loadingFailureAndDisconnectedStatesAreNotEntries() {assertNull(profileReceivedUserGroup(state().copy(ready=false),id));assertNull(profileReceivedUserGroup(state().copy(busy=true),id));assertNull(profileReceivedUserGroup(state().copy(userGroupPhase="loading"),id));assertNull(profileReceivedUserGroup(state().copy(userGroupPhase="failed"),id))}
    @Test fun nameOnlyRecordsDoNotInventLinks() {val result=profileReceivedUserGroup(state(),id);assertNotNull(result);assertFalse(result!!.has("links"))}
}
