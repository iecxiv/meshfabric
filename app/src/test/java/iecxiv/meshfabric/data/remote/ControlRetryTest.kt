package iecxiv.meshfabric.data.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import org.junit.Assert.*
import org.junit.Test

class ControlRetryTest {
    private fun scope()=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)

    @Test(timeout=2000) fun recursiveImmediateRequestDoesNotCancelStartingAttempt() {
        val scope=scope()
        try {
            var wanted=true
            var calls=0
            lateinit var retry:ControlRetry
            retry=ControlRetry(scope,SocketGeneration(),{ wanted },{
                calls++
                retry.request(true)
                assertTrue(retry.hasPending())
                wanted=false
            })
            retry.request(true)
            assertEquals(1,calls)
            assertTrue(scope.isActive)
            assertFalse(retry.hasPending())
        } finally { scope.cancel() }
    }

    @Test(timeout=2000) fun repeatedRequestsShareOneQueuedAttempt() {
        val scope=scope()
        try {
            var calls=0
            val retry=ControlRetry(scope,SocketGeneration(),{ true },{ calls++ },retryDelay={ 60_000L })
            repeat(30) { retry.request() }
            assertTrue(retry.hasPending())
            assertEquals(0,calls)
            retry.cancel()
            assertFalse(retry.hasPending())
        } finally { scope.cancel() }
    }

    @Test(timeout=2000) fun logoutCancelsQueuedAttemptAndRejectsFurtherRequests() {
        val scope=scope()
        try {
            var wanted=true
            var calls=0
            val retry=ControlRetry(scope,SocketGeneration(),{ wanted },{ calls++ },retryDelay={ 60_000L })
            retry.request()
            wanted=false
            retry.cancel()
            retry.request(true)
            assertFalse(retry.hasPending())
            assertEquals(0,calls)
        } finally { scope.cancel() }
    }

    @Test(timeout=2000) fun immediateRequestUpgradesQueuedDelayWithoutDuplicateAttempt() {
        val scope=scope()
        try {
            var wanted=true
            var calls=0
            val retry=ControlRetry(scope,SocketGeneration(),{ wanted },{ calls++; wanted=false },retryDelay={ 60_000L })
            retry.request()
            retry.request(true)
            assertEquals(1,calls)
            assertFalse(retry.hasPending())
        } finally { scope.cancel() }
    }

    @Test(timeout=2000) fun failedStartDoesNotCancelScopeAndQueuesBoundedRetry() {
        val scope=scope()
        try {
            var errors=0
            val retry=ControlRetry(scope,SocketGeneration(),{ true },{ throw IllegalStateException("test") },
                onError={ errors++ },retryDelay={ 60_000L })
            retry.request(true)
            assertEquals(1,errors)
            assertTrue(scope.isActive)
            assertTrue(retry.hasPending())
            retry.cancel()
        } finally { scope.cancel() }
    }

    @Test(timeout=2000) fun cancelledScopeCannotScheduleRetries() {
        val scope=scope()
        var calls=0
        val retry=ControlRetry(scope,SocketGeneration(),{ true },{ calls++ },retryDelay={ 60_000L })
        retry.request()
        scope.cancel()
        retry.request(true)
        assertFalse(retry.hasPending())
        assertEquals(0,calls)
    }

    @Test fun backoffIsCappedAndHandlesInvalidAttemptCounts() {
        assertEquals(500L,controlRetryDelay(-1))
        assertEquals(500L,controlRetryDelay(0))
        assertEquals(1000L,controlRetryDelay(1))
        assertEquals(2000L,controlRetryDelay(2))
        assertEquals(15_000L,controlRetryDelay(100))
    }

    @Test(timeout=2000) fun invalidConfigurationStopsRetriesUntilExplicitCorrection() {
        val scope=scope()
        try {
            var valid=true
            var fail=true
            var calls=0
            val retry=ControlRetry(scope,SocketGeneration(),{ valid },{
                calls++
                if(fail) throw IllegalArgumentException("test configuration")
                valid=false
            },onError={ valid=false },retryDelay={ 60_000L })
            retry.request(true)
            assertEquals(1,calls)
            assertFalse(retry.hasPending())
            fail=false
            valid=true
            retry.request(true)
            assertEquals(2,calls)
            assertFalse(retry.hasPending())
        } finally { scope.cancel() }
    }
}
