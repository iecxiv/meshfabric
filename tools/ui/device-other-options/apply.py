from pathlib import Path
import subprocess,shutil
import xml.etree.ElementTree as ET
resources=Path('app/src/main/res')
def keys(folder):
    return {e.attrib['name'] for e in ET.parse(resources/folder/'device_other_options.xml').getroot() if e.tag=='string'}
assert keys('values')==keys('values-en')==keys('values-es'),'Device option translations incomplete'
p=Path('app/src/main/java/iecxiv/meshfabric/ui/devices/DeviceListScreen.kt')
assert subprocess.check_output(['git','hash-object',str(p)],text=True).strip()=='06fc8e05c8d558a467d9fb4bc5b9fd0cec84d8a8','Source changed'
s=p.read_text()
changes=[('    val os=node.osDisplayName.ifEmpty { stringResource(R.string.device_os_unknown) }','    val os=node.osDisplayName.ifEmpty { stringResource(R.string.device_os_unknown) }\n    var otherExpanded by androidx.compose.runtime.saveable.rememberSaveable(node.id) { mutableStateOf(false) }'),('                LocalDeviceActionMenu.current(node)','                DeviceOptionsToggle(otherExpanded) { otherExpanded = !otherExpanded }\n                LocalDeviceActionMenu.current(node)'),('            if(node.isOnline || listOf(desktopStatus,terminalStatus,fileStatus).any { it!=SessionStatus.NONE }) {','            AnimatedVisibility(otherExpanded) { DeviceOptionsRow(node) }\n            if(node.isOnline || listOf(desktopStatus,terminalStatus,fileStatus).any { it!=SessionStatus.NONE }) {')]
for old,new in changes:
    assert s.count(old)==1
    s=s.replace(old,new,1)
p.write_text(s)
for name,dest in [('DeviceOtherOptions.kt','app/src/main/java/iecxiv/meshfabric/ui/devices/DeviceOtherOptions.kt'),('DeviceOtherOptionsUiTest.kt','app/src/androidTest/java/iecxiv/meshfabric/ui/devices/DeviceOtherOptionsUiTest.kt')]:
    target=Path(dest)
    assert not target.exists()
    target.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(Path(__file__).parent/name,target)
subprocess.run(['git','add','--',str(p),'app/src/main/java/iecxiv/meshfabric/ui/devices/DeviceOtherOptions.kt','app/src/androidTest/java/iecxiv/meshfabric/ui/devices/DeviceOtherOptionsUiTest.kt'],check=True)
