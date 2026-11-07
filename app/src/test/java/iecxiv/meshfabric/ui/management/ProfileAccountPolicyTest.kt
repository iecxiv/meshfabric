package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class ProfileAccountPolicyTest {
@Test fun sectionOrderUsesStableKeys() { assertEquals(listOf("account","groups","users"),profileSectionKeys) }
@Test fun backupValuesPreserveLeadingZeroesAndIntegerNumbers() { assertEquals(listOf("000042","123456","42"),nativeBackupValues(listOf("000042",123456L,42.0))); assertEquals(emptyList<String>(),nativeBackupValues(emptyList())) }
@Test fun invalidBackupPayloadsAreNotDisplayedAsCodes() { for(value in listOf(true,Any(),Double.NaN,Double.POSITIVE_INFINITY,1.5,-1,"a\nb")) assertNull(nativeBackupValues(listOf(value))) }
@Test fun tokenIdentifiersRemainExact() { assertEquals(setOf("~t:AbC","~t:abc"),nativeTokenIds(listOf("~t:AbC","~t:abc"))); assertEquals(emptySet<String>(),nativeTokenIds(emptyList())) }
@Test fun invalidTokenListsAreRejected() { assertNull(nativeTokenIds(listOf(""))); assertNull(nativeTokenIds(listOf("same","same"))); assertNull(nativeTokenIds(listOf("token\n"))) }
}
