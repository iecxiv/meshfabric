package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode

internal enum class AgentConsoleAction(val wire:String) {
    DEFAULT_CORE("uploadagentcore:default"),CLEAR_CORE("uploadagentcore:clear"),RECOVERY_CORE("uploadagentcore:recovery"),TINY_CORE("uploadagentcore:tiny"),
    SOFT_DISCONNECT("agentdisconnect:1"),HARD_DISCONNECT("agentdisconnect:2"),RESTART_SERVICE("console:service restart"),FORCE_UPDATE("updateAgents")
}
internal fun agentActionFullRights(rights:Long)=rights and 0xffffffffL==0xffffffffL
internal fun agentActionAllowed(action:AgentConsoleAction,rights:Long,siteadmin:Int):Boolean {
    if(rights and 16L==0L) return false
    return when(action) {
        AgentConsoleAction.SOFT_DISCONNECT,AgentConsoleAction.HARD_DISCONNECT->siteadmin==-1
        AgentConsoleAction.FORCE_UPDATE->agentActionFullRights(rights)
        else->true
    }
}
internal fun agentActionPacket(node:String,action:AgentConsoleAction,meta:GeneralMeta,inventory:List<Pair<MeshGroup,List<MeshNode>>>,siteadmin:Int):JsonObject {
    require(node.startsWith("node/") && node.split('/').size==3 && node.substringAfterLast('/').isNotBlank() && node.length<=4096 && node.none {it.isISOControl()})
    require(agentConsoleAvailable(node,meta,inventory) && agentActionAllowed(action,meta.rights,siteadmin))
    return when(action) {
        AgentConsoleAction.RESTART_SERVICE->agentConsolePacket(node,"service restart",meta,inventory)
        AgentConsoleAction.SOFT_DISCONNECT,AgentConsoleAction.HARD_DISCONNECT->JsonObject().apply {addProperty("action","agentdisconnect");addProperty("nodeid",node);addProperty("disconnectMode",if(action==AgentConsoleAction.SOFT_DISCONNECT) 1 else 2)}
        AgentConsoleAction.FORCE_UPDATE->JsonObject().apply {addProperty("action","updateAgents");add("nodeids",JsonArray().apply {add(node)})}
        else->JsonObject().apply {addProperty("action","uploadagentcore");add("nodeids",JsonArray().apply {add(node)});addProperty("type",action.wire.substringAfter(':'))}
    }
}
internal fun agentActionLabel(a:AgentConsoleAction,es:Boolean)=when(a) {
    AgentConsoleAction.DEFAULT_CORE->if(es) "Cargar núcleo predeterminado" else "Upload default core"
    AgentConsoleAction.CLEAR_CORE->if(es) "Borrar núcleo" else "Clear core"
    AgentConsoleAction.RECOVERY_CORE->if(es) "Cargar núcleo de recuperación" else "Upload recovery core"
    AgentConsoleAction.TINY_CORE->if(es) "Cargar núcleo reducido" else "Upload tiny core"
    AgentConsoleAction.SOFT_DISCONNECT->if(es) "Desconectar agente (Soft)" else "Soft disconnect agent"
    AgentConsoleAction.HARD_DISCONNECT->if(es) "Desconectar agente (Hard)" else "Hard disconnect agent"
    AgentConsoleAction.RESTART_SERVICE->if(es) "Reiniciar servicio del agente" else "Restart agent service"
    AgentConsoleAction.FORCE_UPDATE->if(es) "Forzar actualización del agente" else "Force agent update"
}
internal fun agentActionDetail(a:AgentConsoleAction,es:Boolean)=when(a) {
    AgentConsoleAction.DEFAULT_CORE->if(es) "El servidor reconstruye y envía su núcleo predeterminado. El agente puede perder funciones mientras se aplica." else "The server rebuilds and sends its default core. The agent may lose features while it is applied."
    AgentConsoleAction.CLEAR_CORE->if(es) "Borra el núcleo del agente. Sin núcleo puede quedar sin escritorio, terminal, archivos ni esta consola hasta cargar otro." else "Clears the agent core. Without a core it may lose desktop, terminal, files and this console until another core is loaded."
    AgentConsoleAction.RECOVERY_CORE->if(es) "Carga el núcleo de recuperación del servidor, de funciones reducidas." else "Loads the server recovery core, which has reduced features."
    AgentConsoleAction.TINY_CORE->if(es) "Carga el núcleo reducido (tiny) del servidor, de funciones reducidas." else "Loads the server tiny core, which has reduced features."
    AgentConsoleAction.SOFT_DISCONNECT->if(es) "El servidor cierra el canal WebSocket del agente. El agente normalmente reconecta por sí mismo." else "The server closes the agent WebSocket channel. The agent normally reconnects by itself."
    AgentConsoleAction.HARD_DISCONNECT->if(es) "El servidor cierra el socket TCP del agente. El agente normalmente reconecta por sí mismo." else "The server closes the agent TCP socket. The agent normally reconnects by itself."
    AgentConsoleAction.RESTART_SERVICE->if(es) "Envía «service restart» a la consola del agente. El agente se reiniciará y esta consola se cerrará." else "Sends \"service restart\" to the agent console. The agent will restart and this console will close."
    AgentConsoleAction.FORCE_UPDATE->if(es) "Pide al servidor forzar la actualización del agente. Requiere derechos completos y que el servidor no tenga desactivadas las actualizaciones." else "Asks the server to force the agent update. Requires full rights and a server with agent updates enabled."
}
