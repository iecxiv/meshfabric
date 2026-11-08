package iecxiv.meshfabric.ui.management

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal val USER_ADVANCED_FIELDS=listOf("flags","removeRights","siteadmin","quota","consent","groups")
internal val USER_REMOVAL_BITS=listOf(8L,65536L,256L,512L,1024L,4194304L,8388608L,16L,32768L,131072L,64L,262144L)
internal val USER_REMOVAL_MASK=USER_REMOVAL_BITS.fold(0L) { a,b->a or b }
internal fun userAdvancedMask(value:Any?):Long {
    if(value==null) return 0L
    require(value is Int || value is Long || value is Double)
    val n=(value as Number).toDouble()
    require(n.isFinite() && n%1.0==0.0 && n>=-2147483648.0 && n<=4294967295.0)
    return n.toLong() and 0xffffffffL
}
internal fun userDelegableMask(actor:Long,self:Boolean):Long {
    if(self) return 0L
    val a=userAdvancedMask(actor)
    if(a==0xffffffffL) return a
    if(a and 2L==0L) return 0L
    return 226L or (a and 768L)
}
internal fun userSiteChangeAllowed(actor:Long,old:Long,next:Long,self:Boolean)=
    (userAdvancedMask(old) xor userAdvancedMask(next)) and userDelegableMask(actor,self).inv() and 0xffffffffL==0L
internal fun userCanonicalRestrictions(value:Long):Long {
    var r=userAdvancedMask(value)
    if(r and 8L!=0L) r=r and (65536L or 256L or 512L or 1024L or 4194304L or 8388608L).inv()
    else if(r and 65536L!=0L) r=r and 256L.inv()
    return r and 0xffffffffL
}
internal fun userRealmValues(value:Any?):List<String> {
    if(value==null || value==JSONObject.NULL) return emptyList()
    require(value is JSONArray && value.length()<=512)
    return (0 until value.length()).map { require(value.opt(it) is String); value.getString(it) }.also { require(it.distinct().size==it.size) }.sorted()
}
internal fun userEditValue(u:JSONObject,key:String):Any?=when(key) {
    "quota"->userQuotaForEdit(if(u.isNull(key)) null else u.opt(key))
    "flags","removeRights","siteadmin","consent"->userAdvancedMask(if(u.isNull(key)) null else u.opt(key))
    "groups"->userRealmValues(u.opt(key))
    else->if(u.isNull(key)) "" else u.optString(key)
}
internal fun userEditFieldMatches(key:String,a:JSONObject,b:JSONObject):Boolean=
    runCatching { userEditValue(a,key)==userEditValue(b,key) }.getOrDefault(false)
internal fun userAdvancedSnapshot(u:JSONObject):JSONObject=JSONObject().apply {
    for(key in USER_ADVANCED_FIELDS) {
        val value=userEditValue(u,key)
        put(key,if(value is List<*>) JSONArray(value) else value ?: JSONObject.NULL)
    }
}
internal fun userAdvancedPacket(id:String,before:JSONObject,draft:JSONObject):JSONObject=JSONObject().put("action","edituser").put("id",id).put("_advancedBefore",JSONObject(before.toString())).apply {
    for(key in USER_ADVANCED_FIELDS) if(key=="quota" || !userEditFieldMatches(key,before,draft)) put(key,draft.get(key))
}
internal fun userAdvancedRealmAllowed(actor:JSONObject,target:JSONObject):Boolean {
    val realms=userRealmValues(actor.opt("groups"))
    return realms.isEmpty() || userRealmValues(target.opt("groups")).any { it in realms }
}
internal fun userCanEditRealms(s:ProfileState,u:JSONObject)=ProfilePermissions.normalized(s.site)==0xffffffffL || (s.user?.isNull("groups")==true && s.id!=u.optString("_id") && ProfilePermissions.normalized(u.optLong("siteadmin"))!=0xffffffffL)
internal fun userAdvancedAllowed(s:ProfileState,target:JSONObject?,p:JSONObject):Boolean=runCatching {
    require(target!=null && s.user!=null && canAdministerUser(s,target) && userAdvancedRealmAllowed(s.user,target))
    require(p.optString("action")=="edituser" && p.optString("id")==target.optString("_id"))
    require(p.keys().asSequence().all { it in USER_ADVANCED_FIELDS || it in setOf("action","id","_advancedBefore") })
    val before=p.getJSONObject("_advancedBefore")
    require(before.keys().asSequence().toSet()==USER_ADVANCED_FIELDS.toSet())
    require(USER_ADVANCED_FIELDS.all { userEditFieldMatches(it,target,before) })
    require(p.has("quota")); userEditValue(p,"quota")
    val changed=USER_ADVANCED_FIELDS.filter { p.has(it) && !userEditFieldMatches(it,target,p) }
    require(changed.isNotEmpty())
    for(key in changed) when(key) {
        "siteadmin"->require(userSiteChangeAllowed(userAdvancedMask(s.user.opt("siteadmin")),userAdvancedMask(target.opt(key)),userAdvancedMask(p.opt(key)),s.id==target.optString("_id")))
        "flags"->{ require(s.server?.optInt("usersSessionRecording")==1); require((userAdvancedMask(target.opt(key)) xor userAdvancedMask(p.opt(key))) and 2L.inv()==0L) }
        "removeRights"->{ val r=userAdvancedMask(p.opt(key)); require((r xor userAdvancedMask(target.opt(key))) and USER_REMOVAL_MASK.inv()==0L); require(r==userCanonicalRestrictions(r)) }
        "consent"->require((userAdvancedMask(target.opt(key)) xor userAdvancedMask(p.opt(key))) and 511L.inv()==0L)
        "groups"->{
            require(userCanEditRealms(s,target))
            val groups=userRealmValues(p.opt(key))
            require(groups.all { it.isNotBlank() && it.length<=64 && it==it.trim().lowercase(Locale.ROOT) && it.none(Char::isISOControl) })
        }
        "quota"->userEditValue(p,key)
    }
    true
}.getOrDefault(false)
