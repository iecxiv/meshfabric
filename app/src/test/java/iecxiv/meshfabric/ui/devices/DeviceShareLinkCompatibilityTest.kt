package iecxiv.meshfabric.ui.devices
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test
class DeviceShareLinkCompatibilityTest {
    private val node="node//a";private val actor="user//admin";private val server="https://server.test/site"
    private val draft=generalShareCreateDraft("Guest","mesh//a",15,1800000000L)
    private fun reply()=JsonObject().apply {addProperty("action","createDeviceShareLink");addProperty("responseid","write");addProperty("nodeid",node);addProperty("url","/site/sharing?c=credential")}
    private fun rows()=JsonObject().apply {
        addProperty("action","deviceShares");addProperty("nodeid",node)
        add("deviceShares",JsonArray().apply {add(JsonObject().apply {
            addProperty("publicid","new");addProperty("userid",actor);addProperty("guestName",draft.guest);addProperty("p",2);addProperty("viewOnly",true);addProperty("consent",9)
            addProperty("startTime",draft.start*1000);addProperty("expireTime",draft.end*1000);addProperty("url","/site/sharing?c=credential")
        })})
    }
    private fun resolve(p:JsonObject=rows(),before:Set<String> = emptySet())=generalShareResolveCreated(p,node,"read",actor,draft,requireNotNull(generalShareCreateReply(reply(),node,"write",server)),before,server)
    @Test fun acceptsCookieUrlAndOptionalPublicIdButRequiresReadback() {val r=requireNotNull(generalShareCreateReply(reply(),node,"write",server));assertEquals("",r.id);assertEquals("$server/sharing?c=credential",r.url);assertEquals("new",requireNotNull(resolve()).id)}
    @Test fun rejectsForeignInsecureOrCredentialSmugglingUrls() {for(url in listOf("https://other.test/site/sharing?c=x","//other.test/site/sharing?c=x","http://server.test/site/sharing?c=x","$server/sharing?c=x&c=y","$server/sharing?c=x&auth=secret","$server/sharing?c=","$server/other?c=x")) assertTrue(runCatching {generalShareCreatedUrl(server,"",url)}.isFailure)}
    @Test fun keepsExactCreateReplyCorrelation() {assertNull(generalShareCreateReply(reply(),node,"other",server));assertNull(generalShareCreateReply(reply().apply {addProperty("nodeid","node//other")},node,"write",server))}
    @Test fun cannotRevealExistingOrChangedEntry() {assertNull(resolve(before=setOf("new")));for(key in listOf("userid","guestName","url")) {val p=rows();p.getAsJsonArray("deviceShares")[0].asJsonObject.addProperty(key,"wrong");assertNull(resolve(p))};for(key in listOf("p","consent","expireTime")) {val p=rows();p.getAsJsonArray("deviceShares")[0].asJsonObject.addProperty(key,123);assertNull(resolve(p))}}
    @Test fun rejectsAmbiguousReadbackAndWrongCorrelation() {val p=rows();p.getAsJsonArray("deviceShares").add(p.getAsJsonArray("deviceShares")[0].deepCopy().asJsonObject.apply {addProperty("publicid","second")});assertNull(resolve(p));assertNull(resolve(rows().apply {addProperty("nodeid","node//other")}));assertNull(resolve(rows().apply {addProperty("responseid","other")}))}
    @Test fun nodeOnlyFallbackRequiresExplicitOptIn() {assertNull(generalShareRevokeList(rows(),node,"read"));assertNotNull(generalShareRevokeList(rows(),node,"read",true))}
}
