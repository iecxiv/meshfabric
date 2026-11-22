package iecxiv.meshfabric.ui.devices
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test
class DeviceSharingOptionsTest {
    private val now=1800000000L
    private val meta=GeneralMeta("Device",emptyList(),524296L,"Linux","mesh//a")
    private fun draft(a:ShareAccess=ShareAccess.VIEW_ONLY,v:ShareValidity=ShareValidity.NOW,m:Int=15,c:ShareConsent=ShareConsent.PROMPT)=generalShareOptionsDraft("Guest",meta.mesh,a,v,m,now+300,now+600,c,now)
    private fun packet(d:GeneralShareCreateDraft)=generalShareCreatePacket("node//a","write",d,meta,now)
    private fun row(d:GeneralShareCreateDraft)=GeneralShare("new",d.guest,"user//admin",d.access.protocols.toLong(),d.access.viewOnly,
        if(d.end==0L && d.validity==ShareValidity.NOW) null else d.start*1000L,if(d.end==0L) null else d.end*1000L,
        d.consent.toLong(),if(d.validity.recurring>0) d.durationMinutes?.toLong() else null,if(d.validity.recurring>0) d.validity.recurring.toLong() else null)
    @Test fun allTypesAndConsentModesUseCorrectProtocolAndFlags() {
        assertEquals(listOf(2,2,1,4,6,5,7),ShareAccess.entries.map {it.protocols})
        for(a in ShareAccess.entries) for(c in ShareConsent.entries) {
            val d=draft(a=a,c=c);val p=packet(d);assertEquals(a.protocols,p.get("p").asInt);assertEquals(a.viewOnly,p.get("viewOnly").asBoolean)
            val notify=listOf(1,1,2,4,5,6,7)[a.ordinal]
            assertEquals(when(c) {ShareConsent.NONE->0;ShareConsent.NOTIFY->notify;ShareConsent.PROMPT->notify or (notify shl 3)},p.get("consent").asInt)
            assertTrue(generalShareOptionsMatch(d,row(d)));assertFalse(generalShareOptionsMatch(d,row(d).copy(consent=123)))
        }
    }
    @Test fun allStartingNowExpirationsIncludingUnlimited() {
        assertEquals(listOf(0,1,5,10,15,30,45,60,120,240,480,720,960,1440,2880,5760),SHARE_EXPIRY_MINUTES)
        for(m in SHARE_EXPIRY_MINUTES) {
            val d=draft(m=m);val p=packet(d)
            if(m==0) {assertEquals(0,p.get("expire").asInt);assertFalse(p.has("start"));assertFalse(p.has("end"))}
            else {assertEquals(now,p.get("start").asLong);assertEquals(now+m*60L,p.get("end").asLong);assertFalse(p.has("expire"))}
            assertTrue(generalShareOptionsMatch(d,row(d)))
        }
    }
    @Test fun explicitRangeIsNotConfusedWithRecurringDuration() {
        val d=draft(v=ShareValidity.RANGE,m=0);val p=packet(d)
        assertEquals(now+300,p.get("start").asLong);assertEquals(now+600,p.get("end").asLong);assertFalse(p.has("expire"));assertFalse(p.has("recurring"))
        assertTrue(generalShareOptionsMatch(d,row(d)))
    }
    @Test fun allRecurringDurationsUseMinutesAndStartSeconds() {
        assertEquals(listOf(1,5,10,15,30,45,60,120,240,480,720),SHARE_DURATION_MINUTES)
        for(v in listOf(ShareValidity.DAILY,ShareValidity.WEEKLY)) for(m in SHARE_DURATION_MINUTES) {
            val d=draft(v=v,m=m);val p=packet(d)
            assertEquals(v.recurring,p.get("recurring").asInt);assertEquals(m,p.get("expire").asInt);assertEquals(d.start,p.get("start").asLong);assertFalse(p.has("end"))
            assertTrue(generalShareOptionsMatch(d,row(d)));assertFalse(generalShareOptionsMatch(d,row(d).copy(duration=m+1L)));assertFalse(generalShareOptionsMatch(d,row(d).copy(recurring=3)))
        }
    }
    @Test fun rejectsInvalidDurationsReversedDatesExpiredAndStaleDrafts() {
        for(m in listOf(-1,2,61,5761)) assertTrue(runCatching {draft(m=m)}.isFailure)
        for(m in listOf(0,960,1440)) assertTrue(runCatching {draft(v=ShareValidity.DAILY,m=m)}.isFailure)
        assertTrue(runCatching {generalShareOptionsDraft("Guest",meta.mesh,ShareAccess.DESKTOP,ShareValidity.RANGE,0,now+600,now+300,ShareConsent.NOTIFY,now)}.isFailure)
        assertTrue(runCatching {generalShareCreatePacket("node//a","w",draft(),meta,now+121)}.isFailure)
        assertTrue(runCatching {generalShareCreatePacket("node//a","w",draft(m=1),meta,now+60)}.isFailure)
        assertTrue(runCatching {generalShareCreatePacket("node//a","w",draft(m=1),meta,now+1)}.isSuccess)
    }
    @Test fun permissionsAreCheckedPerProtocolWithAdminException() {
        for(a in ShareAccess.entries) {
            assertTrue(generalShareAccessAllowed(0xffffffffL,a));assertTrue(generalShareAccessAllowed(-1L,a));assertFalse(generalShareAccessAllowed(8L,a));assertFalse(generalShareAccessAllowed(meta.rights or 4096L,a))
            for((mask,bit) in listOf(1 to 512L,2 to 65536L,4 to 1024L)) if(a.protocols and mask!=0) assertFalse(generalShareAccessAllowed(meta.rights or bit,a))
        }
        assertTrue(runCatching {generalShareCreatePacket("node//a","w",draft(),meta.copy(mesh="mesh//other"),now)}.isFailure)
    }
    @Test fun readbackRejectsChangedScopeConsentTimeOrRecurrence() {
        val d=draft();val r=row(d)
        for(bad in listOf(r.copy(protocols=7),r.copy(viewOnly=false),r.copy(consent=null),r.copy(expires=d.end*1000+1),r.copy(recurring=1),r.copy(duration=1))) assertFalse(generalShareOptionsMatch(d,bad))
        val u=draft(m=0);assertFalse(generalShareOptionsMatch(u,row(u).copy(expires=now*1000)))
    }
    @Test fun parserRetainsScheduleAndRejectsFractionalMetadata() {
        val d=draft(v=ShareValidity.WEEKLY)
        val raw=JsonObject().apply {addProperty("publicid","new");addProperty("p",2);addProperty("viewOnly",true);addProperty("consent",d.consent);addProperty("startTime",d.start*1000);addProperty("duration",15);addProperty("recurring",2)}
        val p=JsonObject().apply {addProperty("action","deviceShares");addProperty("nodeid","node//a");addProperty("responseid","read");add("deviceShares",JsonArray().apply {add(raw)})}
        assertTrue(generalShareOptionsMatch(d,requireNotNull(generalSharesReply(p,"node//a","read")).single()))
        raw.addProperty("duration",1.5);assertTrue(runCatching {generalSharesReply(p,"node//a","read")}.isFailure)
    }
    @Test fun unlimitedAndRecurringLinksDoNotExpireAtEpochZero() {
        assertTrue(generalShareLinkUnexpired(draft(m=0),now+999999));assertTrue(generalShareLinkUnexpired(draft(v=ShareValidity.DAILY),now+999999));assertFalse(generalShareLinkUnexpired(draft(m=1),now+60))
    }
}
