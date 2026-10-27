from pathlib import Path
import hashlib
root = Path('app/src/main/java/iecxiv/meshfabric/data/remote')
def checked(name, expected):
    p = root / name
    raw = p.read_bytes()
    assert hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest() == expected, 'Review changed source: ' + name
    return p, raw.decode()
p, s = checked('RecoverableUpload.kt', '06cec751931cea6cab53930b7ded58baf7440e8e')
a = s.index('    private suspend fun receive(')
b = s.index('    suspend fun list(', a)
receive = r'''    private suspend fun receive(action: String, id: String? = null, parent: String? = null): JsonObject {
        if (action != "uploadack") android.util.Log.d("MeshFilesUpload", "stage=WAIT_RESPONSE expected=$action")
        val response = withTimeoutOrNull(20_000L) {
            while (true) {
                if (!gate.isCurrent(epoch)) throw IOException("Upload attempt retired while waiting for response")
                val packet = UploadResponseSupport.next(messages)
                if (!gate.isCurrent(epoch)) throw IOException("Upload attempt retired before response dispatch")
                val kind = UploadResponseSupport.text(packet, "action") ?: ""
                val replyId = UploadResponseSupport.text(packet, "reqid")
                if (kind == "uploaderror" && (replyId == null || replyId == id)) throw UploadRejected("Agent rejected upload")
                if (UploadResponseSupport.matches(packet, action, id, parent)) return@withTimeoutOrNull packet
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }
        if (response == null) {
            android.util.Log.w("MeshFilesUpload", "stage=RESPONSE_TIMEOUT expected=$action")
            throw IOException("Upload acknowledgement timed out: $action")
        }
        if (action != "uploadack") android.util.Log.d("MeshFilesUpload", "stage=RESPONSE_ACCEPTED expected=$action")
        return response
    }

'''
s = s[:a] + receive + s[b:]
a = s.index('    suspend fun poll(')
b = s.index('    override fun close()', a)
poll = r'''    suspend fun poll(parent: String, predicate: (List<RemoteUploadEntry>) -> Boolean): List<RemoteUploadEntry> =
        withTimeoutOrNull(8000L) {
            repeat(8) {
                val entries = list(parent)
                if (predicate(entries)) return@withTimeoutOrNull entries
                delay(250L)
            }
            null
        } ?: throw IOException("Remote upload finalization not yet confirmed")

'''
s = s[:a] + poll + s[b:]
a = s.index('    private suspend fun cleanupGarbage(')
b = s.index('    suspend fun transfer(', a)
cleanup = r'''    private suspend fun cleanupGarbage(relay: UploadChannel) {
        val finished = withTimeoutOrNull(3000L) {
            for (name in garbage.toList()) {
                try { relay.removeOwned(parent, name); garbage.remove(name) }
                catch (e: CancellationException) { throw e }
                catch (_: IOException) { }
            }
            true
        }
        if (finished != true) android.util.Log.w("MeshFilesUpload", "stage=CLEANUP_YIELDED_TO_RESTART")
    }
'''
s = s[:a] + cleanup + s[b:]
p.write_text(s)
p, s = checked('FileRelayClient.kt', '0c7294aa45bbdc9617fa88e7d1f5b88f5257a9ed')
needle = '                    if (!ensureConnected()) { delay(750L); continue }'
assert s.count(needle) == 1
s = s.replace(needle, '''                    if (!ensureConnected()) {
                        Log.d("MeshFilesUpload", "stage=WAIT_RELAY")
                        delay(750L)
                        continue
                    }''', 1)
needle = '                        session.started = true\n                        session.sentBytes = 0L'
assert s.count(needle) == 1
s = s.replace(needle, '                        Log.d("MeshFilesUpload", "stage=ATTEMPT_START epoch=$epoch")\n' + needle, 1)
p.write_text(s)
