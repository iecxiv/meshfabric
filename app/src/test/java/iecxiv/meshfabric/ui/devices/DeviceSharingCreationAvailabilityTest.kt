package iecxiv.meshfabric.ui.devices
import org.junit.Assert.*
import org.junit.Test
class DeviceSharingCreationAvailabilityTest {
    private fun enabled(connected:Boolean=true,ready:Boolean=true,rights:Long=524296L,phase:String?=null,reloaded:Boolean=false,review:Boolean=false)=generalSharingCreationEnabled(connected,ready,rights,phase,reloaded,review)
    @Test fun initialVerifiedListAllowsCreation() {assertTrue(enabled())}
    @Test fun confirmedRevocationRequiresNewReadBeforeCreation() {assertFalse(enabled(phase="absent"));assertFalse(enabled(phase="absent",reloaded=true,ready=false));assertTrue(enabled(phase="absent",reloaded=true))}
    @Test fun pendingOrUnconfirmedRevocationCannotUnlockCreation() {for(phase in listOf("checking","verifying","error")) assertFalse(enabled(phase=phase,reloaded=true))}
    @Test fun connectionPermissionsAndReviewAreStillRequired() {assertFalse(enabled(connected=false,phase="absent",reloaded=true));assertFalse(enabled(rights=8L,phase="absent",reloaded=true));assertFalse(enabled(review=true,phase="absent",reloaded=true))}
}
