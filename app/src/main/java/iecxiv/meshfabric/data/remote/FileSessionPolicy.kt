package iecxiv.meshfabric.data.remote

import iecxiv.meshfabric.data.model.SessionStatus

/** Retention is independent from the physical socket and cached directory contents. */
internal object FileSessionPolicy {
    fun status(retained: Boolean, irrecoverable: Boolean): SessionStatus = when {
        !retained -> SessionStatus.NONE
        irrecoverable -> SessionStatus.DISCONNECTED_WITH_BUFFER
        else -> SessionStatus.CONNECTED
    }
    fun active(retained: Boolean, irrecoverable: Boolean): Boolean = retained && !irrecoverable
    fun disconnected(retained: Boolean, irrecoverable: Boolean): Boolean = retained && irrecoverable
    fun configurationFailure(error: Exception): Boolean = error is IllegalArgumentException
}
