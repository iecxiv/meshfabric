from pathlib import Path
import subprocess,json
BASE='b1f914d2ba67f2cf279b2ed8ce1e879b7ad40445'
assert subprocess.check_output(['git','rev-parse','HEAD^'],text=True).strip()==BASE,'Source base changed'
root=Path('app/src/main/java/iecxiv/meshfabric')
updates={}
def replace_once(s,old,new):
    assert s.count(old)==1,old
    return s.replace(old,new,1)
p=root/'ui/theme/Color.kt';s=p.read_text()
s=replace_once(s,'val DarkOutline = Color(0xFF334155)      // Soft borders','val DarkOutline = Color(0xFF94A3B8)\nval DarkOutlineVariant = Color(0xFF7C899D)')
updates[str(p)]=s
p=root/'ui/theme/Theme.kt';s=p.read_text()
s=replace_once(s,'outlineVariant = DarkOutline','outlineVariant = DarkOutlineVariant')
s=replace_once(s,'colorScheme = colorScheme,','colorScheme = darkContrastScheme(colorScheme,darkTheme),')
updates[str(p)]=s
p=root/'ui/management/ActivityTimeline.kt';s=p.read_text();lines=s.splitlines(keepends=True);count=0
prefix='iecxiv.meshfabric.ui.theme.activityCardTextColor'
for i,line in enumerate(lines):
    if any(x in line for x in ('Text((if(entry.receivedTime)','Text(entry.summary,style=','Text(if(entry.actor.isNotBlank())')):
        assert line.count('color=scheme.onSurfaceVariant')==1
        lines[i]=line.replace('color=scheme.onSurfaceVariant','color='+prefix+'(scheme.onSurfaceVariant)');count+=1
    elif 'if(entry.description.isNotBlank()) Text(entry.description,style=MaterialTheme.typography.bodySmall,maxLines=1,' in line:
        lines[i]=line.replace('maxLines=1,','color='+prefix+'(LocalContentColor.current),maxLines=1,');count+=1
assert count==4,('Unexpected Activity text sites',count)
updates[str(p)]=''.join(lines)
here=Path(__file__).resolve().parent
for dest,name in [
    ('app/src/main/java/iecxiv/meshfabric/ui/theme/DarkContrast.kt','DarkContrast.kt'),
    ('app/src/test/java/iecxiv/meshfabric/ui/theme/DarkContrastTest.kt','DarkContrastTest.kt'),
    ('app/src/androidTest/java/iecxiv/meshfabric/ui/theme/DarkContrastUiTest.kt','DarkContrastUiTest.kt')]:
    assert not Path(dest).exists(),dest
    updates[dest]=(here/name).read_text()
for name,text in updates.items():
    p=Path(name);p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
Path('validation').mkdir(exist_ok=True)
Path('validation/contrast-files.json').write_text(json.dumps(sorted(updates),indent=2))
subprocess.run(['git','add','--',*sorted(updates)],check=True)
