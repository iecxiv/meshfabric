package iecxiv.meshfabric.ui.management

enum class NotesPhase { LOADING, READY, SAVING, ERROR }
data class DeviceNotes(val nodeId:String,val name:String,val editable:Boolean=false,
    val phase:NotesPhase=NotesPhase.LOADING,val original:String="",val draft:String="",
    val loaded:Boolean=false,val error:String="",val message:String="") {
    val dirty:Boolean get()=loaded && draft!=original
    val canSave:Boolean get()=editable && dirty && phase==NotesPhase.READY
    fun edit(value:String):DeviceNotes=if(editable && phase==NotesPhase.READY) copy(draft=value,error="",message="") else this
    fun received(id:String,value:String):DeviceNotes {
        if(id!=nodeId) return this
        return when {
            phase==NotesPhase.LOADING -> copy(phase=NotesPhase.READY,original=value,draft=value,loaded=true,error="")
            phase==NotesPhase.SAVING && value==draft -> copy(phase=NotesPhase.READY,original=value,loaded=true,error="",message="Guardado confirmado por relectura")
            else -> this
        }
    }
    fun saving():DeviceNotes=if(canSave) copy(phase=NotesPhase.SAVING,error="",message="") else this
    fun failed(message:String):DeviceNotes=copy(phase=if(loaded) NotesPhase.READY else NotesPhase.ERROR,error=message,message="")
}
