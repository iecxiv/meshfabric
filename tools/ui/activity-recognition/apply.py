from pathlib import Path
import subprocess
assert subprocess.check_output(['git','rev-parse','HEAD^'],text=True).strip()=='b79751cb3d36725111af7c7c4363b832f42840a6','Source base changed'
root=Path('app/src/main/java/iecxiv/meshfabric/ui/management')
updates={}
def once(text,old,new):
    assert text.count(old)==1,('Unexpected anchor',old,text.count(old))
    return text.replace(old,new,1)
p=root/'ActivityTimeline.kt'
assert subprocess.check_output(['git','hash-object',str(p)],text=True).strip()=='db665c5132f1050e36856ab302edcc3cce9cba9a'
s=p.read_text()
s=once(s,'val receivedTime:Boolean=false) {','val receivedTime:Boolean=false,val meshId:String="",val groupName:String="") {')
s=once(s,'val target:String get()=deviceName.ifBlank { if(nodeId.isNotBlank()) "Equipo no identificado" else if(kind==ActivityKind.SERVER) "Servidor" else "Origen no identificado" }','val target:String get()=if(deviceName.isNotBlank()) deviceName+if(groupName.isBlank()) "" else " ($groupName)" else if(nodeId.isNotBlank()) "Equipo no identificado" else groupName.ifBlank { if(kind==ActivityKind.SERVER) "Servidor" else "Origen no identificado" }')
s=once(s,'private val currentActor:()->String) {','private val currentActor:()->String,private val inventory:()->ServerEventInventory={ ServerEventInventory() }) {')
s=once(s,'names.putAll(devices.filterValues { it.isNotBlank() }) }','names.putAll(devices.filterValues { it.isNotBlank() }); data.update { rows->rows.map(::resolve) } }')
s=once(s,'    private fun put(entry:ActivityEntry)', '    private fun resolve(entry:ActivityEntry)=resolveActivityEntry(entry,inventory())\n    private fun put(entry:ActivityEntry)')
s=once(s,'(listOf(entry)+it.filterNot { old->old.id==entry.id })','(listOf(resolve(entry))+it.filterNot { old->old.id==entry.id })')
start=s.index('        val node=j.str("nodeid")',s.index('    fun event(j:JSONObject)'))
end=s.index('        val raw=j.opt("time")',start)
s=s[:start]+"""        val device=activityEventDevice(j)
        val node=device.nodeId
        val action=j.str("action")
        val title=activityEventTitle(action)
"""+s[end:]
s=once(s,'name(node).ifBlank { j.str("nodename").ifBlank { j.optJSONObject("node")?.str("name").orEmpty() } }','device.name.ifBlank { name(node) }')
s=once(s,'title,"",ActivityStatus.INFO,j.str("msg").ifBlank { "El servidor no incluyó una descripción adicional." }.take(1000),','title,"",ActivityStatus.INFO,activityEventMessage(j),')
s=once(s,'receivedTime=parsed==null))','receivedTime=parsed==null,meshId=device.meshId,groupName=device.group))')
s=once(s,'                                Text(entry.title,Modifier.weight(1f),','                                ActivityEntryIcon(entry)\n                                Spacer(Modifier.width(8.dp))\n                                Text(entry.title,Modifier.weight(1f),')
updates[str(p)]=s
p=root/'MeshFabricHome.kt'
assert subprocess.check_output(['git','hash-object',str(p)],text=True).strip()=='36b876b25a19498e4f122a8ea70ec572eca5f962'
s=p.read_text()
s=once(s,'currentActor={ source.userInfo.value?.name.orEmpty() })','currentActor={ source.userInfo.value?.name.orEmpty() },\n        inventory={ serverEventInventory(source.groupedDevices.value) })')
s=once(s,'LaunchedEffect(nodes) { management.activity.rememberDevices','LaunchedEffect(groups) { management.activity.rememberDevices')
updates[str(p)]=s
here=Path(__file__).resolve().parent
for dest,name in [
    (str(root/'ActivityEventPresentation.kt'),'ActivityEventPresentation.kt'),
    ('app/src/test/java/iecxiv/meshfabric/ui/management/ActivityRecognitionTest.kt','ActivityRecognitionTest.kt'),
    ('app/src/androidTest/java/iecxiv/meshfabric/ui/management/ActivityRecognitionUiTest.kt','ActivityRecognitionUiTest.kt')]:
    assert not Path(dest).exists(),dest
    updates[dest]=(here/name).read_text()
for name,text in updates.items():
    p=Path(name);p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
Path('validation').mkdir(exist_ok=True)
subprocess.run(['git','add','--',*sorted(updates)],check=True)
