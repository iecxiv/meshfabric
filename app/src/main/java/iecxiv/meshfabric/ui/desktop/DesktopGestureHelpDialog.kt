package iecxiv.meshfabric.ui.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import iecxiv.meshfabric.R
import iecxiv.meshfabric.ui.components.AppDialogAppearance
import iecxiv.meshfabric.ui.components.AppDialogSurface

@Composable
internal fun DesktopGestureHelpDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        AppDialogSurface(
            shape = AppDialogAppearance.shape,
            color = AppDialogAppearance.containerColor(),
            tonalElevation = AppDialogAppearance.tonalElevation,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(stringResource(R.string.desktop_gestures_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.desktop_gestures_body), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        }
    }
}
