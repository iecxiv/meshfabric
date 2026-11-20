package iecxiv.meshfabric.ui.management
internal fun actionChannelConnecting(status:String)=status in setOf("Conectando acciones…","Connecting action channel…")
internal fun noticeChannelLabel(es:Boolean,connected:Boolean)=if(connected) {
    if(es) "Canal de gestión conectado. La recepción depende de los permisos y preferencias de la cuenta." else "Management channel connected. Reception depends on account permissions and preferences."
} else {
    if(es) "Canal de gestión desconectado. Los avisos guardados siguen disponibles." else "Management channel disconnected. Saved notices remain available."
}
