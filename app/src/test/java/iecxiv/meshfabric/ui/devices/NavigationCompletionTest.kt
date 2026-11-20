package iecxiv.meshfabric.ui.devices
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.remote.DeviceInventoryState
import iecxiv.meshfabric.ui.management.accountInlineOption
import org.junit.Assert.*
import org.junit.Test
class NavigationCompletionTest {
    @Test fun coreMatchesInventoryAndNeverUsesAgentVer() {
        val packet=JsonParser.parseString("{\"hardware\":{\"agent\":{\"core\":\"core-value\"}},\"node\":{\"agent\":{\"ver\":999}}}").asJsonObject
        assertEquals("core-value",agentCoreInventoryValue(DeviceInventoryState(packets=mapOf("getsysinfo" to packet))))
        val missing=JsonParser.parseString("{\"node\":{\"agent\":{\"ver\":999}}}").asJsonObject
        assertNull(agentCoreInventoryValue(DeviceInventoryState(packets=mapOf("getsysinfo" to missing))))
    }
    @Test fun accountHistoryIsInline() {assertTrue(accountInlineOption(3))}
}
