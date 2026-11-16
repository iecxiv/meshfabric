package iecxiv.meshfabric.ui.login

import iecxiv.meshfabric.data.model.SessionData
import iecxiv.meshfabric.data.remote.*
import org.junit.Assert.*
import org.junit.Test

class FidoLoginCompletionTest {
    private val pending=FidoLoginHttpTransport.PendingLogin(FidoLoginBinding("https://server.example/tenant","alice",7),"session=verified")
    @Test fun rememberedSessionIsStoredBeforeSuccess() {
        val events=mutableListOf<String>(); var stored:SessionData?=null
        FidoLoginCompletion.complete(pending,true,{stored=it;events.add("persist")},{events.add("forget")},{events.add("connect")},{events.add("disconnect")},{events.add("success")})
        assertEquals(listOf("connect","persist","success"),events)
        assertEquals("alice",stored!!.username); assertEquals("session=verified",stored!!.authCookie)
        assertEquals("https://server.example/tenant",stored!!.serverUrl); assertFalse(stored!!.ignoreSsl); assertTrue(stored!!.isRemembered)
    }
    @Test fun notRememberedNeverPersistsCookieAndForgetsOldAuthentication() {
        val events=mutableListOf<String>()
        FidoLoginCompletion.complete(pending,false,{events.add("persist")},{events.add("forget")},{assertFalse(it.ignoreSsl);events.add("connect")},{events.add("disconnect")},{events.add("success")})
        assertEquals(listOf("connect","forget","success"),events)
    }
    @Test fun persistenceFailureDisconnectsAndDoesNotAnnounceSuccess() {
        val events=mutableListOf<String>()
        val result=runCatching { FidoLoginCompletion.complete(pending,true,{error("disk")},{events.add("forget")},{events.add("connect")},{events.add("disconnect")},{events.add("success")}) }
        assertTrue(result.isFailure); assertEquals(listOf("connect","forget","disconnect"),events)
    }
    @Test fun connectionFailureNeverPersistsOrAnnouncesSuccess() {
        val events=mutableListOf<String>()
        val result=runCatching { FidoLoginCompletion.complete(pending,true,{events.add("persist")},{events.add("forget")},{error("network")},{events.add("disconnect")},{events.add("success")}) }
        assertTrue(result.isFailure); assertEquals(listOf("forget","disconnect"),events)
    }
    @Test fun forgetFailureDoesNotAnnounceSuccessAndStillDisconnects() {
        var success=false; var disconnected=false
        assertTrue(runCatching { FidoLoginCompletion.complete(pending,false,{}, {error("storage")},{},{disconnected=true},{success=true}) }.isFailure)
        assertFalse(success); assertTrue(disconnected)
    }
    @Test fun rollbackFailureDoesNotSkipDisconnect() {
        var disconnected=false
        assertTrue(runCatching { FidoLoginCompletion.complete(pending,true,{error("storage")},{error("rollback")},{},{disconnected=true},{fail("must not publish success")}) }.isFailure)
        assertTrue(disconnected)
    }
}
