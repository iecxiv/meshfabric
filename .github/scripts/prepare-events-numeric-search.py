from pathlib import Path
import json,subprocess,sys,runpy
BASE='57c5caf03960716e4313d548dc210081220dfb1e'
mode=sys.argv[1] if len(sys.argv)>1 else '--prepare'
if mode in ('--build','--verify'):
    import yaml
    ref=BASE+':.github/workflows/publish-events-header-dates.yml'
    assert subprocess.check_output(['git','rev-parse',ref],text=True).strip()=='7bceebb882b902f341a29551465b66c74366d1da'
    old=yaml.safe_load(subprocess.check_output(['git','show',ref],text=True))
    name='Compile execute JVM lint and prepare exact Android runner' if mode=='--build' else 'Verify exact execution source scope and untouched details dialog'
    matches=[s for s in old['jobs']['validate']['steps'] if s.get('name')==name];assert len(matches)==1
    shell=matches[0]['run']
    if mode=='--verify':
        assert shell.count('len(expected)==11')==1
        shell=shell.replace('len(expected)==11','len(expected)==12')
    subprocess.run(['bash','-euo','pipefail','-c',shell],check=True)
    sys.exit(0)
assert mode=='--prepare'
runpy.run_path('.github/scripts/prepare-events-header-dates.py')
p=Path('app/src/androidTest/java/iecxiv/meshfabric/ui/management/LogNumericMessageUiTest.kt')
assert subprocess.check_output(['git','hash-object',str(p)],text=True).strip()=='b95315b429c5c62cc924cdec6e270fbd751596b2'
s=p.read_text()
def replace(old,new):
    global s
    assert s.count(old)==1,old
    s=s.replace(old,new,1)
replace('val state=snapshot();val query=mutableStateOf("")','val state=snapshot()')
replace('compose.setContent {MaterialTheme {ServerEventsPanel(state,query.value,{query.value=it},true,{})}}','compose.setContent {MaterialTheme {SearchableEventsTestPanel(state)}}\n        compose.onNodeWithTag("log-events-search-toggle").performClick()')
assert s.count('@Test')==2
for assertion in ['compose.onNode(hasText("123") and hasSetTextAction().not()).assertIsDisplayed()','compose.onNodeWithText("text message").assertDoesNotExist()','compose.onNodeWithTag("log-events-no-match").assertDoesNotExist()']:
    assert s.count(assertion)==1
p.write_text(s);subprocess.run(['git','add','--',str(p)],check=True)
expected=Path('validation/events-header-expected.json');paths=json.loads(expected.read_text());assert len(paths)==11 and str(p) not in paths
paths.append(str(p));expected.write_text(json.dumps(sorted(paths),indent=2)+'\n')
assert len(paths)==12 and set(subprocess.check_output(['git','diff','--cached','--name-only'],text=True).splitlines())==set(paths)
assert not subprocess.check_output(['git','diff','--name-only'],text=True).strip()
