from pathlib import Path
import subprocess,json
BASE='13c1abd85c6b184b9fbfe82d81524059aaa94aba'
assert subprocess.check_output(['git','rev-parse','HEAD^'],text=True).strip()==BASE,'Source base changed'
root=Path('app/src/main/java/iecxiv/meshfabric')
updates={}
def replace_once(s,old,new):
    assert s.count(old)==1,('Expected exactly one source anchor',old,s.count(old))
    return s.replace(old,new,1)
def patch_activity(s):
    prefix='iecxiv.meshfabric.ui.theme.activityCardTextColor'
    anchors=[
        'Text((if(entry.receivedTime) "Recibido " else "")+DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(entry.time)),style=MaterialTheme.typography.labelSmall,color=scheme.onSurfaceVariant)',
        'Text(entry.summary,style=MaterialTheme.typography.bodySmall,color=scheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)',
        'Text(if(entry.actor.isNotBlank()) "Por ${entry.actor}" else "",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=scheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)'
    ]
    original_details=s.split('if(details) {',1)
    assert len(original_details)==2,'Details anchor changed'
    for old in anchors:
        new=old.replace('color=scheme.onSurfaceVariant','color='+prefix+'(scheme.onSurfaceVariant)')
        s=replace_once(s,old,new)
    old='if(entry.description.isNotBlank()) Text(entry.description,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)'
    new=old.replace('maxLines=1,','color='+prefix+'(LocalContentColor.current),maxLines=1,')
    s=replace_once(s,old,new)
    assert s.count(prefix+'(')==4,'Unexpected text replacement count'
    assert s.split('if(details) {',1)[1]==original_details[1],'Expanded details must remain unchanged'
    return s
p=root/'ui/theme/Color.kt';s=p.read_text()
s=replace_once(s,'val DarkOutline = Color(0xFF334155)      // Soft borders','val DarkOutline = Color(0xFF94A3B8)\nval DarkOutlineVariant = Color(0xFF7C899D)')
updates[str(p)]=s
p=root/'ui/theme/Theme.kt';s=p.read_text()
s=replace_once(s,'outlineVariant = DarkOutline','outlineVariant = DarkOutlineVariant')
s=replace_once(s,'colorScheme = colorScheme,','colorScheme = darkContrastScheme(colorScheme,darkTheme),')
updates[str(p)]=s
p=root/'ui/management/ActivityTimeline.kt'
assert subprocess.check_output(['git','hash-object',str(p)],text=True).strip()=='a542cd3fa22bee1626f722ac6f99f882bedb1c81','Activity source changed'
updates[str(p)]=patch_activity(p.read_text())
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
