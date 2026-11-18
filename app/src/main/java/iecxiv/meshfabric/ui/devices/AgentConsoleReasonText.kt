package iecxiv.meshfabric.ui.devices

internal fun agentConsoleReasonText(code:String,es:Boolean):String {
    val text=when(code) {
        "identity"->"Ha cambiado la identidad de la sesión." to "The session identity has changed."
        "disconnected"->"Se ha perdido la conexión o el agente ya no está disponible." to "The connection was lost or the agent is no longer available."
        "timeout"->"Se agotó el tiempo de verificación." to "Verification timed out."
        "unavailable"->"La sesión o el dispositivo no están disponibles para la consola." to "The session or device is unavailable for the console."
        "connection"->"No se pudo establecer o mantener la conexión con la consola." to "The console connection could not be established or maintained."
        "invalid"->"Se recibió una respuesta no válida." to "An invalid response was received."
        "denied"->"El acceso, el agente o los permisos de consola no están disponibles." to "Access, the agent or console permissions are unavailable."
        "unverified"->"No se pudo verificar el dispositivo y sus permisos." to "The device and its permissions could not be verified."
        "changed"->"Han cambiado las condiciones para ejecutar la acción." to "The conditions for performing the action have changed."
        "local"->"Consola desconectada desde la aplicación." to "Console disconnected from the app."
        "closed"->"Consola cerrada." to "Console closed."
        else->"Consola desconectada por un motivo no identificado." to "Console disconnected for an unidentified reason."
    }
    return if(es) text.first else text.second
}
