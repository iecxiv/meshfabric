package iecxiv.meshfabric.ui.management

import android.content.SharedPreferences

internal val serverHistoryPeriods=listOf(3,8,24,48,72,720)
internal fun normalizedHistoryHours(value:Int)=value.takeIf { it in serverHistoryPeriods } ?: 3
internal class ServerHistoryPreferences(private val preferences:SharedPreferences) {
    fun hours()=normalizedHistoryHours(runCatching { preferences.getInt("hours",3) }.getOrDefault(3))
    fun saveHours(value:Int) {
        require(value in serverHistoryPeriods)
        preferences.edit().putInt("hours",value).apply()
    }
}
