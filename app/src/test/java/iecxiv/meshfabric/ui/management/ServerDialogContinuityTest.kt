package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class ServerDialogContinuityTest {
    @Test fun pauseDoesNotEraseOpenedResult() {val old=ServerReadDialogResult("old","servererrors");assertEquals(old,nextServerReadDialog(old,"",""))}
    @Test fun freshReadReplacesSnapshot() {assertEquals(ServerReadDialogResult("new","serverconfig"),nextServerReadDialog(ServerReadDialogResult("old","servererrors"),"new","serverconfig"))}
    @Test fun noResultDoesNotOpenDialog() {assertNull(nextServerReadDialog(null,"",""))}
    @Test fun unsupportedReplyDoesNotOpenDialog() {assertNull(nextServerReadDialog(null,"data","serverstats"))}
    @Test fun versionOpensExistingProtectedFlow() {assertEquals("serverversion",nextServerReadDialog(null,"data","serverversion")?.action)}
}
