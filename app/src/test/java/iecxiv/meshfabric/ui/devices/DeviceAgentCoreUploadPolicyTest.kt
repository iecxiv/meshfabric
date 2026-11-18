package iecxiv.meshfabric.ui.devices

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import okio.Buffer

class DeviceAgentCoreUploadPolicyTest {
    @Test fun emptyOversizedNulAndInvalidUtf8AreRejected() {
        for(bytes in listOf(byteArrayOf(),ByteArray(AGENT_CORE_FILE_LIMIT+1) {65},byteArrayOf(65,0),byteArrayOf(0xc3.toByte(),0x28))) assertTrue(runCatching {AgentCoreFile("core.js",bytes)}.isFailure)
    }
    @Test fun nameMustBeSafeJavascriptFilename() {for(name in listOf("../core.js","bad\n.js","core.exe","x".repeat(129)+".js","bad\".js")) assertTrue(runCatching {AgentCoreFile(name,"help".toByteArray())}.isFailure)}
    @Test fun reviewedBytesAreFrozenAndHashIsStable() {
        val bytes="abc".toByteArray();val file=AgentCoreFile("core.js",bytes);bytes[0]=0
        assertEquals(3,file.size);assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",file.sha256)
        val out=Buffer();file.body().writeTo(out);assertEquals("abc",out.readUtf8())
        assertFalse(file.toString().contains("abc"))
    }
    @Test fun boundedReaderAcceptsLimitAndRejectsExtraByte() {
        assertEquals(AGENT_CORE_FILE_LIMIT,readAgentCoreFile("core.js",ByteArrayInputStream(ByteArray(AGENT_CORE_FILE_LIMIT) {65})).size)
        assertTrue(runCatching {readAgentCoreFile("core.js",ByteArrayInputStream(ByteArray(AGENT_CORE_FILE_LIMIT+1) {65}))}.isFailure)
    }
    @Test fun onlyMainDomainActorAndNodeAreAllowed() {
        assertTrue(agentCoreMainDomain("node//a","user//alice"))
        for(node in listOf("node/d/a","node//","node//a/b","node//a\n")) assertFalse(agentCoreMainDomain(node,"user//alice"))
        assertFalse(agentCoreMainDomain("node//a","user/d/alice"))
    }
    @Test fun uploadUrlPreservesReverseProxyPrefixAndRejectsUnsafeBases() {
        assertEquals("https://example.test/mesh/uploadmeshcorefile.ashx",agentCoreUploadUrl("https://example.test/mesh/").toString())
        for(url in listOf("http://example.test","https://user:pass@example.test","https://example.test/?token=x","https://example.test/#x")) assertTrue(runCatching {agentCoreUploadUrl(url)}.isFailure)
    }
    @Test fun multipartContainsExactlyAuthTargetAndOneFile() {
        val body=agentCoreUploadBody("node//a","test-cookie",AgentCoreFile("core.js","abc".toByteArray()))
        assertEquals(3,body.parts.size)
        val out=Buffer();body.writeTo(out);val wire=out.readUtf8()
        assertTrue(wire.contains("name=\"auth\""));assertTrue(wire.contains("name=\"attrib\""));assertTrue(wire.contains("node//a"));assertTrue(wire.contains("name=\"files\"; filename=\"core.js\""))
        assertTrue(runCatching {agentCoreUploadBody("node/d/a","cookie",AgentCoreFile("core.js","abc".toByteArray()))}.isFailure)
    }
}
