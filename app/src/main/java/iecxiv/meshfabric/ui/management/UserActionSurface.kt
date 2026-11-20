package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
internal fun profileFormUsesSubview(tab:Int,form:ProfileForm?)=tab in 1..3 && form!=null && (form.users || form.operation in setOf("create","edit","delete"))
internal fun profileLinkedGroupSubview(tab:Int,group:String?,assignment:Pair<String,String>?)=tab in 1..3 && (group!=null || assignment!=null)
@Composable internal fun UserActionSurface(
    onDismissRequest:()->Unit,
    modifier:Modifier=Modifier,
    properties:DialogProperties=DialogProperties(),
    inline:Boolean=LocalProfileUserInline.current,
    title:@Composable ()->Unit,
    text:@Composable ()->Unit,
    confirmButton:@Composable ()->Unit,
    dismissButton:@Composable ()->Unit={}
) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    ProfileUserDetailSurface(inline=inline,onDismissRequest=onDismissRequest,modifier=modifier,properties=properties,
        title={
            if(inline) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=onDismissRequest,modifier=Modifier.testTag("user-action-back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack,if(es) "Volver" else "Back")
                }
                Spacer(Modifier.width(4.dp))
                Box(Modifier.weight(1f)) {title()}
            } else title()
        },text=text,confirmButton=confirmButton,dismissButton=dismissButton)
}
