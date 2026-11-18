package iecxiv.meshfabric.ui.management

import org.json.JSONArray

internal data class AccountSecurityKeyChoice(val id:Long,val name:String)
internal fun accountSecurityKeyChoices(keys:JSONArray?):List<AccountSecurityKeyChoice>? {
    if(keys==null || securityKeyIds(keys)==null) return null
    return (0 until keys.length()).map {i->
        val item=keys.getJSONObject(i)
        val raw=item.opt("name")
        if(raw!=null && raw!=org.json.JSONObject.NULL && raw !is String) return null
        val name=(raw as? String).orEmpty()
        if(name.length>128 || name.any {it.isISOControl()}) return null
        AccountSecurityKeyChoice(requireNotNull(securityKeyId(item)),name)
    }
}
internal fun accountSecurityKeyCanRemove(current:List<AccountSecurityKeyChoice>?,reviewed:AccountSecurityKeyChoice?,enabled:Boolean)=enabled && reviewed!=null && current?.count {it==reviewed}==1
