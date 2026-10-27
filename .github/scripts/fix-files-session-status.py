from pathlib import Path
import hashlib
p = Path('app/src/main/java/iecxiv/meshfabric/data/remote/FileRelayClient.kt')
raw = p.read_bytes()
assert hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest() == '35dbd863349c100d44ddda03377c394b10a00682', 'Review changed file relay before applying'
s = raw.decode()
def once(old, new):
    global s
    assert s.count(old) == 1, old[:140]
    s = s.replace(old, new, 1)
once('    @Volatile var isUserDisconnect = true', '    @Volatile var isUserDisconnect = true\n    @Volatile private var irrecoverable = false')
needle = '    fun isConnected(): Boolean {'
once(needle, '''    fun retainedSessionStatus(): SessionStatus = FileSessionPolicy.status(!isUserDisconnect, irrecoverable)

    fun hasRecoverableState(): Boolean = FileSessionPolicy.active(!isUserDisconnect, irrecoverable)

''' + needle)
once('''    fun connect() {
        isUserDisconnect = false''', '''    fun connect() {
        isUserDisconnect = false
        irrecoverable = false''')
once('        if (isUserDisconnect || recoveryJob?.isActive == true) return', '        if (isUserDisconnect || irrecoverable || recoveryJob?.isActive == true) return')
once('    suspend fun ensureConnected(): Boolean {\n        if (isUserDisconnect || !ensureControlSocketConnected()) return false', '    suspend fun ensureConnected(): Boolean {\n        if (isUserDisconnect || irrecoverable || !ensureControlSocketConnected()) return false')
once('                    if (!isUserDisconnect) {', '                    if (!isUserDisconnect && !irrecoverable) {')
once('''                                Log.w("MeshFiles", "Cannot open files transport (${e.javaClass.simpleName})")
                                retireSocket()''', '''                                Log.w("MeshFiles", "Cannot open files transport (${e.javaClass.simpleName})")
                                if (FileSessionPolicy.configurationFailure(e)) {
                                    irrecoverable = true
                                    recoveryJob?.cancel()
                                    recoveryJob = null
                                    _statusMessage.value = "La configuración impide recuperar Archivos; corrígela y vuelve a intentar"
                                }
                                retireSocket()''')
once('''    fun refreshSession() {
        if (isUserDisconnect) return''', '''    fun refreshSession() {
        if (isUserDisconnect) return
        irrecoverable = false''')
once('''    fun onNetworkChanged() {
        if (isUserDisconnect) return''', '''    fun onNetworkChanged() {
        if (isUserDisconnect || irrecoverable) return''')
once('''            when {
                sess.isConnected() -> SessionStatus.CONNECTED
                sess.fileList.value.isNotEmpty() && !sess.isUserDisconnect -> SessionStatus.DISCONNECTED_WITH_BUFFER
                else -> SessionStatus.NONE
            }''', '            sess.retainedSessionStatus()')
once('            .filter { it.value.isConnected() }', '            .filter { it.value.hasRecoverableState() }')
once('        return sessions.values.any { it.isConnected() }', '        return sessions.values.any { it.hasRecoverableState() }')
once('''        return when {
            session.isConnected() -> SessionStatus.CONNECTED
            session.fileList.value.isNotEmpty() && !session.isUserDisconnect -> SessionStatus.DISCONNECTED_WITH_BUFFER
            else -> SessionStatus.NONE
        }''', '        return session.retainedSessionStatus()')
once('        return sessions.values.any { !it.isConnected() && it.fileList.value.isNotEmpty() && !it.isUserDisconnect }', '        return sessions.values.any { it.retainedSessionStatus() == SessionStatus.DISCONNECTED_WITH_BUFFER }')
once('        return sessions.values.any { (it.isConnected() || it.fileList.value.isNotEmpty()) && !it.isUserDisconnect }', '        return sessions.values.any { !it.isUserDisconnect }')
once('        return sessions.values.count { (it.isConnected() || it.fileList.value.isNotEmpty()) && !it.isUserDisconnect }', '        return sessions.values.count { !it.isUserDisconnect }')
p.write_text('\n'.join(line.rstrip() for line in s.splitlines()).rstrip() + '\n')
