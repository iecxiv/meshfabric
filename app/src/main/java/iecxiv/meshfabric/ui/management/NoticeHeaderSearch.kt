package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
internal data class NoticeSearchState(val open:Boolean=false,val query:String="") {
    fun cleared()=NoticeSearchState()
    fun toggle()=if(open) cleared() else NoticeSearchState(open=true)
    fun edit(value:String)=if(open) copy(query=value.take(256)) else cleared()
}
@Composable internal fun NoticeSearchAction(state:NoticeSearchState,onToggle:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    val keyboard=LocalSoftwareKeyboardController.current
    IconButton(onClick={if(state.open) keyboard?.hide();onToggle()},modifier=Modifier.testTag("notice-search-toggle")) {
        Icon(if(state.open) Icons.Default.Close else Icons.Default.Search,
            contentDescription=if(state.open) {if(es) "Cerrar búsqueda" else "Close search"} else {if(es) "Buscar destinatario" else "Search recipients"})
    }
}
@Composable internal fun NoticeSearchField(state:NoticeSearchState,onQuery:(String)->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    if(state.open) OutlinedTextField(state.query,onQuery,label={Text(if(es) "Buscar destinatario" else "Search recipients")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("notice-search-input"))
}
