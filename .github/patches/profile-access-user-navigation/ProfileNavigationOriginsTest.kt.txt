package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class ProfileNavigationOriginsTest {
    private val user=ProfileOrigin(user=ProfileForm(true,"edit","user//alice"))
    private val group=ProfileOrigin(groupId="mesh//group",groupTab=2)
    @Test fun returnsToTheMostRecentVisibleGroup() {val result=profilePopOrigin(listOf(user,group),setOf("user//alice"),setOf("mesh//group"),true);assertEquals(group,result.first);assertEquals(listOf(user),result.second)}
    @Test fun preservesThePreviousUserPath() {val result=profilePopOrigin(listOf(group,user),setOf("user//alice"),setOf("mesh//group"),true);assertEquals(user,result.first);assertEquals(listOf(group),result.second)}
    @Test fun unavailableOriginsAreSkipped() {val result=profilePopOrigin(listOf(group,user),emptySet(),setOf("mesh//group"),true);assertEquals(group,result.first);assertTrue(result.second.isEmpty())}
    @Test fun disconnectDiscardsAllHistory() {val result=profilePopOrigin(listOf(group,user),setOf("user//alice"),setOf("mesh//group"),false);assertNull(result.first);assertTrue(result.second.isEmpty())}
    @Test fun ambiguousAndDestructiveOriginsAreNeverRestored() {val invalid=listOf(ProfileOrigin(user=ProfileForm(true,"delete","user//alice")),ProfileOrigin(user=ProfileForm(true,"edit","user//alice"),groupId="mesh//group"),ProfileOrigin(groupId="mesh//group",groupTab=9));assertNull(profilePopOrigin(invalid,setOf("user//alice"),setOf("mesh//group"),true).first)}
    @Test fun historyIsBoundedAndMissingTargetsClearIt() {var stack=emptyList<ProfileOrigin>();repeat(30) {stack=profilePushOrigin(stack,group)};assertEquals(16,stack.size);val result=profilePopOrigin(stack,emptySet(),emptySet(),true);assertNull(result.first);assertTrue(result.second.isEmpty())}
}
