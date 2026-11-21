package iecxiv.meshfabric.ui.components
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
@Composable internal fun SubviewBodyViewport(modifier:Modifier=Modifier,content:@Composable ()->Unit) {
    Box(modifier.testTag("shared_subview_body"),contentAlignment=Alignment.CenterStart) {Column(Modifier.fillMaxWidth()) {content()}}
}
