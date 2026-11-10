from pathlib import Path
import bisect,re

def masked(source):
    out=list(source);i=0;n=len(source)
    def blank(a,b):
        for k in range(a,b):
            if out[k]!='\n': out[k]=' '
    while i<n:
        if source[i]=='`':
            end=source.find('`',i+1);assert end!=-1,'Unclosed identifier';i=end+1
        elif source.startswith('//',i):
            end=source.find('\n',i);end=n if end==-1 else end;blank(i,end);i=end
        elif source.startswith('/*',i):
            start=i;i+=2;depth=1
            while i<n and depth:
                if source.startswith('/*',i): depth+=1;i+=2
                elif source.startswith('*/',i): depth-=1;i+=2
                else: i+=1
            assert depth==0,'Unclosed comment';blank(start,i)
        elif source.startswith('"""',i):
            end=source.find('"""',i+3);assert end!=-1,'Unclosed raw string';blank(i,end+3);i=end+3
        elif source[i] in ('"',"'"):
            start=i;quote=source[i];i+=1
            while i<n:
                if source[i]=='\\': i+=2
                elif source[i]==quote: i+=1;break
                else: i+=1
            assert i<=n and source[i-1]==quote,'Unclosed literal';blank(start,i)
        else: i+=1
    return ''.join(out)

def source_tests(source):
    clean=masked(source)
    tokens=list(re.finditer(r'`[^`\n]+`|[\w$]+|::|->|[^\s]',clean))
    values=[t.group() for t in tokens];positions=[t.start() for t in tokens]
    pairs={};stack=[];closing={'}':'{',')':'(',']':'['}
    for i,t in enumerate(values):
        if t in ('{','(', '['): stack.append((t,i))
        elif t in closing:
            assert stack and stack[-1][0]==closing[t],('Unbalanced delimiter',t)
            _,start=stack.pop();pairs[start]=i
    assert not stack,'Unclosed delimiters'
    classes=[]
    for i,t in enumerate(values):
        if t!='class' or i+1>=len(values) or (i and values[i-1] in ('.','::')): continue
        name=values[i+1]
        if not re.fullmatch(r'[\w$]+|`[^`]+`',name): continue
        j=i+2;body=None
        while j<len(values):
            if values[j] in ('(', '['): j=pairs[j]+1;continue
            if values[j]=='{': body=j;break
            if values[j] in (';','class','interface','object','fun','val','var','package','import','}'): break
            j+=1
        if body is not None: classes.append({'name':name.strip('`'),'start':tokens[body].start(),'end':tokens[pairs[body]].end()})
    package=re.search(r'(?m)^package\s+([\w.]+)',clean)
    found=set()
    for c in sorted(classes,key=lambda c:c['start']):
        parents=[p for p in classes if p['start']<c['start'] and c['end']<p['end']]
        parent=min(parents,key=lambda p:p['end']-p['start']) if parents else None
        c['qualified']=(parent['qualified']+'$' if parent else (package.group(1)+'.' if package else ''))+c['name']
    annotations=list(re.finditer(r'@(?:(?:org\.junit|kotlin\.test)\.)?Test\b',clean))
    for annotation in annotations:
        i=bisect.bisect_left(positions,annotation.end())
        if i<len(values) and values[i]=='(': i=pairs[i]+1
        while i<len(values) and values[i]=='@':
            i+=1
            assert i<len(values)
            i+=1
            while i+1<len(values) and values[i]=='.': i+=2
            if i<len(values) and values[i]=='(': i=pairs[i]+1
        while i<len(values) and values[i] in ('public','private','protected','internal','final','open','override','static','suspend','inline'): i+=1
        assert i+2<len(values) and values[i] in ('fun','void') and values[i+2]=='(',('Unsupported test declaration',clean[annotation.start():annotation.start()+180])
        method=values[i+1].strip('`')
        owners=[c for c in classes if c['start']<annotation.start()<c['end']]
        assert owners,('Test without declaring class',method)
        owner=min(owners,key=lambda c:c['end']-c['start'])
        key=(owner['qualified'],method);assert key not in found,('Duplicate test',key);found.add(key)
    return found

def inventory(folder):
    expected=set()
    for p in sorted(Path(folder).rglob('*')):
        if p.suffix not in ('.kt','.java'): continue
        for key in source_tests(p.read_text()):
            assert key not in expected,('Duplicate across files',str(p),key)
            expected.add(key)
    assert expected,folder
    return expected
