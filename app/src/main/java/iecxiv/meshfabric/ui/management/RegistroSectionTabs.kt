package iecxiv.meshfabric.ui.management
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import iecxiv.meshfabric.R
@Composable internal fun RegistroSectionTabs(selected:Int,onSelect:(Int)->Unit) {
    ManagementSectionTabs(listOf(stringResource(R.string.registro_activity),stringResource(R.string.registro_events)),selected.coerceIn(0,1),onSelect)
}
