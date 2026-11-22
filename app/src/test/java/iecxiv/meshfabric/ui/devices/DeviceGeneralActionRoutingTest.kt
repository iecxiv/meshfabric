package iecxiv.meshfabric.ui.devices
import org.junit.Assert.*
import org.junit.Test
class DeviceGeneralActionRoutingTest {
    @Test fun eachActionHasDistinctRootRouteAndRoundTrips() {
        assertEquals(listOf(5,6,7,8,9),DeviceGeneralActionKind.entries.map {deviceGeneralActionStep(it)})
        for(kind in DeviceGeneralActionKind.entries) assertEquals(kind,deviceGeneralActionKind(deviceGeneralActionStep(kind)))
    }
    @Test fun existingGeneralSharingAndReadRoutesAreNotActionRoutes() {for(step in listOf(-1,0,1,2,3,4,10)) assertNull(deviceGeneralActionKind(step))}
}
