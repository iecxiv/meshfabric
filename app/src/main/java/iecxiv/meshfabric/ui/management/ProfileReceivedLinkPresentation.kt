package iecxiv.meshfabric.ui.management
import org.json.JSONObject
internal data class ProfileReceivedLinkPresentation(val label:String,val usable:Boolean)
internal fun profileReceivedLinkPresentation(s:ProfileState,owner:JSONObject,id:String,es:Boolean):ProfileReceivedLinkPresentation {
    val kind=id.substringBefore('/')
    val records=when(kind) {
        "user"->s.users
        "mesh"->s.groups
        "ugrp"->s.listedUserGroups ?: s.userGroups.orEmpty()
        "node"->s.nodes.orEmpty()
        else->emptyList()
    }
    val current=records.singleOrNull {it.optString("_id")==id}
    val reported=owner.optJSONObject("links")?.optJSONObject(id)?.let {JSONObject(it.toString()).put("_id",id)}
    val identity=profileReviewIdentity(current ?: reported,if(current!=null) records else listOfNotNull(reported),es)
    val type=when(kind) {
        "user"->if(es) "Usuario: " else "User: "
        "mesh"->if(es) "Grupo de dispositivos: " else "Device group: "
        "ugrp"->if(es) "Grupo de usuarios: " else "User group: "
        "node"->if(es) "Dispositivo: " else "Device: "
        else->if(es) "Enlace: " else "Link: "
    }
    return ProfileReceivedLinkPresentation(type+identity.label,current!=null && identity.usable)
}
