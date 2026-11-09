package iecxiv.meshfabric.ui.management

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.Locale

internal fun accountAdvertisedLanguages(value:Any?):List<String> {
    val values=when(value) {
        is JSONArray -> (0 until value.length()).mapNotNull { value.opt(it) as? String }
        is String -> {
            val array=runCatching { JSONArray(value) }.getOrNull()
            if(array!=null) (0 until array.length()).mapNotNull { array.opt(it) as? String }
            else value.split(',',';').map { it.trim() }
        }
        else -> emptyList()
    }
    return values.filter(::accountLanguageValid).distinct()
}
internal fun accountLanguageCandidates(value:Any?):List<String> = accountAdvertisedLanguages(value).ifEmpty {
    Locale.getAvailableLocales().map { it.toLanguageTag() }.filter(::accountLanguageValid).distinct().sorted()
}
internal fun securityKeyId(key:JSONObject?):Long? {
    val number=(key?.opt("i") as? Number)?.toDouble() ?: return null
    if(!number.isFinite() || number<0 || number>9007199254740991.0 || number%1.0!=0.0) return null
    return number.toLong()
}
internal fun securityKeyIds(array:JSONArray):List<Long>? {
    val ids=(0 until array.length()).map { securityKeyId(array.optJSONObject(it)) ?: return null }
    return ids.takeIf { it.distinct().size==it.size }
}
internal fun accountLoginOutcome(value:Any?):Boolean? {
    val code=when(value) {
        is Number -> value.toDouble().takeIf { it.isFinite() && it%1.0==0.0 && it in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() }?.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }
    return when(code) { 107->true; 108,109,110->false; else->null }
}
internal fun accountTokenExpiry(token:JSONObject,locale:String,hours24:Boolean,es:Boolean):String {
    val value=token.opt("expire")
    val instant=runCatching {
        when(value) {
            is Number -> {
                val n=value.toDouble()
                if(!n.isFinite() || n%1.0!=0.0) null else when(n.toLong()) {
                    in 946684800L..4102444800L -> Instant.ofEpochSecond(n.toLong())
                    in 946684800000L..4102444800000L -> Instant.ofEpochMilli(n.toLong())
                    else -> null
                }
            }
            is String -> Instant.parse(value)
            else -> null
        }
    }.getOrNull()
    if(instant!=null) return (if(es) "Caduca: " else "Expires: ")+accountTime(instant,locale,hours24)
    val minutes=token.optInt("_requestedExpiryMinutes",0)
    if(minutes in 1..43200) return (if(es) "Caducidad solicitada: " else "Requested lifetime: ")+minutes+" min"
    return if(es) "Caducidad no informada por el servidor" else "Expiry not supplied by the server"
}
