from pathlib import Path
import subprocess
assert subprocess.check_output(['git','rev-parse','HEAD^'],text=True).strip()=='f790c369e973bf4d65ea3f3d7b2943f991d18c3a','Source base changed'
p=Path('app/src/main/java/iecxiv/meshfabric/ui/devices/DeviceListScreen.kt')
assert subprocess.check_output(['git','hash-object',str(p)],text=True).strip()=='f168df581fb379036b62a23296361d7aa4429ea3','Device source changed'
s=p.read_text()
before,body=s.split('@Composable fun DeviceCard(',1)
body,after=body.split('@Composable fun SessionActionButton(',1)
def once(old,new):
    global body
    assert body.count(old)==1,('Unexpected anchor',old,body.count(old))
    body=body.replace(old,new,1)
white='if(iecxiv.meshfabric.ui.theme.LocalAppDarkTheme.current) Color.White else '
once('style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)',
     'style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis,color='+white+'LocalContentColor.current)')
once('if(os.isNotEmpty()) Text(os,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)',
     'if(os.isNotEmpty()) Text(os,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis,color='+white+'MaterialTheme.colorScheme.onSurfaceVariant)')
once('                        color = MaterialTheme.colorScheme.onSurfaceVariant',
     '                        color = '+white+'MaterialTheme.colorScheme.onSurfaceVariant')
assert body.count(white)==3
p.write_text(before+'@Composable fun DeviceCard('+body+'@Composable fun SessionActionButton('+after)
target=Path('app/src/androidTest/java/iecxiv/meshfabric/ui/devices/DeviceCardWhiteTextUiTest.kt')
assert not target.exists()
target.parent.mkdir(parents=True,exist_ok=True)
target.write_text(Path(__file__).with_name('DeviceCardWhiteTextUiTest.kt').read_text())
Path('validation').mkdir(exist_ok=True)
subprocess.run(['git','add','--',str(p),str(target)],check=True)
