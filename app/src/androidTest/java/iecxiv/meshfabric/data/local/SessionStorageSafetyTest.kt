package iecxiv.meshfabric.data.local
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import iecxiv.meshfabric.R
import iecxiv.meshfabric.data.remote.DesktopEncoding
import iecxiv.meshfabric.data.remote.FrameRateOption
import iecxiv.meshfabric.ui.theme.AppThemeMode
import iecxiv.meshfabric.data.model.SessionData
import org.junit.Assert.*
import org.junit.Test
import org.xmlpull.v1.XmlPullParser
class SessionStorageSafetyTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val protectedFiles = setOf("meshcentral_encrypted_prefs.xml", "meshcentral_fallback_prefs.xml")
    private fun excludes(resource: Int): Map<String, Set<String>> {
        val parser = context.resources.getXml(resource)
        val result = mutableMapOf<String, MutableSet<String>>()
        var section = ""
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) when (parser.name) {
                "full-backup-content", "cloud-backup", "device-transfer" -> section = parser.name
                "exclude" -> if (parser.getAttributeValue(null, "domain") == "sharedpref")
                    result.getOrPut(section) { mutableSetOf() }.add(parser.getAttributeValue(null, "path"))
            }
            parser.next()
        }
        return result
    }
    @Test fun backupRulesExcludeCredentialPreferences() {
        assertTrue(excludes(R.xml.backup_rules)["full-backup-content"].orEmpty().containsAll(protectedFiles))
        val extraction = excludes(R.xml.data_extraction_rules)
        assertTrue(extraction["cloud-backup"].orEmpty().containsAll(protectedFiles))
        assertTrue(extraction["device-transfer"].orEmpty().containsAll(protectedFiles))
    }
    @Test fun credentialsNeverReachUnencryptedFallbackPreferences() {
        val manager = SessionManager(context)
        manager.saveSession(SessionData("https://example.invalid", "user", "xid=secret-cookie", false, true))
        try {
            val keys = context.getSharedPreferences("meshcentral_fallback_prefs", Context.MODE_PRIVATE).all.keys
            assertTrue(keys.none { it in setOf("auth_cookie", "server_url", "username") })
        } finally { manager.clearSession() }
    }
    @Test fun clearSessionKeepsSettingsIconPreferenceConsistentAfterRestart() {
        val manager = SessionManager(context)
        val original = manager.hideSettingsIconFlow.value
        try {
            manager.saveHideSettingsIcon(true)
            manager.clearSession()
            assertTrue(manager.hideSettingsIconFlow.value)
            assertTrue(SessionManager(context).hideSettingsIconFlow.value)
        } finally { manager.saveHideSettingsIcon(original) }
    }

    private fun withStorage(body:(SessionManager,android.content.SharedPreferences)->Unit) {
        val manager=SessionManager(context)
        val field=SessionManager::class.java.getDeclaredField("prefs");field.isAccessible=true
        val prefs=field.get(manager) as android.content.SharedPreferences
        val saved=prefs.all.mapValues {(_,value)->if(value is Set<*>) value.toSet() else value}
        try {body(manager,prefs)} finally {
            val editor=prefs.edit().clear()
            for((key,value) in saved) when(value) {
                is String -> editor.putString(key,value)
                is Boolean -> editor.putBoolean(key,value)
                is Int -> editor.putInt(key,value)
                is Long -> editor.putLong(key,value)
                is Float -> editor.putFloat(key,value)
                is Set<*> -> editor.putStringSet(key,value.map {it as String}.toSet())
                null -> editor.remove(key)
                else -> error("Unsupported preference type")
            }
            assertTrue(editor.commit())
        }
    }
    @Test fun logoutPreservesDesktopSettingsExceptAutomaticClipboard() = withStorage {manager,_ ->
        val settings=manager.getDesktopSettings().copy(quality=76,scale=1536,frameRate=FrameRateOption.SLOW,encoding=DesktopEncoding.PNG,swapMouseButtons=true,reverseMouseWheel=true,useRemoteKeyboardMap=true,autoClipboard=true,lockOnDisconnect=true)
        manager.saveDesktopSettings(settings);manager.saveDesktopBarCollapsed(true);manager.clearSession()
        val expected=settings.copy(autoClipboard=false)
        assertEquals(expected,manager.getDesktopSettings());assertTrue(manager.isDesktopBarCollapsed())
        val restarted=SessionManager(context)
        assertEquals(expected,restarted.getDesktopSettings());assertTrue(restarted.isDesktopBarCollapsed())
    }
    @Test fun logoutClearsAccountDataAndPreservesGeneralPreferences() = withStorage {manager,prefs ->
        manager.saveCustomAppName("Logout regression");manager.saveThemeMode(AppThemeMode.values().last())
        manager.saveHideSettingsIcon(true);manager.saveOnlyOnlineDevices(true);manager.saveHideDeviceNetworkInfo(true);manager.saveTerminalBuffersSetting(true)
        manager.saveCollapsedGroups(setOf("mesh//private"));manager.saveAllGroupsExpandedState(false)
        prefs.edit().putString("server_url","https://example.invalid").putString("username","fixture-user").putString("auth_cookie","fixture-cookie").putBoolean("ignore_ssl",true).putBoolean("is_remembered",true).putString("unknown_account_state","fixture").apply()
        assertTrue(prefs.contains("auth_cookie"));manager.clearSession()
        assertTrue(prefs.all.keys.none {it in setOf("server_url","username","auth_cookie","ignore_ssl","is_remembered","collapsed_groups","all_groups_expanded","unknown_account_state")})
        val restarted=SessionManager(context)
        assertNull(manager.getSession());assertNull(restarted.getSession());assertTrue(restarted.getCollapsedGroups().isEmpty());assertTrue(restarted.isAllGroupsExpanded())
        for(current in listOf(manager,restarted)) {
            assertEquals("Logout regression",current.appNameFlow.value);assertEquals(AppThemeMode.values().last(),current.themeModeFlow.value)
            assertTrue(current.hideSettingsIconFlow.value);assertTrue(current.onlyOnlineDevicesFlow.value);assertTrue(current.hideDeviceNetworkInfoFlow.value)
            assertTrue(current.saveTerminalBuffersFlow.value);assertTrue(current.isSaveTerminalBuffersEnabled())
            assertEquals(manager.customIconPathFlow.value,current.customIconPathFlow.value)
        }
    }
    @Test fun repeatedLogoutKeepsDesktopPreferencesAndClipboardDisabled() = withStorage {manager,_ ->
        manager.saveDesktopSettings(manager.getDesktopSettings().copy(quality=79,autoClipboard=true))
        manager.saveDesktopBarCollapsed(true);manager.clearSession();manager.clearSession()
        assertEquals(79,manager.getDesktopSettings().quality);assertFalse(manager.getDesktopSettings().autoClipboard);assertTrue(manager.isDesktopBarCollapsed())
        val restarted=SessionManager(context);assertEquals(79,restarted.getDesktopSettings().quality);assertTrue(restarted.isDesktopBarCollapsed());assertFalse(restarted.getDesktopSettings().autoClipboard)
    }
    @Test fun logoutNormalizesCorruptEnumsWithoutLosingDesktopQuality() = withStorage {manager,prefs ->
        prefs.edit().putInt("desktop_quality",88).putString("desktop_fps","invalid").putString("desktop_encoding","invalid").apply()
        manager.clearSession()
        val restored=SessionManager(context).getDesktopSettings()
        assertEquals(88,restored.quality);assertEquals(FrameRateOption.FAST,restored.frameRate);assertEquals(DesktopEncoding.JPEG,restored.encoding);assertFalse(restored.autoClipboard)
    }
}
