package iecxiv.meshfabric.ui.components
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
internal class BottomLoadingState {
    private val active=mutableStateListOf<Any>()
    val loading:Boolean get()=active.isNotEmpty()
    fun begin(token:Any) {if(token !in active) active.add(token)}
    fun end(token:Any) {active.remove(token)}
}
internal val LocalBottomLoadingState=staticCompositionLocalOf<BottomLoadingState?> {null}
@Composable internal fun RoutedViewLoadingIndicator(modifier:Modifier=Modifier) {
    val owner=LocalBottomLoadingState.current
    if(owner==null) {LinearProgressIndicator(modifier);return}
    val token=remember {Any()}
    DisposableEffect(owner,token) {
        owner.begin(token)
        onDispose {owner.end(token)}
    }
}
@Composable internal fun BottomViewLoadingBar() {
    val owner=LocalBottomLoadingState.current
    Box(Modifier.fillMaxWidth().height(4.dp).testTag("bottom_loading_slot")) {
        if(owner?.loading==true) LinearProgressIndicator(Modifier.fillMaxSize().testTag("bottom_loading_indicator"))
    }
}
