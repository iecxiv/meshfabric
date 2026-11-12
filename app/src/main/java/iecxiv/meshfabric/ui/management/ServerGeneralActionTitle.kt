package iecxiv.meshfabric.ui.management
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
@Composable internal fun ServerGeneralActionTitle(text:String) {
    Text(text,style=MaterialTheme.typography.bodyLarge.copy(fontSize=18.sp,fontWeight=FontWeight.Bold))
}
