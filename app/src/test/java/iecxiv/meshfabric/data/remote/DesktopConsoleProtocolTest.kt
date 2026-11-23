package iecxiv.meshfabric.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopConsoleProtocolTest {
    private fun parse(text: String) = desktopConsoleMessage(Gson().fromJson(text, JsonObject::class.java))
    @Test fun returnsTrimmedAgentText() {
        assertEquals("Waiting for user to grant access...", parse("{\"type\":\"console\",\"msg\":\"  Waiting for user to grant access...  \"}"))
    }
    @Test fun clearsOnEmptyBlankNullMissingOrNonText() {
        assertNull(parse("{\"type\":\"console\",\"msg\":\"\"}"))
        assertNull(parse("{\"type\":\"console\",\"msg\":\"   \"}"))
        assertNull(parse("{\"type\":\"console\",\"msg\":null}"))
        assertNull(parse("{\"type\":\"console\"}"))
        assertNull(parse("{\"type\":\"console\",\"msg\":{\"a\":1}}"))
    }
    @Test fun boundsVeryLongText() {
        assertEquals(500, parse("{\"msg\":\"" + "x".repeat(900) + "\"}")!!.length)
    }
}
