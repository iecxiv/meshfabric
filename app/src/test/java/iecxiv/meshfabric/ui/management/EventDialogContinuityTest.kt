package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class EventDialogContinuityTest {
    @Test fun verifiedPauseIsRetained() {assertTrue(retainEventDialog(ServerEventsPhase.INVALIDATED,true,true,0))}
    @Test fun verifiedResumeIsRetained() {assertTrue(retainEventDialog(ServerEventsPhase.LOADING,true,true,0))}
    @Test fun ordinaryInvalidationCloses() {assertFalse(retainEventDialog(ServerEventsPhase.INVALIDATED,false,true,0))}
    @Test fun ordinaryReloadCloses() {assertFalse(retainEventDialog(ServerEventsPhase.LOADING,false,true,0))}
    @Test fun freshUniqueMatchIsRetained() {assertTrue(retainEventDialog(ServerEventsPhase.READY,false,true,1))}
    @Test fun missingOrAmbiguousMatchCloses() {for(n in listOf(0,2)) assertFalse(retainEventDialog(ServerEventsPhase.READY,false,true,n))}
    @Test fun lossOfMainConnectionCloses() {assertFalse(retainEventDialog(ServerEventsPhase.INVALIDATED,true,false,0))}
    @Test fun errorsAndDenialsAlwaysClose() {for(p in listOf(ServerEventsPhase.DENIED,ServerEventsPhase.ERROR,ServerEventsPhase.INVALID,ServerEventsPhase.TIMEOUT,ServerEventsPhase.IDLE)) assertFalse(retainEventDialog(p,true,true,1))}
}
