package iecxiv.meshfabric.ui.management
import androidx.compose.runtime.*
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

internal data class AgentGroupDialog(val mesh:String,val enrollment:Boolean)
internal val LocalAgentGroupDialogOpener=staticCompositionLocalOf<((AgentGroupDialog)->Unit)?> {null}
@Composable internal fun AgentGroupDialogsHost(identity:Any,render:@Composable (AgentGroupDialog,()->Unit)->Unit,content:@Composable ()->Unit) {
    var target by remember(identity) {mutableStateOf<AgentGroupDialog?>(null)}
    CompositionLocalProvider(LocalAgentGroupDialogOpener provides {value->target=value}) {content()}
    target?.let {value->render(value) {target=null}}
}
internal class EnrollmentSaveFailure(val code:String):IOException(code)
internal data class EnrollmentSaveReceipt(val size:Long,val sha256:List<Byte>)
private fun saveGuard(current:()->Boolean) {if(!current()) throw EnrollmentSaveFailure("SAVE_IDENTITY_CHANGED")}
internal fun copyEnrollmentSave(file:File,open:()->OutputStream,current:()->Boolean):EnrollmentSaveReceipt {
    if(!file.isFile || file.length()<=0) throw EnrollmentSaveFailure("SAVE_SOURCE_MISSING_OR_EMPTY")
    val expected=file.length();saveGuard(current)
    val digest=MessageDigest.getInstance("SHA-256");var count=0L
    file.inputStream().use {input->open().use {output->
        val bytes=ByteArray(65536)
        while(true) {saveGuard(current);val n=input.read(bytes);if(n<0) break;count+=n;if(count>expected) throw EnrollmentSaveFailure("SAVE_SOURCE_CHANGED");output.write(bytes,0,n);digest.update(bytes,0,n)}
        output.flush()
    }}
    saveGuard(current)
    if(count<=0 || count!=expected || file.length()!=expected) throw EnrollmentSaveFailure("SAVE_BYTE_COUNT")
    return EnrollmentSaveReceipt(count,digest.digest().toList())
}
internal fun verifyEnrollmentSave(receipt:EnrollmentSaveReceipt,open:()->InputStream,current:()->Boolean) {
    saveGuard(current);val digest=MessageDigest.getInstance("SHA-256");var count=0L
    open().use {input->val bytes=ByteArray(65536);while(true) {saveGuard(current);val n=input.read(bytes);if(n<0) break;count+=n;if(count>receipt.size) throw EnrollmentSaveFailure("SAVE_VERIFY_SIZE");digest.update(bytes,0,n)}}
    saveGuard(current)
    if(receipt.size<=0 || count!=receipt.size || digest.digest().toList()!=receipt.sha256) throw EnrollmentSaveFailure("SAVE_VERIFY_CONTENT")
}
