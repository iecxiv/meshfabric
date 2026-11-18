package iecxiv.meshfabric.data.remote

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class DesktopViewerMetadataTest {
    private fun count(json: String) = desktopViewerCount(JsonParser.parseString(json).asJsonObject)
    @Test fun oneViewer() { assertEquals(1, count("""{"users":{"user//a":1}}""")) }
    @Test fun twoAccounts() { assertEquals(2, count("""{"users":{"user//a":1,"user//b":1}}""")) }
    @Test fun sameAccountCountsAllViewers() { assertEquals(3, count("""{"users":{"user//a":3}}""")) }
    @Test fun mixedAccountsSumConnections() { assertEquals(3, count("""{"users":{"user//a":2,"user//b":1}}""")) }
    @Test fun missingAndEmptyAreUnknown() {
        for (json in listOf("{}", """{"users":null}""", """{"users":{}}""", """{"users":[]}""")) assertNull(count(json))
    }
    @Test fun malformedValuesAreRejected() {
        for (json in listOf("""{"users":{"a":0}}""", """{"users":{"a":-1}}""", """{"users":{"a":1.5}}""", """{"users":{"a":true}}""", """{"users":{"a":"2"}}""", """{"users":{"a":null}}""", """{"users":{"a":{}}}""", """{"users":{"a":[]}}""")) assertNull(count(json))
    }
    @Test fun overflowIsRejected() {
        assertNull(count("""{"users":{"a":2147483648}}"""))
        assertNull(count("""{"users":{"a":2147483647,"b":1}}"""))
    }
}
