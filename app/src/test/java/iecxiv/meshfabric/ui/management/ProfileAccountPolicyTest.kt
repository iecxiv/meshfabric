package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class ProfileAccountPolicyTest {
@Test fun sectionOrderUsesStableKeys() { assertEquals(listOf("account","groups","users"),profileSectionKeys) }
@Test fun structuredBackupCodesKeepUsageAndEightDigits() {
assertEquals(listOf(NativeBackupCode("00001234",true),NativeBackupCode("01234567",false)),nativeBackupCodes(listOf(mapOf("p" to 1234,"u" to true),mapOf("p" to "01234567","u" to false))))
}
@Test fun legacyBackupCodesUseEightDigits() {
assertEquals(listOf("00000042","00123456","00000007"),nativeBackupValues(listOf("000042",123456L,7.0)))
assertEquals(emptyList<NativeBackupCode>(),nativeBackupCodes(emptyList()))
}
@Test fun copyingExcludesUsedCodes() {
val codes=nativeBackupCodes(listOf(mapOf("p" to 1234,"u" to true),mapOf("p" to 5678,"u" to false)))!!
assertEquals(listOf("00001234"),nativeBackupCopyValues(codes))
assertEquals(emptyList<String>(),nativeBackupCopyValues(listOf(NativeBackupCode("00005678",false))))
}
@Test fun malformedStructuredEntriesAreRejected() {
for(value in listOf(mapOf("p" to 1234),mapOf("u" to true),mapOf("p" to 1234,"u" to "true"),mapOf("p" to 1.5,"u" to true))) assertNull(nativeBackupCodes(listOf(value)))
assertNull(nativeBackupCodes(listOf(mapOf("p" to 42,"u" to true),mapOf("p" to "00000042","u" to false))))
}
@Test fun invalidBackupPayloadsAreNotDisplayedAsCodes() {
for(value in listOf(true,Any(),Double.NaN,Double.POSITIVE_INFINITY,1.5,-1,100000000L,"a\nb","abcdef","１２３４５６７８")) assertNull(nativeBackupCodes(listOf(value)))
}
@Test fun tokenIdentifiersRemainExact() { assertEquals(setOf("~t:AbC","~t:abc"),nativeTokenIds(listOf("~t:AbC","~t:abc"))); assertEquals(emptySet<String>(),nativeTokenIds(emptyList())) }
@Test fun invalidTokenListsAreRejected() { assertNull(nativeTokenIds(listOf(""))); assertNull(nativeTokenIds(listOf("same","same"))); assertNull(nativeTokenIds(listOf("token\n"))) }
}
