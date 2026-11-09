package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import org.json.JSONObject

internal fun profileGroupMeshReportedRights(record:JSONObject?,id:String):Long?=runCatching {
    val value=record?.optJSONObject("links")?.optJSONObject(id)?.opt("rights")
    require(value is Number)
    userAdvancedMask(value)
}.getOrNull()
internal fun profileGroupMeshReviewAllowed(s:ProfileState,gid:String,mid:String,server:String):Boolean=runCatching {
    val group=requireNotNull(s.listedUserGroups?.singleOrNull {it.optString("_id")==gid})
    val mesh=requireNotNull(s.groups.singleOrNull {it.optString("_id")==mid})
    val candidate=JSONObject().put("action","removemeshuser").put("meshid",mid).put("userid",gid).put("_meshActor",s.id).put("_meshServer",server).put("_meshGroupBefore",profileUserGroupDeleteSnapshot(group)).put("_meshDeviceBefore",profileUserGroupDeleteSnapshot(mesh)).put("_meshConfirmed",true)
    profileGroupMeshRevokeAllowed(s,candidate,server)
}.getOrDefault(false)
@Composable internal fun GroupMeshAssignmentReview(session:ProfileSession,s:ProfileState,gid:String,mid:String,onDismiss:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    fun mask(value:Long?)=value?.let {java.lang.String.format(java.util.Locale.ROOT,"0x%08X",it)} ?: t("No informado","Not reported")
    val server=session.broadcastServerLabel()
    val group=s.listedUserGroups?.singleOrNull {it.optString("_id")==gid}
    val mesh=s.groups.singleOrNull {it.optString("_id")==mid}
    val eligible=profileGroupMeshReviewAllowed(s,gid,mid,server)
    ManagementDialog(onDismissRequest=onDismiss,modifier=Modifier.widthIn(max=640.dp).fillMaxWidth(0.94f),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn,usePlatformDefaultWidth=false,dismissOnClickOutside=false),title={Text(t("Revisar asignación","Review assignment"))},text={
        Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*0.68f).dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(t("Solo lectura: esta pantalla todavía no permite retirar la asignación.","Read only: this screen does not yet allow removing the assignment."),style=MaterialTheme.typography.titleSmall)
            Text(server,style=MaterialTheme.typography.bodySmall)
            Text(t("Grupo de usuarios: ","User group: ")+group?.optString("name").orEmpty());Text(gid,style=MaterialTheme.typography.bodySmall)
            Text(t("Grupo de dispositivos: ","Device group: ")+mesh?.optString("name").orEmpty());Text(mid,style=MaterialTheme.typography.bodySmall)
            if(s.loading || s.userGroupPhase=="loading") LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(t("Enlace recibido en el grupo de usuarios: ","Link received in the user group: ")+mask(profileGroupMeshReportedRights(group,mid)))
            Text(t("Enlace recibido en el grupo de dispositivos: ","Link received in the device group: ")+mask(profileGroupMeshReportedRights(mesh,gid)))
            Text(t("Derechos directos recibidos del actor: ","Received direct actor rights: ")+mask(profileGroupMeshReportedRights(mesh,s.id)))
            Text(if(eligible) t("Los datos recibidos cumplen la política local preliminar. No es autorización final del servidor ni confirmación de una retirada.","Received data meets the preliminary local policy. This is not final server authorization or confirmation of a removal.") else t("No se puede acreditar la política local con estos datos: pueden faltar registros, enlaces, permisos o coherencia entre ambos extremos. Espera la consulta o actualiza.","These data do not establish the local policy: records, links, rights or agreement between both sides may be missing. Wait for the query or refresh."),style=MaterialTheme.typography.bodySmall)
            ProfileDisclosure(t("Alcance y limitaciones","Scope and limitations")) {
                Text(t("Se solicitan de nuevo los listados al abrir. No es una lectura atómica ni una comprobación de todos los permisos efectivos. Las máscaras se muestran completas, sin interpretar ni cambiar bits desconocidos.","Lists are requested again on opening. This is not an atomic read or a calculation of all effective rights. Complete masks are shown without interpreting or changing unknown bits."),style=MaterialTheme.typography.bodySmall)
                Text(t("La política inicial exige el bit 2 en un enlace directo del actor. No deduce autoridad heredada ni excepciones por administración global. Una asignación 0xFFFFFFFF exige autoridad directa 0xFFFFFFFF.","Initial policy requires bit 2 in a direct actor link. It does not infer inherited authority or global-administrator exceptions. A 0xFFFFFFFF assignment requires direct 0xFFFFFFFF authority."),style=MaterialTheme.typography.bodySmall)
                Text(t("Retirar la asignación afectaría al acceso o políticas heredadas de los miembros; no borraría cuentas ni grupos. El envío y la verificación posterior aún no están integrados.","Removing this assignment could affect members' inherited access or policies; it would not delete accounts or groups. Submission and post-operation verification are not integrated yet."),style=MaterialTheme.typography.bodySmall)
            }
        }
    },confirmButton={TextButton(onClick=onDismiss) {Text(t("Cerrar","Close"))}},dismissButton={})
}
