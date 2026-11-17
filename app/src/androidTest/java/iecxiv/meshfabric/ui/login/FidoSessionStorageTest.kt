package iecxiv.meshfabric.ui.login

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import iecxiv.meshfabric.data.local.SessionManager
import iecxiv.meshfabric.data.model.SessionData
import iecxiv.meshfabric.data.remote.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FidoSessionStorageTest {
    @Test fun forgettingAuthenticationPreservesApplicationAndDesktopPreferences() {
        val manager=SessionManager(InstrumentationRegistry.getInstrumentation().targetContext)
        val previous=manager.getSession()
        val desktop=manager.getDesktopSettings(); val appName=manager.appNameFlow.value
        val online=manager.onlyOnlineDevicesFlow.value; val hidden=manager.hideDeviceNetworkInfoFlow.value
        try {
            manager.saveSession(SessionData("https://server.example/tenant","alice","session=test",false,true))
            assertNotNull(manager.getSession()); manager.forgetAuthentication(); assertNull(manager.getSession())
            assertEquals(desktop,manager.getDesktopSettings()); assertEquals(appName,manager.appNameFlow.value)
            assertEquals(online,manager.onlyOnlineDevicesFlow.value); assertEquals(hidden,manager.hideDeviceNetworkInfoFlow.value)
        } finally { if(previous!=null) manager.saveSession(previous) else manager.forgetAuthentication() }
    }
    @Test fun completionPersistsRememberedAccountAndForgetsUnrememberedCookie() {
        val manager=SessionManager(InstrumentationRegistry.getInstrumentation().targetContext)
        val previous=manager.getSession(); var successes=0
        val pending=FidoLoginHttpTransport.PendingLogin(FidoLoginBinding("https://server.example/tenant","alice",1),"session=test")
        try {
            FidoLoginCompletion.complete(pending,true,manager::saveSession,manager::forgetAuthentication,{}, {},{successes++})
            val stored=requireNotNull(manager.getSession())
            assertEquals("alice",stored.username); assertEquals("session=test",stored.authCookie); assertFalse(stored.ignoreSsl)
            FidoLoginCompletion.complete(pending,false,manager::saveSession,manager::forgetAuthentication,{}, {},{successes++})
            assertNull(manager.getSession()); assertEquals(2,successes)
        } finally { if(previous!=null) manager.saveSession(previous) else manager.forgetAuthentication() }
    }
}
