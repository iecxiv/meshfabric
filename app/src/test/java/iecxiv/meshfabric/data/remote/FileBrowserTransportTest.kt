package iecxiv.meshfabric.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class FileBrowserTransportTest {
    @Test fun marksOnlyTheClientEndpointAsBrowser() {
        val endpoint = FileBrowserTransport.endpoint("node%2F%2Ftest","tunnel1","token")
        assertTrue(endpoint.startsWith("/meshrelay.ashx?browser=1&p=5&"))
        assertTrue(endpoint.contains("nodeid=node%2F%2Ftest"))
        assertTrue(endpoint.contains("id=tunnel1"))
        assertTrue(endpoint.endsWith("rauth=token&auth=token"))
    }
    @Test fun doesNotDoubleEncodeNodeOrToken() {
        val endpoint = FileBrowserTransport.endpoint("node%2F%2Fid%24","t1","a%2Bb%3D")
        assertTrue(endpoint.contains("nodeid=node%2F%2Fid%24"))
        assertTrue(endpoint.contains("rauth=a%2Bb%3D"))
        assertFalse(endpoint.contains("%252F"))
    }
    @Test fun listingIsBinaryUtf8WithUnchangedRootAndRequestId() {
        val json = JsonObject().apply {
            addProperty("action","ls")
            addProperty("path","")
            addProperty("reqid",17L)
        }
        val binary = FileBrowserTransport.listingPayload(json.toString())
        val parsed = JsonParser.parseString(binary.utf8()).asJsonObject
        assertEquals("ls",parsed.get("action").asString)
        assertEquals("",parsed.get("path").asString)
        assertEquals(17L,parsed.get("reqid").asLong)
    }
    @Test fun unicodePathsRoundTripInBinary() {
        val json = JsonObject().apply {
            addProperty("action","ls")
            addProperty("path","C:/Usuarios/José/Documentos")
        }
        assertEquals(json.toString(),FileBrowserTransport.listingPayload(json.toString()).utf8())
    }
}
