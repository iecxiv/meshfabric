package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal val serverOutputIconSize=18.dp
internal fun serverConsoleExportBytes(text:String)=text.toByteArray(Charsets.UTF_8)
internal fun serverTraceAcceptsLive(admin:Boolean,paused:Boolean)=admin && !paused
internal fun userGroupActionCanStayOpen(connectionReady:Boolean,actor:String)=connectionReady && actor.isNotBlank()
@Composable internal fun CenteredServerControls(content:@Composable RowScope.()->Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val viewportWidth=maxWidth
        Row(Modifier.horizontalScroll(rememberScrollState()).widthIn(min=viewportWidth),horizontalArrangement=Arrangement.spacedBy(6.dp,Alignment.CenterHorizontally),verticalAlignment=Alignment.CenterVertically,content=content)
    }
}
