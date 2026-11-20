package iecxiv.meshfabric.ui.components
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
@Composable internal fun SubviewHeader(
    onBack:()->Unit,
    title:@Composable ()->Unit,
    modifier:Modifier=Modifier,
    backTag:String="subview-back",
    actions:@Composable RowScope.()->Unit={}
) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    Row(modifier.fillMaxWidth().heightIn(min=48.dp).testTag("subview-header"),verticalAlignment=Alignment.CenterVertically) {
        IconButton(onClick=onBack,modifier=Modifier.size(48.dp).testTag(backTag)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack,if(es) "Volver" else "Back",Modifier.size(24.dp))
        }
        Spacer(Modifier.width(4.dp))
        Box(Modifier.weight(1f).semantics {heading()}) {
            ProvideTextStyle(MaterialTheme.typography.titleMedium) {title()}
        }
        actions()
    }
}
