from pathlib import Path
import shutil
p=Path('app/src/main/java/iecxiv/meshfabric/ui/devices/DeviceListScreen.kt')
s=p.read_text()
import subprocess
assert subprocess.check_output(['git','hash-object',str(p)],text=True).strip()=='06fc8e05c8d558a467d9fb4bc5b9fd0cec84d8a8','DeviceCard changed'
a='    val os=node.osDisplayName.ifEmpty { stringResource(R.string.device_os_unknown) }'
b=a+'\n    var otherOptionsExpanded by androidx.compose.runtime.saveable.rememberSaveable(node.id) { mutableStateOf(false) }'
assert s.count(a)==1
s=s.replace(a,b,1)
a='                LocalDeviceActionMenu.current(node)'
b='                DeviceOtherOptionsToggle(otherOptionsExpanded) { otherOptionsExpanded = !otherOptionsExpanded }\n'+a
assert s.count(a)==1
s=s.replace(a,b,1)
a='            if(node.isOnline || listOf(desktopStatus,terminalStatus,fileStatus).any { it!=SessionStatus.NONE }) {'
b='            AnimatedVisibility(otherOptionsExpanded) { DeviceOtherOptionsRow(node) }\n'+a
assert s.count(a)==1
s=s.replace(a,b,1)
p.write_text(s)
base=Path(__file__).parent
for source,dest in [('DeviceOtherOptions.kt','app/src/main/java/iecxiv/meshfabric/ui/devices/DeviceOtherOptions.kt'),('DeviceOtherOptionsUiTest.kt','app/src/androidTest/java/iecxiv/meshfabric/ui/devices/DeviceOtherOptionsUiTest.kt')]:
    target=Path(dest)
    assert not target.exists()
    target.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(base/source,target)
subprocess.run(['git','add','--',str(p),'app/src/main/java/iecxiv/meshfabric/ui/devices/DeviceOtherOptions.kt','app/src/androidTest/java/iecxiv/meshfabric/ui/devices/DeviceOtherOptionsUiTest.kt'],check=True)
