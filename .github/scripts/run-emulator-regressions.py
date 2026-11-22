from pathlib import Path
import json, os, re, shutil, subprocess
import xml.etree.ElementTree as ET
classes=['iecxiv.meshfabric.data.remote.MeshSnapshotControlTest','iecxiv.meshfabric.data.remote.NodeSnapshotControlTest','iecxiv.meshfabric.data.remote.NodeConnectivityControlTest','iecxiv.meshfabric.SessionRuntimeCallbacksTest','iecxiv.meshfabric.SessionResumeAndDeviceCardTest','iecxiv.meshfabric.data.local.SessionStorageSafetyTest','iecxiv.meshfabric.ui.management.DeviceGroupSearchTest','iecxiv.meshfabric.ui.management.FourVisualCorrectionsTest','iecxiv.meshfabric.ui.management.UnifiedSubviewLayoutTest']
archive=Path(os.environ['RUNNER_TEMP'])/'resume-regressions'
archive.mkdir(exist_ok=True)
summary={}
for name in classes:
    source=Path('app/src/androidTest/java')/(name.replace('.','/')+'.kt')
    text=source.read_text()
    expected=len(re.findall(r'@Test\b',text))
    assert expected>0,('No test methods',name)
    results=Path('app/build/outputs/androidTest-results')
    if results.exists(): shutil.rmtree(results)
    subprocess.run(['bash','./gradlew',':app:connectedDebugAndroidTest','-Pandroid.testInstrumentationRunnerArguments.class='+name,'--rerun-tasks','--no-daemon','--stacktrace'],check=True)
    reports=sorted(results.rglob('*.xml'))
    assert reports,('No XML',name)
    destination=archive/name
    destination.mkdir(exist_ok=True)
    cases=[]
    for index,path in enumerate(reports):
        shutil.copy2(path,destination/(str(index)+'.xml'))
        root=ET.parse(path).getroot()
        cases.extend(root.iter('testcase'))
        for suite in root.iter():
            if suite.tag in ('testsuite','testsuites'):
                assert all(int(suite.get(key,'0'))==0 for key in ('failures','errors','skipped')),(name,path,suite.attrib)
    found={case.get('classname') for case in cases}
    print(json.dumps({'requested':name,'found':sorted(str(value) for value in found),'reports':[str(path) for path in reports]}),flush=True)
    assert found=={name},('Unexpected or missing classes',name,found)
    names={case.get('name') for case in cases}
    assert len(names)>=expected,('Missing methods',name,expected,names)
    assert all(case.find(key) is None for case in cases for key in ('failure','error','skipped')),('Unsuccessful tests',name)
    summary[name]={'expected':expected,'passed':len(names)}
    (archive/'summary.json').write_text(json.dumps(summary,indent=2))
assert set(summary)==set(classes)
