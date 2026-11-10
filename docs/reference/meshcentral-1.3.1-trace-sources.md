# MeshCentral 1.3.1 trace source state compatibility

References inspected: https://unpkg.com/meshcentral@1.3.1/meshcentral.js and https://unpkg.com/meshcentral@1.3.1/meshuser.js .

The server initializes debugRemoteSources to null. For a full server administrator, when domain trace access is allowed, its initial traceinfo packet carries that value. Explicit traceSources:null means a received state with no active sources; it is distinct from an absent field. Arrays must consist entirely of nonblank strings within the existing count/name bounds. Malformed or error replies never become an empty state. Unknown valid source names are preserved.

Incoming traceinfo is accepted only after administrator identity on the current connection. Both direct packets and wrapped traceinfo events share the decoder. No initial or timeout/reconnect traceinfo request is sent: that command changes global sources, rather than performing a read. Only a deliberate source toggle may send a traceinfo write; existing base/desired comparison and server-readback confirmation remain in place.

After 20 seconds without an initial source state in the trace view, show an unavailable-state error and manual reconnect instead of waiting forever. This does not assert that access was denied, and does not disable the independently available console. Invalid state disables the selector until a valid state or manual reconnect. Connection generation and identity guard stale messages. No permissions or domain restrictions are bypassed. No automatic write retries, trace exports or diagnostic payload logging are added.

Synthetic JVM and Android channel tests cover null, empty/populated arrays, malformed/missing/error data, readback, non-admin access, stale sockets and absence of unsolicited trace writes. Real server behavior and physical-device visual review still require verification.
