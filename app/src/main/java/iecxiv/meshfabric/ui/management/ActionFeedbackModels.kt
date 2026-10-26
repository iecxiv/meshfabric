package iecxiv.meshfabric.ui.management

internal enum class CommandPhase { WAITING, ACCEPTED, OUTPUT, ERROR, UNKNOWN }
internal data class CommandFeedback(val id:String,val nodeId:String,val title:String,val command:String,
    val phase:CommandPhase=CommandPhase.WAITING,val output:String="",val message:String="Esperando respuesta del agente…") {
    val busy:Boolean get()=phase==CommandPhase.WAITING || phase==CommandPhase.ACCEPTED
    fun reply(action:String,type:String,id:String,node:String,result:String,hasResult:Boolean):CommandFeedback {
        if(id!=this.id || (node.isNotBlank() && node!=nodeId)) return this
        if(action=="msg" && type=="runcommands" && hasResult) return copy(phase=CommandPhase.OUTPUT,output=result.take(64000),
            message=if(result.length>64000) "Salida recibida (limitada a 64000 caracteres)" else if(result.isEmpty()) "El agente devolvió una salida vacía" else "Salida recibida del agente")
        if(action=="runcommands" && hasResult) return if(result.equals("ok",true)) {
            if(busy) copy(phase=CommandPhase.ACCEPTED,message="Solicitud aceptada; esperando la salida…") else this
        } else if(phase!=CommandPhase.OUTPUT) copy(phase=CommandPhase.ERROR,message=result.take(1000)) else this
        return this
    }
    fun interrupted(message:String):CommandFeedback=if(busy) copy(phase=CommandPhase.UNKNOWN,message=message) else this
}
internal enum class ClipboardPhase { VERIFYING, VERIFIED, ERROR, UNKNOWN }
internal data class ClipboardFeedback(val id:String,val nodeId:String,val title:String,val transport:String,
    val phase:ClipboardPhase=ClipboardPhase.VERIFYING,val acceptedByAgent:Boolean=false,val mismatch:Boolean=false,
    val message:String="Texto enviado; esperando verificación…") {
    fun acknowledged(success:Boolean):ClipboardFeedback=if(phase!=ClipboardPhase.VERIFYING) this else if(success)
        copy(acceptedByAgent=true,message="El agente aceptó la petición; falta verificar el contenido.")
        else copy(phase=ClipboardPhase.ERROR,message="El agente ha rechazado la actualización del portapapeles.")
    fun readback(matches:Boolean):ClipboardFeedback=if(phase!=ClipboardPhase.VERIFYING) this else if(matches)
        copy(phase=ClipboardPhase.VERIFIED,message="El agente devuelve el texto enviado. Esto no garantiza que otra sesión gráfica comparta ese portapapeles.")
        else copy(mismatch=true,message="El agente devuelve un contenido diferente; verificando…")
    fun timeout():ClipboardFeedback=if(phase!=ClipboardPhase.VERIFYING) this else copy(phase=ClipboardPhase.UNKNOWN,message=when {
        mismatch -> "El agente sigue devolviendo un contenido diferente. No se confirma la actualización del portapapeles."
        acceptedByAgent -> "El agente aceptó el envío, pero no se pudo verificar la lectura del portapapeles."
        else -> "No se ha recibido confirmación. Revisa permisos ClipboardSet/ClipboardGet y la sesión gráfica del equipo remoto."
    })
}
internal fun clipboardPacket(text:String):ByteArray? {
    if('\u0000' in text) return null
    val bytes=text.toByteArray(Charsets.UTF_8)
    if(bytes.isEmpty()) return null
    val size=bytes.size+4
    if(size>65535) return null
    return ByteArray(size).apply {
        this[1]=9; this[2]=(size shr 8).toByte(); this[3]=size.toByte()
        bytes.copyInto(this,4)
    }
}
internal fun comparableClipboard(text:String):String=text.replace("\r\n","\n").replace("\r","\n")
