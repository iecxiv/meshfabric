package iecxiv.meshfabric.ui.login

import iecxiv.meshfabric.data.model.SessionData
import iecxiv.meshfabric.data.remote.FidoLoginHttpTransport

/** Called only inside the flow's guarded verification-success callback. */
internal object FidoLoginCompletion {
    fun complete(
        verified:FidoLoginHttpTransport.PendingLogin,
        remember:Boolean,
        persist:(SessionData)->Unit,
        forget:()->Unit,
        connect:(SessionData)->Unit,
        disconnect:()->Unit,
        success:()->Unit
    ) {
        val session=SessionData(verified.binding.server,verified.binding.account,verified.cookie,false,remember)
        try {
            connect(session)
            if(remember) persist(session) else forget()
            success()
        } catch(e:Exception) {
            runCatching { forget() }
            runCatching { disconnect() }
            throw e
        }
    }
}
