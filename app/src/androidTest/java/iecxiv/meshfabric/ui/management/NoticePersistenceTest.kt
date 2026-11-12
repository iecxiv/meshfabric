package iecxiv.meshfabric.ui.management
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.io.File
import java.security.KeyStore
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class NoticePersistenceTest {
    @get:Rule val compose=createComposeRule()
    private fun fixture(block:(NoticeInboxDisk,ContextWrapper,File,String)->Unit) {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val dir=File(app.cacheDir,"notice-test-"+UUID.randomUUID());dir.mkdirs()
        val alias="notice-test-"+UUID.randomUUID()
        val context=object:ContextWrapper(app) {override fun getNoBackupFilesDir()=dir}
        try {block(NoticeInboxDisk(context,alias),context,dir,alias)} finally {dir.deleteRecursively();KeyStore.getInstance("AndroidKeyStore").apply {load(null);deleteEntry(alias)}}
    }
    @Test fun reopeningRestoresUnreadAndReadStateWithoutPlaintext()=fixture {disk,context,dir,alias->
        val scope=noticeInboxStorageKey("https://server","user//a")
        val rows=(1L..250L).map {NoticeInboxEntry(AccountNotice(it,"general","private notice content","Device","Group"))}
        disk.save(scope,rows)
        val reopened=NoticeInboxDisk(context,alias)
        assertEquals(rows,reopened.load(scope))
        assertFalse(String(File(dir,"notice-inbox-v1/$scope.bin").readBytes(),Charsets.ISO_8859_1).contains("private notice content"))
        disk.save(scope,noticeInboxRead(rows,1));assertTrue(reopened.load(scope).single {it.notice.serial==1L}.read)
        assertTrue(reopened.load(noticeInboxStorageKey("https://server","user//b")).isEmpty())
        disk.save(scope,emptyList());assertTrue(reopened.load(scope).isEmpty())
    }
    @Test fun damagedCiphertextIsNotSilentlyReplaced()=fixture {disk,_,dir,_->
        val scope=noticeInboxStorageKey("https://server","user//a")
        disk.save(scope,listOf(NoticeInboxEntry(AccountNotice(1,"general","secret","",""))))
        val file=File(dir,"notice-inbox-v1/$scope.bin");val bytes=file.readBytes();bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();file.writeBytes(bytes)
        assertTrue(runCatching {disk.load(scope)}.isFailure);assertArrayEquals(bytes,file.readBytes())
    }
    @Test fun storageErrorIsVisibleAndRetryIsExplicit() {
        var retries=0
        compose.setContent {MaterialTheme {NoticeInboxStorageStatus(NoticeInboxState(storageError=true),false) {retries++}}}
        compose.onNodeWithText("Local storage is unconfirmed",substring=true).assertExists()
        compose.onNodeWithText("Retry storage").performClick();assertEquals(1,retries)
    }
}
