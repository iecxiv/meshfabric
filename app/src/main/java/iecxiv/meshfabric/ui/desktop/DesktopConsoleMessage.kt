package iecxiv.meshfabric.ui.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
internal fun DesktopConsoleMessage(message: String?) {
    if (message.isNullOrBlank()) return
    Box(Modifier.fillMaxSize().zIndex(1f), contentAlignment = Alignment.TopCenter) {
        Text(
            text = message,
            color = Color(0xFFFFEB3B),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp).testTag("desktop-console-message")
        )
    }
}
