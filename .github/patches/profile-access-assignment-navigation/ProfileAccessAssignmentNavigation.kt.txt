package iecxiv.meshfabric.ui.management

internal fun profileAccessAssignmentPair(s:ProfileState,gid:String,mid:String):Pair<String,String>? {
    if(!s.canWrite || s.detailsBusy || !profileGroupMeshId(s.id,mid) || profileReceivedUserGroup(s,gid)==null) return null
    val mesh=s.groups.singleOrNull {it.optString("_id")==mid} ?: return null
    if(mesh.optJSONObject("links")?.optJSONObject(gid)==null) return null
    return gid to mid
}
