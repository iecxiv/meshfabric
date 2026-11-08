package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class ProfileBroadcastPolicyTest {
    @Test fun plainAndMultilineMessagesAreAccepted() {assertTrue(profileBroadcastMessageValid("Maintenance"));assertTrue(profileBroadcastMessageValid("Line one\nLine two"))}
    @Test fun blankMessagesAreRejected() {assertFalse(profileBroadcastMessageValid(""));assertFalse(profileBroadcastMessageValid(" \n "))}
    @Test fun actualServerLengthLimitIs512() {assertTrue(profileBroadcastMessageValid("a".repeat(512)));assertFalse(profileBroadcastMessageValid("a".repeat(513)))}
    @Test fun emojiLengthUsesUtf16Units() {assertTrue(profileBroadcastMessageValid("😀".repeat(256)));assertFalse(profileBroadcastMessageValid("😀".repeat(257)))}
    @Test fun nulIsRejected() {assertFalse(profileBroadcastMessageValid("message\u0000"))}
    @Test fun roleRequiresUserAdministration() {assertFalse(profileBroadcastRoleAllowed(0));assertTrue(profileBroadcastRoleAllowed(2));assertTrue(profileBroadcastRoleAllowed(0xffffffffL))}
    @Test fun serverLabelOmitsCredentialsQueriesAndFragments() {assertEquals("https://example.invalid:443/domain/",profileBroadcastServerLabel("https://name:password@example.invalid:443/domain/control.ashx?key=private#fragment"));assertEquals("https://example.invalid/",profileBroadcastServerLabel("wss://example.invalid/control.ashx?key=private"))}
    @Test fun invalidServerLabelFailsClosed() {assertEquals("",profileBroadcastServerLabel("file:///private"));assertEquals("",profileBroadcastServerLabel("not a URL"))}
}
