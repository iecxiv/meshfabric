package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal data class DeviceBrowseMenuState(val sort:DeviceListSort,val reverse:Boolean,val filter:DeviceListFilter,
    val osName:Boolean,val selecting:Boolean,val onSort:(DeviceListSort)->Unit,val onReverse:()->Unit,
    val onFilter:(DeviceListFilter)->Unit,val onOsName:()->Unit,val onSelection:()->Unit)
private val BrowseBlue=Color(0xFF2196F3)
private fun sortLabel(s:DeviceListSort,es:Boolean)=when(s) {
    DeviceListSort.GROUP->if(es) "Grupo" else "Group"
    DeviceListSort.POWER->if(es) "Encendido" else "Power"
    DeviceListSort.DEVICE->if(es) "Dispositivo" else "Device"
    DeviceListSort.TAGS->if(es) "Etiqueta" else "Tags"
    DeviceListSort.GROUP_TAGS->if(es) "Etiquetas de grupo" else "Group tags"
    DeviceListSort.LAST_SEEN->if(es) "Última vez visto" else "Last seen"
    DeviceListSort.LAST_BOOT->if(es) "Último arranque" else "Last boot up time"
}
private fun filterLabel(f:DeviceListFilter,es:Boolean)=when(f) {
    DeviceListFilter.ALL->if(es) "Todos" else "All"
    DeviceListFilter.ONLINE->if(es) "En línea" else "Online"
    DeviceListFilter.OFFLINE->if(es) "Fuera de línea" else "Offline"
    DeviceListFilter.SESSIONS->if(es) "Sesiones" else "Sessions"
    DeviceListFilter.STARRED->if(es) "Destacado" else "Starred"
    DeviceListFilter.AMT->"Intel AMT"
    DeviceListFilter.HELP->if(es) "Ayuda" else "Help"
    DeviceListFilter.TAGGED->if(es) "Etiquetado" else "Tagged"
    DeviceListFilter.UNTAGGED->if(es) "Sin etiquetar" else "Untagged"
}
@Composable private fun ColumnScope.BrowseSortItems(menu:DeviceBrowseMenuState,es:Boolean,close:()->Unit) {
    DeviceListSort.entries.forEach { option ->
        DropdownMenuItem(text={Text(sortLabel(option,es))},leadingIcon={Icon(browseSortImage(option),null,Modifier.size(20.dp))},trailingIcon={RadioButton(selected=menu.sort==option,onClick=null)},
            onClick={menu.onSort(option);close()})
    }
    HorizontalDivider()
    DropdownMenuItem(text={Text(if(es) "Invertir orden" else "Reverse order")},leadingIcon={Icon(Icons.Default.SwapVert,null,Modifier.size(20.dp))},
        trailingIcon={Checkbox(checked=menu.reverse,onCheckedChange=null)},onClick=menu.onReverse)
}
@Composable internal fun DeviceHeaderSort(menu:DeviceBrowseMenuState) {
    var expanded by remember {mutableStateOf(false)}
    val es=LocalConfiguration.current.locales[0].language=="es"
    Box {
        IconButton(onClick={expanded=true},modifier=Modifier.size(38.dp).testTag("devices_header_sort")) {
            Icon(Icons.AutoMirrored.Filled.Sort,if(es) "Ordenar" else "Sort",Modifier.size(20.dp),tint=if(menu.sort!=DeviceListSort.GROUP || menu.reverse) BrowseBlue else LocalContentColor.current)
        }
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false},modifier=Modifier.heightIn(max=(LocalConfiguration.current.screenHeightDp.dp-96.dp).coerceAtLeast(96.dp)),
            shape=MaterialTheme.shapes.medium,containerColor=MaterialTheme.colorScheme.surfaceVariant,tonalElevation=2.dp,
            border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) { BrowseSortItems(menu,es) {expanded=false} }
    }
}
@Composable internal fun DeviceHeaderBrowseFilter(showIps:Boolean,showDisconnected:Boolean,onShowIps:(Boolean)->Unit,
    onShowDisconnected:(Boolean)->Unit,menu:DeviceBrowseMenuState) {
    var expanded by remember {mutableStateOf(false)}
    var page by remember {mutableStateOf(0)}
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun close() {expanded=false;page=0}
    Box {
        IconButton(onClick={page=0;expanded=true},modifier=Modifier.size(38.dp).testTag("devices_header_filter")) {
            Icon(Icons.Default.FilterAlt,if(es) "Filtros de equipos" else "Device filters",Modifier.size(20.dp),
                tint=if(showIps || showDisconnected || menu.filter!=DeviceListFilter.ALL || menu.selecting) BrowseBlue else LocalContentColor.current)
        }
        DropdownMenu(expanded=expanded,onDismissRequest={close()},modifier=Modifier.heightIn(max=(LocalConfiguration.current.screenHeightDp.dp-96.dp).coerceAtLeast(96.dp)).testTag("devices_header_filter_menu"),
            shape=MaterialTheme.shapes.medium,containerColor=MaterialTheme.colorScheme.surfaceVariant,tonalElevation=2.dp,
            border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
            if(page!=0) DropdownMenuItem(text={Text(if(es) "Volver" else "Back")},leadingIcon={Icon(Icons.AutoMirrored.Filled.ArrowBack,null)},onClick={page=0})
            when(page) {
                2->DeviceListFilter.entries.forEach { option -> DropdownMenuItem(text={Text(filterLabel(option,es))},leadingIcon={Icon(browseFilterImage(option),null,Modifier.size(20.dp))},
                    trailingIcon={RadioButton(selected=menu.filter==option,onClick=null)},onClick={menu.onFilter(option);close()}) }
                else->{
                    DropdownMenuItem(text={Text(if(menu.selecting) {if(es) "Cancelar selección" else "Cancel selection"} else {if(es) "Seleccionar" else "Select"})},
                        leadingIcon={Icon(Icons.Default.CheckBox,null)},onClick={menu.onSelection();close()})
                    DropdownMenuItem(text={Text(if(es) "Filtrar" else "Filter")},leadingIcon={Icon(Icons.Default.FilterAlt,null)},trailingIcon={Icon(Icons.Default.ChevronRight,null)},onClick={page=2})
                    DropdownMenuItem(text={Text(if(es) "Mostrar IPs" else "Show IPs")},leadingIcon={Icon(Icons.Default.Visibility,null)},
                        trailingIcon={Checkbox(checked=showIps,onCheckedChange=null)},modifier=Modifier.testTag("devices_header_show_ips").semantics {selected=showIps},onClick={onShowIps(!showIps);close()})
                    DropdownMenuItem(enabled=menu.filter!=DeviceListFilter.OFFLINE,text={Text(if(es) "Mostrar desconectados" else "Show disconnected devices")},
                        leadingIcon={Icon(Icons.Default.LinkOff,null)},trailingIcon={Checkbox(checked=showDisconnected,onCheckedChange=null)},
                        modifier=Modifier.testTag("devices_header_show_disconnected").semantics {selected=showDisconnected},onClick={onShowDisconnected(!showDisconnected);close()})
                    HorizontalDivider()
                    DropdownMenuItem(text={Text(if(es) "Nombre de sistema operativo" else "Show OS name")},leadingIcon={Icon(Icons.Default.Computer,null,Modifier.size(20.dp))},trailingIcon={Checkbox(checked=menu.osName,onCheckedChange=null)},onClick={menu.onOsName();close()})
                    HorizontalDivider()
                    DropdownMenuItem(text={Text(if(es) "Restablecer filtros" else "Reset filters")},
                        leadingIcon={Icon(Icons.Default.FilterAltOff,null,Modifier.size(20.dp))},
                        modifier=Modifier.testTag("devices_header_reset_filters"),
                        onClick={menu.onFilter(DeviceListFilter.ALL);onShowIps(false);onShowDisconnected(false);close()})
                }
            }
        }
    }
}

private fun browseSortImage(value:DeviceListSort)=when(value) {
    DeviceListSort.GROUP->Icons.Default.Folder
    DeviceListSort.POWER->Icons.Default.PowerSettingsNew
    DeviceListSort.DEVICE->Icons.Default.Computer
    DeviceListSort.TAGS->Icons.Default.Label
    DeviceListSort.GROUP_TAGS->Icons.Default.FolderShared
    DeviceListSort.LAST_SEEN->Icons.Default.History
    DeviceListSort.LAST_BOOT->Icons.Default.RestartAlt
}
private fun browseFilterImage(value:DeviceListFilter)=when(value) {
    DeviceListFilter.ALL->Icons.Default.Devices
    DeviceListFilter.ONLINE->Icons.Default.Link
    DeviceListFilter.OFFLINE->Icons.Default.LinkOff
    DeviceListFilter.SESSIONS->Icons.Default.DesktopWindows
    DeviceListFilter.STARRED->Icons.Default.Star
    DeviceListFilter.AMT->Icons.Default.Memory
    DeviceListFilter.HELP->Icons.Default.LiveHelp
    DeviceListFilter.TAGGED->Icons.Default.Label
    DeviceListFilter.UNTAGGED->Icons.Default.LabelOff
}
