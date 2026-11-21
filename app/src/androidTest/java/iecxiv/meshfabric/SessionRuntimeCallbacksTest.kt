package iecxiv.meshfabric
import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import iecxiv.meshfabric.service.KeepAliveService
import iecxiv.meshfabric.ui.devices.DeviceChatKeepAlive
import org.junit.Assert.*
import org.junit.Test
class SessionRuntimeCallbacksTest {
    @Test fun callbacksSurviveRecreationAndBackgroundWithoutReplacement() {
        lateinit var network: () -> Unit
        lateinit var task: () -> Unit
        lateinit var chat: () -> Unit
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                network=requireNotNull(MeshFabricApp.onNetworkChangedListener)
                task=requireNotNull(KeepAliveService.onTaskRemovedListener)
                chat=requireNotNull(DeviceChatKeepAlive.onChanged)
            }
            scenario.recreate()
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity {
                assertSame(network,MeshFabricApp.onNetworkChangedListener)
                assertSame(task,KeepAliveService.onTaskRemovedListener)
                assertSame(chat,DeviceChatKeepAlive.onChanged)
            }
        }
    }
    @Test fun finishingActivityReleasesAllOwnedCallbacks() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                assertNotNull(MeshFabricApp.onNetworkChangedListener)
                assertNotNull(KeepAliveService.onTaskRemovedListener)
                assertNotNull(DeviceChatKeepAlive.onChanged)
            }
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertNull(MeshFabricApp.onNetworkChangedListener)
        assertNull(KeepAliveService.onTaskRemovedListener)
        assertNull(DeviceChatKeepAlive.onChanged)
    }
    @Test fun clearingOldRuntimeDoesNotDetachNewRuntimeCallbacks() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val first=ViewModelStore(); val second=ViewModelStore()
            val app=ApplicationProvider.getApplicationContext<Application>()
            val factory=ViewModelProvider.AndroidViewModelFactory(app)
            try {
                ViewModelProvider(first,factory)[RemoteSessionRuntime::class.java]
                ViewModelProvider(second,factory)[RemoteSessionRuntime::class.java]
                val network=MeshFabricApp.onNetworkChangedListener
                val task=KeepAliveService.onTaskRemovedListener
                val chat=DeviceChatKeepAlive.onChanged
                assertNotNull(network); assertNotNull(task); assertNotNull(chat)
                first.clear()
                assertSame(network,MeshFabricApp.onNetworkChangedListener)
                assertSame(task,KeepAliveService.onTaskRemovedListener)
                assertSame(chat,DeviceChatKeepAlive.onChanged)
            } finally { first.clear(); second.clear() }
            assertNull(MeshFabricApp.onNetworkChangedListener)
            assertNull(KeepAliveService.onTaskRemovedListener)
            assertNull(DeviceChatKeepAlive.onChanged)
        }
    }
}
