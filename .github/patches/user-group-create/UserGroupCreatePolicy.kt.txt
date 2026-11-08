package iecxiv.meshfabric.ui.management

import org.json.JSONObject

internal fun profileCreatedUserGroupId(actor:String,id:String)=profileUserGroupDomainAllowed(actor,id) && id.length<=1024 && id.split('/')[2].isNotBlank() && !id.contains('\u0000')
internal fun profileUserGroupCreateContext(s:ProfileState,actor:String,server:String,beforeServer:String):Boolean=runCatching {
    require(s.canWrite && s.userGroupPhase=="ready" && s.user!=null && profileUserGroupEditRole(userAdvancedMask(s.user.opt("siteadmin"))))
    require(actor==s.id && actor.startsWith("user/") && actor.split('/').size==3 && actor.split('/')[2].isNotBlank())
    require(server.isNotBlank() && server==beforeServer)
    true
}.getOrDefault(false)
internal fun profileUserGroupCreatePacket(actor:String,server:String,name:String,desc:String,confirmed:Boolean=false)=JSONObject().put("action","createusergroup").put("name",name).put("desc",desc).put("_groupActor",actor).put("_groupServer",server).put("_groupConfirmed",confirmed)
internal fun profileUserGroupCreateAllowed(s:ProfileState,p:JSONObject,server:String):Boolean=runCatching {
    require(p.keys().asSequence().toSet()==setOf("action","name","desc","_groupActor","_groupServer","_groupConfirmed"))
    require(p.optString("action")=="createusergroup" && p.opt("_groupConfirmed")==true)
    require(p.opt("name") is String && profileUserGroupNameValid(p.getString("name")) && p.opt("desc") is String && profileUserGroupDescValid(p.getString("desc")))
    profileUserGroupCreateContext(s,p.getString("_groupActor"),server,p.getString("_groupServer"))
}.getOrDefault(false)
internal fun profileUserGroupCreateWire(p:JSONObject)=JSONObject().put("action","createusergroup").put("name",p.getString("name")).put("desc",p.getString("desc"))
