from pathlib import Path
import subprocess
assert subprocess.check_output(['git','rev-parse','HEAD^'],text=True).strip()=='5a2ac793a44dd11838fc9edd50ab607d77bac690','Base changed'
p=Path('app/src/main/java/iecxiv/meshfabric/ui/management/ServerGeneralPresentation.kt')
assert subprocess.check_output(['git','hash-object',str(p)],text=True).strip()=='c20c70355f481cc3fe2a3758c2e936e7697391db','Source changed'
s=p.read_text()
marker='@Composable internal fun ServerReadResultDialog('
assert s.count(marker)==1
prefix,body=s.split(marker)
old='title={ Text(title) }'
new='title={ Text(title,style=MaterialTheme.typography.headlineSmall) }'
assert body.count(old)==2
updated=prefix+marker+body.replace(old,new)
assert updated.replace(new,old)==s,'Unexpected edits'
p.write_text(updated)
target=Path('app/src/androidTest/java/iecxiv/meshfabric/ui/management/ServerDialogTitleTypographyUiTest.kt')
assert not target.exists()
target.parent.mkdir(parents=True,exist_ok=True)
target.write_text(Path(__file__).with_name('ServerDialogTitleTypographyUiTest.kt').read_text())
subprocess.run(['git','add','--',str(p),str(target)],check=True)
