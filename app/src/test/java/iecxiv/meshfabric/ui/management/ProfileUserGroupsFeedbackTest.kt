package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class ProfileUserGroupsFeedbackTest {
    @Test fun disconnectedAndUnknownCanReconnect() {assertEquals(UserGroupsRefreshAction.RECONNECT,userGroupsRefreshAction(false,false,false,"idle","disconnected"));assertEquals(UserGroupsRefreshAction.RECONNECT,userGroupsRefreshAction(false,false,false,"idle","unknown"))}
    @Test fun connectingNeverStartsAnotherAttempt() {assertEquals(UserGroupsRefreshAction.NONE,userGroupsRefreshAction(false,false,false,"idle","connecting"))}
    @Test fun pendingWritesAndReadsDisableRefresh() {assertEquals(UserGroupsRefreshAction.NONE,userGroupsRefreshAction(true,true,false,"ready","sent"));assertEquals(UserGroupsRefreshAction.NONE,userGroupsRefreshAction(true,false,true,"ready","ready"));assertEquals(UserGroupsRefreshAction.NONE,userGroupsRefreshAction(true,false,false,"loading","ready"))}
    @Test fun readyAndFailedListsUseReadRefresh() {assertEquals(UserGroupsRefreshAction.REFRESH,userGroupsRefreshAction(true,false,false,"ready","ready"));assertEquals(UserGroupsRefreshAction.REFRESH,userGroupsRefreshAction(true,false,false,"failed","readFailed"))}
    @Test fun unknownOutcomeKeepsTheUncertaintyWarning() {val text=userGroupsNoticeText("unknown",true);assertTrue(text.contains("sin confirmar"));assertTrue(text.contains("No hay reenvíos automáticos"));assertTrue(userGroupsNoticeIsError("unknown"))}
    @Test fun rejectionAndIncompleteDataAreErrors() {assertTrue(userGroupsNoticeIsError("rejected"));assertTrue(userGroupsNoticeIsError("readFailed"));assertTrue(userGroupsNoticeText("rejected",true).contains("No se considera completada"));assertTrue(userGroupsNoticeText("readFailed",true).contains("modificaciones bloqueadas"))}
    @Test fun sentIsNotSuccessAndEnglishIsAvailable() {assertTrue(userGroupsNoticeText("sent",true).contains("sin confirmar"));assertFalse(userGroupsNoticeIsError("sent"));assertTrue(userGroupsNoticeText("unknown",false).contains("unconfirmed"));assertTrue(userGroupsNoticeText("broadcastAccepted",false).contains("not established"))}
    @Test fun existingSpecificSuccessMessagesRemainOwnedByThePane() {for(notice in listOf("ready","userGroupCreated","userGroupDeleted","userGroupObserved","userGroupMemberObserved","userGroupMemberAddedObserved","groupMeshRevokedObserved")) assertEquals("",userGroupsNoticeText(notice,true))}
}
