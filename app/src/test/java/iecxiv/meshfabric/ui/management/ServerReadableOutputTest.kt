package iecxiv.meshfabric.ui.management

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

class ServerReadableOutputTest {
    @Test fun preservesPlainStackTracesAndLineBreaks() {
        assertEquals("Error: failed\n  at server.js:10",readableServerText("Error: failed\r\n  at server.js:10"))
    }
    @Test fun decodesJsonStringsAndIndentsObjects() {
        assertEquals("first\nsecond",readableServerText(Gson().toJson("first\nsecond")))
        assertEquals("{\n  \"count\": 2\n}",readableServerText("{\"count\":2}"))
    }
    @Test fun traceSeparatesTimestampSourceAndArguments() {
        val rendered=readableServerTrace("""{"time":1700000000000,"source":"db","args":["hello",{"count":2}]}""",ZoneId.of("UTC"),Locale.ENGLISH)
        assertTrue(rendered.startsWith("14/11/2023 22:13:20 UTC · db\n"))
        assertTrue(rendered.contains("hello\n{\n  \"count\": 2\n}"))
        assertFalse(rendered.contains("\"args\""))
    }
    @Test fun sourceOptionsRetainUnknownActiveSourcesWithoutDuplicates() {
        val options=availableTraceOptions(setOf("db","future-source"))
        assertEquals(options.size,options.toSet().size)
        assertTrue("future-source" in options)
        assertTrue(validTraceSelection(setOf("future-source"),setOf("future-source","db")))
        assertEquals("future-source",traceSourceName("future-source",true))
    }
}
