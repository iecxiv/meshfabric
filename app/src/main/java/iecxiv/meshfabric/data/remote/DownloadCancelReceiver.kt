package iecxiv.meshfabric.data.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DownloadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        if (action == "ACTION_CANCEL_DOWNLOAD" || action == "ACTION_CANCEL_TRANSFER") {
            val filePath = intent.getStringExtra("FILE_PATH")
            if (!filePath.isNullOrBlank()) {
                FileRelayClient.activeInstance?.cancelTransfer(filePath)
            } else {
                FileRelayClient.activeInstance?.cancelDownload()
            }
        }
    }
}
