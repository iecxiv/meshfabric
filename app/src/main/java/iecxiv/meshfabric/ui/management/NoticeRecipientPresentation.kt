package iecxiv.meshfabric.ui.management
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
internal fun noticeUserRecipientLabel(name:String,es:Boolean)=name.trim().ifBlank {if(es) "Usuario sin nombre" else "Unnamed user"}
internal fun noticeDeviceRecipientLabel(name:String,group:String,es:Boolean):String {
    val device=name.trim().ifBlank {if(es) "Dispositivo sin nombre" else "Unnamed device"}
    val mesh=group.trim().ifBlank {if(es) "Grupo no disponible" else "Group unavailable"}
    return "$device ($mesh)"
}
@Composable internal fun NoticeRecipientName(label:String) {Text(label,style=MaterialTheme.typography.titleSmall)}
