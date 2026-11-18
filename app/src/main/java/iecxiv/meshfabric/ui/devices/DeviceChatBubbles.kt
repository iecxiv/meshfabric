package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.data.model.DevicePlatform
import iecxiv.meshfabric.ui.theme.OnlineGreen
import java.util.Locale
import kotlin.math.roundToInt

internal class DeviceChatBubblePosition(initialY:Float) {
    var x by mutableFloatStateOf(1f)
    var y by mutableFloatStateOf(initialY.coerceIn(0f,1f))
}
internal data class DeviceChatBubble(val id:String,val name:String,val position:DeviceChatBubblePosition,val platform:DevicePlatform?=null)

@Composable internal fun DeviceChatBubbles(chats:List<DeviceChatBubble>,onRestore:(String)->Unit,onClose:(String)->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    val density=LocalDensity.current
    val diameter=with(density) {56.dp.toPx()}
    val targetHalf=with(density) {32.dp.toPx()}
    val closeRadius=with(density) {48.dp.toPx()}
    val measuredSizes=remember {mutableMapOf<String,IntSize>()}
    var dragging by remember {mutableStateOf<String?>(null)}
    val restore by rememberUpdatedState(onRestore)
    val close by rememberUpdatedState(onClose)
    SideEffect {measuredSizes.keys.retainAll(chats.map {it.id}.toSet())}
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
        val width=constraints.maxWidth.toFloat()
        val height=constraints.maxHeight.toFloat()
        val bubbleMaxWidth=minOf(maxWidth,240.dp)
        fun bubbleSize(chat:DeviceChatBubble)=measuredSizes[chat.id] ?: IntSize(diameter.roundToInt(),diameter.roundToInt())
        fun overClose(chat:DeviceChatBubble):Boolean {
            val size=bubbleSize(chat)
            val maxX=(width-size.width).coerceAtLeast(0f)
            val maxY=(height-size.height).coerceAtLeast(0f)
            val dx=chat.position.x*maxX+size.width/2f-width/2f
            val dy=chat.position.y*maxY+size.height/2f-(height-targetHalf)
            return dx*dx+dy*dy<=closeRadius*closeRadius
        }
        val highlighted=chats.firstOrNull {it.id==dragging}?.let {overClose(it)}==true
        if(dragging!=null) Surface(modifier=Modifier.align(Alignment.BottomCenter).size(64.dp).testTag("device_chat_drop_close")
            .semantics {contentDescription=if(es) "Soltar para cerrar el chat" else "Drop to close chat"},shape=CircleShape,
            color=if(highlighted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.errorContainer,
            contentColor=if(highlighted) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onErrorContainer) {
            Box(contentAlignment=Alignment.Center) {Icon(Icons.Default.Close,null,Modifier.size(28.dp))}
        }
        chats.forEach {chat->key(chat.id) {
            val p=chat.position
            val interaction=remember(chat.id) {MutableInteractionSource()}
            val description=(if(es) "Restaurar chat con " else "Restore chat with ")+chat.name
            Surface(modifier=Modifier.layout {measurable,bubbleConstraints->
                    val placeable=measurable.measure(bubbleConstraints)
                    measuredSizes[chat.id]=IntSize(placeable.width,placeable.height)
                    layout(placeable.width,placeable.height) {
                        placeable.placeRelative(
                            (p.x*(width-placeable.width).coerceAtLeast(0f)).roundToInt(),
                            (p.y*(height-placeable.height).coerceAtLeast(0f)).roundToInt())
                    }
                }
                .widthIn(min=minOf(56.dp,bubbleMaxWidth),max=bubbleMaxWidth).heightIn(min=minOf(56.dp,maxHeight))
                .testTag("device_chat_bubble_${chat.id}")
                .semantics {
                    contentDescription=description
                    customActions=listOf(CustomAccessibilityAction(if(es) "Cerrar chat" else "Close chat") {close(chat.id);true})
                }
                .pointerInput(chat.id,width,height) {
                    detectDragGestures(
                        onDragStart={dragging=chat.id},
                        onDragCancel={p.x=if(p.x<0.5f) 0f else 1f;dragging=null},
                        onDragEnd={
                            val remove=overClose(chat)
                            p.x=if(p.x<0.5f) 0f else 1f
                            dragging=null
                            if(remove) close(chat.id)
                        },
                        onDrag={change,amount->
                            change.consume()
                            val size=bubbleSize(chat)
                            val maxX=(width-size.width).coerceAtLeast(0f)
                            val maxY=(height-size.height).coerceAtLeast(0f)
                            p.x=if(maxX>0f) ((p.x*maxX+amount.x)/maxX).coerceIn(0f,1f) else 0f
                            p.y=if(maxY>0f) ((p.y*maxY+amount.y)/maxY).coerceIn(0f,1f) else 0f
                        })
                }.clickable(interactionSource=interaction,indication=null) {restore(chat.id)},shape=RoundedCornerShape(28.dp),
                color=MaterialTheme.colorScheme.primaryContainer,contentColor=MaterialTheme.colorScheme.onPrimaryContainer,shadowElevation=if(dragging==chat.id) 0.dp else 6.dp) {
                Row(Modifier.padding(horizontal=12.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(when(chat.platform) {
                        DevicePlatform.WINDOWS->Icons.Default.DesktopWindows
                        DevicePlatform.ANDROID->Icons.Default.Android
                        else->Icons.Default.Computer
                    },null,Modifier.size(24.dp),tint=OnlineGreen)
                    Text(chat.name.lowercase(Locale.ROOT),modifier=Modifier.weight(1f,fill=false).testTag("device_chat_bubble_name_${chat.id}"),style=MaterialTheme.typography.labelMedium,softWrap=true)
                }
            }
        }}
    }
}
