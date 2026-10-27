package iecxiv.meshfabric.data.remote

import iecxiv.meshfabric.data.model.SessionStatus

internal object DesktopRecoveryPolicy {
    fun status(retained: Boolean): SessionStatus =
        if (retained) SessionStatus.CONNECTED else SessionStatus.NONE

    fun retryDelay(failures: Int): Long =
        (1000L shl failures.coerceIn(0, 5)).coerceAtMost(30_000L)
}
