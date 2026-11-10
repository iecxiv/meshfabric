from pathlib import Path
import collections,json,os,re,subprocess,sys,xml.etree.ElementTree as ET
OUT=Path('validation/trace-focused');OUT.mkdir(parents=True,exist_ok=True)
PREFIX='iecxiv.meshfabric.ui.management.'
ANDROID={
'TraceSourcesChannelTest':{'initialNullEnablesSelectionWithoutWritingGlobalSources','malformedStateIsNotAcceptedAsAnEmptySelection','missingStateTimesOutAndReconnectDoesNotWriteSources','nonAdminCannotUnlockOrChangeSources','userChangeWritesOnceAndOnlyReadbackConfirmsIt','staleSocketCannotUnlockANewConnection'},
'ServerConsoleWebViewTest':{'localWebViewConsumesInitialAndUpdatedSnapshotsWithoutServer'}}
JVM=['TraceSourcesPolicyTest','ServerAdminPolicyTest','ServerConsolePolicyTest','ServerConsoleSubmissionGateTest','ServerCsvTest']
def scope():
    expected=json.loads(Path('validation/trace-sources-expected.json').read_text())
    assert len(expected)==5 and set(subprocess.check_output(['git','diff','--cached','--name-only'],text=True).splitlines())==set(expected)
    assert not subprocess.check_output(['git','diff','--name-only'],text=True).strip()
def unit_results():
    cases=[]
    for path in Path('app/build/test-results/testDebugUnitTest').rglob('*.xml'):
        root=ET.parse(path).getroot()
        assert not root.findall('.//failure') and not root.findall('.//error') and not root.findall('.//skipped'),str(path)
        cases.extend(root.iter('testcase'))
    assert {c.get('classname') for c in cases}=={PREFIX+n for n in JVM}
    for name in JVM:
        source=Path('app/src/test/java/iecxiv/meshfabric/ui/management')/(name+'.kt')
        expected=set(re.findall(r'@Test(?:\([^)]*\))?\s+fun\s+(\w+)\s*\(',source.read_text()))
        assert expected,name
        found=[c.get('name') for c in cases if c.get('classname')==PREFIX+name]
        assert collections.Counter(found)==collections.Counter(expected),(name,found,expected)
    assert sum(c.get('classname')==PREFIX+'TraceSourcesPolicyTest' for c in cases)==8
    (OUT/'jvm-summary.json').write_text(json.dumps({'classes':JVM,'executed_cases':len(cases),'passed':True},indent=2)+'\n')
def decode_instrumentation(text,classname,methods):
    bundle={};starts=[];finishes=[];codes=[]
    for line in text.splitlines():
        if line.startswith('INSTRUMENTATION_STATUS: '):
            value=line[len('INSTRUMENTATION_STATUS: '):]
            if '=' in value:
                key,value=value.split('=',1);bundle[key]=value
        elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
            code=int(line.split(':',1)[1].strip())
            assert bundle.get('class')==classname and bundle.get('test') in methods,(code,bundle)
            if code==1: starts.append(bundle['test'])
            else: finishes.append({'name':bundle['test'],'status_code':code,'stack':bundle.get('stack','')})
            bundle={}
        elif line.startswith('INSTRUMENTATION_CODE: '): codes.append(int(line.split(':',1)[1].strip()))
    assert codes==[-1],codes
    assert 'INSTRUMENTATION_FAILED' not in text and 'INSTRUMENTATION_ABORTED' not in text and 'INSTRUMENTATION_RESULT: shortMsg=' not in text
    assert collections.Counter(starts)==collections.Counter(methods),starts
    assert collections.Counter(x['name'] for x in finishes)==collections.Counter(methods),finishes
    assert all(x['status_code']==0 for x in finishes),finishes
    return finishes
if sys.argv[1]=='unit':
    unit_results();scope()
elif sys.argv[1]=='android':
    devices=subprocess.check_output(['adb','devices'],text=True)
    serials=[line.split()[0] for line in devices.splitlines() if len(line.split())==2 and line.split()[1]=='device']
    assert len(serials)==1,devices
    adb=['adb','-s',serials[0]]
    for folder in ['app/build/outputs/apk/debug','app/build/outputs/apk/androidTest/debug']:
        apks=list(Path(folder).glob('*.apk'));assert len(apks)==1,(folder,apks)
        subprocess.run(adb+['install','-r','-t',str(apks[0])],check=True,timeout=120)
    results=[]
    for name,methods in ANDROID.items():
        classname=PREFIX+name;raw='';code=None;cases=[];error=''
        try:
            process=subprocess.run(adb+['shell','am','instrument','-w','-r','-e','class',classname,'iecxiv.meshfabric.test/androidx.test.runner.AndroidJUnitRunner'],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=300)
            raw=process.stdout;code=process.returncode
            (OUT/(name+'.txt')).write_text(raw)
            assert code==0,code
            cases=decode_instrumentation(raw,classname,methods)
        except subprocess.TimeoutExpired as exc:
            raw=exc.stdout or '';raw=raw.decode(errors='replace') if isinstance(raw,bytes) else raw
            error='Instrumentation timeout'
        except Exception as exc: error=str(exc)
        (OUT/(name+'.txt')).write_text(raw)
        result={'class':classname,'passed':not error,'exit_code':code,'expected_methods':sorted(methods),'cases':cases,'error':error}
        (OUT/(name+'.json')).write_text(json.dumps(result,indent=2)+'\n');results.append(result)
    (OUT/'summary.json').write_text(json.dumps(results,indent=2)+'\n')
    assert len(results)==2 and all(r['passed'] for r in results),results
elif sys.argv[1]=='verify':
    unit_results();scope()
    results=json.loads((OUT/'summary.json').read_text())
    assert len(results)==2 and {r['class'] for r in results}=={PREFIX+n for n in ANDROID}
    for result in results:
        name=result['class'].split('.')[-1]
        cases=decode_instrumentation((OUT/(name+'.txt')).read_text(),result['class'],ANDROID[name])
        assert result['passed'] is True and result['exit_code']==0 and result['cases']==cases,result
    assert sum(len(r['cases']) for r in results)==7
    (OUT/'validation.json').write_text(json.dumps({'android_classes':2,'android_methods':7,'apk_builds_per_class':False,'all_assertions_preserved':True,'live_server_verified':False,'physical_visual_review_verified':False},indent=2)+'\n')
else: raise AssertionError('Unknown validation mode')
