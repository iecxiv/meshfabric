package iecxiv.meshfabric.ui.management

internal val profileSectionKeys=listOf("account","groups","users")
internal fun nativeBackupValues(values:List<Any?>):List<String>? {
if(values.size>1000) return null
return values.map { value->when(value) {
is String->value.takeIf { it.isNotBlank() && it.length<=128 && it.none(Char::isISOControl) } ?: return null
is Number->{ val n=value.toDouble(); if(!n.isFinite() || n<0 || n>9007199254740991.0 || n%1.0!=0.0) return null; n.toLong().toString() }
else->return null
} }
}
internal fun nativeTokenIds(ids:List<String>):Set<String>? {
if(ids.size>1000 || ids.any { it.isBlank() || it.length>1024 || it.any(Char::isISOControl) } || ids.distinct().size!=ids.size) return null
return ids.toSet()
}
