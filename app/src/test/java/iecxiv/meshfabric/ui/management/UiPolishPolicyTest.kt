package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class UiPolishPolicyTest {
    @Test fun channelConnectingOnlyMatchesActualConnectingStates() {
        assertTrue(actionChannelConnecting("Conectando acciones…"))
        assertTrue(actionChannelConnecting("Connecting action channel…"))
        assertFalse(actionChannelConnecting("Acciones conectadas"))
        assertFalse(actionChannelConnecting("Action channel disconnected; remote sessions have not been closed"))
        assertFalse(actionChannelConnecting(""))
    }
    @Test fun disconnectedInboxDoesNotClaimSavedDataWasLost() {
        assertTrue(noticeChannelLabel(true,false).contains("guardados siguen disponibles"))
        assertTrue(noticeChannelLabel(false,false).contains("Saved notices remain available"))
        assertTrue(noticeChannelLabel(true,true).contains("permisos"))
        assertTrue(noticeChannelLabel(false,true).contains("preferences"))
    }
}
