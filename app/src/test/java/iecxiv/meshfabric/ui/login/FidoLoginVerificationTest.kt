package iecxiv.meshfabric.ui.login

import com.google.gson.JsonObject
import iecxiv.meshfabric.data.remote.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class FidoLoginVerificationTest {
    private class Rig(val accepted:Boolean=true):java.io.Closeable {
        var config=FidoLoginFlow.Config("https://server.example/tenant","alice")
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val calls=AtomicInteger(); val commits=AtomicInteger(); val closes=AtomicInteger()
        val entered=CountDownLatch(1); val release=CountDownLatch(1)
        var block=false; var fail=false; var timeout=60_000L
        val now=AtomicLong(100)
        lateinit var flow:FidoLoginFlow
        fun start() {
            flow=FidoLoginFlow(scope,{config},{b,_->object:FidoLoginPort {
                override fun begin(password:String)=JsonObject()
                override fun submit(credential:JsonObject)=FidoLoginHttpTransport.PendingLogin(b,"session=verified")
                override fun close() {}
            }},{_,_->object:FidoVerificationPort {
                override fun verify(pending:FidoLoginHttpTransport.PendingLogin):Boolean {
                    calls.incrementAndGet(); entered.countDown()
                    if(block) check(release.await(5,TimeUnit.SECONDS))
                    if(fail) error("verification failed")
                    return accepted
                }
                override fun close() { closes.incrementAndGet(); release.countDown() }
            }},timeout,{now.get()})
            assertTrue(flow.start("secret"))
        }
        suspend fun await(phase:FidoLoginFlow.Phase)=withTimeout(5000) { flow.state.first { it.phase==phase } }
        suspend fun pending():Long {
            val ready=await(FidoLoginFlow.Phase.AUTHENTICATOR)
            flow.submit(ready.id,JsonObject())
            return await(FidoLoginFlow.Phase.PENDING).id
        }
        fun verify(id:Long)=flow.verify(id) { commits.incrementAndGet() }
        override fun close() { if(::flow.isInitialized) flow.close(); release.countDown(); scope.cancel() }
    }
    @Test fun pendingDoesNotCommitWithoutVerification()=runBlocking {
        Rig().use { r -> r.start(); r.pending(); assertEquals(0,r.commits.get()); assertEquals(0,r.calls.get()) }
    }
    @Test fun acceptedIdentityCommitsExactlyOnce()=runBlocking {
        Rig().use { r -> r.start(); val id=r.pending(); r.verify(id); r.await(FidoLoginFlow.Phase.VERIFIED); r.verify(id)
            assertEquals(1,r.commits.get()); assertEquals(1,r.calls.get()); assertNull(r.flow.pendingForVerification(id)) }
    }
    @Test fun rejectedIdentityNeverCommits()=runBlocking {
        Rig(false).use { r -> r.start(); r.verify(r.pending()); r.await(FidoLoginFlow.Phase.FAILED); assertEquals(0,r.commits.get()) }
    }
    @Test fun verifierExceptionNeverCommits()=runBlocking {
        Rig().use { r -> r.fail=true; r.start(); r.verify(r.pending()); r.await(FidoLoginFlow.Phase.FAILED); assertEquals(0,r.commits.get()) }
    }
    @Test fun cancelDiscardsLateVerificationAndClosesVerifier()=runBlocking {
        Rig().use { r -> r.block=true; r.start(); r.verify(r.pending()); assertTrue(r.entered.await(5,TimeUnit.SECONDS))
            r.flow.cancel(); r.release.countDown(); assertEquals(FidoLoginFlow.Phase.CANCELLED,r.flow.state.value.phase)
            assertEquals(0,r.commits.get()); assertTrue(r.closes.get()>0) }
    }
    private suspend fun changed(change:(FidoLoginFlow.Config)->FidoLoginFlow.Config) {
        Rig().use { r -> r.block=true; r.start(); r.verify(r.pending()); assertTrue(r.entered.await(5,TimeUnit.SECONDS))
            r.config=change(r.config); r.flow.configurationChanged(); r.release.countDown()
            assertEquals(FidoLoginFlow.Phase.CANCELLED,r.flow.state.value.phase); assertEquals(0,r.commits.get()) }
    }
    @Test fun serverChangeCancelsVerification()=runBlocking { changed { it.copy(server="https://other.example") } }
    @Test fun accountChangeCancelsVerification()=runBlocking { changed { it.copy(account="bob") } }
    @Test fun tlsBypassChangeCancelsVerification()=runBlocking { changed { it.copy(ignoreSsl=true) } }
    @Test fun bindingIsRecheckedEvenWithoutConfigurationNotification()=runBlocking {
        Rig().use { r -> r.block=true; r.start(); r.verify(r.pending()); assertTrue(r.entered.await(5,TimeUnit.SECONDS))
            r.config=r.config.copy(account="bob"); r.release.countDown(); r.await(FidoLoginFlow.Phase.CANCELLED); assertEquals(0,r.commits.get()) }
    }
    @Test fun elapsedDeadlineRejectsLateIdentity()=runBlocking {
        Rig().use { r -> r.block=true; r.start(); r.verify(r.pending()); assertTrue(r.entered.await(5,TimeUnit.SECONDS))
            r.now.set(60_100); r.release.countDown(); r.await(FidoLoginFlow.Phase.EXPIRED); assertEquals(0,r.commits.get()) }
    }
    @Test fun timeoutClosesVerifierWithoutCommitting()=runBlocking {
        Rig().use { r -> r.timeout=1000; r.block=true; r.start(); r.verify(r.pending())
            r.await(FidoLoginFlow.Phase.EXPIRED); assertEquals(0,r.commits.get()); assertTrue(r.closes.get()>0) }
    }
    @Test fun completionExceptionLeavesFlowFailedAndCannotRepeat()=runBlocking {
        Rig().use { r -> r.start(); val id=r.pending(); r.flow.verify(id) { error("storage failed") }
            r.await(FidoLoginFlow.Phase.FAILED); r.verify(id); assertEquals(0,r.commits.get()) }
    }
}
