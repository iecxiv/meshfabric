package iecxiv.meshfabric.ui.management
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.json.JSONObject

internal class ProfileDisplayNames {
    private val received=linkedMapOf<String,String>()
    fun observe(records:List<JSONObject>) {
        for(record in records) {
            val id=record.optString("_id");val name=record.optString("name").trim()
            if(id.isNotBlank() && name.isNotBlank() && name!=id && !name.startsWith("mesh/") && !name.startsWith("ugrp/") && !name.startsWith("node/") && !name.startsWith("user/")) received[id]=name
        }
        while(received.size>4096) received.remove(received.keys.first())
    }
    fun label(id:String,es:Boolean):String=received[id]?.let {"«"+it+"»"} ?: when {
        id.startsWith("mesh/")->if(es) "grupo de dispositivos (nombre no disponible)" else "device group (name unavailable)"
        id.startsWith("ugrp/")->if(es) "grupo de usuarios (nombre no disponible)" else "user group (name unavailable)"
        id.startsWith("node/")->if(es) "dispositivo (nombre no disponible)" else "device (name unavailable)"
        id.startsWith("user/")->if(es) "usuario (nombre no disponible)" else "user (name unavailable)"
        else->if(es) "elemento (nombre no disponible)" else "item (name unavailable)"
    }
}
@Composable internal fun rememberProfileDisplayNames(s:ProfileState,server:String):ProfileDisplayNames {
    val names=remember(s.id,server) {ProfileDisplayNames()}
    names.observe(s.groups+s.users+s.listedUserGroups.orEmpty()+s.userGroups.orEmpty()+s.nodes.orEmpty()+listOfNotNull(s.user))
    return names
}
