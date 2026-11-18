package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test

class DeviceNotificationPolicyTest {
    private val actor="user//alice"
    private val node="node//a"
    private fun user(value:Long)=JsonObject().apply {addProperty("_id",actor);add("notify",JsonObject().apply {addProperty(node,value)})}
    @Test fun readsOnlyCurrentDevicePreference() {
        val value=user(2);value.getAsJsonObject("notify").addProperty("node//b",4)
        assertEquals(2L,deviceNotifyValue(value,actor,node))
    }
    @Test fun missingOwnPreferenceIsZero() {assertEquals(0L,deviceNotifyValue(JsonObject().apply {addProperty("_id",actor)},actor,node))}
    @Test fun refusesAnotherActor() {assertTrue(runCatching {deviceNotifyValue(user(2),"user//bob",node)}.isFailure)}
    @Test fun refusesAnotherDomain() {assertTrue(runCatching {deviceNotifyValue(user(2),actor,"node/other/a")}.isFailure)}
    @Test fun rejectsMalformedPreference() {
        val value=user(2);value.getAsJsonObject("notify").addProperty(node,"2")
        assertTrue(runCatching {deviceNotifyValue(value,actor,node)}.isFailure)
        value.getAsJsonObject("notify").addProperty(node,-1)
        assertTrue(runCatching {deviceNotifyValue(value,actor,node)}.isFailure)
    }
    @Test fun mergePreservesEveryNonWebBit() {
        val before=0xffffffffL
        assertEquals(before and 6L.inv(),deviceNotifyMerge(before,0))
        assertEquals(before,deviceNotifyMerge(before,6))
    }
    @Test fun refusesChangingOtherChannels() {assertTrue(runCatching {deviceNotifyMerge(0,16)}.isFailure)}
    @Test fun writesOnlyOwnPreferenceCommand() {
        val change=deviceNotifyPacket(node,16,2)
        assertEquals("changeusernotify",change.get("action").asString)
        assertEquals(node,change.get("nodeid").asString)
        assertEquals(18L,change.get("notify").asLong)
        assertEquals(setOf("action","nodeid","notify"),change.entrySet().map {it.key}.toSet())
    }
    @Test fun unchangedPreferenceDoesNotProduceWrite() {assertTrue(runCatching {deviceNotifyPacket(node,2,2)}.isFailure)}
    @Test fun loadingAndSavingCannotEdit() {
        assertFalse(DeviceNotifyState(2,"loading").canEdit)
        assertFalse(DeviceNotifyState(2,"saving").canEdit)
        assertFalse(DeviceNotifyState(phase="error").canEdit)
        assertTrue(DeviceNotifyState(2,"ready").canEdit)
    }
}
