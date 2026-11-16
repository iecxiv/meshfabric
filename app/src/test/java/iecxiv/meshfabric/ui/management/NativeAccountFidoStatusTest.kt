package iecxiv.meshfabric.ui.management

import iecxiv.meshfabric.data.remote.FidoRegistrationSession.Phase
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeAccountFidoStatusTest {
    @Test fun completeIsConfirmed() { assertEquals("confirmed", fidoPhaseStatus(Phase.COMPLETE)) }
    @Test fun rejectedIsRejected() { assertEquals("rejected", fidoPhaseStatus(Phase.REJECTED)) }
    @Test fun expiredHasOwnStatus() { assertEquals("fidoExpired", fidoPhaseStatus(Phase.EXPIRED)) }
    @Test fun invalidIsIncompatible() { assertEquals("incompatible", fidoPhaseStatus(Phase.INVALID)) }
    @Test fun uncertainOutcomeIsUnknown() { assertEquals("unknown", fidoPhaseStatus(Phase.UNKNOWN)) }
    @Test fun cancelledAndIncompletePhasesAreNeverConfirmed() {
        listOf(Phase.NEW, Phase.OPTIONS, Phase.AUTHENTICATOR, Phase.ACK, Phase.READBACK, Phase.CANCELLED).forEach { assertEquals("fidoCancelled", fidoPhaseStatus(it)) }
    }
}
