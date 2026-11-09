package iecxiv.meshfabric.ui.management
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
internal fun profileOwnAccount(actor:String,target:String)=actor.isNotBlank() && actor==target
internal data class ProfileOwnAccountRequest(val actor:String,val server:String,val action:String)
internal fun profileOwnAccountMode(request:ProfileOwnAccountRequest,actor:String,server:String):Int? {
    if(!profileOwnAccount(actor,request.actor) || server.isBlank() || request.server!=server) return null
    return when(request.action) {"password"->7;"mfa"->10;else->null}
}
@Composable internal fun ProfileOwnAccountActions(enabled:Boolean,onPassword:()->Unit,onMfa:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    ProfileActionGroup {
        ProfileGroupActionButton(if(es) "Cambiar contraseña" else "Change password",Icons.Default.Lock,enabled,onClick=onPassword)
        ProfileGroupActionButton(if(es) "Gestionar MFA" else "Manage MFA",Icons.Default.Security,enabled,onClick=onMfa)
    }
}
@Composable internal fun ProfileOtherUserActions(actor:String,target:String,content:@Composable ()->Unit) {
    if(!profileOwnAccount(actor,target)) content()
}
@Composable internal fun ProfileOwnAccountLaunch(request:ProfileOwnAccountRequest?,actor:String,server:String,ready:Boolean,busy:Boolean,onOpen:(Int)->Unit,onConsumed:()->Unit) {
    LaunchedEffect(request,actor,server,ready,busy) {
        if(request!=null) {
            val mode=profileOwnAccountMode(request,actor,server)
            if(mode==null) onConsumed() else if(ready && !busy) {onConsumed();onOpen(mode)}
        }
    }
}
@Composable internal fun ProfileOwnMfaChoices(enabled:Boolean,onSelect:(Int)->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    val labels=if(es) listOf("Administrar la aplicación de autenticación","Administrar claves de seguridad","Administrar códigos de respaldo") else listOf("Manage authenticator app","Manage security keys","Manage backup codes")
    ProfileActionGroup {labels.forEachIndexed {index,label->TextButton(enabled=enabled,onClick={onSelect(index)}) {Text(label)}}}
}
