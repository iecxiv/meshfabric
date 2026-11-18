package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.ui.components.AppRefreshIconButton

private val HeaderActiveBlue=Color(0xFF2196F3)

@Composable internal fun DeviceHeaderFilter(showIps:Boolean,showDisconnected:Boolean,onShowIps:(Boolean)->Unit,onShowDisconnected:(Boolean)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    val inactiveColor=LocalContentColor.current
    val configuration=LocalConfiguration.current
    val es=configuration.locales[0].language=="es"
    val density=LocalDensity.current
    val insets=WindowInsets.safeDrawing
    val insetHeight=with(density) {(insets.getTop(this)+insets.getBottom(this)).toDp()}
    val maxHeight=(configuration.screenHeightDp.dp-insetHeight-48.dp).coerceAtLeast(0.dp)
    Box {
        IconButton(onClick={expanded=true},modifier=Modifier.size(38.dp).testTag("devices_header_filter")) {
            Icon(Icons.Default.FilterAlt,if(es) "Filtros de equipos" else "Device filters",Modifier.size(20.dp),
                tint=if(showIps || showDisconnected) HeaderActiveBlue else inactiveColor)
        }
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false},modifier=Modifier.heightIn(max=maxHeight).testTag("devices_header_filter_menu"),
            shape=MaterialTheme.shapes.medium,containerColor=MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation=2.dp,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
            DropdownMenuItem(text={Text(if(es) "Mostrar IPs" else "Show IPs")},
                leadingIcon={Icon(Icons.Default.Visibility,null,tint=if(showIps) HeaderActiveBlue else inactiveColor)},
                modifier=Modifier.testTag("devices_header_show_ips").semantics {selected=showIps},
                onClick={onShowIps(!showIps);expanded=false})
            DropdownMenuItem(text={Text(if(es) "Mostrar desconectados" else "Show disconnected devices")},
                leadingIcon={Icon(Icons.Default.LinkOff,null,tint=if(showDisconnected) HeaderActiveBlue else inactiveColor)},
                modifier=Modifier.testTag("devices_header_show_disconnected").semantics {selected=showDisconnected},
                onClick={onShowDisconnected(!showDisconnected);expanded=false})
        }
    }
}

@Composable internal fun DeviceHeaderRefresh(onRefresh:()->Unit) {
    AppRefreshIconButton(onRefresh=onRefresh,modifier=Modifier.testTag("devices_header_refresh"))
}
