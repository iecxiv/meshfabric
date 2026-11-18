package iecxiv.meshfabric.ui.desktop

import org.junit.Assert.*
import org.junit.Test

class DesktopViewportStateTest {
    @Test fun sameLogicalSessionPreservesTheViewport() {
        val state=DesktopViewportState()
        val session=Any()
        state.bindSession(session)
        state.scale.floatValue=3.25f
        state.offsetX.floatValue=120f
        state.offsetY.floatValue=-75f
        state.barRelativeY.floatValue=0.37f
        repeat(5) { state.bindSession(session) }
        assertEquals(3.25f,state.scale.floatValue,0f)
        assertEquals(120f,state.offsetX.floatValue,0f)
        assertEquals(-75f,state.offsetY.floatValue,0f)
        assertEquals(0.37f,state.barRelativeY.floatValue,0f)
    }
    @Test fun aDifferentLogicalSessionStartsAtTheInitialZoom() {
        val state=DesktopViewportState()
        val first=Any()
        val second=Any()
        state.bindSession(first)
        state.scale.floatValue=4f
        state.offsetX.floatValue=80f
        state.offsetY.floatValue=-30f
        state.barRelativeY.floatValue=0.8f
        state.bindSession(second)
        assertEquals(1f,state.scale.floatValue,0f)
        assertEquals(0f,state.offsetX.floatValue,0f)
        assertEquals(0f,state.offsetY.floatValue,0f)
        assertEquals(1f,state.barRelativeY.floatValue,0f)
    }
    @Test fun explicitNewSessionCanResetAReusedSessionObject() {
        val state=DesktopViewportState()
        val session=Any()
        state.bindSession(session)
        state.scale.floatValue=5f
        state.offsetX.floatValue=50f
        state.offsetY.floatValue=25f
        state.resetForNewSession()
        state.bindSession(session)
        assertEquals(1f,state.scale.floatValue,0f)
        assertEquals(0f,state.offsetX.floatValue,0f)
        assertEquals(0f,state.offsetY.floatValue,0f)
    }
    @Test fun deviceViewportsRemainIndependent() {
        val first=DesktopViewportState()
        val second=DesktopViewportState()
        val session=Any()
        first.bindSession(session)
        first.scale.floatValue=2.5f
        second.resetForNewSession()
        assertEquals(2.5f,first.scale.floatValue,0f)
        assertEquals(1f,second.scale.floatValue,0f)
    }
}
