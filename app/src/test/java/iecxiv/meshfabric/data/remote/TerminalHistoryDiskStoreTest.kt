package iecxiv.meshfabric.data.remote

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class TerminalHistoryDiskStoreTest {
    private fun record(id: String = "one", text: String = "output") = TerminalHistoryRecord(id, "node", 1, 2, text, false, 80, 25)
    @Test fun upsertKeepsOneRecordPerSession() {
        val root = Files.createTempDirectory("terminal-history").toFile()
        try {
            val store = TerminalHistoryDiskStore(root) { true }
            assertTrue(store.write("account", record()))
            assertTrue(store.write("account", record(text = "latest")))
            assertEquals(listOf("latest"), store.read("account").map { it.text })
        } finally { root.deleteRecursively() }
    }
    @Test fun accountsDoNotShareHistories() {
        val root = Files.createTempDirectory("terminal-history").toFile()
        try {
            val store = TerminalHistoryDiskStore(root) { true }
            store.write("server-a/user", record())
            assertTrue(store.read("server-b/user").isEmpty())
        } finally { root.deleteRecursively() }
    }
    @Test fun disabledSettingPurgesAndPreventsWrites() {
        val root = Files.createTempDirectory("terminal-history").toFile()
        try {
            var enabled = true
            val store = TerminalHistoryDiskStore(root) { enabled }
            store.write("account", record()); enabled = false
            store.write("account", record("two"))
            assertTrue(store.read("account").isEmpty())
            assertFalse(root.exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun deletionSurvivesNewStoreInstance() {
        val root = Files.createTempDirectory("terminal-history").toFile()
        try {
            val store = TerminalHistoryDiskStore(root) { true }
            store.write("account", record()); assertTrue(store.delete("account", "one"))
            assertTrue(TerminalHistoryDiskStore(root) { true }.read("account").isEmpty())
        } finally { root.deleteRecursively() }
    }
    @Test fun untrustedIdentifiersStayInsideStorageRoot() {
        val root = Files.createTempDirectory("terminal-history").toFile()
        try {
            val store = TerminalHistoryDiskStore(root) { true }
            assertTrue(store.write("../../account", record("../../record")))
            assertEquals(1, store.read("../../account").size)
            assertTrue(root.walkTopDown().filter { it.isFile }.all { it.canonicalPath.startsWith(root.canonicalPath + java.io.File.separator) })
        } finally { root.deleteRecursively() }
    }
    @Test fun oversizedRecordIsRejected() {
        val root = Files.createTempDirectory("terminal-history").toFile()
        try { assertFalse(TerminalHistoryDiskStore(root) { true }.write("account", record(text = "a".repeat(200001)))) }
        finally { root.deleteRecursively() }
    }
}
