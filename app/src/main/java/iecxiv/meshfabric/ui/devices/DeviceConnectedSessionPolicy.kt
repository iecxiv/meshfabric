package iecxiv.meshfabric.ui.devices

import iecxiv.meshfabric.data.model.SessionStatus

internal fun hasDeviceConnectedSession(desktop: SessionStatus, terminal: SessionStatus, files: SessionStatus): Boolean =
    desktop == SessionStatus.CONNECTED || terminal == SessionStatus.CONNECTED || files == SessionStatus.CONNECTED
