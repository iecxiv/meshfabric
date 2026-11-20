package iecxiv.meshfabric.ui.management
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    ProfileUserDetailSurface(inline=inline,onDismissRequest=onDismissRequest,modifier=modifier,properties=properties,title=title,text=text,confirmButton=confirmButton,dismissButton=dismissButton)
}
