package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

internal fun profileMemberAddCandidate(actor:String,group:JSONObject,account:JSONObject):Boolean=runCatching {
    val uid=account.getString("_id");val gid=group.getString("_id")
    profileUserGroupMemberId(actor,uid) && profileCreatedUserGroupId(actor,gid) && profileUserGroupAddToken(uid)!=null && group.isNull("membershipType") && !group.getJSONObject("links").has(uid) && !account.getJSONObject("links").has(gid)
}.getOrDefault(false)
internal fun profileMemberAddPacket(actor:String,server:String,group:JSONObject,account:JSONObject,confirmed:Boolean=false)=JSONObject().put("action","addusertousergroup").put("ugrpid",group.getString("_id")).put("usernames",JSONArray().put(requireNotNull(profileUserGroupAddToken(account.getString("_id"))))).put("_addActor",actor).put("_addServer",server).put("_addGroupBefore",profileUserGroupDeleteSnapshot(group)).put("_addUser",account.getString("_id")).put("_addUserBefore",profileUserGroupDeleteSnapshot(account)).put("_addConfirmed",confirmed)
@Composable internal fun UserGroupMemberAddDialog(session:ProfileSession,s:ProfileState,group:JSONObject,onDismiss:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    val actor=remember {s.id}
    val server=session.broadcastServerLabel()
    val beforeServer=remember {server}
    var query by remember {mutableStateOf("")}
    var selected by remember {mutableStateOf<JSONObject?>(null)}
    var review by remember {mutableStateOf(false)}
    var approved by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf(false)}
    val groupIdentity=profileReviewIdentity(group,s.listedUserGroups.orEmpty(),es)
    val selectedIdentity=profileReviewIdentity(selected,s.users,es)
    val identityReady=groupIdentity.usable && selectedIdentity.usable
    var invalidated by remember {mutableStateOf(false)}
    val context=groupIdentity.usable && !invalidated && s.canWrite && s.userGroupPhase=="ready" && s.id==actor && server.isNotBlank() && server==beforeServer && ProfilePermissions.manageUsers(s.site) && profileUserGroupEditRole(s.site)
    val packet=selected?.let {runCatching {profileMemberAddPacket(actor,beforeServer,group,it,approved)}.getOrNull()}
    val canReview=context && identityReady && packet!=null && profileUserGroupMemberAddAllowed(s,JSONObject(packet.toString()).put("_addConfirmed",true),server)
    LaunchedEffect(review,canReview) {if(review && !canReview) {approved=false;invalidated=true}}
    val rows=s.users.filter {profileMemberAddCandidate(actor,group,it) && (query.isBlank() || it.optString("_id").contains(query,true) || it.optString("name").contains(query,true) || it.optString("realname").contains(query,true) || it.optString("email").contains(query,true))}
    ManagementDialog(onDismissRequest=onDismiss,modifier=Modifier.widthIn(max=640.dp).fillMaxWidth(0.94f),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn,usePlatformDefaultWidth=false,dismissOnClickOutside=false),title={Text(t("Incorporar usuario al grupo","Add user to group"))},text={
        Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*0.68f).dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(groupIdentity.label,style=MaterialTheme.typography.titleSmall)
            if(!groupIdentity.usable || (selected!=null && !selectedIdentity.usable)) Text(t("No se puede confirmar: falta un nombre o los datos visibles no distinguen inequívocamente la cuenta y el grupo. Cierra y actualiza o selecciona una cuenta identificable.","Cannot confirm: a name is missing or visible information does not uniquely identify the account and group. Close and refresh or select an identifiable account."),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            Text(beforeServer,style=MaterialTheme.typography.bodySmall)
            Text(t("Primera versión: una cuenta ASCII sin mayúsculas, con enlaces conocidos y sin membresía previa. Requiere administrar usuarios y grupos. Los grupos externos quedan excluidos.","Initial version: one ASCII account without uppercase characters, known links and no existing membership. User and group administration are required. External groups are excluded."),style=MaterialTheme.typography.bodySmall)
            if(review) {
                Text(t("Usuario: ","User: ")+selectedIdentity.label)
                Row {Checkbox(approved,{approved=it},enabled=canReview,modifier=Modifier.testTag("member-add-consent"));Text(t("Confirmo incorporar esta cuenta a este grupo.","I confirm adding this account to this group."),Modifier.weight(1f))}
            } else {
                OutlinedTextField(query,{query=it.take(256)},label={Text(t("Buscar cuenta elegible","Search eligible accounts"))},enabled=context,singleLine=true,modifier=Modifier.fillMaxWidth().testTag("member-add-search"))
                Box(Modifier.fillMaxWidth().height(144.dp)) {LazyColumn {items(rows.take(100),key={it.optString("_id")}) {account->TextButton(modifier=Modifier.testTag("member-add-candidate-"+account.optString("_id")),enabled=context && profileReviewIdentity(account,s.users,es).usable,onClick={val copy=runCatching {JSONObject(account.toString()).also {profileUserGroupDeleteSnapshot(it)}}.getOrNull();if(copy!=null) {selected=copy;approved=false;error=false}}) {Text(profileReviewIdentity(account,s.users,es).label)}}}}
                if(rows.size>100) Text(t("Primeras 100 coincidencias; busca para acotar.","First 100 matches; search to narrow them down."),style=MaterialTheme.typography.bodySmall)
                if(rows.isEmpty()) Text(t("No hay cuentas elegibles recibidas para esta búsqueda.","No received eligible accounts match this search."),style=MaterialTheme.typography.bodySmall)
                selected?.let {Text(t("Seleccionado: ","Selected: ")+selectedIdentity.label,style=MaterialTheme.typography.bodySmall)}
                if(rows.any {!profileReviewIdentity(it,s.users,es).usable}) Text(t("Hay cuentas sin nombre o sin contexto suficiente para distinguirlas. No se pueden seleccionar hasta recibir datos inequívocos.","Some accounts have no name or insufficient distinguishing information. They cannot be selected until unambiguous data is received."),style=MaterialTheme.typography.bodySmall)
            }
            Text(t("El alta establece la membresía con rights=1 y puede cambiar acceso o políticas heredadas. La comprobación previa no bloquea cambios concurrentes. Se verifican respuesta y enlace del grupo, no todos los permisos efectivos ni el vínculo inverso.","Addition sets membership rights=1 and may change inherited access or policies. Preflight checks do not lock out concurrent changes. The response and group link are checked, not all effective rights or the reverse link."),style=MaterialTheme.typography.bodySmall)
            if(!context || error || (selected!=null && !canReview)) Text(t("Espera la actualización o vuelve a seleccionar tras revisar datos y permisos. Si cambió el grupo, cierra y actualiza.","Wait for refresh or reselect after reviewing data and rights. If the group changed, close and refresh."),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            Text(t("No hay reintentos automáticos. Si no se confirma, consulta el estado antes de repetir.","No automatic retries. If unconfirmed, check state before repeating."),style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={Button(modifier=Modifier.testTag(if(review) "member-add-send" else "member-add-prepare"),enabled=if(review) context && identityReady && packet!=null && profileUserGroupMemberAddAllowed(s,packet,server) else canReview,onClick={if(!review) {review=true;approved=false} else if(canReview && packet!=null && session.submit(packet)) onDismiss() else error=true}) {Text(if(review) t("Incorporar","Add member") else t("Revisar","Review"))}},dismissButton={Row {if(review) TextButton(onClick={review=false;approved=false;invalidated=false}) {Text(t("Volver","Back"))};TextButton(onClick=onDismiss) {Text(t("Cancelar","Cancel"))}}})
}
