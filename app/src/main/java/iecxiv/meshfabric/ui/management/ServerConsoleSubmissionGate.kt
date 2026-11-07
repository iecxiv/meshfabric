package iecxiv.meshfabric.ui.management

internal fun validServerConsoleCommand(text:String)=text.isNotBlank() && text.length<=1000 &&
    text.none { it.isISOControl() || it=='\u2028' || it=='\u2029' }

internal class ServerConsoleSubmissionGate {
    private var closed=false
    private var lastId=0
    fun claim(id:Int,text:String,allowed:Boolean):Boolean {
        if(closed || id<=0 || id<=lastId) return false
        lastId=id
        return allowed && validServerConsoleCommand(text)
    }
    fun close() { closed=true }
}
