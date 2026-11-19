package iecxiv.meshfabric.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import iecxiv.meshfabric.R
import iecxiv.meshfabric.ui.components.AppDialogAppearance
import iecxiv.meshfabric.ui.components.AppDialogSurface

@Composable internal fun AppearanceCitySelector(city:SolarCity?,maximumWidth:Dp,onSelect:(SolarCity)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    var query by remember {mutableStateOf("")}
    val searchLabel=stringResource(R.string.appearance_city_search)
    val label=city?.name ?: stringResource(R.string.appearance_choose_city)
    val measurer=rememberTextMeasurer()
    val style=MaterialTheme.typography.bodyLarge
    val pixels=(solarCities.map {it.name}+searchLabel).maxOf {measurer.measure(AnnotatedString(it),style=style,softWrap=false).size.width}
    val contentWidth=with(LocalDensity.current) {pixels.toDp().value}
    val menuWidth=appearanceCityPopupWidth(contentWidth,maximumWidth.value).dp
    val cities=solarCities.filter {appearanceSearch(it.name).contains(appearanceSearch(query))}
    Box {
        OutlinedButton(onClick={query="";expanded=true},modifier=Modifier.widthIn(max=maximumWidth).testTag("appearance_city_selector"),
            shape=MaterialTheme.shapes.medium,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp)) {
            Text(label,modifier=Modifier.weight(1f,fill=false),maxLines=2)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowDropDown,contentDescription=null,modifier=Modifier.size(20.dp))
        }
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false},modifier=Modifier.width(menuWidth).heightIn(max=320.dp).testTag("appearance_city_menu"),
            shape=MaterialTheme.shapes.medium,containerColor=MaterialTheme.colorScheme.surfaceVariant,tonalElevation=2.dp,
            border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
            OutlinedTextField(query,{query=it},singleLine=true,label={Text(searchLabel)},modifier=Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp).testTag("appearance_city_search"))
            if(cities.isEmpty()) DropdownMenuItem(enabled=false,text={Text(stringResource(R.string.appearance_no_cities))},onClick={})
            cities.forEach { option ->
                val chosen=option.id==city?.id
                DropdownMenuItem(text={Text(option.name)},modifier=Modifier.testTag("appearance_city_option_${option.id}").semantics {this.selected=chosen},onClick={onSelect(option);expanded=false})
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable internal fun AppearanceAnalogTimeDialog(title:String,initialMinutes:Int,otherMinutes:Int,is24Hour:Boolean,onDismiss:()->Unit,onSave:(Int)->Unit) {
    val initial=initialMinutes.coerceIn(0,1439)
    val state=rememberTimePickerState(initialHour=initial/60,initialMinute=initial%60,is24Hour=is24Hour)
    val canSave=appearanceTimeCanSave(state.hour,state.minute,otherMinutes)
    val scheme=MaterialTheme.colorScheme
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        BoxWithConstraints(Modifier.fillMaxWidth().safeDrawingPadding().imePadding().padding(12.dp),contentAlignment=Alignment.Center) {
            val layout=if(maxWidth>=520.dp && maxWidth>maxHeight) TimePickerLayoutType.Horizontal else TimePickerLayoutType.Vertical
            AppDialogSurface(modifier=Modifier.widthIn(max=560.dp).heightIn(max=maxHeight).testTag("appearance_time_dialog")) {
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(title,modifier=Modifier.fillMaxWidth(),fontWeight=FontWeight.Bold,fontSize=18.sp)
                    Box(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),contentAlignment=Alignment.Center) {
                        TimePicker(state=state,modifier=Modifier.testTag("appearance_analog_clock"),layoutType=layout,
                            colors=TimePickerDefaults.colors(clockDialColor=scheme.surfaceVariant,selectorColor=scheme.primary,containerColor=AppDialogAppearance.containerColor()))
                    }
                    if(!canSave) Text(stringResource(R.string.appearance_schedule_note),style=MaterialTheme.typography.bodySmall,color=scheme.error)
                    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.End),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick=onDismiss,modifier=Modifier.testTag("appearance_time_cancel")) {Text(stringResource(R.string.cancel))}
                        TextButton(enabled=canSave,onClick={if(canSave) onSave(state.hour*60+state.minute)},modifier=Modifier.testTag("appearance_time_save")) {Text(stringResource(R.string.save))}
                    }
                }
            }
        }
    }
}
