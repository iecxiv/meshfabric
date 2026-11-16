package iecxiv.meshfabric.ui.login

import com.google.gson.JsonObject
import iecxiv.meshfabric.data.remote.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

class FidoLoginDiagnosticsTest {
    private class Rig:java.io.Closeable {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        var submitError:Exception?=null; var verifyError:Exception?=null
        var accept=true; var block=false
        val entered=CountDownLatch(1); val release=CountDownLatch(1)
        val flow=FidoLoginFlow(scope,{FidoLoginFlow.Config("https://server.example/tenant","alice")},{b,_->object:FidoLoginPort {
            override fun begin(password:String)=JsonObject()
            override fun submit(credential:JsonObject):FidoLoginHttpTransport.PendingLogin {
                entered.countDown(); if(block) check(release.await(5,TimeUnit.SECONDS))
                submitError?.let { throw it }; return FidoLoginHttpTransport.PendingLogin(b,"secret-cookie")
            }
            override fun close() { release.countDown() }
        }},{_,_->object:FidoVerificationPort {
            override fun verify(pending:FidoLoginHttpTransport.PendingLogin):Boolean { verifyError?.let { throw it }; return accept }
            override fun close() {}
        }})
        suspend fun waitFor(phase:FidoLoginFlow.Phase)=withTimeout(5000) { flow.state.first { it.phase==phase } }
        suspend fun ready():Long { assertTrue(flow.start("secret-password")); return waitFor(FidoLoginFlow.Phase.AUTHENTICATOR).id }
        suspend fun pending():Long { val id=ready(); flow.submit(id,JsonObject()); waitFor(FidoLoginFlow.Phase.PENDING); return id }
        override fun close() { flow.close(); release.countDown(); scope.cancel() }
    }
    @Test fun publicCodesAreFixedAndUnique() {
        val codes=FidoLoginFailure.Code.values().map { it.tag }
        assertEquals(codes.size,codes.toSet().size); assertTrue(codes.all { it.matches(Regex("FIDO-[0-9]{3}")) })
    }
    @Test fun nestedFailureRetainsOnlyFixedCode() {
        val wrapped=IOException("secret-cookie",FidoLoginFailure(FidoLoginFailure.Code.COOKIES))
        assertEquals(FidoLoginFailure.Code.COOKIES,FidoLoginFailure.codeOf(wrapped,FidoLoginFailure.Code.SUBMIT))
        assertEquals("FIDO-204",FidoLoginFailure(FidoLoginFailure.Code.COOKIES).message)
    }
    @Test fun unknownExceptionUsesSafeFallback() {
        assertEquals(FidoLoginFailure.Code.SUBMIT,FidoLoginFailure.codeOf(IOException("password=secret"),FidoLoginFailure.Code.SUBMIT))
    }
    @Test fun tlsClassificationDoesNotExposeMessage() {
        assertEquals(FidoLoginFailure.Code.TLS,FidoLoginFailure.transportCode(IOException("secret",SSLException("secret host"))))
        assertEquals(FidoLoginFailure.Code.TRANSPORT,FidoLoginFailure.transportCode(IOException("secret")))
    }
    @Test fun webFailureIsIdentified()=runBlocking {
        Rig().use { r -> r.flow.fail(r.ready()); assertEquals(FidoLoginFailure.Code.WEB_RESPONSE,r.flow.state.value.failure) }
    }
    @Test fun assertionFailureIsIdentified()=runBlocking {
        Rig().use { r -> r.submitError=IOException("private",FidoLoginFailure(FidoLoginFailure.Code.ASSERTION))
            val id=r.ready(); r.flow.submit(id,JsonObject()); assertEquals(FidoLoginFailure.Code.ASSERTION,r.waitFor(FidoLoginFlow.Phase.FAILED).failure) }
    }
    @Test fun lateWebFailureCannotAbortSubmission():Unit=runBlocking {
        Rig().use { r -> r.block=true; val id=r.ready(); r.flow.submit(id,JsonObject())
            assertTrue(r.entered.await(5,TimeUnit.SECONDS)); r.flow.fail(id)
            assertEquals(FidoLoginFlow.Phase.SUBMITTING,r.flow.state.value.phase)
            r.release.countDown(); r.waitFor(FidoLoginFlow.Phase.PENDING) }
    }
    @Test fun lateWebFailureCannotAbortPendingAccountVerification():Unit=runBlocking {
        Rig().use { r -> val id=r.pending(); r.flow.fail(id); assertEquals(FidoLoginFlow.Phase.PENDING,r.flow.state.value.phase)
            r.flow.verify(id) {}; r.waitFor(FidoLoginFlow.Phase.VERIFIED) }
    }
    @Test fun accountRejectionIsIdentified()=runBlocking {
        Rig().use { r -> r.accept=false; val id=r.pending(); r.flow.verify(id) { fail("no success allowed") }
            assertEquals(FidoLoginFailure.Code.ACCOUNT,r.waitFor(FidoLoginFlow.Phase.FAILED).failure) }
    }
    @Test fun sessionCompletionFailureIsIdentified()=runBlocking {
        Rig().use { r -> val id=r.pending(); r.flow.verify(id) { throw IOException("private storage details") }
            val state=r.waitFor(FidoLoginFlow.Phase.FAILED)
            assertEquals(FidoLoginFailure.Code.SESSION,state.failure); assertFalse(state.toString().contains("private")); assertFalse(state.toString().contains("secret-cookie")) }
    }
}
