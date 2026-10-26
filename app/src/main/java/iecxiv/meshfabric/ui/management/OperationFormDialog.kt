package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import iecxiv.meshfabric.R

@Composable internal fun OperationFormDialog(form:Boolean,title:String,onDismiss:()->Unit,confirm:String,
    enabled:Boolean=true,dangerous:Boolean=false,onConfirm:()->Unit,content:@Composable ()->Unit) {
    if(!form) {
        FabricDialog(title=title,onDismiss=onDismiss,confirm=confirm,enabled=enabled,
            dangerous=dangerous,onConfirm=onConfirm,content=content)
        return
    }
    Dialog(onDismissRequest=onDismiss) {
        Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface,tonalElevation=6.dp,
            modifier=Modifier.fillMaxWidth().padding(horizontal=4.dp,vertical=8.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(title,fontWeight=FontWeight.Bold,fontSize=18.sp)
                Spacer(Modifier.height(10.dp))
                Column(Modifier.fillMaxWidth().heightIn(max=440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge) { content() }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick=onDismiss,modifier=Modifier.height(36.dp)) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick=onConfirm,enabled=enabled,modifier=Modifier.height(38.dp),
                        colors=if(dangerous) ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error,
                            contentColor=MaterialTheme.colorScheme.onError) else ButtonDefaults.buttonColors()) {
                        Text(confirm,fontSize=12.sp)
                    }
                }
            }
        }
    }
}
