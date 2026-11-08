package iecxiv.meshfabric.ui.management

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountPresentationPolicyTest {
    @Test fun languagesAcceptAdvertisedArrayAndString() {
        assertEquals(listOf("en","es"),accountAdvertisedLanguages(JSONArray().put("en").put("es")))
        assertEquals(listOf("en","es"),accountAdvertisedLanguages("en,es"))
        assertTrue(accountLanguageCandidates(null).isNotEmpty())
        assertTrue(accountLanguageCandidates(null).all(::accountLanguageValid))
    }
    @Test fun largeSecurityKeyIdsRemainExact() {
        for(id in listOf(0L,2147483648L,4294967295L)) assertEquals(id,securityKeyId(JSONObject().put("i",id)))
        assertNull(securityKeyId(JSONObject().put("i",1.5)))
        assertNull(securityKeyId(JSONObject().put("i",-1)))
        assertNull(securityKeyIds(JSONArray().put(JSONObject().put("i",1)).put(JSONObject().put("i",1))))
    }
    @Test fun unknownLoginEventsAreNotMarkedInvalid() {
        assertEquals(true,accountLoginOutcome(107))
        for(code in listOf(108,109,110)) assertEquals(false,accountLoginOutcome(code))
        assertNull(accountLoginOutcome(999)); assertNull(accountLoginOutcome(null))
    }
    @Test fun requestedTokenLifetimeIsNotAnObservedExpiryDate() {
        val token=JSONObject().put("_requestedExpiryMinutes",60)
        assertTrue(accountTokenExpiry(token,"en",true,false).startsWith("Requested lifetime:"))
        assertTrue(accountTokenExpiry(JSONObject(),"en",true,false).startsWith("Expiry not supplied"))
    }
}
