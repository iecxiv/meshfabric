package iecxiv.meshfabric.ui.components
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
@Composable internal fun SubviewBodyViewport(modifier:Modifier=Modifier,content:@Composable ()->Unit) {
    Box(modifier.testTag("shared_subview_body"),contentAlignment=Alignment.CenterStart) {content()}
}
