package iecxiv.meshfabric.service

import org.junit.Assert.*
import org.junit.Test

class TransferNotificationCompletionTest {
    @Test fun noNotificationsDoNotMeanCompletion() {
        assertFalse(transferNotificationsCompleted(emptyList(), false))
        assertFalse(transferNotificationsCompleted(emptyList(), true))
    }
    @Test fun activeTransfersPreventCompletion() {
        assertFalse(transferNotificationsCompleted(listOf(true), true))
        assertFalse(transferNotificationsCompleted(listOf(true, true), true))
    }
    @Test fun allRemainingNotificationsMustHaveSuccessIcons() {
        assertTrue(transferNotificationsCompleted(listOf(true), false))
        assertTrue(transferNotificationsCompleted(listOf(true, true), false))
        assertFalse(transferNotificationsCompleted(listOf(false), false))
        assertFalse(transferNotificationsCompleted(listOf(true, false), false))
    }
}
