package iecxiv.meshfabric.ui.management

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

internal data class ActionFeedbackState(val commands:List<CommandFeedback> = emptyList(),val commandWindow:String?=null,
    val clipboard:ClipboardFeedback?=null,val clipboardWindow:Boolean=false)
private data class PendingClipboard(val id:String,val nodeId:String,val expected:String,val reads:MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet())
internal class RemoteActionFeedback(private val scope:CoroutineScope,private val sendPacket:(JSONObject)->Boolean,
    private val nativeClipboard:(String,String)->Boolean?) {
    private val mutable=MutableStateFlow(ActionFeedbackState())
    val state=mutable.asStateFlow()
    private val pendingClip=AtomicReference<PendingClipboard?>(null)
    @Volatile private var clipboardJob:Job?=null
    fun handles(j:JSONObject):Boolean=j.optString("action")=="runcommands" || (j.optString("action")=="msg" && j.optString("type")=="setclip")
    fun submit(title:String,payload:JSONObject):Boolean {
        if(payload.optString("action")=="runcommands") {
            val nodes=payload.optJSONArray("nodeids") ?: return false
            if(nodes.length()!=1) return false
            val id=payload.optString("responseid").ifBlank { UUID.randomUUID().toString() }
            val node=nodes.optString(0)
            val command=CommandFeedback(id,node,title,payload.optString("cmds"))
            mutable.update { it.copy(commands=(listOf(command)+it.commands).take(20)) }
            val sent=sendPacket(JSONObject(payload.toString()).put("responseid",id))
            mutable.update { s->s.copy(commandWindow=if(sent) id else s.commandWindow,clipboardWindow=if(sent) false else s.clipboardWindow,commands=s.commands.map {
                if(it.id==id && !sent) it.copy(phase=CommandPhase.ERROR,message="No se pudo enviar el comando.") else it
            }) }
            if(sent) scope.launch {
                delay(60000)
                mutable.update { s->s.copy(commands=s.commands.map { if(it.id==id) it.interrupted("Sin salida confirmada tras 60 segundos. El comando puede seguir ejecutándose; no se ha reenviado.") else it }) }
            }
            return sent
        }
        val node=payload.optString("nodeid")
        val text=payload.optString("data")
        if(node.isBlank() || text.length>64000 || '\u0000' in text) return false
        clipboardJob?.cancel()
        val id=payload.optString("responseid").ifBlank { UUID.randomUUID().toString() }
        val pending=PendingClipboard(id,node,text)
        pendingClip.set(pending)
        mutable.update { it.copy(clipboard=ClipboardFeedback(id,node,title,"Agente (canal de control)"),clipboardWindow=false) }
        val native=runCatching { nativeClipboard(node,text) }.getOrNull()
        val sent=if(native==true) true else sendPacket(JSONObject(payload.toString()).put("responseid",id))
        mutable.update { s->if(s.clipboard?.id==id) s.copy(clipboard=s.clipboard?.copy(transport=if(native==true) "Escritorio nativo ya conectado" else "Agente (canal de control)",
            phase=if(sent) ClipboardPhase.VERIFYING else ClipboardPhase.ERROR,
            message=if(sent) "Texto enviado; esperando verificación…" else "No se pudo enviar el texto."),clipboardWindow=sent,commandWindow=if(sent) null else s.commandWindow) else s }
        if(sent) clipboardJob=scope.launch {
            repeat(4) { attempt->
                delay(600L+400L*attempt)
                if(pendingClip.get()?.id!=id || mutable.value.clipboard?.phase!=ClipboardPhase.VERIFYING) return@launch
                val rid=UUID.randomUUID().toString()
                pending.reads.add(rid)
                sendPacket(JSONObject().put("action","msg").put("type","getclip").put("nodeid",node).put("tag",2).put("responseid",rid))
            }
            delay(1500)
            mutable.update { s->if(s.clipboard?.id==id) s.copy(clipboard=s.clipboard?.timeout()) else s }
            pendingClip.compareAndSet(pending,null)
        } else pendingClip.compareAndSet(pending,null)
        return sent
    }
    fun receive(j:JSONObject):Boolean {
        val action=j.optString("action")
        val type=j.optString("type")
        if(action=="runcommands" || (action=="msg" && type=="runcommands")) {
            val rid=j.optString("responseid")
            val node=j.optString("nodeid")
            val result=when { j.has("result") && !j.isNull("result")->j.optString("result")
                j.has("value") && !j.isNull("value")->j.optString("value"); else->"" }
            mutable.update { s->s.copy(commands=s.commands.map { it.reply(action,type,rid,node,result,j.has("result") || j.has("value")) }) }
            return true
        }
        val clipType=if(action=="msg") type else action
        if(clipType !in setOf("setclip","getclip")) return false
        val pending=pendingClip.get() ?: return true
        val node=j.optString("nodeid")
        val rid=j.optString("responseid")
        if(node.isNotBlank() && node!=pending.nodeId) return true
        if(clipType=="setclip") {
            if(rid.isNotBlank() && rid!=pending.id) return true
            if(node!=pending.nodeId && rid!=pending.id) return true
            if(j.has("success")) mutable.update { s->if(s.clipboard?.id==pending.id) s.copy(clipboard=s.clipboard?.acknowledged(j.optBoolean("success"))) else s }
        } else {
            if(rid.isNotBlank() && rid !in pending.reads) return true
            if(node!=pending.nodeId && rid !in pending.reads) return true
            if(j.has("data") && !j.isNull("data")) {
                val matches=comparableClipboard(j.optString("data"))==comparableClipboard(pending.expected)
                mutable.update { s->if(s.clipboard?.id==pending.id) s.copy(clipboard=s.clipboard?.readback(matches)) else s }
            }
        }
        if(mutable.value.clipboard?.phase!=ClipboardPhase.VERIFYING) {
            clipboardJob?.cancel(); pendingClip.compareAndSet(pending,null)
        }
        return true
    }
    fun openCommand(id:String) { mutable.update { s->if(s.commands.any { it.id==id }) s.copy(commandWindow=id,clipboardWindow=false) else s } }
    fun openClipboard() { mutable.update { it.copy(clipboardWindow=it.clipboard!=null,commandWindow=null) } }
    fun closeCommand() { mutable.update { it.copy(commandWindow=null) } }
    fun closeClipboard() { mutable.update { it.copy(clipboardWindow=false) } }
    fun interrupted() {
        clipboardJob?.cancel(); pendingClip.set(null)
        mutable.update { s->s.copy(commands=s.commands.map { it.interrupted("Conexión interrumpida. Resultado desconocido; no se repetirá el comando.") },
            clipboard=s.clipboard?.let { if(it.phase==ClipboardPhase.VERIFYING) it.copy(phase=ClipboardPhase.UNKNOWN,message="Conexión interrumpida. No se ha confirmado la actualización del portapapeles.") else it }) }
    }
    fun clear() { clipboardJob?.cancel(); pendingClip.set(null); mutable.value=ActionFeedbackState() }
}
