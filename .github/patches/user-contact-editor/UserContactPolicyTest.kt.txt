package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class UserContactPolicyTest {
    @Test fun emptyPhoneIsExplicitRemoval() { assertTrue(userPhoneInputAllowed("")) }
    @Test fun conservativePhoneFormatIsAccepted() { assertTrue(userPhoneInputAllowed("+34612345678"));assertTrue(userPhoneInputAllowed("612345678")) }
    @Test fun malformedPhoneInputIsRejected() { for(v in listOf("1234","++34612345678","+34 612345678","abc","123456789012345678")) assertFalse(userPhoneInputAllowed(v)) }
    @Test fun verifyingRequiresEmailAndServerCapability() { assertTrue(userVerificationAllowed("person@example.invalid",true,true));assertFalse(userVerificationAllowed("",true,true));assertFalse(userVerificationAllowed("person@example.invalid",false,true)) }
    @Test fun clearingStatusStillRequiresServerCapability() { assertTrue(userVerificationAllowed("",true,false));assertFalse(userVerificationAllowed("",false,false)) }
}
