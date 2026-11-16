package iecxiv.meshfabric.ui.login

import com.google.gson.JsonObject
import iecxiv.meshfabric.data.remote.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable

internal interface FidoLoginPort:Closeable {
    fun begin(password:String):JsonObject
    fun submit(credential:JsonObject):FidoLoginHttpTransport.PendingLogin
}
internal interface FidoVerificationPort:Closeable {
    fun verify(pending:FidoLoginHttpTransport.PendingLogin):Boolean
}
internal class FidoLoginFlow(
    private val scope:CoroutineScope,
    private val current:()->Config,
    private val factory:(FidoLoginBinding,()->FidoLoginBinding)->FidoLoginPort={ b,live ->
        val http=FidoLoginHttpTransport(b,live)
        object:FidoLoginPort {
            override fun begin(password:String)=http.begin(password)
            override fun submit(credential:JsonObject)=http.submit(credential)
            override fun close()=http.close()
        }
    },
    private val verifierFactory:(FidoLoginBinding,()->FidoLoginBinding)->FidoVerificationPort={ b,live ->
        val verifier=FidoSessionVerifier(b,live)
        object:FidoVerificationPort {
            override fun verify(pending:FidoLoginHttpTransport.PendingLogin):Boolean {
                if(!verifier.verify(pending)) throw FidoLoginFailure(verifier.failureCode ?: FidoLoginFailure.Code.ACCOUNT)
                return true
            }
            override fun close()=verifier.close()
        }
    },
    private val attemptTimeoutMs:Long=60_000L,
    private val clock:()->Long={System.nanoTime()/1_000_000L}
):Closeable {
    data class Config(val server:String,val account:String,val ignoreSsl:Boolean=false) {
        fun binding(id:Long)=FidoLoginBinding(OkHttpHelper.sanitizeServerUrl(server.trim()),account.trim(),if(ignoreSsl) Long.MIN_VALUE else id)
    }
    enum class Phase { IDLE,CONNECTING,AUTHENTICATOR,SUBMITTING,PENDING,VERIFYING,VERIFIED,CANCELLED,FAILED,EXPIRED }
    data class State(val phase:Phase=Phase.IDLE,val id:Long=0,val server:String="",val options:JsonObject?=null,val failure:FidoLoginFailure.Code?=null) {
        val busy:Boolean get()=phase in setOf(Phase.CONNECTING,Phase.AUTHENTICATOR,Phase.SUBMITTING,Phase.PENDING,Phase.VERIFYING)
    }
    private val mutable=MutableStateFlow(State())
    val state=mutable.asStateFlow()
    private var serial=0L
    private var bound:FidoLoginBinding?=null
    private var port:FidoLoginPort?=null
    private var job:Job?=null
    private var timer:Job?=null
    private var pending:FidoLoginHttpTransport.PendingLogin?=null
    private var verification:FidoVerificationPort?=null
    private var deadline=0L
    init { require(attemptTimeoutMs in 1..60_000L) }
    private fun invalidPhase()=if(clock()>=deadline) Phase.EXPIRED else Phase.CANCELLED
    private fun live()=current().binding(serial)
    private fun valid(id:Long)=serial==id && bound==live() && clock()<deadline
    private fun defaultFailure()=when(mutable.value.phase) {
        Phase.AUTHENTICATOR -> FidoLoginFailure.Code.WEB_RESPONSE
        Phase.SUBMITTING -> FidoLoginFailure.Code.SUBMIT
        Phase.PENDING,Phase.VERIFYING -> FidoLoginFailure.Code.ACCOUNT
        else -> FidoLoginFailure.Code.PREPARE
    }
    private fun retire(phase:Phase,failure:FidoLoginFailure.Code?=null) {
        val reason=if(phase==Phase.FAILED) failure ?: defaultFailure() else null
        serial++; job?.cancel(); timer?.cancel(); verification?.close(); verification=null; port?.close()
        job=null; timer=null; port=null; pending=null; bound=null
        mutable.value=State(phase,serial,failure=reason)
    }
    @Synchronized fun start(password:String):Boolean {
        if(mutable.value.busy) return false
        val config=current(); val id=++serial; val binding=config.binding(id)
        val engine=try {
            require(!config.ignoreSsl && config.server.isNotBlank() && binding.account.isNotBlank() && password.isNotEmpty())
            FidoWebOriginPolicy(binding.server)
            factory(binding,{ synchronized(this) { live() } })
        } catch(_:Exception) { retire(Phase.FAILED); return false }
        deadline=clock()+attemptTimeoutMs; bound=binding; port=engine; mutable.value=State(Phase.CONNECTING,id,binding.server)
        timer=scope.launch { delay(attemptTimeoutMs); synchronized(this@FidoLoginFlow) { if(serial==id) retire(Phase.EXPIRED) } }
        job=scope.launch {
            try {
                val options=withContext(Dispatchers.IO) { engine.begin(password) }
                synchronized(this@FidoLoginFlow) {
                    if(valid(id)) mutable.value=State(Phase.AUTHENTICATOR,id,binding.server,options.deepCopy())
                    else if(serial==id) retire(Phase.CANCELLED) else engine.close()
                }
            } catch(e:Exception) { synchronized(this@FidoLoginFlow) { if(serial==id) retire(Phase.FAILED,FidoLoginFailure.codeOf(e,defaultFailure())) } }
        }
        return true
    }
    @Synchronized fun submit(id:Long,credential:JsonObject) {
        if(!valid(id) || mutable.value.phase!=Phase.AUTHENTICATOR) return
        val engine=port ?: return
        mutable.value=mutable.value.copy(phase=Phase.SUBMITTING,options=null)
        job=scope.launch {
            try {
                val result=withContext(Dispatchers.IO) { engine.submit(credential) }
                synchronized(this@FidoLoginFlow) {
                    if(valid(id) && result.binding==bound) {
                        pending=result; mutable.value=mutable.value.copy(phase=Phase.PENDING)
                    } else if(serial==id) retire(Phase.CANCELLED) else engine.close()
                }
            } catch(e:Exception) { synchronized(this@FidoLoginFlow) { if(serial==id) retire(Phase.FAILED,FidoLoginFailure.codeOf(e,defaultFailure())) } }
        }
    }
    @Synchronized fun verify(id:Long,onVerified:(FidoLoginHttpTransport.PendingLogin)->Unit) {
        val result=pendingForVerification(id) ?: return
        val verifier=try { verifierFactory(result.binding,{ synchronized(this) { live() } }) }
            catch(_:Exception) { retire(Phase.FAILED); return }
        verification=verifier
        mutable.value=mutable.value.copy(phase=Phase.VERIFYING)
        job=scope.launch {
            try {
                val accepted=withContext(Dispatchers.IO) { verifier.verify(result) }
                synchronized(this@FidoLoginFlow) {
                    if(serial==id) {
                        when {
                            !valid(id) -> retire(invalidPhase())
                            !accepted -> retire(Phase.FAILED)
                            else -> {
                                try { onVerified(result) } catch(_:Exception) { throw FidoLoginFailure(FidoLoginFailure.Code.SESSION) }
                                retire(Phase.VERIFIED)
                            }
                        }
                    }
                }
            } catch(e:Exception) {
                synchronized(this@FidoLoginFlow) { if(serial==id) retire(Phase.FAILED,FidoLoginFailure.codeOf(e,defaultFailure())) }
            } finally { verifier.close() }
        }
    }
    @Synchronized fun configurationChanged() { if(mutable.value.busy && !valid(mutable.value.id)) retire(Phase.CANCELLED) }
    @Synchronized fun cancel() { retire(Phase.CANCELLED) }
    @Synchronized fun fail(id:Long) { if(serial==id && mutable.value.phase==Phase.AUTHENTICATOR) retire(Phase.FAILED,FidoLoginFailure.Code.WEB_RESPONSE) }
    @Synchronized fun pendingForVerification(id:Long):FidoLoginHttpTransport.PendingLogin? =
        if(valid(id) && mutable.value.phase==Phase.PENDING) pending else null
    override fun close()=cancel()
}
