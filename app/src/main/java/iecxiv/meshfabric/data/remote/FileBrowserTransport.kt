package iecxiv.meshfabric.data.remote

import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

internal object FileBrowserTransport {
    // Inputs are already URL-encoded by the caller, as in the existing tunnel setup.
    fun endpoint(encodedNodeId: String, tunnelId: String, encodedRelayToken: String): String =
        "/meshrelay.ashx?browser=1&p=5&nodeid=$encodedNodeId&id=$tunnelId&rauth=$encodedRelayToken&auth=$encodedRelayToken"

    fun listingPayload(json: String): ByteString = json.encodeUtf8()
}
