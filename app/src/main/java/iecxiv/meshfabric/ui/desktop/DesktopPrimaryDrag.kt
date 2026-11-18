package iecxiv.meshfabric.ui.desktop

internal enum class DesktopPrimaryPhase { DOWN, MOVE, UP }

internal class DesktopPrimaryDrag(private val binding:()->Any?,private val emit:(Any,Int,Int,Int)->Unit) {
    private var origin:Any?=null
    private var x=0;private var y=0;private var up=0x04
    fun down(px:Int,py:Int,swapped:Boolean):Boolean {
        if(origin!=null) return binding()===origin
        val socket=binding() ?: return false
        origin=socket;x=px;y=py;up=if(swapped) 0x10 else 0x04
        emit(socket,0x00,x,y);emit(socket,if(swapped) 0x08 else 0x02,x,y)
        return true
    }
    fun move(px:Int,py:Int):Boolean {
        val socket=origin ?: return false
        if(binding()!==socket) {origin=null;return false}
        x=px;y=py;emit(socket,0x00,x,y);return true
    }
    fun release() {
        val socket=origin ?: return
        origin=null
        if(binding()===socket) emit(socket,up,x,y)
    }
}
