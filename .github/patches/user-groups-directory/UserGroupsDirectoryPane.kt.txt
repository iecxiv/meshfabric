package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

internal fun profileUserGroupDomainAllowed(actor:String,group:String)=actor.startsWith("user/") && actor.split('/').size==3 && group.startsWith("ugrp/") && group.split('/').size==3 && userDomain(actor)==userDomain(group)
@Composable internal fun UserGroupsDirectoryPane(session:ProfileSession,s:ProfileState,modifier:Modifier=Modifier,onOpenUser:(String)->Unit,onOpenDeviceGroup:(String)->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    var query by rememberSaveable(s.id) { mutableStateOf("") }
    LaunchedEffect(s.ready,s.id,s.site,s.userGroupPhase) { if(s.ready && s.userGroupPhase=="idle") session.loadUserGroupDirectory() }
    val rows=s.listedUserGroups.orEmpty().filter { deviceGroupMatches(query,it.optString("name"),it.optString("desc")) }
    LazyColumn(modifier,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item {
            Text(t("Grupos de usuarios","User groups"),style=MaterialTheme.typography.titleMedium)
            Text(t("Grupos del dominio actual; son distintos de los grupos de dispositivos.","Groups in the current domain; these are separate from device groups."),style=MaterialTheme.typography.bodySmall)
            TextButton(enabled=s.ready && !s.busy && s.userGroupPhase!="loading",onClick={session.loadUserGroupDirectory()}) {Text(t("Actualizar","Refresh"))}
            OutlinedTextField(query,{query=it.take(256)},label={Text(t("Buscar grupo","Search groups"))},singleLine=true,modifier=Modifier.fillMaxWidth())
            if(s.userGroupPhase=="loading") LinearProgressIndicator(Modifier.fillMaxWidth())
            if(!s.ready) Text(t("Sin conexión.","Offline."))
            if(s.userGroupPhase=="failed") Text(t("No se ha podido consultar el listado. Puedes volver a actualizar.","The list could not be retrieved. Refresh to try again."),color=MaterialTheme.colorScheme.error)
            if(s.ready && ProfilePermissions.normalized(s.site) and 256L==0L) Text(t("Vista limitada: el servidor proporciona solo nombres.","Limited view: the server provides names only."),style=MaterialTheme.typography.bodySmall)
            if(s.userGroupPhase=="ready" && rows.isEmpty()) Text(if(query.isBlank()) t("No hay grupos en el dominio actual.","No groups in the current domain.") else t("Sin coincidencias.","No matches."))
        }
        items(rows,key={it.optString("_id")}) { group->
            ManagementCard {
                Text(group.optString("name").ifBlank { t("Grupo sin nombre","Unnamed group") },style=MaterialTheme.typography.titleSmall)
                if(group.optString("desc").isNotBlank()) Text(group.optString("desc"),style=MaterialTheme.typography.bodySmall)
                ProfileDisclosure(t("Miembros y accesos recibidos","Received members and access")) {
                    val links=group.optJSONObject("links")
                    if(links==null) Text(t("El servidor no ha informado detalles de accesos para esta ficha.","The server has not provided access details for this entry."),style=MaterialTheme.typography.bodySmall) else {
                        val ids=links.keys().asSequence().sorted().toList()
                        if(ids.isEmpty()) Text(t("Sin asignaciones recibidas.","No assignments received."))
                        for(id in ids.take(100)) {
                            val user=s.users.firstOrNull {it.optString("_id")==id}
                            val mesh=s.groups.firstOrNull {it.optString("_id")==id}
                            when {
                                user!=null && canAdministerUser(s,user)->TextButton(enabled=s.canWrite,onClick={onOpenUser(id)}) {Text(t("Usuario: ","User: ")+user.optString("name"))}
                                mesh!=null->TextButton(enabled=s.ready && !s.busy,onClick={onOpenDeviceGroup(id)}) {Text(t("Grupo de dispositivos: ","Device group: ")+mesh.optString("name"))}
                                else->Text(id,style=MaterialTheme.typography.bodySmall)
                            }
                        }
                        if(ids.size>100) Text(t("Se muestran los primeros 100 enlaces recibidos.","Showing the first 100 received links."),style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
