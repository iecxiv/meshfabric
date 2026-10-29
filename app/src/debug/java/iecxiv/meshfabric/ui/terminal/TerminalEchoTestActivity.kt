package iecxiv.meshfabric.ui.terminal

import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout

/** Debug-only, unexported host for synthetic WebView echo tests. */
class TerminalEchoTestActivity : Activity() {
    lateinit var host: FrameLayout
        private set
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        host = FrameLayout(this)
        setContentView(host)
    }
}
