package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class ServerCsvTest {
    @Test fun escapesQuotesCommasAndMultilineText() {
        assertEquals("\"a,\"\"b\"\"\nnext\"",serverCsvCell("a,\"b\"\nnext"))
        assertEquals("\"\"",serverCsvCell(""))
    }
    @Test fun spreadsheetFormulaTextIsProtected() {
        listOf("=1+1"," +value","-value","@value","\t=SUM(A1)").forEach { assertTrue(serverCsvCell(it).startsWith("\"'")) }
        assertEquals("\"0.5\"",serverCsvCell("0.5"))
    }
    @Test fun writesUtf8WithBom() {
        val bytes=serverCsvBytes("ñ")
        assertArrayEquals(byteArrayOf(0xEF.toByte(),0xBB.toByte(),0xBF.toByte()),bytes.take(3).toByteArray())
        assertEquals("ñ",bytes.drop(3).toByteArray().toString(Charsets.UTF_8))
    }
    @Test fun historyExportsSelectedKeysAndMissingIsNotZero() {
        val csv=serverHistoryCsv(listOf(HistoryPoint(1000,"server",true,mapOf("cpu.0" to 0.0,"mem.rss" to 99.0))),listOf("cpu.0","cpu.1"),3)
        assertTrue(csv.contains("cpu.0_load"))
        assertFalse(csv.contains("mem.rss"))
        assertTrue(csv.contains("1970-01-01T00:00:01Z"))
        assertTrue(csv.contains("\"0.0\",\"\"\r\n"))
    }
    @Test fun traceCsvFiltersSelectedSourcesWithoutReorderingEntries() {
        val entries=listOf(ServerTraceCsvEntry("new","new","db","a,b\nnext"),ServerTraceCsvEntry("old","old","agent","other"))
        val csv=serverTraceCsv(entries,setOf("db"))
        assertTrue(csv.contains("a,b\nnext"))
        assertFalse(csv.contains("other"))
        assertFalse(csv.contains("agent"))
    }
    @Test fun traceBufferIsBoundedAndKeepsNewest() {
        val entry=ServerTraceCsvEntry("x".repeat(16000),"","db","x".repeat(16000))
        val entries=(1..10).fold(emptyList<ServerTraceCsvEntry>()) { result,_->boundedServerTrace(result,entry) }
        assertTrue(entries.sumOf { it.size }<=64000)
        assertTrue(entries.size<=200)
    }
}
