package iecxiv.meshfabric.ui.components
import org.junit.Assert.*
import org.junit.Test
class BottomLoadingStateTest {
    @Test fun overlappingLoadsDoNotHideEachOther() {
        val state=BottomLoadingState();val first=Any();val second=Any()
        assertFalse(state.loading)
        state.begin(first);state.begin(second);assertTrue(state.loading)
        state.end(first);assertTrue(state.loading)
        state.end(second);assertFalse(state.loading)
    }
    @Test fun duplicateRegistrationAndRepeatedCleanupAreSafe() {
        val state=BottomLoadingState();val token=Any()
        state.begin(token);state.begin(token);state.end(token);assertFalse(state.loading)
        state.end(token);assertFalse(state.loading)
    }
    @Test fun ownersAreIndependentAcrossPages() {
        val old=BottomLoadingState();val current=BottomLoadingState();val token=Any()
        old.begin(token);assertFalse(current.loading);old.end(token);assertFalse(current.loading)
    }
}
