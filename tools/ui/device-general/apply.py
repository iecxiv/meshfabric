from pathlib import Path
import shutil,subprocess
p=Path('app/src/main/java/iecxiv/meshfabric/ui/devices/DeviceOtherOptions.kt')
assert subprocess.check_output(['git','hash-object',str(p)],text=True).strip()=='dbcc6c547a609f11cb98559d6f3214317f616467','Base changed'
files={
'DeviceGeneralEditor.kt':'app/src/main/java/iecxiv/meshfabric/ui/devices/DeviceGeneralEditor.kt',
'DeviceGeneralDialog.kt':'app/src/main/java/iecxiv/meshfabric/ui/devices/DeviceGeneralDialog.kt',
'DeviceGeneralPolicyTest.kt':'app/src/test/java/iecxiv/meshfabric/ui/devices/DeviceGeneralPolicyTest.kt',
'DeviceGeneralDialogUiTest.kt':'app/src/androidTest/java/iecxiv/meshfabric/ui/devices/DeviceGeneralDialogUiTest.kt'}
for name,dest in files.items():
    target=Path(dest)
    assert not target.exists(),dest
    target.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(Path(__file__).parent/name,target)
s=p.read_text()
start=s.index('    if(general) iecxiv.meshfabric.ui.components.AppAlertDialog(')
end=s.index('\n}\n\n@Composable private fun DeviceOptionButton',start)
s=s[:start]+'    if(general) DeviceGeneralDialog(node) { general=false }'+s[end:]
p.write_text(s)
subprocess.run(['git','add','--',str(p),*files.values()],check=True)
