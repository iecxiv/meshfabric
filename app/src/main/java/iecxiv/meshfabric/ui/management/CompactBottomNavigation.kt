package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import iecxiv.meshfabric.R
import iecxiv.meshfabric.ui.devices.LocalDeviceDetailsClient

@Composable internal fun CompactBottomNavigation(page: Int, onSelect: (Int) -> Unit) {
val source = LocalDeviceDetailsClient.current
var profileVisible by remember(source) { mutableStateOf(false) }
ProfileNavigationBar(if (profileVisible) 3 else page) { next ->
if (next == 3) profileVisible = source != null else { profileVisible = false; onSelect(next) }
}
if (profileVisible && source != null) ProfileDestination(source,
onDismiss = { profileVisible = false },
onNavigate = { next -> if (next != 3) { profileVisible = false; onSelect(next) } })
}

@Composable internal fun ProfileNavigationBar(page: Int, onSelect: (Int) -> Unit) {
val scheme = MaterialTheme.colorScheme
val es = LocalConfiguration.current.locales[0].language == "es"
Surface(color = scheme.surface, tonalElevation = 0.dp, shadowElevation = 0.dp) {
Column {
HorizontalDivider(color = scheme.outlineVariant)
Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)).selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
listOf(stringResource(R.string.my_server_devices), stringResource(R.string.my_server_activity), stringResource(R.string.navigation_server), if (es) "Perfil" else "Profile").forEachIndexed { index, label ->
val selected = page == index
val color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant
Column(Modifier.weight(1f).padding(horizontal = 3.dp, vertical = 4.dp).heightIn(min = 52.dp).clip(MaterialTheme.shapes.small)
.background(if (selected) scheme.primaryContainer else scheme.surface)
.selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }).padding(horizontal = 2.dp, vertical = 6.dp),
verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
Icon(when (index) { 0 -> Icons.Default.Computer; 1 -> Icons.Default.History; 2 -> Icons.Default.Storage; else -> Icons.Default.Person }, null, Modifier.size(20.dp), tint = color)
Spacer(Modifier.height(2.dp))
Text(label, color = color, style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
}
}
}
}
}
