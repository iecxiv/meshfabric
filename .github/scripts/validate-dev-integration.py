from pathlib import Path
import collections,json,re,subprocess,sys,xml.etree.ElementTree as ET
OUT=Path('validation/integration');OUT.mkdir(parents=True,exist_ok=True)
DEV='ee873325bafb864db729491fef4c973517c91065'
def scope():
    expected={'README.md','README_ES.md','app/build.gradle.kts'}
    assert set(subprocess.check_output(['git','diff','--cached','--name-only'],text=True).splitlines())==expected
    assert not subprocess.check_output(['git','diff','--name-only'],text=True).strip()
    assert 'versionName = "1.3.2"' in Path('app/build.gradle.kts').read_text()
def inventory(folder):
    expected=set()
    pattern=r'@(?:org\.junit\.)?Test(?:\([^)]*\))?\s+(?:fun\s+(`[^`]+`|\w+)\s*\(|public\s+void\s+(\w+)\s*\()'
    for p in sorted(Path(folder).rglob('*')):
        if p.suffix not in ('.kt','.java'): continue
        s=p.read_text();methods=[(a or b).strip('`') for a,b in re.findall(pattern,s)]
        if not methods: continue
        package=re.search(r'(?m)^package\s+([\w.]+)',s)
        assert package and re.search(r'\bclass\s+'+re.escape(p.stem)+r'\b',s),str(p)
        for method in methods:
            key=(package.group(1)+'.'+p.stem,method);assert key not in expected,key;expected.add(key)
    assert expected,folder
    return expected
def units():
    expected=inventory('app/src/test/java');cases=[]
    for p in Path('app/build/test-results/testDebugUnitTest').rglob('*.xml'):
        root=ET.parse(p).getroot();assert not any(root.findall('.//'+tag) for tag in ('failure','error','skipped')),str(p)
        cases.extend(root.iter('testcase'))
    actual=[(c.get('classname'),c.get('name')) for c in cases]
    assert collections.Counter(actual)==collections.Counter(expected),(set(actual)^expected,len(actual),len(expected))
    (OUT/'jvm.json').write_text(json.dumps({'executed_methods':len(actual),'passed':True},indent=2)+'\n')
def instrumentation(text,expected):
    bundle={};starts=[];finishes=[];codes=[]
    for line in text.splitlines():
        if line.startswith('INSTRUMENTATION_STATUS: '):
            value=line[len('INSTRUMENTATION_STATUS: '):]
            if '=' in value:
                k,v=value.split('=',1);bundle[k]=v
        elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
            code=int(line.split(':',1)[1]);key=(bundle.get('class'),bundle.get('test'))
            assert key in expected,(code,bundle)
            if code==1: starts.append(key)
            else: finishes.append((key,code))
            bundle={}
        elif line.startswith('INSTRUMENTATION_CODE: '): codes.append(int(line.split(':',1)[1]))
    assert codes==[-1] and 'INSTRUMENTATION_FAILED' not in text and 'INSTRUMENTATION_RESULT: shortMsg=' not in text,codes
    assert collections.Counter(starts)==collections.Counter(expected),(set(starts)^expected,len(starts),len(expected))
    assert collections.Counter(k for k,_ in finishes)==collections.Counter(expected)
    assert all(code==0 for _,code in finishes),[x for x in finishes if x[1]!=0]
    return len(finishes)
mode=sys.argv[1]
if mode=='js':
    files=sorted(Path('app/src/test/js').rglob('*.test.js'));assert files
    for p in files:
        result=subprocess.run(['node',str(p)],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=180)
        (OUT/(p.name+'.txt')).write_text(result.stdout);assert result.returncode==0,str(p)
    (OUT/'javascript.json').write_text(json.dumps({'files':[str(p) for p in files],'passed':True},indent=2)+'\n')
elif mode=='unit': units();scope()
elif mode=='android':
    expected=inventory('app/src/androidTest/java')
    (OUT/'android-expected.json').write_text(json.dumps(sorted(expected),indent=2)+'\n')
    devices=subprocess.check_output(['adb','devices'],text=True)
    serials=[s.split()[0] for s in devices.splitlines() if len(s.split())==2 and s.split()[1]=='device'];assert len(serials)==1,devices
    adb=['adb','-s',serials[0]]
    for directory in ['app/build/outputs/apk/debug','app/build/outputs/apk/androidTest/debug']:
        apks=list(Path(directory).glob('*.apk'));assert len(apks)==1,(directory,apks)
        subprocess.run(adb+['install','-r','-t',str(apks[0])],check=True,timeout=120)
    log=OUT/'android-instrumentation.txt'
    with log.open('w') as output:
        result=subprocess.run(adb+['shell','am','instrument','-w','-r','iecxiv.meshfabric.test/androidx.test.runner.AndroidJUnitRunner'],stdout=output,stderr=subprocess.STDOUT,text=True,timeout=3000)
    assert result.returncode==0,result.returncode
    count=instrumentation(log.read_text(),expected)
    (OUT/'android.json').write_text(json.dumps({'executed_methods':count,'passed':True},indent=2)+'\n')
elif mode=='verify':
    units();scope()
    expected=inventory('app/src/androidTest/java');count=instrumentation((OUT/'android-instrumentation.txt').read_text(),expected)
    javascript=json.loads((OUT/'javascript.json').read_text());assert javascript['passed'] is True
    assert javascript['files']==[str(p) for p in sorted(Path('app/src/test/js').rglob('*.test.js'))]
    assert json.loads((OUT/'android.json').read_text())=={'executed_methods':count,'passed':True}
    dev=subprocess.check_output(['git','rev-parse','origin/dev'],text=True).strip();assert dev==DEV,dev
    subprocess.run(['git','merge-base','--is-ancestor',DEV,'HEAD'],check=True)
    tree=subprocess.check_output(['git','write-tree'],text=True).strip()
    (OUT/'integration-ready.json').write_text(json.dumps({'versionName':'1.3.2','tested_tree':tree,'dev_base':DEV,'android_methods':count,'all_suites_executed':True,'physical_visual_review_verified':False,'merged_into_dev':False},indent=2)+'\n')
else: raise AssertionError(mode)
