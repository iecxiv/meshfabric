from pathlib import Path
import subprocess
root=Path('app/src/main/java/iecxiv/meshfabric/ui/devices')
p=root/'DeviceOtherOptions.kt'
q=root/'DeviceListScreen.kt'
for path,sha in [(p,'d119b4feaa61cf4bddf7c52e867d056d7398ccba'),(q,'3c37235edcc370c05a253e7a6aad7d2caac5925a')]:
    assert subprocess.check_output(['git','hash-object',str(path)],text=True).strip()==sha,'Source changed: '+str(path)
s=p.read_text();t=q.read_text()
original_session=t.split('@Composable fun SessionActionButton',1)[1]
original_groups=t.split('@Composable fun GroupHeaderCard',1)[1].split('@Composable fun DeviceCard',1)[0]
old='Modifier.width(width).heightIn(min=48.dp).testTag("device_option_$key")'
assert s.count(old)==1
s=s.replace(old,'Modifier.width(width).testTag("device_option_$key")',1)
old='modifier=Modifier.testTag("device_options_toggle")'
assert s.count(old)==1
s=s.replace(old,'modifier=Modifier.size(38.dp).testTag("device_options_toggle")',1)
old='    OutlinedButton(onClick=onClick,enabled=enabled,modifier=Modifier.width(width)'
assert s.count(old)==1
s=s.replace(old,'    val contentColor=if(iecxiv.meshfabric.ui.theme.LocalAppDarkTheme.current) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface\n'+old,1)
old='        contentPadding=PaddingValues(horizontal=2.dp,vertical=4.dp)) {'
assert s.count(old)==1
s=s.replace(old,'        colors=ButtonDefaults.outlinedButtonColors(contentColor=contentColor,disabledContentColor=contentColor.copy(alpha=0.38f)),\n'+old,1)
old='                DeviceOptionsToggle(otherExpanded) { otherExpanded = !otherExpanded }\n                LocalDeviceActionMenu.current(node)'
new='''                Row(horizontalArrangement=Arrangement.spacedBy((-6).dp),verticalAlignment=Alignment.CenterVertically) {
                    DeviceOptionsToggle(otherExpanded) { otherExpanded = !otherExpanded }
                    Box(Modifier.size(38.dp),contentAlignment=Alignment.Center) {
                        LocalDeviceActionMenu.current(node)
                    }
                }'''
assert t.count(old)==1
t=t.replace(old,new,1)
assert t.split('@Composable fun SessionActionButton',1)[1]==original_session
assert t.split('@Composable fun GroupHeaderCard',1)[1].split('@Composable fun DeviceCard',1)[0]==original_groups
p.write_text(s);q.write_text(t)
test=Path('app/src/androidTest/java/iecxiv/meshfabric/ui/devices/DeviceOptionsVisualUiTest.kt')
assert not test.exists()
test.parent.mkdir(parents=True,exist_ok=True)
test.write_text(Path(__file__).with_name('DeviceOptionsVisualUiTest.kt').read_text())
subprocess.run(['git','add','--',str(p),str(q),str(test)],check=True)
