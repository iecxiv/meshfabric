package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class ProfileDialogContinuityTest {
    @Test fun freshMatchingIdentityIsEligible() {assertTrue(profileDialogSnapshotAllowed(true,false,false,"a","a"))}
    @Test fun disconnectedCannotAuthorize() {assertFalse(profileDialogSnapshotAllowed(false,false,false,"a","a"))}
    @Test fun loadingCannotAuthorize() {assertFalse(profileDialogSnapshotAllowed(true,true,false,"a","a"))}
    @Test fun readFailureCannotAuthorize() {assertFalse(profileDialogSnapshotAllowed(true,false,true,"a","a"))}
    @Test fun changedActorCannotAuthorize() {assertFalse(profileDialogSnapshotAllowed(true,false,false,"a","b"))}
    @Test fun missingActorCannotAuthorize() {assertFalse(profileDialogSnapshotAllowed(true,false,false,"",""))}
}
