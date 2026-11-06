package iecxiv.meshfabric.data.local

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DeviceNetworkVisibilityPersistenceTest {
    private fun isolated(test: (Context) -> Unit) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "visibility_test_" + UUID.randomUUID().toString() + "_"
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                base.getSharedPreferences(prefix + name, mode)
        }
        try { test(context) } finally {
            base.deleteSharedPreferences(prefix + "meshcentral_encrypted_prefs")
            base.deleteSharedPreferences(prefix + "meshcentral_fallback_prefs")
        }
    }
    @Test fun aNewManagerRestoresBothVisibilityStates() = isolated { context ->
        val manager = SessionManager(context)
        assertFalse(manager.hideDeviceNetworkInfoFlow.value)
        manager.saveHideDeviceNetworkInfo(true)
        assertTrue(SessionManager(context).hideDeviceNetworkInfoFlow.value)
        manager.saveHideDeviceNetworkInfo(false)
        assertFalse(SessionManager(context).hideDeviceNetworkInfoFlow.value)
    }
    @Test fun clearingSessionPreservesPrivacyChoice() = isolated { context ->
        val manager = SessionManager(context)
        manager.saveHideDeviceNetworkInfo(true)
        manager.clearSession()
        assertTrue(manager.hideDeviceNetworkInfoFlow.value)
        assertTrue(SessionManager(context).hideDeviceNetworkInfoFlow.value)
    }
}
