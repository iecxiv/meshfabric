package iecxiv.meshfabric.ui.login

import androidx.test.core.app.ActivityScenario
import iecxiv.meshfabric.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Test

class FidoHostLifecycleTest {
    @Test fun hostCanBeConstructedAfterActivityResumes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = FidoWebViewHost(activity, activity.fidoClient)
                host.close()
                host.close()
            }
        }
    }

    @Test fun closedHostRejectsCreationBeforeLoadingAnyPage() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = FidoWebViewHost(activity, activity.fidoClient)
                host.close()
                val result = runCatching { host.create("https://example.invalid") {} }
                assertTrue(result.exceptionOrNull() is IllegalStateException)
            }
        }
    }
}
