package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
@Composable internal fun ProfileAlignedCheckboxRow(content:@Composable RowScope.()->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,content=content)
}
@Composable internal fun ProfileActionGroup(content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(0.dp),content=content)
}
@Composable internal fun ProfileActionCard(content:@Composable ColumnScope.()->Unit) {
    Surface(Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.medium,color= iecxiv.meshfabric.ui.theme.appCardBackground(
MaterialTheme.colorScheme.surface
),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),
    contentColor = androidx.compose.material3.contentColorFor(
MaterialTheme.colorScheme.surface
)
,
    tonalElevation = iecxiv.meshfabric.ui.theme.appCardTonalElevation(
androidx.compose.ui.unit.Dp(0f)
)
) {
        Column(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(0.dp),content=content)
    }
}
