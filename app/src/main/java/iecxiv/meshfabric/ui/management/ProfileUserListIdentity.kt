package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONObject
@Composable internal fun ProfileUserListIdentity(account:JSONObject,all:List<JSONObject>) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    val identity=profileReviewIdentity(account,all,es)
    key(account.optString("_id")) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Default.Person,null,Modifier.size(20.dp).testTag("profile-user-list-icon"),tint=MaterialTheme.colorScheme.primary)
            Text(identity.label,Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,maxLines=3,overflow=TextOverflow.Ellipsis)
        }
        ProfileDisclosure(if(es) "Identificador" else "Identifier") {Text(account.optString("_id"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}
