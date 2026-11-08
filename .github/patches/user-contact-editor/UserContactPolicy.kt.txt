package iecxiv.meshfabric.ui.management

import org.json.JSONObject

internal val USER_CONTACT_FIELDS=listOf("email","emailVerified","phone","quota")
internal fun userPhoneInputAllowed(value:String)=value.isEmpty() || Regex("[+]?[0-9]{5,17}").matches(value)
internal fun userVerificationAllowed(email:String,enabled:Boolean,next:Boolean)=enabled && (!next || email.isNotBlank())
internal fun userContactValue(u:JSONObject,key:String):Any?=when(key) {
    "quota"->userQuotaForEdit(if(u.isNull(key)) null else u.opt(key))
    "emailVerified"->{ val v=if(u.isNull(key)) false else u.opt(key); require(v is Boolean); v }
    else->{ val v=if(u.isNull(key)) "" else u.opt(key); require(v is String); v }
}
internal fun userContactFieldMatches(key:String,a:JSONObject,b:JSONObject)=runCatching { userContactValue(a,key)==userContactValue(b,key) }.getOrDefault(false)
internal fun userContactSnapshot(u:JSONObject)=JSONObject().apply { for(key in USER_CONTACT_FIELDS) put(key,userContactValue(u,key) ?: JSONObject.NULL) }
internal fun userContactPacket(id:String,before:JSONObject,field:String,value:Any)=JSONObject().put("action","edituser").put("id",id).put("_contactBefore",JSONObject(before.toString())).put("quota",before.get("quota")).put(field,value)
internal fun userContactAllowed(s:ProfileState,target:JSONObject?,p:JSONObject):Boolean=runCatching {
    require(s.canWrite && target!=null && s.user!=null && canAdministerUser(s,target) && userAdvancedRealmAllowed(s.user,target))
    require(p.optString("action")=="edituser" && p.optString("id")==target.optString("_id"))
    val editable=listOf("phone","emailVerified").filter(p::has)
    require(editable.size==1)
    val field=editable.single()
    require(p.keys().asSequence().toSet()==setOf("action","id","_contactBefore","quota",field))
    val before=p.getJSONObject("_contactBefore")
    require(before.keys().asSequence().toSet()==USER_CONTACT_FIELDS.toSet())
    require(USER_CONTACT_FIELDS.all { userContactFieldMatches(it,target,before) })
    require(userContactFieldMatches("quota",target,p))
    require(!userContactFieldMatches(field,target,p))
    if(field=="phone") require(userPhoneInputAllowed(userContactValue(p,field) as String))
    else require(userVerificationAllowed(userContactValue(target,"email") as String,s.server?.opt("emailcheck")==true,userContactValue(p,field) as Boolean))
    true
}.getOrDefault(false)
