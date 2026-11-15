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
    }
):Closeable {
    data class Config(val server:String,val account:String,val ignoreSsl:Boolean=false) {
        fun binding(id:Long)=FidoLoginBinding(OkHttpHelper.sanitizeServerUrl(server.trim()),account.trim(),if(ignoreSsl) Long.MIN_VALUE else id)
    }
    enum class Phase { IDLE,CONNECTING,AUTHENTICATOR,SUBMITTING,PENDING,CANCELLED,FAILED,EXPIRED }
    data class State(val phase:Phase=Phase.IDLE,val id:Long=0,val server:String="",val options:JsonObject?=null) {
        val busy:Boolean get()=phase in setOf(Phase.CONNECTING,Phase.AUTHENTICATOR,Phase.SUBMITTING,Phase.PENDING)
    }
    private val mutable=MutableStateFlow(State())
    val state=mutable.asStateFlow()
    private var serial=0L
    private var bound:FidoLoginBinding?=null
    private var port:FidoLoginPort?=null
    private var job:Job?=null
    private var timer:Job?=null
    private var pending:FidoLoginHttpTransport.PendingLogin?=null
    private fun live()=current().binding(serial)
    private fun valid(id:Long)=serial==id && bound==live()
    private fun retire(phase:Phase) {
        serial++; job?.cancel(); timer?.cancel(); port?.close()
        job=null; timer=null; port=null; pending=null; bound=null
        mutable.value=State(phase,serial)
    }
    @Synchronized fun start(password:String):Boolean {
        if(mutable.value.busy) return false
        val config=current(); val id=++serial; val binding=config.binding(id)
        val engine=try {
            require(!config.ignoreSsl && config.server.isNotBlank() && binding.account.isNotBlank() && password.isNotEmpty())
            FidoWebOriginPolicy(binding.server)
            factory(binding,{ synchronized(this) { live() } })
        } catch(_:Exception) { retire(Phase.FAILED); return false }
        bound=binding; port=engine; mutable.value=State(Phase.CONNECTING,id,binding.server)
        timer=scope.launch { delay(60_000); synchronized(this@FidoLoginFlow) { if(serial==id) retire(Phase.EXPIRED) } }
        job=scope.launch {
            try {
                val options=withContext(Dispatchers.IO) { engine.begin(password) }
                synchronized(this@FidoLoginFlow) {
                    if(valid(id)) mutable.value=State(Phase.AUTHENTICATOR,id,binding.server,options.deepCopy())
                    else if(serial==id) retire(Phase.CANCELLED) else engine.close()
                }
            } catch(_:Exception) { synchronized(this@FidoLoginFlow) { if(serial==id) retire(Phase.FAILED) } }
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
            } catch(_:Exception) { synchronized(this@FidoLoginFlow) { if(serial==id) retire(Phase.FAILED) } }
        }
    }
    @Synchronized fun configurationChanged() { if(mutable.value.busy && !valid(mutable.value.id)) retire(Phase.CANCELLED) }
    @Synchronized fun cancel() { retire(Phase.CANCELLED) }
    @Synchronized fun fail(id:Long) { if(serial==id) retire(Phase.FAILED) }
    @Synchronized fun pendingForVerification(id:Long):FidoLoginHttpTransport.PendingLogin? =
        if(valid(id) && mutable.value.phase==Phase.PENDING) pending else null
    override fun close()=cancel()
}
