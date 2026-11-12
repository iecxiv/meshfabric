from pathlib import Path
import subprocess
assert subprocess.check_output(['git','rev-parse','HEAD^'],text=True).strip()=='3a51ed41e1523771611b9d799139e627b280a061','Base changed'
p=Path('app/src/main/java/iecxiv/meshfabric/ui/devices/DeviceListScreen.kt')
assert subprocess.check_output(['git','hash-object',str(p)],text=True).strip()=='0e4478766154cb7cab4d5850b242baaa2820cc8d','Source changed'
s=p.read_text()
marker='@Composable fun DeviceCard('
assert s.count(marker)==1
prefix,body=s.split(marker)
old='Card(Modifier.fillMaxWidth().padding(vertical=4.dp),'
new=old+'\n        border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),'
assert body.count(old)==1
updated=prefix+marker+body.replace(old,new,1)
assert updated.replace(new,old,1)==s,'Unexpected edits'
assert updated.split(marker)[0]==prefix,'Group headers changed'
p.write_text(updated)
test=Path('app/src/androidTest/java/iecxiv/meshfabric/ui/devices/DeviceCardOutlineUiTest.kt')
assert not test.exists()
test.parent.mkdir(parents=True,exist_ok=True)
test.write_text(Path(__file__).with_name('DeviceCardOutlineUiTest.kt').read_text())
subprocess.run(['git','add','--',str(p),str(test)],check=True)
