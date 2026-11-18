from pathlib import Path
import subprocess,shutil
p=Path('app/src/main/java/iecxiv/meshfabric/ui/devices/DeviceGeneralDialog.kt')
reference=Path('app/src/main/java/iecxiv/meshfabric/ui/devices/DeviceDetailsDialog.kt')
for path,sha in [(p,'98d20abd6ceced92caeb7c6b3deb79b11c601097'),(reference,'0a80d91760eab0e6731ef7fe77b7367253daf20d')]:
    assert subprocess.check_output(['git','hash-object',str(path)],text=True).strip()==sha,'Source changed: '+str(path)
s=p.read_text()
start=s.index('    iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest=close,')
end=s.index('    review?.let',start)
main=s[start:end]
old='''    iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest=close,
        title={ Text(node.name,fontSize=18.sp,fontWeight=FontWeight.Bold) },
        text={ Column(Modifier.fillMaxWidth().heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {'''
new='''    DeviceGeneralFrame(node.copy(osdesc=meta?.os ?: node.osdesc),close,
        body={'''
assert main.count(old)==1
main=main.replace(old,new,1)
for line in ['            Text("General",style=MaterialTheme.typography.titleSmall)\n','            Text(text("Sistema: ","OS: ")+(meta?.os ?: node.osdesc.orEmpty()).ifBlank { text("Sin datos","No data") })\n','            Text(if(node.isOnline) text("Conectado","Connected") else text("Desconectado","Disconnected"))\n']:
    assert main.count(line)==1
    main=main.replace(line,'',1)
old='        } },\n        confirmButton={'
assert main.count(old)==1
main=main.replace(old,'        },\n        footer={',1)
old='''        },modifier=Modifier.testTag("general_save")) { Text(text("Revisar guardado","Review save")) } },
        dismissButton={ TextButton(onClick=close) { Text(text("Cerrar","Close")) } })'''
new='''        },modifier=Modifier.testTag("general_save")) { Text(text("Revisar guardado","Review save")) }
            TextButton(onClick=close) { Text(text("Cerrar","Close")) }
        })'''
assert main.count(old)==1
main=main.replace(old,new,1)
updated=s[:start]+main+s[end:]
assert updated[end+len(main)-(end-start):]==s[end:]
p.write_text(updated)
files={
'DeviceGeneralFrame.kt':'app/src/main/java/iecxiv/meshfabric/ui/devices/DeviceGeneralFrame.kt',
'DeviceGeneralLayoutUiTest.kt':'app/src/androidTest/java/iecxiv/meshfabric/ui/devices/DeviceGeneralLayoutUiTest.kt'}
for name,dest in files.items():
    target=Path(dest)
    assert not target.exists(),dest
    target.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(Path(__file__).parent/name,target)
subprocess.run(['git','add','--',str(p),*files.values()],check=True)
