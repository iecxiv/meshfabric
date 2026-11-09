package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import org.json.JSONArray
import org.json.JSONObject
internal val USER_DEVICE_GRANT_BITS=listOf(8L,256L,16L,64L,128L,16384L,131072L,1048576L)
internal fun userDeviceGrantRights(value:Long)=value>0L && value and USER_DEVICE_GRANT_BITS.fold(0L) {a,b->a or b}.inv()==0L
internal fun userDeviceGrantPacket(user:JSONObject,node:JSONObject,rights:Long):JSONObject {
    require(userDeviceGrantRights(rights))
    return JSONObject().put("action","adddeviceuser").put("nodeid",node.getString("_id")).put("nodename",node.getString("name")).put("userids",JSONArray().put(user.getString("_id"))).put("rights",rights)
}
internal fun userDeviceGrantSame(a:JSONObject?,b:JSONObject?,keys:List<String>)=a!=null && b!=null && keys.all {a.opt(it)?.toString()==b.opt(it)?.toString()}
internal fun userDeviceGrantContext(s:ProfileState,user:JSONObject?,node:JSONObject?,mesh:JSONObject?,actor:String,site:Long,beforeServer:String,server:String,es:Boolean):Boolean {
    if(!s.canWrite || s.detailsBusy || user==null || node==null || mesh==null || actor.isBlank() || s.id!=actor || ProfilePermissions.normalized(s.site)!=ProfilePermissions.normalized(site) || beforeServer.isBlank() || server!=beforeServer) return false
    val currentUser=s.users.singleOrNull {it.optString("_id")==user.optString("_id")} ?: return false
    val currentNode=s.nodes.orEmpty().singleOrNull {it.optString("_id")==node.optString("_id")} ?: return false
    val currentMesh=s.groups.singleOrNull {it.optString("_id")==mesh.optString("_id")} ?: return false
    return currentNode.optString("meshid")==currentMesh.optString("_id") && canAdministerUser(s,currentUser) && canGrantDevice(s,currentNode) && profileReviewIdentity(user,s.users,es).usable && profileReviewIdentity(node,s.nodes.orEmpty(),es).usable && profileReviewIdentity(mesh,s.groups,es).usable && userDeviceGrantSame(user,currentUser,listOf("_id","name","realname","email","siteadmin","links","groups")) && userDeviceGrantSame(node,currentNode,listOf("_id","name","desc","meshid","links")) && userDeviceGrantSame(mesh,currentMesh,listOf("_id","name","desc","mtype","links"))
}
internal data class UserDeviceChoice(val node:JSONObject,val mesh:JSONObject?,val label:String,val usable:Boolean)
internal fun userDeviceChoices(s:ProfileState,user:JSONObject,query:String,es:Boolean):List<UserDeviceChoice> = s.nodes.orEmpty().map {node->
    val np=profileReceivedLinkPresentation(s,user,node.optString("_id"),es)
    val mid=node.optString("meshid");val mesh=s.groups.singleOrNull {it.optString("_id")==mid}
    val gp=profileReceivedLinkPresentation(s,user,mid,es)
    UserDeviceChoice(node,mesh,np.label+" · "+gp.label,np.usable && gp.usable && s.canWrite && !s.detailsBusy && canGrantDevice(s,node))
}.filter {it.label.contains(query.trim(),true)}
@Composable internal fun UserDeviceGrantDialog(session:ProfileSession,s:ProfileState,uid:String,onBack:()->Unit,onSaved:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    val subject=remember {s.users.singleOrNull {it.optString("_id")==uid}?.let {JSONObject(it.toString())}}
    val actor=remember {s.id};val site=remember {s.site};val server=remember {session.broadcastServerLabel()}
    var query by remember {mutableStateOf("")}
    var selected by remember {mutableStateOf<JSONObject?>(null)}
    var parent by remember {mutableStateOf<JSONObject?>(null)}
    var rights by remember {mutableLongStateOf(264L)}
    var frozen by remember {mutableStateOf<Long?>(null)}
    var approved by remember {mutableStateOf(false)}
    var invalidated by remember {mutableStateOf(false)}
    var submitted by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf(false)}
    val identity=profileReviewIdentity(subject,s.users,es)
    val context=userDeviceGrantContext(s,subject,selected,parent,actor,site,server,session.broadcastServerLabel(),es)
    LaunchedEffect(frozen,context) {if(frozen!=null && !context && !submitted) {invalidated=true;approved=false}}
    val current=context && !invalidated && !submitted
    val choices=if(subject!=null && frozen==null && !submitted) userDeviceChoices(s,subject,query,es) else emptyList()
    ManagementDialog(onDismissRequest=onBack,modifier=Modifier.widthIn(max=640.dp).fillMaxWidth(0.94f),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn,usePlatformDefaultWidth=false,dismissOnClickOutside=false),title={Text(t("Agregar dispositivo","Add device"))},text={
        Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*0.68f).dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(t("Usuario: ","User: ")+identity.label,style=MaterialTheme.typography.titleSmall)
            Text(server,style=MaterialTheme.typography.bodySmall)
            Text(t("Concede acceso directo a un dispositivo existente; no instala un agente. No revoca permisos heredados y puede sustituir una asignación directa existente.","Grants direct access to an existing device; does not install an agent. Does not revoke inherited rights and may replace an existing direct assignment."),style=MaterialTheme.typography.bodySmall)
            if(s.detailsBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(frozen==null && !submitted) {
                OutlinedTextField(query,{query=it.take(256)},label={Text(t("Buscar dispositivo","Search device"))},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("device-grant-search"))
                for(choice in choices.take(100)) TextButton(enabled=identity.usable && choice.usable,modifier=Modifier.testTag("device-grant-candidate"),onClick={selected=JSONObject(choice.node.toString());parent=choice.mesh?.let {JSONObject(it.toString())};approved=false;error=false}) {Text((if(selected?.optString("_id")==choice.node.optString("_id")) "● " else "○ ")+choice.label)}
                if(choices.isEmpty()) Text(t("Sin resultados disponibles. Actualiza o cambia la búsqueda.","No available results. Refresh or change the search."))
                Text(t("Máximo 100 resultados. Los destinos sin ficha inequívoca o sin autoridad quedan bloqueados.","Maximum 100 results. Targets without an unambiguous entry or authority are disabled."),style=MaterialTheme.typography.bodySmall)
                for(bit in USER_DEVICE_GRANT_BITS) ProfileAlignedCheckboxRow {
                    Checkbox(rights and bit!=0L,{checked->rights=if(checked) rights or bit else rights and bit.inv();approved=false},enabled=s.canWrite && !s.detailsBusy)
                    Text(deviceGroupRightsLabel(bit,es),Modifier.weight(1f))
                }
            }
            if(frozen!=null) {
                Text(t("Dispositivo: ","Device: ")+profileReviewIdentity(selected,s.nodes.orEmpty(),es).label)
                Text(t("Grupo de dispositivos: ","Device group: ")+profileReviewIdentity(parent,s.groups,es).label)
                Text(t("Derechos congelados: ","Frozen rights: ")+String.format(java.util.Locale.ROOT,"0x%08X",frozen))
                Text(deviceGroupRightsLabel(requireNotNull(frozen),es),style=MaterialTheme.typography.bodySmall)
                if(!submitted) ProfileAlignedCheckboxRow {Checkbox(approved,{approved=it},enabled=current,modifier=Modifier.testTag("device-grant-consent"));Text(t("Confirmo asignar estos derechos al usuario y dispositivo revisados.","I confirm assigning these rights to the reviewed user and device."),Modifier.weight(1f))}
            }
            Text(t("La revisión no es atómica con el servidor. Un cambio concurrente puede sobrescribirse. No hay reintentos automáticos; cerrar no cancela un envío. Aceptar el envío no confirma el resultado en el servidor.","Review is not atomic with the server. A concurrent change may be overwritten. There are no automatic retries; closing does not cancel a sent request. Accepting submission does not confirm the server outcome."),style=MaterialTheme.typography.bodySmall)
            if(invalidated || (frozen!=null && !context)) Text(t("Revisión invalidada. Vuelve a seleccionar y revisa de nuevo; si persiste el cambio, cierra y actualiza.","Review invalidated. Select and review again; if the change persists, close and refresh."),color=MaterialTheme.colorScheme.error)
            if(error) Text(t("No se pudo iniciar el envío de forma segura. Comprueba el estado antes de repetir.","Submission could not be safely initiated. Check state before repeating."),color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={if(!submitted) Button(enabled=current && userDeviceGrantRights(frozen ?: rights) && (frozen==null || approved),modifier=Modifier.testTag(if(frozen==null) "device-grant-prepare" else "device-grant-send"),onClick={
        if(frozen==null) {frozen=rights;approved=false}
        else if(current && approved) {
            val packet=userDeviceGrantPacket(requireNotNull(subject),requireNotNull(selected),requireNotNull(frozen))
            submitted=true;approved=false
            if(session.submit(packet)) onSaved() else error=true
        }
    }) {Text(if(frozen==null) t("Revisar asignación","Review assignment") else t("Asignar derechos","Assign rights"))}},dismissButton={Row {
        if(frozen!=null && !submitted) TextButton(onClick={frozen=null;selected=null;parent=null;approved=false;invalidated=false;error=false}) {Text(t("Volver a seleccionar","Select again"))}
        TextButton(onClick=onBack) {Text(t("Cerrar","Close"))}
    }})
}
