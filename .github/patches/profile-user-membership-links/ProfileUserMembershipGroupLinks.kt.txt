package iecxiv.meshfabric.ui.management

import org.json.JSONObject

internal fun profileUserMembershipGroupIds(s:ProfileState,user:JSONObject):List<String>? {
    if(!s.ready || s.busy || s.loading || s.detailsBusy || s.userGroupPhase=="loading" || !canAdministerUser(s,user)) return null
    val groups=s.listedUserGroups ?: s.userGroups
    val links=user.optJSONObject("links")
    if(links==null && groups==null) return null
    val ids=mutableSetOf<String>()
    links?.keys()?.forEach {id->if(profileCreatedUserGroupId(s.id,id) && links.opt(id) is JSONObject) ids.add(id)}
    for(group in groups.orEmpty()) {
        val id=group.optString("_id")
        if(profileCreatedUserGroupId(s.id,id) && group.optJSONObject("links")?.opt(user.optString("_id")) is JSONObject) ids.add(id)
    }
    return ids.sorted()
}
