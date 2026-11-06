# Device-card interface IPv4

Restore IP below the OS from getnetworkinfo interface inventory only. DeviceListViewModel enriches node copies with a transient networkIpv4 field. The displayIp getter no longer falls back to node.ip, gip or localip; those transport/agent values remain untouched for other uses.

The parser reads netif2 (object keyed by interface, arrays of address records), with older netif object/JSON-string fallback. It only reads address fields, never gateway, netmask or MAC. Include bridges such as vmbr0 and virtual interfaces with valid assigned addresses. Exclude internal/loopback, invalid/unspecified, multicast/reserved destinations and IPv6. Do not filter by physical adapter type, RFC1918 range or interface-name priority. Deduplicate addresses, retaining combined interface names. One address is shown as IP: address; multiple addresses each have their interface and wrap without truncating to a guessed primary.

Reference tests: Android wlan0 192.168.1.143, Proxmox vmbr0 192.168.1.154 among physical interfaces without IP and virtual veth/tap entries. Agent IP 192.168.1.31 must never appear merely as fallback. Additional tests cover duplicates, multiple networks, gateways, legacy payloads and malformed values.

Requests run for composed visible cards while the list is resumed and control is connected. Each node has a 60-second request interval and one 15-second pending window. Manual refresh invalidates interval state and restarts visible-card effects. No extra periodic requests for hidden/collapsed cards. Server inventory may itself be cached/stale, just as in the web Details page; absence, missing rights or timeout leaves no invented address. No shell, terminal, ADB, network scan, DNS lookup or changes on managed devices.

Replies are correlated by explicit requested nodeid or an exact known responseid. Anonymous, conflicting, unknown and expired replies are rejected. Keep domain-qualified node IDs intact. Existing control-socket generation checks discard old-connection messages. Reconnection clears pending requests; server/account identity changes and logout also clear cached addresses. Network data is memory-only and is not logged. Existing cards, ordering, session buttons, desktop keyboard/Hold and version remain unchanged.

Eighteen JVM tests prepared. Publication requires assembleDebug and testDebugUnitTest before committing implementation. Live server/device checks are still required for the Android and Proxmox agents, especially payload compatibility, permissions and interface updates.

Protocol reference: https://github.com/d-maggipinto/meshcentral-desktop/blob/main/docs/PROTOCOL.md (getnetworkinfo returns message.netif2).
