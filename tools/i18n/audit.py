from pathlib import Path
import csv,json,re,sys,subprocess
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]
FORMAT=re.compile(r'%(?:(\d+)\$)?[-#+ 0,(<]*\d*(?:\.\d+)?([tT][A-Za-z]|[A-Za-z%])')
def signature(text):
    result=set();index=1;previous=1
    for match in FORMAT.finditer(text):
        kind=match[2]
        if kind in ('%','n'): continue
        number=int(match[1]) if match[1] else previous if '<' in match[0] else index
        if not match[1] and '<' not in match[0]: index+=1
        previous=number;result.add((number,kind))
    return sorted(result)
def leaves(kind,node):
    if kind=='string': return {'text':''.join(node.itertext()).strip()}
    return {(item.get('quantity') if kind=='plurals' else str(i)):''.join(item.itertext()).strip() for i,item in enumerate(node.findall('item'))}
def write_candidates(stream,rows):
    writer=csv.writer(stream,lineterminator="\n",quoting=csv.QUOTE_ALL)
    writer.writerow(['path','line','kind','literal','context'])
    writer.writerows([[str(cell).replace("\r","\\r").replace("\n","\\n") for cell in row] for row in rows])

def main():
    out=ROOT/'docs/i18n';out.mkdir(parents=True,exist_ok=True)
    issues=[];locales={};excluded=[];candidates=[]
    for locale,folder in [('default','values'),('en','values-en'),('es','values-es')]:
        values={}
        for path in sorted((ROOT/'app/src/main/res'/folder).glob('*.xml')):
            for node in ET.parse(path).getroot():
                kind='string' if node.tag=='item' and node.get('type')=='string' else node.tag
                if kind not in ('string','plurals','string-array') or not node.get('name'): continue
                key=(kind,node.get('name'))
                if key in values: issues.append({'kind':'duplicate','locale':locale,'resource':list(key),'path':str(path.relative_to(ROOT))})
                values[key]=node
        locales[locale]=values
    base={key:node for key,node in locales['default'].items() if node.get('translatable')!='false'}
    aliases=0
    for locale in ('en','es'):
        for key,node in sorted(base.items()):
            other=locales[locale].get(key)
            if other is None:
                issues.append({'kind':'missing','locale':locale,'resource':list(key)});continue
            expected=leaves(key[0],node);actual=leaves(key[0],other)
            if key[0]=='string-array' and len(expected)!=len(actual): issues.append({'kind':'array_length','locale':locale,'resource':list(key)})
            for slot,text in expected.items():
                translated=actual.get(slot,actual.get('other') if key[0]=='plurals' else None)
                if translated is None:
                    issues.append({'kind':'missing_item','locale':locale,'resource':list(key),'slot':slot});continue
                if text.startswith('@') or translated.startswith('@'): aliases+=1;continue
                if node.get('formatted')=='false' or other.get('formatted')=='false': continue
                if signature(text)!=signature(translated): issues.append({'kind':'format','locale':locale,'resource':list(key),'slot':slot,'default':signature(text),'localized':signature(translated)})
    token=re.compile(r'//[^\n]*|/\*[\s\S]*?\*/|"""[\s\S]*?"""|"(?:\\.|[^"\\])*"')
    js=re.compile(r'//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|\x27(?:\\.|[^\x27\\])*\x27|`[\s\S]*?`')
    scanned=0
    for path in sorted((ROOT/'app/src/main').rglob('*')):
        if not path.is_file() or path.suffix not in ('.kt','.java','.js','.html','.xml'): continue
        rel=str(path.relative_to(ROOT))
        if any(part in ('node_modules','vendor','third_party') for part in path.parts) or path.name.startswith('xterm') or path.name.endswith('.min.js') or path.stat().st_size>2000000:
            excluded.append(rel);continue
        if path.suffix=='.xml' and path.parent.name.startswith('values'): continue
        text=path.read_text(errors='replace');scanned+=1
        if path.suffix=='.xml':
            for node in ET.fromstring(text).iter():
                for attr,value in node.attrib.items():
                    if attr.rsplit('}',1)[-1] in ('text','textOn','textOff','hint','contentDescription','title','summary','dialogTitle') and value and not value.startswith(('@','?')):
                        candidates.append([rel,0,'xml_ui_attribute',value[:240],attr])
            continue
        for match in (js if path.suffix in ('.js','.html') else token).finditer(text):
            value=match[0]
            if value.startswith(('//','/*')) or not re.search(r'[A-Za-zÀ-ÿ]',value): continue
            line=text.count('\n',0,match.start())+1
            context=text[max(0,text.rfind('\n',0,match.start())+1):text.find('\n',match.end()) if text.find('\n',match.end())!=-1 else len(text)][:260]
            kind='direct_ui_candidate' if re.search(r'\b(Text|Icon|Toast|Snackbar|contentDescription|setText|setTitle)\b',context) else 'literal_candidate'
            candidates.append([rel,line,kind,value[:240],context])
    equal=[]
    for key in sorted(set(locales['en']) & set(locales['es']) & set(base)):
        if leaves(key[0],locales['en'][key])==leaves(key[0],locales['es'][key]): equal.append(list(key))
    docrefs=[]
    for path in [ROOT/'README.md',ROOT/'README_ES.md',*(ROOT/'docs').rglob('*.md')]:
        for line,value in enumerate(path.read_text(errors='replace').splitlines(),1):
            if re.search(r'versionName|app version|versión actual de la app',value,re.I): docrefs.append({'path':str(path.relative_to(ROOT)),'line':line,'text':value[:240]})
    summary={'audited_base_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'working_tree_candidate':True,'default_translatable_resources':len(base),'resource_issues':len(issues),'source_files_scanned':scanned,'literal_candidates':len(candidates),'aliases_requiring_review':aliases,'identical_en_es_resources':equal,'excluded_sources':excluded,'app_documentation_reference_candidates':docrefs,'all_ui_localized':None,'all_ui_externalized':False,'manual_review_required':True,'limitations':['Heuristic literals include technical and already bilingual text.','HTML body text, composed strings, translation meaning and runtime UI require manual review.','Remote output and user data are not automatically translated.']}
    (out/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n')
    (out/'resource-issues.json').write_text(json.dumps(issues,ensure_ascii=False,indent=2)+'\n')
    with (out/'candidates.csv').open('w',newline='') as stream:
        write_candidates(stream,candidates)
    print(json.dumps({'resource_issues':len(issues),'literal_candidates':len(candidates),'manual_review_required':True}))
    return 1 if issues else 0
if __name__=='__main__':
    if '--self-test' in sys.argv:
        assert signature('%s %d')==signature('%1$s %2$d')
        assert signature('%2$d %1$s')==signature('%1$s %2$d')
        assert signature('100%%')==[]
        assert signature('%1$d')!=signature('%1$s')
        import io
        sample=io.StringIO(newline='')
        write_candidates(sample,[['file.kt',1,'candidate','line\nnext','context\r\nwith trailing spaces  ']])
        serialized=sample.getvalue()
        assert '\r' not in serialized
        assert len(serialized.splitlines())==2
        assert not any(line.endswith((' ','\t')) for line in serialized.splitlines())
        decoded=list(csv.reader(io.StringIO(serialized)))
        assert len(decoded)==2 and len(decoded[1])==5
        assert decoded[1][3]=='line\\nnext'
        assert decoded[1][4]=='context\\r\\nwith trailing spaces  '

    else: raise SystemExit(main())
