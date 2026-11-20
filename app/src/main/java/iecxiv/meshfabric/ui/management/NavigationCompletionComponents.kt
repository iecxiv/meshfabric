package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
internal val LocalDeviceGroupInline=staticCompositionLocalOf {false}
internal val LocalNoticeComposeInline=staticCompositionLocalOf {false}
internal fun Modifier.blockCoveredNavigation(covered:Boolean):Modifier=if(!covered) this else this.clearAndSetSemantics {}.pointerInput(Unit) {awaitPointerEventScope {while(true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach {it.consume()}}}
internal data class NoticeNavigationChoice(val id:String,val label:String,val device:Boolean)
@Composable internal fun NoticeNavigationGrid(choices:List<NoticeNavigationChoice>,state:LazyGridState,enabled:Boolean,modifier:Modifier,onSelect:(NoticeNavigationChoice)->Unit) {
    LazyVerticalGrid(columns=GridCells.Adaptive(220.dp),state=state,modifier=modifier,horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        items(choices,key={it.id}) {choice->
            Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.TopCenter) {
                OutlinedCard(onClick={onSelect(choice)},enabled=enabled,modifier=Modifier.widthIn(max=320.dp).fillMaxWidth(),shape=MaterialTheme.shapes.medium,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Icon(if(choice.device) Icons.Default.Computer else Icons.Default.Person,null,Modifier.size(24.dp),tint=MaterialTheme.colorScheme.primary)
                        Text(choice.label,Modifier.fillMaxWidth(),textAlign=TextAlign.Center,style=MaterialTheme.typography.titleSmall,maxLines=3,overflow=TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
