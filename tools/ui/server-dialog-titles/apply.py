from pathlib import Path
import subprocess
assert subprocess.check_output(['git','rev-parse','HEAD^'],text=True).strip()=='6726aa814e83a57bea5fa4ae6ae810ae07d2e91a','Base changed'
root=Path('app/src/main/java/iecxiv/meshfabric/ui')
def checked(path,sha):
    assert subprocess.check_output(['git','hash-object',str(path)],text=True).strip()==sha,('Source changed',str(path))
    return path.read_text()
reference=checked(root/'desktop/RemoteDesktopScreen.kt','d05b156982f7af8329a93538905df48c9b9eab2b')
assert reference.count('Text(stringResource(R.string.keyboard_and_shortcuts), fontWeight = FontWeight.Bold, fontSize = 18.sp)')==1
checked(root/'theme/Theme.kt','0631bb5161ba84e97c4ac7ac704a0ac1fe2c49e7')
changes=[
('ServerGeneralPresentation.kt','f8c35eddb289fdeb7860eb953249acda15ddc0c8','title={ Text(title,style=MaterialTheme.typography.headlineSmall) }','title={ if(action=="serverconfig" || action=="servererrors") ServerGeneralActionTitle(title) else Text(title,style=MaterialTheme.typography.headlineSmall) }',2),
('ServerBackupPanel.kt','06db6fd04ad2e7f66772e4072bfb2bf8aedc1581','title={ Text(stringResource(R.string.my_server_backup_dialog_title)) }','title={ ServerGeneralActionTitle(stringResource(R.string.my_server_backup_dialog_title)) }',1),
('ServerRestorePanel.kt','fc373ed28af8e5e08fd5f5c4a34a826463be1664','title={ Text(label("Restaurar servidor desde copia","Restore server from backup")) }','title={ ServerGeneralActionTitle(label("Restaurar servidor desde copia","Restore server from backup")) }',1),
('ServerVersionDialog.kt','cf98b365169abcc5dcb4ee2648b3db2841843e5b','title={ Text(stringResource(R.string.my_server_version)) }','title={ ServerGeneralActionTitle(stringResource(R.string.my_server_version)) }',1),
('MyServerScreen.kt','418327ffbc9b518174dc0935fffe29d4b78ebd6e','title={ Text(stringResource(R.string.my_server_config)) }','title={ ServerGeneralActionTitle(stringResource(R.string.my_server_config)) }',1)]
updates={}
for name,sha,old,new,count in changes:
    p=root/'management'/name;s=checked(p,sha)
    assert s.count(old)==count,('Anchor mismatch',name)
    updated=s.replace(old,new)
    assert updated.replace(new,old)==s
    updates[str(p)]=updated
helper=root/'management/ServerGeneralActionTitle.kt'
assert not helper.exists()
updates[str(helper)]='''package iecxiv.meshfabric.ui.management
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
@Composable internal fun ServerGeneralActionTitle(text:String) {
    Text(text,style=MaterialTheme.typography.bodyLarge.copy(fontSize=18.sp,fontWeight=FontWeight.Bold))
}
'''
test=Path('app/src/androidTest/java/iecxiv/meshfabric/ui/management/ServerDialogTitleTypographyUiTest.kt')
checked(test,'7dfc23745ed7ec60536719c753fe89ee65c8e7e1')
updates[str(test)]=Path(__file__).with_name('ServerDialogTitleTypographyUiTest.kt').read_text()
for name,text in updates.items():
    p=Path(name);p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
subprocess.run(['git','add','--',*sorted(updates)],check=True)
