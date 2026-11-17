package iecxiv.meshfabric.ui.devices

import iecxiv.meshfabric.ui.management.*
import org.json.JSONObject

internal data class GeneralAuthorizationRow(val subject:String,val label:String,val rights:Long?,val inherited:Boolean)
private fun authorizationId(id:String,prefix:String,domain:String):Boolean {
    val p=id.split('/')
    return p.size==3 && p[0]==prefix && p[1]==domain && p[2].isNotBlank() && id.length<=4096 && id.none {it.isISOControl()}
}
internal fun generalAuthorizationScope(s:ProfileState,actor:String,nodeId:String,meshId:String,bound:Boolean):ProfileState? {
    val parts=actor.split('/')
    if(!bound || !s.ready || s.loading || s.readError || s.detailsBusy || s.id!=actor || parts.size!=3 || parts[0]!="user" || parts[2].isBlank()) return null
    if(!authorizationId(nodeId,"node",parts[1]) || !authorizationId(meshId,"mesh",parts[1])) return null
    val node=s.nodes.orEmpty().singleOrNull {it.optString("_id")==nodeId && it.optString("meshid")==meshId} ?: return null
    if(s.nodes.orEmpty().count {it.optString("_id")==nodeId}!=1) return null
    val mesh=s.groups.singleOrNull {it.optString("_id")==meshId} ?: return null
    if(listOf(node,mesh).any {it.has("links") && !it.isNull("links") && it.optJSONObject("links")==null}) return null
    return s.copy(nodes=listOf(node),groups=listOf(mesh))
}
internal fun generalAuthorizationRows(s:ProfileState):List<GeneralAuthorizationRow> {
    val node=s.nodes.orEmpty().singleOrNull() ?: return emptyList()
    val mesh=s.groups.singleOrNull() ?: return emptyList()
    val domain=s.id.split('/').getOrNull(1) ?: return emptyList()
    val records=s.users+s.userGroups.orEmpty()+s.listedUserGroups.orEmpty()
    return listOf(node to false,mesh to true).flatMap {(record,inherited)->
        val links=record.optJSONObject("links") ?: return@flatMap emptyList<GeneralAuthorizationRow>()
        links.keys().asSequence().filter {authorizationId(it,"user",domain) || authorizationId(it,"ugrp",domain)}.map {id->
            val link=links.optJSONObject(id)
            val raw=link?.opt("rights")
            val number=(raw as? Number)?.toDouble()
            val rights=number?.takeIf {it.isFinite() && it%1.0==0.0 && it>=-1.0 && it<=4294967295.0}?.toLong()?.let {it and 0xffffffffL}
            val resolved=records.singleOrNull {it.optString("_id")==id}
            val name=(resolved?.opt("name") as? String).orEmpty().ifBlank {(link?.opt("name") as? String).orEmpty()}
            GeneralAuthorizationRow(id,if(name.isBlank()) id else name+" ("+id+")",rights,inherited)
        }.toList()
    }.sortedWith(compareBy<GeneralAuthorizationRow> {it.inherited}.thenBy {it.label})
}
internal fun generalAuthorizationCandidates(s:ProfileState,es:Boolean):List<JSONObject> {
    if(!s.canWrite || s.detailsBusy || !ProfilePermissions.manageUsers(s.site)) return emptyList()
    val node=s.nodes.orEmpty().singleOrNull() ?: return emptyList()
    if(s.groups.size!=1 || !canGrantDevice(s,node)) return emptyList()
    val domain=s.id.split('/').getOrNull(1) ?: return emptyList()
    val links=node.optJSONObject("links")
    return s.users.filter {user->
        val id=user.optString("_id")
        authorizationId(id,"user",domain) && id!=s.id && links?.has(id)!=true &&
            s.users.count {it.optString("_id")==id}==1 && canAdministerUser(s,user) && profileReviewIdentity(user,s.users,es).usable
    }
}
