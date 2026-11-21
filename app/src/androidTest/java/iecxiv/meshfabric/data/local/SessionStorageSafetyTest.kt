package iecxiv.meshfabric.data.local
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import iecxiv.meshfabric.R
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
}
