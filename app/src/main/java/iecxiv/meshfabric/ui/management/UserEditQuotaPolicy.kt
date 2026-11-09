package iecxiv.meshfabric.ui.management

internal const val MAX_SAFE_USER_QUOTA=9007199254740991L

internal fun userQuotaForEdit(value:Any?):Long? {
    if(value==null) return null
    require(value is Int || value is Long || value is Double) { "Unsupported quota type" }
    val number=(value as Number).toDouble()
    require(number.isFinite() && number>=0.0 && number<=MAX_SAFE_USER_QUOTA.toDouble() && number%1.0==0.0) { "Invalid quota" }
    return number.toLong()
}
internal fun userQuotaValuesMatch(actual:Any?,expected:Any?):Boolean=
    runCatching { userQuotaForEdit(actual)==userQuotaForEdit(expected) }.getOrDefault(false)
