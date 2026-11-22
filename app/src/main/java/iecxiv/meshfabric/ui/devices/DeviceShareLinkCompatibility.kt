package iecxiv.meshfabric.ui.devices
import com.google.gson.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
internal fun generalShareCompatibleUrl(server:String,id:String,url:String):String {
    require(id.length<=1024 && id.none {it.isISOControl()} && url.isNotBlank() && url.length<=16384 && url.none {it.isISOControl()})
    val base=requireNotNull(server.toHttpUrlOrNull())
    require(base.scheme=="https" && base.username.isEmpty() && base.password.isEmpty() && base.query==null && base.fragment==null)
    val link=requireNotNull(base.newBuilder().encodedPath(base.encodedPath.trimEnd('/')+"/").build().resolve(url))
    require(link.scheme=="https" && base.host==link.host && base.port==link.port && link.username.isEmpty() && link.password.isEmpty() && link.fragment==null)
    require(link.encodedPath==base.encodedPath.trimEnd('/')+"/sharing")
    if(link.queryParameterNames==setOf("c")) require(link.queryParameterValues("c").size==1 && !link.queryParameter("c").isNullOrBlank())
    else require(link.queryParameterNames==setOf("id") && id.isNotBlank() && link.queryParameterValues("id").size==1 && link.queryParameter("id")==id)
    return link.toString()
}
internal fun generalShareResolveCreated(p:JsonObject,node:String,request:String,actor:String,d:GeneralShareCreateDraft,reply:GeneralShareCreatedLink,before:Set<String>,server:String):GeneralShareCreatedLink? {
    val rows=generalShareRevokeList(p,node,request,true) ?: return null
    val candidates=rows.filter {it.id !in before && (reply.id.isEmpty() || it.id==reply.id)}.filter {row->
        val raw=p.getAsJsonArray("deviceShares").single {it.asJsonObject.get("publicid").asString==row.id}.asJsonObject
        val url=raw.get("url")?.takeIf {it.isJsonPrimitive && it.asJsonPrimitive.isString}?.asString ?: return@filter false
        runCatching {generalShareCompatibleUrl(server,row.id,url)==reply.url && generalShareCreateVerified(p,node,request,actor,d,row.id,before,true)}.getOrDefault(false)
    }
    return candidates.singleOrNull()?.let {GeneralShareCreatedLink(it.id,reply.url)}
}
