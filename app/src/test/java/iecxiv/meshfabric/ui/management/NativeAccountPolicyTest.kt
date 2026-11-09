package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class NativeAccountPolicyTest {
@Test fun passwordSuccessIsOnlyMessage20() {
assertEquals(true,nativePasswordResult(20))
for(id in listOf(17,18,19,21)) assertEquals(false,nativePasswordResult(id))
}
@Test fun unrelatedNotificationsAreNotPasswordAcknowledgements() {
for(id in listOf(-1,0,1,16,22,100)) assertNull(nativePasswordResult(id))
}
@Test fun totpRequiresABase32SecretAndExactlySixAsciiDigits() {
assertTrue(nativeTotpValid("JBSWY3DPEHPK3PXP","123456"))
assertFalse(nativeTotpValid("JBSWY3DPEHPK3PXP","12345"))
assertFalse(nativeTotpValid("JBSWY3DPEHPK3PXP","１２３４５６"))
assertFalse(nativeTotpValid("invalid secret","123456"))
}
}
