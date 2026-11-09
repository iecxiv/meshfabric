package iecxiv.meshfabric.ui.management
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class DeviceGroupAdvancedRequestTest {
    private val actor="user//admin";private val mid="mesh//group";private val server="https://example.invalid/"
    private fun state(type:Int=2,flags:Long=0x80000002L,consent:Long=0x80000000L)=ProfileState(ready=true,user=JSONObject().put("_id",actor).put("siteadmin",0),server=JSONObject().put("devGroupSessionRecording",1).put("consent",0).put("autoremoveinactivedevices",90),groups=listOf(JSONObject().put("_id",mid).put("mtype",type).put("flags",flags).put("consent",consent).put("links",JSONObject().put(actor,JSONObject().put("rights",1)))))
    private fun changes(flags:Long=0x80000002L,days:Int=30)=JSONObject().put("flags",flags).put("expireDevs",days)
    private fun packet(s:ProfileState,c:JSONObject=changes(),confirmed:Boolean=true)=deviceGroupAdvancedPacket(s,s.groups.single(),c,server,confirmed)
    @Test fun exactFieldsExplicitConfirmationAndScopeAreRequired() {
        val s=state();val p=packet(s);assertTrue(deviceGroupAdvancedAllowed(s,p,server))
        assertFalse(deviceGroupAdvancedAllowed(s,packet(s,confirmed=false),server))
        assertFalse(deviceGroupAdvancedAllowed(s,p,"https://other.invalid/"))
        assertFalse(deviceGroupAdvancedAllowed(s,JSONObject(p.toString()).put("_advancedActor","user//other"),server))
        assertFalse(deviceGroupAdvancedAllowed(s,JSONObject(p.toString()).put("relayid","node//relay"),server))
        assertFalse(deviceGroupAdvancedAllowed(s,JSONObject(p.toString()).put("meshid","mesh/other/group"),server))
        val wire=deviceGroupAdvancedWire(p);assertEquals(setOf("action","meshid","flags","expireDevs"),wire.keys().asSequence().toSet())
        assertEquals(0x80000002L,wire.getLong("flags"));assertEquals(30L,wire.getLong("expireDevs"))
    }
    @Test fun unknownBitsAndRecordingRestrictionsArePreserved() {
        val s=state();assertFalse(deviceGroupAdvancedAllowed(s,packet(s,changes(2L)),server))
        val off=state().copy(server=JSONObject().put("devGroupSessionRecording",0).put("consent",0))
        assertFalse(deviceGroupAdvancedAllowed(off,packet(off,changes(0x80000006L)),server))
        val existing=state(flags=0x80000006L).copy(server=JSONObject().put("devGroupSessionRecording",0))
        assertTrue(deviceGroupAdvancedAllowed(existing,packet(existing,changes(0x80000006L)),server))
        val type4=state(4);assertFalse(deviceGroupAdvancedAllowed(type4,packet(type4,changes(0x80000006L)),server))
    }
    @Test fun dependenciesAndExpiryBoundsAreStrictAndDoNotInventDefaults() {
        val s=state()
        assertFalse(deviceGroupAdvancedAllowed(s,packet(s,changes(0x80000008L)),server))
        assertFalse(deviceGroupAdvancedAllowed(s,packet(s,changes(0x80000003L,30)),server))
        assertTrue(deviceGroupAdvancedAllowed(s,packet(s,changes(0x80000003L,0)),server))
        assertTrue(deviceGroupAdvancedAllowed(s,packet(s,changes(days=2000)),server))
        for(value in listOf<Any>(-1,2001,1.5,"30",JSONObject.NULL)) {
            val p=JSONObject(packet(s).toString()).put("expireDevs",value)
            assertFalse(deviceGroupAdvancedAllowed(s,p,server))
        }
        assertFalse(deviceGroupAdvancedAllowed(s,JSONObject(packet(s).toString()).apply {remove("flags")},server))
        assertFalse(deviceGroupAdvancedAllowed(s,JSONObject(packet(s).toString()).apply {remove("expireDevs")},server))
        assertFalse(deviceGroupAdvancedAllowed(s,packet(s,changes(days=0)),server))
    }
    @Test fun nineConsentBitsCannotRemoveUnknownOrMandatoryServerBits() {
        val s=state().copy(server=JSONObject().put("consent",8).put("devGroupSessionRecording",1))
        assertTrue(deviceGroupAdvancedAllowed(s,packet(s,JSONObject().put("consent",0x800001ffL)),server))
        assertFalse(deviceGroupAdvancedAllowed(s,packet(s,JSONObject().put("consent",0x80000001L)),server))
        assertFalse(deviceGroupAdvancedAllowed(s,packet(s,JSONObject().put("consent",511)),server))
        assertEquals(setOf("action","meshid","consent"),deviceGroupAdvancedWire(packet(s,JSONObject().put("consent",0x800001ffL))).keys().asSequence().toSet())
    }
    @Test fun staleSettingsCapabilitiesOrInsufficientAuthorityBlock() {
        val s=state();val p=packet(s);s.groups.single().put("consent",0x80000008L)
        assertFalse(deviceGroupAdvancedAllowed(s,p,server))
        val role=state();role.groups.single().getJSONObject("links").getJSONObject(actor).put("rights",8)
        assertFalse(deviceGroupAdvancedAllowed(role,packet(role),server))
        val fresh=state();val frozen=packet(fresh)
        for(blocked in listOf(fresh.copy(loading=true),fresh.copy(busy=true),fresh.copy(readError=true),fresh.copy(detailsBusy=true),fresh.copy(groups=emptyList()),fresh.copy(server=JSONObject().put("consent",8)))) assertFalse(deviceGroupAdvancedAllowed(blocked,frozen,server))
    }
    @Test fun exactReadbackNeedsAckAndFreshMeshReadAndZeroMayBeAbsent() {
        val s=state();val p=packet(s)
        s.groups.single().put("expireDevs",30)
        for(ack in listOf(false,true)) for(read in listOf(false,true)) assertEquals(ack && read,deviceGroupAdvancedReadbackReady(s,p,ack,read))
        s.groups.single().put("flags",2);assertFalse(deviceGroupAdvancedReadbackReady(s,p,true,true))
        val inherited=state();inherited.groups.single().put("expireDevs",30)
        val zero=packet(inherited,changes(days=0));inherited.groups.single().remove("expireDevs")
        assertTrue(deviceGroupAdvancedReadbackReady(inherited,zero,true,true))
        assertFalse(deviceGroupAdvancedReadbackReady(inherited.copy(readError=true),zero,true,true))
        val reply=JSONObject().put("action","editmesh").put("responseid","request").put("result","ok")
        assertTrue(deviceGroupAdvancedAck(reply,"request"));assertFalse(deviceGroupAdvancedAck(reply,"other"))
        assertFalse(deviceGroupAdvancedAck(JSONObject(reply.toString()).put("error","denied"),"request"))
    }
}
