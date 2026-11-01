package iecxiv.meshfabric.ui.files

import iecxiv.meshfabric.data.model.TransferProgress
import iecxiv.meshfabric.data.model.TransferType

internal fun transferProgressDetail(transfer: TransferProgress, byteDetail: String): String =
    if (transfer.type != TransferType.COMPRESS) byteDetail
    else if (transfer.isCompleted) "100%"
    else if (transfer.totalBytes > 0L) "${(transfer.percentage * 100).toInt()}%"
    else "..."
