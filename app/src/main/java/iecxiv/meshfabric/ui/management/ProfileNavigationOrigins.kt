package iecxiv.meshfabric.ui.management

import org.json.JSONObject

internal data class ProfileOrigin(val user:ProfileForm?=null,val groupId:String="",val groupTab:Int=0)
internal fun profilePushOrigin(stack:List<ProfileOrigin>,origin:ProfileOrigin)=(stack+origin).takeLast(16)
internal fun profilePopOrigin(stack:List<ProfileOrigin>,users:Set<String>,groups:Set<String>,ready:Boolean):Pair<ProfileOrigin?,List<ProfileOrigin>> {
    if(!ready) return null to emptyList()
    var remaining=stack
    while(remaining.isNotEmpty()) {
        val origin=remaining.last();remaining=remaining.dropLast(1)
        val user=origin.user
        val valid=if(user!=null) origin.groupId.isEmpty() && user.users && user.operation=="edit" && user.id.startsWith("user/") && user.id.split('/').size==3 && user.id.split('/')[2].isNotBlank() && user.id in users else origin.groupTab in 0..2 && origin.groupId.startsWith("mesh/") && origin.groupId.split('/').size==3 && origin.groupId.split('/')[2].isNotBlank() && origin.groupId in groups
        if(valid) return origin to remaining
    }
    return null to emptyList()
}
internal fun profileAccessUserTarget(s:ProfileState,id:String):JSONObject?=if(!s.canWrite) null else s.users.singleOrNull {it.optString("_id")==id && canAdministerUser(s,it)}
