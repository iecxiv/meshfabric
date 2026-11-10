package iecxiv.meshfabric.ui.management
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.R
import java.text.DateFormat
import java.util.Date
@Composable internal fun ServerEventsSearchHeader(query:String,onQuery:(String)->Unit,expanded:Boolean,onExpanded:(Boolean)->Unit,header:@Composable (@Composable ()->Unit)->Unit,tabs:@Composable ()->Unit={}) {
    val requester=remember {FocusRequester()}
    val keyboard=LocalSoftwareKeyboardController.current
    val focus=LocalFocusManager.current
    Column {
        header {
            IconButton(onClick={
                if(expanded) {onQuery("");focus.clearFocus(force=true);keyboard?.hide()}
                onExpanded(!expanded)
            },modifier=Modifier.size(38.dp).testTag("log-events-search-toggle")) {
                Icon(if(expanded) Icons.Default.Close else Icons.Default.Search,stringResource(R.string.search),Modifier.size(20.dp))
            }
        }
        tabs()
        AnimatedVisibility(expanded) {
            LaunchedEffect(expanded) {if(expanded) {withFrameNanos {};requester.requestFocus();keyboard?.show()}}
            OutlinedTextField(query,onQuery,singleLine=true,placeholder={Text(stringResource(R.string.log_search))},
                modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp).focusRequester(requester).testTag("log-events-search"),
                trailingIcon={if(query.isNotEmpty()) IconButton(onClick={onQuery("")},modifier=Modifier.testTag("log-events-search-clear")) {Icon(Icons.Default.Close,stringResource(R.string.clear_search))}})
        }
    }
}
@Composable internal fun ServerEventDayHeader(group:ServerEventDay) {
    val scheme=MaterialTheme.colorScheme
    val label=group.date?.let {DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(group.entries.first().value.time!!))} ?: stringResource(R.string.log_date_unknown)
    Surface(Modifier.fillMaxWidth().testTag("log-date-${group.date ?: "unknown"}"),shape=MaterialTheme.shapes.medium,color=scheme.primaryContainer) {
        Text(label,Modifier.padding(horizontal=12.dp,vertical=6.dp),style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold,color=scheme.onPrimaryContainer)
    }
}
