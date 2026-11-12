from pathlib import Path
import subprocess,shutil
p=Path('app/src/main/java/iecxiv/meshfabric/ui/devices/DeviceGeneralDialog.kt')
assert subprocess.check_output(['git','hash-object',str(p)],text=True).strip()=='fb170e009295ae2b4713d317926548a04c6eb357','General changed'
files={name:'app/src/'+folder+'/java/iecxiv/meshfabric/ui/devices/'+name for name,folder in [('DevicePowerHistory.kt','main'),('DevicePowerCsv.kt','main'),('DevicePowerSection.kt','main'),('DevicePowerPolicyTest.kt','test'),('DevicePowerSectionUiTest.kt','androidTest')]}
for name,dest in files.items():
    target=Path(dest)
    assert not target.exists(),dest
    target.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(Path(__file__).parent/name,target)
s=p.read_text()
old='            if(state.phase in setOf("loading","saving")) {'
assert s.count(old)==1
s=s.replace(old,'            DevicePowerSection(node.id)\n'+old,1)
p.write_text(s)
subprocess.run(['git','add','--',str(p),*files.values()],check=True)
