package iecxiv.meshfabric.ui.management
import org.json.JSONObject
import java.util.Locale
internal data class ProfileReviewIdentity(val label:String,val usable:Boolean)
private fun reviewKey(value:String)=value.trim().lowercase(Locale.ROOT)
private fun reviewText(value:Any?):String {
    val text=(value as? String)?.trim().orEmpty()
    return text.takeUnless {it.any {c->c.isISOControl()} || listOf("user/","ugrp/","mesh/","node/").any {prefix->it.startsWith(prefix,true)}}.orEmpty()
}
internal fun profileReviewIdentity(record:JSONObject?,all:List<JSONObject>,es:Boolean):ProfileReviewIdentity {
    val id=record?.optString("_id").orEmpty()
    val kind=id.substringBefore('/')
    val name=reviewText(record?.opt("name"))
    val missing=if(es) "nombre no disponible" else "name unavailable"
    if(name.isBlank() || kind !in listOf("user","ugrp","mesh","node")) return ProfileReviewIdentity(missing,false)
    fun context(r:JSONObject):String {
        val keys=if(kind=="user") listOf("realname","email") else listOf("desc")
        return keys.map {reviewText(r.opt(it))}.filter {it.isNotBlank()}.distinct().joinToString(" · ")
    }
    val peers=all.filter {it.optString("_id").substringBefore('/')==kind && reviewKey(reviewText(it.opt("name")))==reviewKey(name)}.distinctBy {it.optString("_id")}
    val detail=context(requireNotNull(record))
    val current=peers.singleOrNull {it.optString("_id")==id}
    val matchesCurrent=current!=null && reviewKey(context(current))==reviewKey(detail)
    val unique=peers.size==1 || (detail.isNotBlank() && peers.count {reviewKey(context(it))==reviewKey(detail)}==1)
    return ProfileReviewIdentity(name+if(detail.isNotBlank()) " · "+detail else "",matchesCurrent && unique)
}
