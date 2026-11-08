package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

@Composable internal fun UserAdvancedEditor(session:ProfileSession,s:ProfileState,user:JSONObject,onDismiss:()->Unit,onSaved:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    val id=user.optString("_id")
    val initial=remember(id) { runCatching { userAdvancedSnapshot(user) }.getOrNull() }
    if(initial==null) {
        ManagementDialog(onDismissRequest=onDismiss,title={Text(t("Datos incompatibles","Incompatible data"))},text={Text(t("No se pueden editar estos valores sin perder información. Actualiza la ficha.","These values cannot be edited without losing information. Refresh the details."))},confirmButton={TextButton(onClick=onDismiss){Text(t("Cerrar","Close"))}})
        return
    }
    var raw by remember(id) { mutableStateOf(initial.toString()) }
    var quota by remember(id) { mutableStateOf(initial.opt("quota").takeUnless { it==JSONObject.NULL }?.toString().orEmpty()) }
    var inherit by remember(id) { mutableStateOf(initial.isNull("quota")) }
    var approved by remember(id) { mutableStateOf(false) }
    var feedback by remember(id) { mutableStateOf("") }
    val draft=JSONObject(raw)
    fun put(key:String,value:Any) { raw=JSONObject(raw).put(key,value).toString(); approved=false; feedback="" }
    fun mask(key:String)=userAdvancedMask(draft.opt(key))
    fun toggle(key:String,bit:Long,checked:Boolean) {
        var next=if(checked) mask(key) or bit else mask(key) and bit.inv()
        if(key=="removeRights") next=userCanonicalRestrictions(next)
        put(key,next)
    }
    @Composable fun check(label:String,checked:Boolean,enabled:Boolean=true,onChange:(Boolean)->Unit) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Checkbox(checked,onChange,enabled=enabled && s.canWrite); Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium) }
    }
    draft.put("quota",if(inherit) JSONObject.NULL else quota.toLongOrNull()?.takeIf { it in 0..MAX_SAFE_USER_QUOTA } ?: "invalid")
    val packet=runCatching { userAdvancedPacket(id,initial,draft) }.getOrNull()
    val current=s.users.firstOrNull { it.optString("_id")==id }
    val allowed=s.canWrite && packet!=null && userAdvancedAllowed(s,current,packet)
    ManagementDialog(onDismissRequest=onDismiss,modifier=Modifier.widthIn(max=900.dp).fillMaxWidth(0.96f),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn,usePlatformDefaultWidth=false),
        title={Text(t("Permisos y características","Permissions and features"))},text={
        Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*0.72f).dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(user.optString("name"),style=MaterialTheme.typography.titleSmall); Text(id,style=MaterialTheme.typography.bodySmall)
            Text(t("Características","Features"),style=MaterialTheme.typography.titleMedium)
            if(s.server?.optInt("usersSessionRecording")==1) check(t("Grabar sesiones","Record sessions"),mask("flags") and 2L!=0L) { toggle("flags",2L,it) }
            val restrictions=if(es) listOf("Sin control remoto","Sin escritorio","Escritorio solo visualización","Sin terminal","Sin archivos","Sin Registro","Sin software","Sin consola del agente","Sin desinstalación","Sin comandos remotos","Sin despertar dispositivos","Sin reiniciar o apagar") else listOf("No remote control","No desktop","Desktop view only","No terminal","No files","No Registry","No software","No agent console","No uninstall","No remote commands","No wake","No reset or power off")
            for((i,bit) in USER_REMOVAL_BITS.withIndex()) {
                val enabled=!(bit in listOf(65536L,256L,512L,1024L,4194304L,8388608L) && mask("removeRights") and 8L!=0L) && !(bit==256L && mask("removeRights") and 65536L!=0L)
                check(restrictions[i],mask("removeRights") and bit!=0L,enabled) { toggle("removeRights",bit,it) }
            }
            HorizontalDivider()
            Text(t("Permisos del servidor","Server rights"),style=MaterialTheme.typography.titleMedium)
            val actor=ProfilePermissions.normalized(s.site)
            val self=s.id==id
            val full=mask("siteadmin")==0xffffffffL
            check(t("Administrador total","Full administrator"),full,actor==0xffffffffL && !self) { put("siteadmin",if(it) 0xffffffffL else 0L) }
            val rights=if(es) listOf("Copia de seguridad","Administrar usuarios","Restaurar servidor","Archivos del servidor","Actualizar servidor","Cuenta bloqueada","Sin nuevos grupos","Sin MeshCmd","Grupos de usuarios","Grabaciones","Configuración bloqueada","Todos los eventos","Sin nuevos dispositivos") else listOf("Server backup","Manage users","Server restore","Server files","Server update","Account locked","No new groups","No MeshCmd","User groups","Recordings","Locked settings","All events","No new devices")
            for((i,label) in rights.withIndex()) { val bit=1L shl i; check(label,mask("siteadmin") and bit!=0L,!full && userDelegableMask(actor,self) and bit!=0L) { toggle("siteadmin",bit,it) } }
            Text(t("No se cambian tus propios permisos del servidor. Los permisos no delegables quedan bloqueados.","Your own server rights cannot be changed. Non-delegable rights are disabled."),style=MaterialTheme.typography.bodySmall)
            check(t("Usar cuota predeterminada","Use default quota"),inherit) { inherit=it; approved=false }
            if(!inherit) OutlinedTextField(quota,{quota=it;approved=false},label={Text(t("Cuota personalizada en bytes","Custom quota in bytes"))},singleLine=true,enabled=s.canWrite,isError=quota.toLongOrNull()?.let { it !in 0..MAX_SAFE_USER_QUOTA } ?: true,modifier=Modifier.fillMaxWidth())
            HorizontalDivider()
            Text(t("Consentimiento del usuario","User consent"),style=MaterialTheme.typography.titleMedium)
            val consent=if(es) listOf("Avisar: escritorio","Avisar: terminal","Avisar: archivos","Solicitar autorización: escritorio","Solicitar autorización: terminal","Solicitar autorización: archivos","Mostrar barra de conexión","Avisar: Registro","Solicitar autorización: Registro") else listOf("Notify: desktop","Notify: terminal","Notify: files","Prompt: desktop","Prompt: terminal","Prompt: files","Show connection toolbar","Notify: Registry","Prompt: Registry")
            for((i,label) in consent.withIndex()) { val bit=1L shl i; check(label,mask("consent") and bit!=0L) { toggle("consent",bit,it) } }
            Text(t("Estas opciones se combinan con las exigidas por el dominio, grupo y dispositivo; desmarcarlas no anula las heredadas.","These options combine with domain, group and device requirements; clearing them does not override inherited requirements."),style=MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text(t("Reinos administrativos","Administrative realms"),style=MaterialTheme.typography.titleMedium)
            val realms=draft.getJSONArray("groups").let { a->(0 until a.length()).map { a.optString(it) } }
            val realmEnabled=userCanEditRealms(s,user) && s.canWrite
            for((i,realm) in realms.withIndex()) Row(verticalAlignment=Alignment.CenterVertically) {
                OutlinedTextField(realm,{v->put("groups",JSONArray(realms.toMutableList().apply { set(i,v) }))},singleLine=true,enabled=realmEnabled,label={Text(t("Reino","Realm"))},modifier=Modifier.weight(1f))
                TextButton(enabled=realmEnabled,onClick={put("groups",JSONArray(realms.filterIndexed { j,_->j!=i }))}) { Text(t("Quitar","Remove")) }
            }
            TextButton(enabled=realmEnabled && realms.size<512,onClick={put("groups",JSONArray(realms+""))}) { Text(t("Añadir reino","Add realm")) }
            Text(t("No son grupos de dispositivos. Introduce nombres únicos de hasta 64 caracteres, sin espacios al principio o al final y en minúsculas.","These are not device groups. Use unique lowercase names up to 64 characters, with no leading or trailing spaces."),style=MaterialTheme.typography.bodySmall)
            if(!allowed) Text(t("Sin cambios válidos, datos modificados desde otra sesión o permisos insuficientes. Cierra y actualiza la ficha si fuera necesario.","No valid changes, data changed in another session, or insufficient rights. Close and refresh if needed."),style=MaterialTheme.typography.bodySmall)
            check(t("Confirmo aplicar los cambios a este usuario.","I confirm applying the changes to this user."),approved) { approved=it }
            if(feedback.isNotBlank()) Text(feedback,color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={Button(enabled=allowed && approved,onClick={if(packet!=null && session.submit(packet)) onSaved() else feedback=t("No se ha enviado el cambio.","The change was not sent.")}) {Text(t("Aplicar","Apply"))}},dismissButton={TextButton(onClick=onDismiss){Text(t("Cancelar","Cancel"))}})
}
