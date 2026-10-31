package iecxiv.meshfabric.diagnostics
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.R
@Composable internal fun DiagnosticSettings() {
    val active by AppDiagnostics.active.collectAsState()
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.BugReport,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.diagnostic_switch_title),
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Switch(
                    checked = active,
                    onCheckedChange = AppDiagnostics::setEnabled,
                    colors = SwitchDefaults.colors(
                        uncheckedBorderColor = if (darkTheme) Color.White else Color.Unspecified
                    )
                )
            }
            Text(
                text = stringResource(R.string.diagnostic_switch_description),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
