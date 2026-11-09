package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class AccountPreferencesPolicyTest {
@Test fun independentNotificationFilters() {
val o=AccountNoticeOptions(connections=true)
assertTrue(accountNoticeAllowed("connect",o)); assertFalse(accountNoticeAllowed("disconnect",o)); assertFalse(accountNoticeAllowed("amt",o)); assertTrue(accountNoticeAllowed("general",o)); assertFalse(accountNoticeAllowed("unknown",o))
assertTrue(accountNoticeAllowed("amt",o.copy(amt=true)))
}
@Test fun initialAndRepeatedConnectivityDoNotNotify() { assertEquals(0 to 0,accountConnectionChange(null,1)); assertEquals(0 to 0,accountConnectionChange(1,1)) }
@Test fun connectivityChannelsRemainDistinct() { assertEquals(4 to 1,accountConnectionChange(1,4)); assertEquals(0 to 0,accountConnectionChange(0,8)); assertEquals(0 to 16,accountConnectionChange(16,0)) }
@Test fun languageTagsPreserveServerSpellingAndDefault() { assertTrue(accountLanguageValid("pt-br")); assertTrue(accountLanguageValid("zh-chs")); assertTrue(accountLanguageValid("es-ES")); assertFalse(accountLanguageValid("en/../../")); assertEquals("*",accountDefaultLanguage("")); assertEquals("pt-br",accountDefaultLanguage("pt-br")) }
}
