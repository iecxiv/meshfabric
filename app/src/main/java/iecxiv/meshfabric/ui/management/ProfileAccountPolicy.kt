package iecxiv.meshfabric.ui.management

internal val profileSectionKeys=listOf("account","groups","users","userGroups")
internal data class NativeBackupCode(val code:String,val available:Boolean)
private fun nativeBackupCodeText(value:Any?):String?=when(value) {
is String->value.takeIf { it.matches(Regex("[0-9]{1,8}")) }?.padStart(8,'0')
is Number->{ val n=value.toDouble(); if(!n.isFinite() || n<0 || n>99999999 || n%1.0!=0.0) null else n.toLong().toString().padStart(8,'0') }
else->null
}
internal fun nativeBackupCodes(values:List<Any?>):List<NativeBackupCode>? {
if(values.size>1000) return null
val codes=values.map { value->
if(value is Map<*,*>) {
val available=value["u"] as? Boolean ?: return null
val code=nativeBackupCodeText(value["p"]) ?: return null
NativeBackupCode(code,available)
} else NativeBackupCode(nativeBackupCodeText(value) ?: return null,true)
}
if(codes.map { it.code }.distinct().size!=codes.size) return null
return codes
}
internal fun nativeBackupCopyValues(codes:List<NativeBackupCode>):List<String> = codes.filter { it.available }.map { it.code }
internal fun nativeBackupValues(values:List<Any?>):List<String>?=nativeBackupCodes(values)?.let(::nativeBackupCopyValues)
internal fun nativeTokenIds(ids:List<String>):Set<String>? {
if(ids.size>1000 || ids.any { it.isBlank() || it.length>1024 || it.any(Char::isISOControl) } || ids.distinct().size!=ids.size) return null
return ids.toSet()
}
