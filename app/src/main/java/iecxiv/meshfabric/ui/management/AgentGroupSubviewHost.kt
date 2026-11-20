package iecxiv.meshfabric.ui.management
import androidx.compose.runtime.*
internal val LocalAgentGroupSubviewTarget=staticCompositionLocalOf<AgentGroupDialog?> {null}
internal val LocalAgentGroupSubviewClose=staticCompositionLocalOf<()->Unit> { {} }
internal fun profileAuxiliarySubview(tab:Int,agent:Boolean,device:Boolean)=tab in 1..3 && (agent || device)
@Composable internal fun AgentGroupSubviewHost(identity:Any,content:@Composable ()->Unit) {
    var target by remember(identity) {mutableStateOf<AgentGroupDialog?>(null)}
    CompositionLocalProvider(
        LocalAgentGroupDialogOpener provides {value->target=value},
        LocalAgentGroupSubviewTarget provides target,
        LocalAgentGroupSubviewClose provides {target=null}
    ) {content()}
}
