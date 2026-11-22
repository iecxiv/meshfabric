package iecxiv.meshfabric.ui.devices
import org.junit.Assert.*
import org.junit.Test
class DeviceGeneralResumePolicyTest {
    @Test fun resumeReadRequiresAllConditions() {for(stopped in listOf(false,true)) for(connected in listOf(false,true)) for(resumed in listOf(false,true)) assertEquals(stopped && connected && resumed,generalResumeRead(stopped,connected,resumed))}
    @Test fun interruptedWritesRemainUncertain() {assertTrue(generalInterruptedWrite("saving",""));assertTrue(generalInterruptedWrite("error","unconfirmed"));assertFalse(generalInterruptedWrite("ready",""));assertFalse(generalInterruptedWrite("confirmed",""))}
}
