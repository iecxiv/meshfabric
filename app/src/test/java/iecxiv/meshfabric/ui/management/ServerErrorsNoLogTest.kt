package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class ServerErrorsNoLogTest {
    @Test fun actionOnlyMeansNoServerErrorLog() {
        val result=serverReadableResult("""{"action":"servererrors"}""","servererrors")
        assertTrue(result.noErrorLog)
        assertFalse(result.missing)
        assertFalse(result.invalidJson)
        assertEquals("",result.text)
    }
    @Test fun explicitErrorsAreNotHiddenAsNoLog() {
        listOf(
            """{"action":"servererrors","error":"denied"}""",
            """{"action":"servererrors","result":"Access denied"}"""
        ).forEach { raw->
            val result=serverReadableResult(raw,"servererrors")
            assertFalse(result.noErrorLog)
            assertTrue(result.missing)
            assertTrue(result.text.contains("denied"))
        }
    }
    @Test fun anEmptyObjectIsStillUnexpected() {
        val result=serverReadableResult("{}","servererrors")
        assertFalse(result.noErrorLog)
        assertTrue(result.missing)
    }
    @Test fun aDifferentActionIsStillUnexpected() {
        val result=serverReadableResult("""{"action":"other"}""","servererrors")
        assertFalse(result.noErrorLog)
        assertTrue(result.missing)
    }
    @Test fun missingConfigurationIsStillUnexpected() {
        val result=serverReadableResult("""{"action":"serverconfig"}""","serverconfig")
        assertFalse(result.noErrorLog)
        assertTrue(result.missing)
    }
    @Test fun realLogsKeepTextAndNormalizedLineBreaks() {
        val result=serverReadableResult("""{"action":"servererrors","data":"line 1\r\nline 2\rline 3"}""","servererrors")
        assertFalse(result.noErrorLog)
        assertFalse(result.missing)
        assertEquals("line 1\nline 2\nline 3",result.text)
    }
    @Test fun explicitlyEmptyDataKeepsExistingEmptyLogBehavior() {
        listOf("""{"action":"servererrors","data":""}""","""{"action":"servererrors","data":null}""").forEach { raw->
            val result=serverReadableResult(raw,"servererrors")
            assertFalse(result.noErrorLog)
            assertFalse(result.missing)
            assertEquals("",result.text)
        }
    }
    @Test fun malformedJsonIsNotTreatedAsNoLog() {
        val result=serverReadableResult("not JSON","servererrors")
        assertFalse(result.noErrorLog)
        assertTrue(result.missing)
    }
}
