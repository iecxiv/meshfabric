package iecxiv.meshfabric.diagnostics
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.R
@Composable internal fun DiagnosticSettings() {
    val active by AppDiagnostics.active.collectAsState()
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment=Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.BugReport,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).padding(end=12.dp)) {
                Text(stringResource(R.string.diagnostic_switch_title), style=MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.diagnostic_switch_description), style=MaterialTheme.typography.bodySmall)
            }
            Switch(checked=active, onCheckedChange=AppDiagnostics::setEnabled)
        }
    }
}
