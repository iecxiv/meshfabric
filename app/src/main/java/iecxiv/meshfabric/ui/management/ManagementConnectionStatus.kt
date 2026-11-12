package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
internal fun managementConnectionTexts(es:Boolean)=if(es) listOf("Conectando con el canal de gestión...","Canal de gestión conectado. El servidor valida los permisos.","Canal de gestión desconectado. Reconectar") else listOf("Connecting to the management channel...","Management channel connected. The server validates permissions.","Management channel disconnected. Reconnect")
@Composable internal fun ManagementConnectionStatus(ready:Boolean,connecting:Boolean,reconnectEnabled:Boolean=true,modifier:Modifier=Modifier,onReconnect:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    val labels=managementConnectionTexts(es)
    val active=if(ready) 1 else if(connecting) 0 else 2
    val reconnect=if(es) "Reconectar" else "Reconnect"
    Box(modifier.fillMaxWidth().testTag("management-connection-row").padding(vertical=8.dp)) {
        Box(Modifier.fillMaxWidth().testTag("management-connection-label").semantics {liveRegion=LiveRegionMode.Polite}) {
            labels.forEachIndexed {index,label->
                val visible=index==active
                var textModifier=Modifier.fillMaxWidth()
                textModifier=if(visible) textModifier else textModifier.alpha(0f).clearAndSetSemantics {}
                if(visible && index==2) textModifier=textModifier.testTag("management-reconnect").clickable(enabled=reconnectEnabled,onClickLabel=reconnect,role=Role.Button,onClick=onReconnect)
                val content=buildAnnotatedString {
                    if(index==2) {
                        append(label.removeSuffix(reconnect))
                        withStyle(SpanStyle(color=if(reconnectEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,textDecoration=TextDecoration.Underline)) {append(reconnect)}
                    } else append(label)
                }
                Text(content,textModifier,style=MaterialTheme.typography.bodySmall,color=if(active==2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
