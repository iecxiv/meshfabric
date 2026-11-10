package iecxiv.meshfabric.ui.management
import androidx.compose.runtime.Composable
internal fun serverStatisticsRefreshEnabled(tab:Int,ready:Boolean,busy:Boolean)=tab==1 && ready && !busy
@Composable internal fun ServerStatisticsHeader(tab:Int,ready:Boolean,busy:Boolean,header:@Composable (@Composable ()->Unit)->Unit,onRefresh:()->Unit) {
    header {if(tab==1) HeaderRefreshButton(serverStatisticsRefreshEnabled(tab,ready,busy),"server-statistics-refresh",onRefresh)}
}
