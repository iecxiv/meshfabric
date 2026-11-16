package iecxiv.meshfabric.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal class FidoWebOriginPolicy(server:String,ignoreSsl:Boolean=false) {
    private val base:HttpUrl=requireNotNull(server.toHttpUrlOrNull())
    val entryUrl:String
    val origin:String
    init {
        require(!ignoreSsl && base.scheme=="https")
        require(base.username.isEmpty() && base.password.isEmpty() && base.query==null && base.fragment==null)
        entryUrl=base.toString()
        origin=base.newBuilder().encodedPath("/").build().toString().removeSuffix("/")
    }
    private fun same(url:HttpUrl)=url.scheme=="https" && url.host==base.host && url.port==base.port && url.username.isEmpty() && url.password.isEmpty()
    fun allowsNavigation(value:String):Boolean=value.toHttpUrlOrNull()?.let(::same)==true
    fun allowsOrigin(value:String):Boolean=value.toHttpUrlOrNull()?.let {
        same(it) && it.encodedPath=="/" && it.query==null && it.fragment==null
    }==true
    fun allowsMessage(sourceOrigin:String,isMainFrame:Boolean)=isMainFrame && allowsOrigin(sourceOrigin)
}
