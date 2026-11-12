from pathlib import Path
import subprocess
assert subprocess.check_output(['git','rev-parse','HEAD^'],text=True).strip()=='481d9bb322de73f3947623bbbddce6ffe843c788','Source base changed'
root=Path('app/src/main/java/iecxiv/meshfabric/ui/management')
updates={}
def once(s,old,new):
    assert s.count(old)==1,('Unexpected anchor',old,s.count(old))
    return s.replace(old,new,1)
p=root/'ActivityTimeline.kt'
assert subprocess.check_output(['git','hash-object',str(p)],text=True).strip()=='13489f9a867bc7bfe6082029f9cd21de30e4d292'
s=p.read_text()
s=once(s,'import androidx.compose.ui.Modifier','import androidx.compose.ui.Modifier\nimport androidx.compose.ui.platform.testTag')
s=once(s,'Card(Modifier.fillMaxWidth(),colors= iecxiv.meshfabric.ui.theme.appCardColors(',
    'Card(Modifier.fillMaxWidth().testTag("activity-card-${entry.id}"),shape=MaterialTheme.shapes.medium,border=BorderStroke(1.dp,scheme.outlineVariant),colors= iecxiv.meshfabric.ui.theme.appCardColors(')
updates[str(p)]=s
p=root/'LogScreen.kt'
assert subprocess.check_output(['git','hash-object',str(p)],text=True).strip()=='e58cc92eace995d5075c30d6fed9b929ed1c19cb'
s=p.read_text()
s=once(s,'Card(Modifier.fillMaxWidth().testTag("log-event-row-$index").clickable {detail=row},',
    'Card(Modifier.fillMaxWidth().testTag("log-event-row-$index").clickable {detail=row},shape=MaterialTheme.shapes.medium,border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),')
updates[str(p)]=s
p=Path('app/src/androidTest/java/iecxiv/meshfabric/ui/management/LogCardOutlineUiTest.kt')
assert not p.exists()
updates[str(p)]=Path(__file__).with_name('LogCardOutlineUiTest.kt').read_text()
for name,text in updates.items():
    p=Path(name);p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
Path('validation').mkdir(exist_ok=True)
subprocess.run(['git','add','--',*sorted(updates)],check=True)
