package iecxiv.meshfabric.ui.management
import org.json.JSONObject

internal val DEVICE_GROUP_ADVANCED_FIELDS=setOf("flags","consent","expireDevs")
internal fun deviceGroupAdvancedFlagMask(type:Int,recording:Boolean):Long {
    require(type in 1..4)
    return (when(type) {2->27L;4->3L;else->0L}) or (if(recording && type!=4) 4L else 0L)
}
internal fun deviceGroupAdvancedMergeMask(before:Long,selected:Long,allowed:Long):Long? {
    if(before !in 0L..0xffffffffL || allowed !in 0L..0xffffffffL || selected<0L || selected and allowed!=selected) return null
    return (before and allowed.inv() and 0xffffffffL) or selected
}
private fun advancedInteger(raw:Any?,max:Long):Long {
    require(raw is Int || raw is Long || raw is Double)
    val n=(raw as Number).toDouble()
    require(n.isFinite() && n%1.0==0.0 && n>=0.0 && n<=max.toDouble())
    return n.toLong()
}
internal fun deviceGroupAdvancedValue(mesh:JSONObject,key:String):Long=when(key) {
    "flags","consent"->if(!mesh.has(key)) 0L else userAdvancedMask(mesh.get(key))
    "expireDevs"->if(!mesh.has(key)) 0L else advancedInteger(mesh.get(key),2000L)
    "mtype"->advancedInteger(mesh.get(key),4L).also {require(it>=1L)}
    else->error("Unsupported field")
}
internal fun deviceGroupAdvancedSnapshot(mesh:JSONObject)=JSONObject().put("_id",mesh.getString("_id")).apply {
    for(key in listOf("mtype","flags","consent","expireDevs")) put(key,deviceGroupAdvancedValue(mesh,key))
}.toString()
private fun advancedRecording(s:ProfileState)=(s.server?.opt("devGroupSessionRecording") as? Number)?.toDouble()==1.0
internal fun deviceGroupAdvancedContext(s:ProfileState)=JSONObject().put("recording",advancedRecording(s)).put("consent",userAdvancedMask(s.server?.opt("consent"))).put("autoRemove",s.server?.opt("autoremoveinactivedevices") ?: JSONObject.NULL).toString()
internal fun deviceGroupAdvancedPacket(s:ProfileState,mesh:JSONObject,changes:JSONObject,server:String,confirmed:Boolean=false):JSONObject {
    require(changes.length()>0 && changes.keys().asSequence().all {it in DEVICE_GROUP_ADVANCED_FIELDS})
    return JSONObject().put("action","editmesh").put("meshid",mesh.getString("_id")).put("_advancedActor",s.id).put("_advancedServer",server).put("_advancedBefore",deviceGroupAdvancedSnapshot(mesh)).put("_advancedContext",deviceGroupAdvancedContext(s)).put("_advancedConfirmed",confirmed).apply {
        for(key in changes.keys()) put(key,deviceGroupAdvancedValue(changes,key))
    }
}
internal fun deviceGroupAdvancedAllowed(s:ProfileState,p:JSONObject,server:String):Boolean=runCatching {
    val metadata=setOf("action","meshid","_advancedActor","_advancedServer","_advancedBefore","_advancedContext","_advancedConfirmed")
    val keys=p.keys().asSequence().toSet()
    require(keys.containsAll(metadata) && keys.all {it in metadata || it in DEVICE_GROUP_ADVANCED_FIELDS})
    require(s.canWrite && !s.detailsBusy && s.user!=null && p.opt("_advancedConfirmed")==true && p.optString("action")=="editmesh")
    require(server.isNotBlank() && p.getString("_advancedServer")==server && p.getString("_advancedActor")==s.id)
    val mid=p.getString("meshid");require(profileGroupMeshId(s.id,mid))
    val mesh=requireNotNull(s.groups.singleOrNull {it.optString("_id")==mid})
    require(p.getString("_advancedBefore")==deviceGroupAdvancedSnapshot(mesh) && p.getString("_advancedContext")==deviceGroupAdvancedContext(s))
    val authority=mesh.optJSONObject("links")?.optJSONObject(s.id)?.opt("rights")
    val rights=if(authority is Number) userAdvancedMask(authority) else 0L
    require(ProfilePermissions.editGroup(userAdvancedMask(s.user.opt("siteadmin")),rights))
    val fields=keys.intersect(DEVICE_GROUP_ADVANCED_FIELDS);require(fields.isNotEmpty())
    require(fields.any {deviceGroupAdvancedValue(mesh,it)!=deviceGroupAdvancedValue(p,it)})
    if("flags" in fields || "expireDevs" in fields) {
        require("flags" in fields && "expireDevs" in fields)
        val type=deviceGroupAdvancedValue(mesh,"mtype").toInt()
        val before=deviceGroupAdvancedValue(mesh,"flags");val next=deviceGroupAdvancedValue(p,"flags")
        require((before xor next) and deviceGroupAdvancedFlagMask(type,advancedRecording(s)).inv() and 0xffffffffL==0L)
        if(type==2) require(next and 24L==0L || next and 2L!=0L)
        require(next and 1L==0L || deviceGroupAdvancedValue(p,"expireDevs")==0L)
    }
    if("consent" in fields) {
        val before=deviceGroupAdvancedValue(mesh,"consent");val next=deviceGroupAdvancedValue(p,"consent")
        require((before xor next) and 511L.inv() and 0xffffffffL==0L)
        val mandatory=userAdvancedMask(s.server?.opt("consent")) and 511L
        require(next and mandatory==mandatory)
    }
    true
}.getOrDefault(false)
internal fun deviceGroupAdvancedWire(p:JSONObject)=JSONObject().put("action","editmesh").put("meshid",p.getString("meshid")).apply {
    for(key in DEVICE_GROUP_ADVANCED_FIELDS) if(p.has(key)) put(key,deviceGroupAdvancedValue(p,key))
}
internal fun deviceGroupAdvancedAck(p:JSONObject,request:String)=request.isNotBlank() && p.optString("action")=="editmesh" && p.optString("responseid")==request && p.optString("result").equals("ok",true) && !p.has("error") && !p.has("err")
internal fun deviceGroupAdvancedReadbackReady(s:ProfileState,p:JSONObject,ack:Boolean,meshRead:Boolean):Boolean=runCatching {
    require(ack && meshRead && s.ready && !s.loading && !s.readError)
    val mesh=requireNotNull(s.groups.singleOrNull {it.optString("_id")==p.getString("meshid")})
    require(deviceGroupAdvancedValue(mesh,"mtype")==JSONObject(p.getString("_advancedBefore")).getLong("mtype"))
    val fields=DEVICE_GROUP_ADVANCED_FIELDS.filter {p.has(it)};require(fields.isNotEmpty())
    fields.all {deviceGroupAdvancedValue(mesh,it)==deviceGroupAdvancedValue(p,it)}
}.getOrDefault(false)
