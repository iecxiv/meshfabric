package iecxiv.meshfabric.ui.desktop

import org.junit.Assert.*
import org.junit.Test

class DesktopPrimaryDragTest {
    private data class Sent(val socket:Any,val action:Int,val x:Int,val y:Int)
    private class Fixture {
        var socket:Any?=Any()
        val sent=mutableListOf<Sent>()
        val drag=DesktopPrimaryDrag({socket}) {s,a,x,y->sent+=Sent(s,a,x,y)}
    }
    @Test fun downDoesNotReleaseUntilRequested() {
        val f=Fixture();assertTrue(f.drag.down(10,20,false))
        assertEquals(listOf(0x00,0x02),f.sent.map {it.action})
        f.drag.move(30,40);f.drag.move(50,60)
        assertEquals(listOf(0x00,0x02,0x00,0x00),f.sent.map {it.action})
        f.drag.release();assertEquals(Sent(f.socket!!,0x04,50,60),f.sent.last())
    }
    @Test fun duplicateDownAndReleaseDoNotDuplicateClick() {
        val f=Fixture();f.drag.down(1,2,false);f.drag.down(1,2,false);f.drag.release();f.drag.release()
        assertEquals(listOf(0x00,0x02,0x04),f.sent.map {it.action})
    }
    @Test fun swappedButtonKeepsMatchingRelease() {
        val f=Fixture();f.drag.down(1,2,true);f.drag.down(1,2,false);f.drag.release()
        assertEquals(listOf(0x00,0x08,0x10),f.sent.map {it.action})
    }
    @Test fun disconnectedSessionDoesNotReceiveDownOrMove() {
        val f=Fixture();f.socket=null;assertFalse(f.drag.down(1,2,false));assertFalse(f.drag.move(3,4));f.drag.release();assertTrue(f.sent.isEmpty())
    }
    @Test fun reconnectCannotReceiveOldMoveOrRelease() {
        val f=Fixture();val old=f.socket;f.drag.down(1,2,false);f.socket=Any()
        assertFalse(f.drag.move(3,4));f.drag.release()
        assertEquals(2,f.sent.size);assertTrue(f.sent.all {it.socket===old})
    }
    @Test fun releaseAfterReconnectDoesNotSendToReplacement() {
        val f=Fixture();f.drag.down(1,2,false);f.socket=Any();f.drag.release();assertEquals(2,f.sent.size)
    }
    @Test fun canceledGestureReleasesAtLastPositionOnce() {
        val f=Fixture();f.drag.down(1,2,false);f.drag.move(7,8);f.drag.release();f.drag.release()
        assertEquals(1,f.sent.count {it.action==0x04});assertEquals(7,f.sent.last().x);assertEquals(8,f.sent.last().y)
    }
    @Test fun nextGestureStartsOnlyWithNewDown() {
        val f=Fixture();f.drag.down(1,2,false);f.drag.release();assertFalse(f.drag.move(3,4))
        assertTrue(f.drag.down(5,6,false));f.drag.release();assertEquals(2,f.sent.count {it.action==0x02})
    }
}
