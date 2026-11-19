package iecxiv.meshfabric.ui.devices

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable internal fun DeviceBatchDialogTitle(text: String) {
    Text(text = text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
}
