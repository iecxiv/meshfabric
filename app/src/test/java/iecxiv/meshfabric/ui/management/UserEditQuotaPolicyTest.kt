package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class UserEditQuotaPolicyTest {
    @Test fun absentQuotaStaysInherited() { assertNull(userQuotaForEdit(null)) }
    @Test fun numericQuotaIsPreservedExactly() {
        assertEquals(0L,requireNotNull(userQuotaForEdit(0)))
        assertEquals(2048L,requireNotNull(userQuotaForEdit(2048)))
        assertEquals(2048L,requireNotNull(userQuotaForEdit(2048.0)))
        assertEquals(4294967296L,requireNotNull(userQuotaForEdit(4294967296L)))
        assertEquals(MAX_SAFE_USER_QUOTA,requireNotNull(userQuotaForEdit(MAX_SAFE_USER_QUOTA)))
    }
    @Test fun malformedOrUnsafeQuotaIsRejected() {
        for(value in listOf<Any>("2048",true,-1L,1.5,Double.NaN,Double.POSITIVE_INFINITY,9007199254740992L)) {
            assertThrows(IllegalArgumentException::class.java) { userQuotaForEdit(value) }
        }
    }
    @Test fun observationComparesNumbersAndAbsence() {
        assertTrue(userQuotaValuesMatch(2048.0,2048L))
        assertTrue(userQuotaValuesMatch(null,null))
        assertFalse(userQuotaValuesMatch(null,0))
        assertFalse(userQuotaValuesMatch(2048,4096))
        assertFalse(userQuotaValuesMatch("invalid","invalid"))
    }
}
