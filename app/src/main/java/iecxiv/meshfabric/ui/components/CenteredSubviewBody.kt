package iecxiv.meshfabric.ui.components
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
@Composable internal fun CenteredSubviewBody(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    BoxWithConstraints(modifier.testTag("centered_body_viewport")) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min=maxHeight).padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp,Alignment.CenterVertically),content=content)
    }
}
