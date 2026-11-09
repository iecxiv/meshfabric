package iecxiv.meshfabric.ui.management
import org.json.JSONObject
import java.util.Locale
private fun groupChoiceKey(mesh:JSONObject)=mesh.optString("name").trim().lowercase(Locale.ROOT)
internal fun profileGroupChoiceLabel(mesh:JSONObject,all:List<JSONObject>,es:Boolean):String {
    val name=mesh.optString("name").trim()
    if(name.isBlank()) return if(es) "Grupo sin nombre" else "Unnamed group"
    val duplicates=all.count {groupChoiceKey(it)==groupChoiceKey(mesh)}>1
    val desc=mesh.optString("desc").trim()
    return if(duplicates && desc.isNotBlank()) name+" · "+desc else name
}
internal fun profileGroupChoiceUsable(mesh:JSONObject?,all:List<JSONObject>):Boolean {
    if(mesh==null || mesh.optString("name").isBlank()) return false
    val peers=all.filter {groupChoiceKey(it)==groupChoiceKey(mesh)}
    if(peers.size<=1) return true
    val desc=mesh.optString("desc").trim()
    return desc.isNotBlank() && peers.count {it.optString("desc").trim().equals(desc,true)}==1
}
