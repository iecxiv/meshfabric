package iecxiv.meshfabric.ui.management

internal fun profileListReadMatches(action:String,expected:String?,tag:Any?,responseid:Any?,hasData:Boolean):Boolean {
    if(action !in setOf("users","meshes") || expected.isNullOrBlank()) return false
    if(tag is String) return tag==expected
    if(tag!=null) return false
    return !hasData && responseid is String && responseid==expected
}
