package iecxiv.meshfabric.ui.devices
import com.google.gson.JsonObject
internal enum class ShareAccess(val protocols:Int,val viewOnly:Boolean=false) {
    DESKTOP(2),VIEW_ONLY(2,true),TERMINAL(1),FILES(4),DESKTOP_FILES(6),TERMINAL_FILES(5),ALL(7)
}
internal enum class ShareValidity(val recurring:Int=0) {NOW,RANGE,DAILY(1),WEEKLY(2)}
internal enum class ShareConsent {NOTIFY,PROMPT,NONE}
internal val SHARE_EXPIRY_MINUTES=listOf(0,1,5,10,15,30,45,60,120,240,480,720,960,1440,2880,5760)
internal val SHARE_DURATION_MINUTES=SHARE_EXPIRY_MINUTES.filter {it in 1..720}
internal data class GeneralShareCreateDraft(
    val guest:String,val mesh:String,val start:Long,val end:Long,
    val access:ShareAccess=ShareAccess.VIEW_ONLY,val validity:ShareValidity=ShareValidity.RANGE,
    val consent:Int=9,val durationMinutes:Int?=15,val reviewedAt:Long=start
)
internal fun generalShareConsent(access:ShareAccess,consent:ShareConsent):Int {
    val p=access.protocols
    val notify=(if(p and 2!=0) 1 else 0) or (if(p and 1!=0) 2 else 0) or (if(p and 4!=0) 4 else 0)
    return when(consent) {ShareConsent.NONE->0;ShareConsent.NOTIFY->notify;ShareConsent.PROMPT->notify or (notify shl 3)}
}
internal fun generalShareOptionsDraft(guest:String,mesh:String,access:ShareAccess,validity:ShareValidity,minutes:Int,start:Long,end:Long,consent:ShareConsent,now:Long):GeneralShareCreateDraft {
    val s=if(validity==ShareValidity.NOW) now else start
    val e=when(validity) {ShareValidity.NOW->if(minutes==0) 0L else s+minutes*60L;ShareValidity.RANGE->end;else->0L}
    return GeneralShareCreateDraft(guest.trim(),mesh,s,e,access,validity,generalShareConsent(access,consent),if(validity==ShareValidity.RANGE) null else minutes,now).also {generalShareValidateOptions(it,now)}
}
internal fun generalShareValidateOptions(d:GeneralShareCreateDraft,now:Long) {
    require(generalShareGuestValid(d.guest) && d.mesh.isNotBlank())
    require(now in 1L..253402297199L && d.reviewedAt in 1L..now && now-d.reviewedAt<=120L)
    require(d.start in 1L..253402297199L && d.start>=d.reviewedAt-60L)
    require(d.consent in ShareConsent.entries.map {generalShareConsent(d.access,it)})
    when(d.validity) {
        ShareValidity.NOW->{
            require(d.durationMinutes in SHARE_EXPIRY_MINUTES && d.start==d.reviewedAt)
            require(d.end==if(d.durationMinutes==0) 0L else d.start+requireNotNull(d.durationMinutes)*60L)
        }
        ShareValidity.RANGE->{
            require(d.end>d.start)
            d.durationMinutes?.let {require(it in listOf(15,30,60) && d.end-d.start==it*60L)}
        }
        else->require(d.durationMinutes in SHARE_DURATION_MINUTES && d.end==0L)
    }
    require(d.end==0L || d.end in (now+1)..253402297199L)
}
internal fun generalShareAccessAllowed(rights:Long,access:ShareAccess):Boolean {
    if(!generalSharesAllowed(rights)) return false
    if(rights and 0xffffffffL==0xffffffffL) return true
    if(rights and 4352L!=0L) return false
    val p=access.protocols
    return !(p and 2!=0 && rights and 65536L!=0L || p and 1!=0 && rights and 512L!=0L || p and 4!=0 && rights and 1024L!=0L)
}
internal fun generalShareOptionsPacket(node:String,request:String,d:GeneralShareCreateDraft,meta:GeneralMeta,now:Long):JsonObject {
    generalShareValidateOptions(d,now)
    require(d.mesh==meta.mesh && generalShareAccessAllowed(meta.rights,d.access))
    return generalSharesRequest(node,request).apply {
        addProperty("action","createDeviceShareLink");addProperty("guestname",d.guest)
        addProperty("p",d.access.protocols);addProperty("viewOnly",d.access.viewOnly);addProperty("consent",d.consent)
        when {
            d.validity.recurring>0->{addProperty("start",d.start);addProperty("expire",requireNotNull(d.durationMinutes));addProperty("recurring",d.validity.recurring)}
            d.end==0L->addProperty("expire",0)
            else->{addProperty("start",d.start);addProperty("end",d.end)}
        }
    }
}
internal fun generalShareOptionsMatch(d:GeneralShareCreateDraft,row:GeneralShare):Boolean {
    if(row.protocols!=d.access.protocols.toLong() || (row.viewOnly==true)!=d.access.viewOnly || row.consent!=d.consent.toLong()) return false
    return when {
        d.validity.recurring>0->row.start==d.start*1000L && (row.expires ?: 0L)==0L && row.duration==d.durationMinutes?.toLong() && row.recurring==d.validity.recurring.toLong()
        d.end==0L->(row.start ?: 0L)==0L && (row.expires ?: 0L)==0L && (row.duration ?: 0L)==0L && (row.recurring ?: 0L)==0L
        else->row.start==d.start*1000L && row.expires==d.end*1000L && (row.duration ?: 0L)==0L && (row.recurring ?: 0L)==0L
    }
}
internal fun generalShareLinkUnexpired(d:GeneralShareCreateDraft,now:Long)=d.end==0L || now<d.end
