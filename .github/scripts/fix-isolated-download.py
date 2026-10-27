from pathlib import Path
import hashlib
root = Path('app/src/main/java/iecxiv/meshfabric/data/remote')
p = root / 'FileRelayClient.kt'
raw = p.read_bytes()
assert hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest() == '925174015ed5f25670d3dccf7b74752508206b05', 'Review changed file relay before applying'
s = raw.decode()
a = s.index('                    // 2. Attempt WebSocket readfile download if HTTP did not complete')
b = s.index('                    if (!downloadSuccess && activeDownloads.containsKey(item.path)) {', a)
s = s[:a] + r"""                    // A dedicated relay owns every binary block of this fallback attempt.
                    if (!downloadSuccess && activeDownloads.containsKey(item.path) && coroutineContext.isActive) {
                        if (!ensureConnected()) throw java.io.IOException("Files relay unavailable for isolated download")
                        Log.d("MeshFilesDownload", "stage=WS_ISOLATED_START epoch=$attemptEpoch")
                        val output = createDownloadOutputStream(context, targetName)
                        session.outputStream = output
                        session.receivedBytes = 0L
                        try {
                            val received = IsolatedFileDownload.download(
                                meshClient, fullNodeId, item.path, item.size.takeIf { it > 0L },
                                output, session.networkAttempt, attemptEpoch
                            ) { count ->
                                session.receivedBytes = count
                                _activeTransfers.update { transfers ->
                                    transfers.map {
                                        if (it.filePath == item.path) it.copy(bytesTransferred = count,
                                            totalBytes = if (item.size > 0L) item.size else count) else it
                                    }
                                }
                                updateDownloadNotification(context, targetName, item.path, count, item.size)
                            }
                            session.receivedBytes = received
                        } finally {
                            try { output.close() } finally { session.outputStream = null }
                        }
                        if (activeDownloads[item.path] === session && coroutineContext.isActive &&
                            session.networkAttempt.isCurrent(attemptEpoch)) {
                            downloadSuccess = true
                            Log.d("MeshFilesDownload", "stage=WS_ISOLATED_COMPLETE epoch=$attemptEpoch")
                            finishDownload(item.path)
                            return@launch
                        }
                        throw java.io.IOException("Isolated download attempt was cancelled or retired")
                    }

""" + s[b:]
import re
pattern = re.compile(r'(?m)^( *)val activeDl = activeDownloads.values.firstOrNull\(\)\n\1if \([^\n]*\) \{')
removed = 0
while True:
    match = pattern.search(s)
    if match is None:
        break
    end = re.search(r'(?m)^' + re.escape(match.group(1)) + r'}', s[match.end():])
    assert end is not None, 'Shared download handler has no closing brace at its indentation'
    closing = match.end() + end.end() - 1
    if s.startswith(' else if ', closing + 1):
        s = s[:match.start()] + match.group(1) + s[closing + 7:]
    else:
        s = s[:match.start()] + s[closing + 1:]
    removed += 1
assert removed == 5, 'Review shared socket download routing count'
assert 'val activeDl = activeDownloads.values.firstOrNull()' not in s
needle = '                if (activeDownloads.isNotEmpty()) finishDownload()'
assert s.count(needle) == 1
s = s.replace(needle, '                // Shared relay completion belongs only to browser copy/read operations.')
needle = 'import kotlinx.coroutines.flow.asStateFlow'
assert s.count(needle) == 1
s = s.replace(needle, needle + '\nimport kotlinx.coroutines.flow.update')
needle = '                        if (!response.isSuccessful) {\n'
assert s.count(needle) == 1
s = s.replace(needle, '                        Log.d("MeshFilesDownload", "stage=HTTP_STATUS code=${response.code} epoch=$attemptEpoch")\n' + needle, 1)
p.write_text('\n'.join(line.rstrip() for line in s.splitlines()).rstrip() + '\n')
