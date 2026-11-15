package iecxiv.meshfabric.ui.login

import com.google.gson.JsonObject
import iecxiv.meshfabric.data.remote.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FidoLoginFlowTest {
    private var config=FidoLoginFlow.Config("https://server.example/tenant","alice")
    private suspend fun await(flow:FidoLoginFlow,phase:FidoLoginFlow.Phase)=withTimeout(5000) { flow.state.first { it.phase==phase } }
    private fun fake(binding:FidoLoginBinding,submit:()->Unit={}):FidoLoginPort=object:FidoLoginPort {
        override fun begin(password:String)=JsonObject()
        override fun submit(credential:JsonObject):FidoLoginHttpTransport.PendingLogin { submit(); return FidoLoginHttpTransport.PendingLogin(binding,"session=pending") }
        override fun close() {}
    }
    @Test fun pendingResponseIsNotLoginSuccess()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try { val f=FidoLoginFlow(scope,{config},{b,_->fake(b)}); assertTrue(f.start("secret")); val ready=await(f,FidoLoginFlow.Phase.AUTHENTICATOR); f.submit(ready.id,JsonObject()); val pending=await(f,FidoLoginFlow.Phase.PENDING); assertNotNull(f.pendingForVerification(pending.id)); assertNull(pending.options); f.cancel(); assertNull(f.pendingForVerification(pending.id)) } finally { scope.cancel() }
    }
    @Test fun duplicateCredentialIsNotSubmittedTwice()=runBlocking {
        var calls=0; val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try { val f=FidoLoginFlow(scope,{config},{b,_->fake(b){calls++}}); f.start("secret"); val s=await(f,FidoLoginFlow.Phase.AUTHENTICATOR); f.submit(s.id,JsonObject()); f.submit(s.id,JsonObject()); await(f,FidoLoginFlow.Phase.PENDING); assertEquals(1,calls); f.cancel() } finally { scope.cancel() }
    }
    @Test fun changedAccountCancelsAttempt()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try { val f=FidoLoginFlow(scope,{config},{b,_->fake(b)}); f.start("secret"); await(f,FidoLoginFlow.Phase.AUTHENTICATOR); config=config.copy(account="bob"); f.configurationChanged(); assertEquals(FidoLoginFlow.Phase.CANCELLED,f.state.value.phase) } finally { scope.cancel() }
    }
    @Test fun tlsBypassNeverStartsTransport() {
        var factories=0; val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try { config=config.copy(ignoreSsl=true); val f=FidoLoginFlow(scope,{config},{b,_->factories++;fake(b)}); assertFalse(f.start("secret")); assertEquals(0,factories) } finally { scope.cancel() }
    }
    @Test fun staleCredentialIsIgnoredAfterCancellation()=runBlocking {
        var calls=0; val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try { val f=FidoLoginFlow(scope,{config},{b,_->fake(b){calls++}}); f.start("secret"); val s=await(f,FidoLoginFlow.Phase.AUTHENTICATOR); f.cancel(); f.submit(s.id,JsonObject()); assertEquals(0,calls) } finally { scope.cancel() }
    }
    @Test fun lateChallengeCannotReviveCancelledAttempt()=runBlocking {
        val entered=CountDownLatch(1); val release=CountDownLatch(1); val returned=CountDownLatch(1)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try { val f=FidoLoginFlow(scope,{config},{b,_->object:FidoLoginPort {
            override fun begin(password:String):JsonObject { entered.countDown(); check(release.await(5,TimeUnit.SECONDS)); returned.countDown(); return JsonObject() }
            override fun submit(credential:JsonObject)=FidoLoginHttpTransport.PendingLogin(b,"unused")
            override fun close() { release.countDown() }
        }}); f.start("secret"); assertTrue(entered.await(5,TimeUnit.SECONDS)); f.cancel(); release.countDown(); assertTrue(returned.await(5,TimeUnit.SECONDS)); assertEquals(FidoLoginFlow.Phase.CANCELLED,f.state.value.phase) } finally { release.countDown(); scope.cancel() }
    }
}
