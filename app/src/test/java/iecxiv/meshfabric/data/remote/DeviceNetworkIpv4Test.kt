package iecxiv.meshfabric.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.model.DeviceIpv4
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Assert.*
import org.junit.Test

class DeviceNetworkIpv4Test {
    private fun json(value: String) = JsonParser.parseString(value).asJsonObject
    private fun reply(node: String, ip: String): JsonObject = json("""{"action":"getnetworkinfo","nodeid":"$node","netif2":{"eth0":[{"address":"$ip"}]}}""")

    @Test fun androidWlanUsesInterfaceNotAgentIp() {
        val message = json("""{"ip":"192.168.1.31","netif2":{"wlan0":[{"family":"IPv6","address":"fe80::1"},{"family":"IPv4","address":"192.168.1.143"}],"lo":[{"address":"127.0.0.1"}]}}""")
        assertEquals(listOf(DeviceIpv4("wlan0", "192.168.1.143")), DeviceNetworkIpv4.decode(message))
    }
    @Test fun proxmoxFindsBridgeAmongEmptyPhysicalAndVirtualInterfaces() {
        val message = json("""{"ip":"192.168.1.31","netif2":{"enp2s0f1":[{"mac":"8C:3B:4A:34:02:63"}],"wlp3s0":[],"vmbr0":[{"family":"IPv4","address":"192.168.1.154","gateway":"192.168.1.1","netmask":"255.255.255.0"}],"veth104i0":[{"mac":"FE:53:DF:6B:A4:4F"}],"tap100i0":[]}}""")
        assertEquals(listOf(DeviceIpv4("vmbr0", "192.168.1.154")), DeviceNetworkIpv4.decode(message))
    }
    @Test fun duplicateAddressesKeepAllInterfaceNamesButOneIp() {
        val result = DeviceNetworkIpv4.decode(json("""{"netif2":{"vmbr0":[{"address":"192.168.1.154"}],"eth0":[{"address":"192.168.1.154"},{"address":"192.168.1.154"}]}}"""))
        assertEquals(listOf(DeviceIpv4("eth0/vmbr0", "192.168.1.154")), result)
    }
    @Test fun multipleAddressesAreKeptWithoutInventingAPrimary() {
        val result = DeviceNetworkIpv4.decode(json("""{"netif2":{"vpn0":[{"address":"10.8.0.2"}],"vmbr0":[{"address":"192.168.1.154"}]}}"""))
        assertEquals(setOf("10.8.0.2", "192.168.1.154"), result.map { it.address }.toSet())
    }
    @Test fun loopbackInternalIpv6UnspecifiedAndMulticastAreExcluded() {
        val result = DeviceNetworkIpv4.decode(json("""{"netif2":{"lo":[{"address":"10.0.0.1"}],"lo0":[{"address":"10.0.0.2"}],"eth0":[{"address":"127.0.0.2"},{"address":"0.0.0.0"},{"address":"224.0.0.1"},{"address":"255.255.255.255"},{"address":"::ffff:192.168.1.1"},{"address":"10.0.0.3","internal":true}]}}"""))
        assertTrue(result.isEmpty())
    }
    @Test fun legacyJsonStringIsSupported() {
        val message = JsonObject().apply { addProperty("netif", """{"eth0":[{"address":"192.168.1.143"}]}""") }
        assertEquals("192.168.1.143", DeviceNetworkIpv4.decode(message).single().address)
    }
    @Test fun malformedAndMissingPayloadNeverUseTopLevelIps() {
        listOf("""{"ip":"192.168.1.31","localip":"192.168.1.31","gip":"192.168.1.31"}""",
            """{"netif":"broken"}""", """{"netif2":{"eth0":[null,5,"bad",{"address":15}]}}""").forEach {
            assertTrue(DeviceNetworkIpv4.decode(json(it)).isEmpty())
        }
    }
    @Test fun strictIpv4ValidationDoesNotResolveHostnames() {
        listOf("localhost", "1.2.3", "1.2.3.999", "1.2.3.-1", "1.2.3.4/24", "1.2.3.4:22").forEach {
            assertNull(DeviceNetworkIpv4.validAddress(it))
        }
        assertEquals("192.168.1.154", DeviceNetworkIpv4.validAddress("192.168.001.154"))
    }
    @Test fun modelDisplayNeverFallsBackToAgentAddresses() {
        val node = MeshNode(ip="192.168.1.31", gip="192.168.1.31", localip="192.168.1.31")
        assertNull(node.displayIp)
        assertEquals("192.168.1.154", node.copy(networkIpv4=listOf(DeviceIpv4("vmbr0", "192.168.1.154"))).displayIp)
        assertNull(Gson().fromJson("""{"_id":"node//a","networkIpv4":[{"interfaceName":"fake","address":"192.168.1.31"}]}""", MeshNode::class.java).displayIp)
    }
    @Test fun reversedRepliesStayAssociatedWithTheirNodes() {
        val cache = DeviceNetworkInfoCache { 0L }
        cache.request("node//a"); cache.request("node//b")
        assertTrue(cache.receive(reply("node//b", "192.168.1.154")))
        assertTrue(cache.receive(reply("node//a", "192.168.1.143")))
        assertEquals("192.168.1.143", cache.addresses.value["a"]!!.single().address)
        assertEquals("192.168.1.154", cache.addresses.value["b"]!!.single().address)
    }
    @Test fun anonymousAndUnrequestedRepliesAreRejected() {
        val cache = DeviceNetworkInfoCache { 0L }
        cache.request("node//a")
        val anonymous = reply("node//a", "192.168.1.143").apply { remove("nodeid") }
        assertFalse(cache.receive(anonymous))
        assertFalse(cache.receive(reply("node//b", "192.168.1.154")))
        assertTrue(cache.addresses.value.isEmpty())
    }
    @Test fun knownResponseTokenCanIdentifyReplyWithoutNodeId() {
        val cache = DeviceNetworkInfoCache { 0L }
        val request = cache.request("node//a")!!
        val response = reply("node//a", "192.168.1.143").apply {
            remove("nodeid"); add("responseid", request.get("responseid"))
        }
        assertTrue(cache.receive(response))
        assertEquals("192.168.1.143", cache.addresses.value["a"]!!.single().address)
    }
    @Test fun contradictoryNodeAndTokenAreRejected() {
        val cache = DeviceNetworkInfoCache { 0L }
        val request = cache.request("node//a")!!
        cache.request("node//b")
        val response = reply("node//b", "192.168.1.154").apply { add("responseid", request.get("responseid")) }
        assertFalse(cache.receive(response))
        assertTrue(cache.addresses.value.isEmpty())
    }
    @Test fun expiredRepliesAreNotAccepted() {
        var time = 0L
        val cache = DeviceNetworkInfoCache { time }
        cache.request("node//a")
        time = 15_000L
        assertFalse(cache.receive(reply("node//a", "192.168.1.143")))
    }
    @Test fun ttlAndManualRefreshLimitDuplicateRequests() {
        var time = 0L
        val cache = DeviceNetworkInfoCache { time }
        assertNotNull(cache.request("node//a"))
        assertNull(cache.request("node//a"))
        cache.receive(reply("node//a", "192.168.1.143"))
        time = 59_999L
        assertNull(cache.request("node//a"))
        cache.invalidate()
        assertNotNull(cache.request("node//a"))
    }
    @Test fun noInfoClearsPreviousAddress() {
        var time = 0L
        val cache = DeviceNetworkInfoCache { time }
        cache.request("node//a"); cache.receive(reply("node//a", "192.168.1.143"))
        time = 60_000L
        cache.request("node//a")
        assertTrue(cache.receive(json("""{"action":"getnetworkinfo","nodeid":"node//a","noinfo":true}""")))
        assertTrue(cache.addresses.value["a"]!!.isEmpty())
    }
    @Test fun domainsArePreservedAndNotConflated() {
        assertEquals("node/team/device", DeviceNetworkIpv4.fullNodeId("node/team/device"))
        assertEquals("node//device", DeviceNetworkIpv4.fullNodeId("device"))
        assertNotEquals(DeviceNetworkIpv4.nodeKey("node/team/device"), DeviceNetworkIpv4.nodeKey("node//device"))
    }
    @Test fun sessionClearRejectsOldTokensAndDropsCachedAddresses() {
        val cache = DeviceNetworkInfoCache { 0L }
        val request = cache.request("node//a")!!
        cache.receive(reply("node//a", "192.168.1.143"))
        cache.clear()
        cache.request("node//a")
        val old = reply("node//a", "192.168.1.31").apply { add("responseid", request.get("responseid")) }
        assertFalse(cache.receive(old))
        assertTrue(cache.addresses.value.isEmpty())
    }
}
