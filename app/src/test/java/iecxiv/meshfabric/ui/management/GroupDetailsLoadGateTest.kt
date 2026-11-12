package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class GroupDetailsLoadGateTest {
    private fun claim(g:GroupDetailsLoadGate,epoch:Long=1,ready:Boolean=true,loading:Boolean=false,busy:Boolean=false,error:Boolean=false,available:Boolean=true)=g.claim(epoch,ready,loading,busy,error,available)
    @Test fun firstReadyLoadIsClaimedOnce() {val g=GroupDetailsLoadGate();assertTrue(claim(g));repeat(20) {assertFalse(claim(g))}}
    @Test fun ownLoadCompletionCannotRetrigger() {val g=GroupDetailsLoadGate();assertTrue(claim(g));assertFalse(claim(g,loading=true));assertFalse(claim(g));assertFalse(claim(g,busy=true));assertFalse(claim(g))}
    @Test fun realNewConnectionCanLoadOnce() {val g=GroupDetailsLoadGate();assertTrue(claim(g));assertFalse(claim(g,ready=false));assertTrue(claim(g,epoch=2));assertFalse(claim(g,epoch=2))}
    @Test fun loadingWaitsWithoutConsumingClaim() {val g=GroupDetailsLoadGate();assertFalse(claim(g,loading=true));assertTrue(claim(g))}
    @Test fun busyWaitsWithoutConsumingClaim() {val g=GroupDetailsLoadGate();assertFalse(claim(g,busy=true));assertTrue(claim(g))}
    @Test fun failedReadCannotStartLoad() {val g=GroupDetailsLoadGate();assertFalse(claim(g,error=true));assertTrue(claim(g))}
    @Test fun missingOrAmbiguousGroupCannotStartLoad() {val g=GroupDetailsLoadGate();assertFalse(claim(g,available=false));assertTrue(claim(g))}
    @Test fun notReadyCannotStartLoad() {val g=GroupDetailsLoadGate();assertFalse(claim(g,ready=false));assertTrue(claim(g))}
}
