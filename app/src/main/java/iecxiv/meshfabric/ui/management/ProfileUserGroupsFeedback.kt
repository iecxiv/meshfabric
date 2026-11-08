package iecxiv.meshfabric.ui.management

internal enum class UserGroupsRefreshAction { NONE, RECONNECT, REFRESH }
internal fun userGroupsRefreshAction(ready:Boolean,busy:Boolean,loading:Boolean,phase:String,notice:String):UserGroupsRefreshAction=when {
    busy || loading || phase=="loading" || notice=="connecting"->UserGroupsRefreshAction.NONE
    !ready->UserGroupsRefreshAction.RECONNECT
    else->UserGroupsRefreshAction.REFRESH
}
internal fun userGroupsNoticeIsError(notice:String)=notice in setOf("unknown","rejected","readFailed","timeout")
internal fun userGroupsNoticeText(notice:String,es:Boolean):String {
    fun t(a:String,b:String)=if(es) a else b
    return when(notice) {
        "sent"->t("Solicitud enviada; ejecución todavía sin confirmar.","Request sent; execution remains unconfirmed.")
        "unknown"->t("Resultado sin confirmar. Reconecta y comprueba el estado antes de repetir. No hay reenvíos automáticos.","Outcome unconfirmed. Reconnect and check state before repeating. No automatic resends.")
        "rejected"->t("El servidor rechazó la solicitud. No se considera completada.","The server rejected the request. It is not considered completed.")
        "readFailed"->t("Datos incompletos: modificaciones bloqueadas. Usa Actualizar para volver a consultar.","Incomplete data: changes are blocked. Use Refresh to read again.")
        "connecting"->t("Conectando la sesión de Perfil…","Connecting the Profile session…")
        "disconnected"->t("Sesión de Perfil desconectada. Usa Reconectar; si no conecta, comprueba la conexión principal al servidor.","Profile session disconnected. Use Reconnect; if it cannot connect, check the main server connection.")
        "timeout"->t("Se agotó la espera al conectar la sesión de Perfil. Puedes volver a conectar; no se reenvían operaciones anteriores.","Profile session connection timed out. You can reconnect; previous operations are not resent.")
        "confirmed"->t("Solicitud confirmada por el servidor; actualizando datos.","Server acknowledged the request; refreshing data.")
        "observed"->t("Se observó el estado solicitado en el servidor.","The requested state was observed on the server.")
        "broadcastAccepted"->t("El servidor aceptó la difusión. No acredita recepción ni lectura.","The server accepted the broadcast. Receipt and reading are not established.")
        else->""
    }
}
