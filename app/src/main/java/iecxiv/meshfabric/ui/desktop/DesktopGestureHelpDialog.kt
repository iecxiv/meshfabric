package iecxiv.meshfabric.ui.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import iecxiv.meshfabric.R
import iecxiv.meshfabric.ui.components.AppDialogAppearance
import iecxiv.meshfabric.ui.components.AppDialogSurface

@Composable
internal fun DesktopGestureHelpDialog(onDismiss: () -> Unit) {
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val landscapeWidth = (configuration.screenWidthDp - 48).coerceIn(1, 600).dp
    Dialog(
        properties = DialogProperties(usePlatformDefaultWidth = !landscape),
        onDismissRequest = onDismiss
    ) {
        AppDialogSurface(
            shape = AppDialogAppearance.shape,
            color = AppDialogAppearance.containerColor(),
            tonalElevation = AppDialogAppearance.tonalElevation,
            modifier = Modifier
                .then(if (landscape) Modifier.width(landscapeWidth) else Modifier.fillMaxWidth())
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    stringResource(R.string.desktop_gestures_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(stringResource(R.string.desktop_gestures_body), fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.height(36.dp)) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}
