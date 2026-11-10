from pathlib import Path
import subprocess,sys,runpy,ast
BASE='5ecbb9308f39635af1c7e71c94d2b4cfb0b0af29'
mode=sys.argv[1] if len(sys.argv)>1 else '--prepare'
if mode in ('--build','--verify'):
    import yaml
    old=yaml.safe_load(subprocess.check_output(['git','show',BASE+':.github/workflows/publish-events-selector-width.yml'],text=True))
    name='Build execute JVM lint and prepare independent Android classes' if mode=='--build' else 'Require executed regressions and exact scope'
    matches=[s for s in old['jobs']['validate']['steps'] if s.get('name')==name];assert len(matches)==1
    shell=matches[0]['run']
    if mode=='--verify':
        assert shell.count("'EventsSelectorWidthTest':3")==1
        shell=shell.replace("'EventsSelectorWidthTest':3","'EventsSelectorWidthTest':5").replace("'android_cases':22","'android_cases':24")
    subprocess.run(['bash','-euo','pipefail','-c',shell],check=True)
    if mode=='--build':
        p=Path('validation/run-log-class-isolation.py');s=p.read_text()
        anchor="'selectionDoesNotResizeTypeAndCountMenuMatchesItsHeader'}"
        assert s.count(anchor)==1
        s=s.replace(anchor,"'selectionDoesNotResizeTypeAndCountMenuMatchesItsHeader','portraitUsesTwoToOneSplit','largeLayoutIsCappedAt560Dp'}")
        s=s.replace('Expected: 22 distinct methods across 8 independent class runs.','Expected: 24 distinct methods across 8 independent class runs.')
        ast.parse(s);p.write_text(s)
    sys.exit(0)
p=Path('.github/scripts/prepare-events-selector-width.py')
assert subprocess.check_output(['git','hash-object',str(p)],text=True).strip()=='dc4fef4f9df13a4be1751a59f38a58ebb31632c1'
runpy.run_path(str(p))
p=Path('app/src/main/java/iecxiv/meshfabric/ui/management/ServerEventsControls.kt');s=p.read_text()
def replace(old,new):
    global s
    assert s.count(old)==1,old
    s=s.replace(old,new,1)
replace('import androidx.compose.ui.text.AnnotatedString\nimport androidx.compose.ui.text.rememberTextMeasurer\nimport androidx.compose.ui.platform.LocalDensity','import androidx.compose.foundation.clickable\nimport androidx.compose.ui.semantics.Role')
replace('@OptIn(ExperimentalLayoutApi::class)\n','')
start=s.index('    val measurer=rememberTextMeasurer()')
end=s.index('                EventFilterButton(labels[selection.type.ordinal]',start)
s=s[:start]+"""    Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
    BoxWithConstraints(Modifier.widthIn(max=560.dp).fillMaxWidth().testTag("log-event-controls")) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            BoxWithConstraints(Modifier.weight(2f)) {
                val typeWidth=maxWidth
"""+s[end:]
replace('            Box(Modifier.width(countWidth)) {','            BoxWithConstraints(Modifier.weight(1f)) {\n                val countWidth=maxWidth')
replace('    }\n    if(selection.limit==null)','    }}\n    if(selection.limit==null)')
start=s.index('                    ServerEventType.entries.forEach')
end=s.index('\n                }',start)
s=s[:start]+"""                    ServerEventType.entries.forEach {type->
                        val key=type.wire.ifBlank {"all"}
                        EventFilterOption(labels[type.ordinal],"log-event-type-$key","log-event-type-text-$key",serverEventImage(type)) {typesOpen=false;onSelection(selection.copy(type=type))}
                    }"""+s[end:]
start=s.index('                    serverEventLimitChoices.forEach')
end=s.index('\n                }',start)
s=s[:start]+"""                    serverEventLimitChoices.forEach {value->
                        val label=if(value==0) stringResource(R.string.log_event_unlimited) else stringResource(R.string.log_event_latest,value)
                        EventFilterOption(label,"log-event-limit-$value","log-event-limit-text-$value") {limitsOpen=false;onSelection(selection.copy(limit=value.takeUnless {it==0}))}
                    }"""+s[end:]
s+="""
@Composable private fun EventFilterOption(label:String,tag:String,textTag:String,icon:ImageVector?=null,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).testTag(tag).clickable(role=Role.Button,onClick=onClick).padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
        if(icon!=null) {
            Icon(icon,null,Modifier.size(20.dp).testTag(tag.replace("log-event-type-","log-event-type-icon-")))
            Spacer(Modifier.width(12.dp))
        }
        Text(label,Modifier.weight(1f).testTag(textTag),textAlign=if(icon==null) TextAlign.Center else TextAlign.Start,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium,softWrap=true,overflow=TextOverflow.Clip)
    }
}
"""
assert 'rememberTextMeasurer' not in s and 'FlowRow' not in s
p.write_text(s)
t=Path('app/src/androidTest/java/iecxiv/meshfabric/ui/management/EventsSelectorWidthTest.kt');text=t.read_text()
text=text.replace('import androidx.compose.foundation.layout.width','import androidx.compose.foundation.layout.width\nimport androidx.compose.foundation.layout.requiredWidth')
text=text.replace('private fun show(scale:Float=1f)','private fun show(scale:Float=1f,width:Int=320)')
text=text.replace('Box(Modifier.width(320.dp))','Box(Modifier.requiredWidth(width.dp))')
text=text.replace('assertFalse(results.single().hasVisualOverflow)','assertFalse("type=$key widthOverflow=${results.single().didOverflowWidth} heightOverflow=${results.single().didOverflowHeight} size=${results.single().size}",results.single().hasVisualOverflow)')
assert text.endswith('}\n')
text=text[:-2]+"""    @Test fun portraitUsesTwoToOneSplit() {
        show()
        val type=compose.onNodeWithTag("log-event-type").fetchSemanticsNode().size.width
        val count=compose.onNodeWithTag("log-event-count").fetchSemanticsNode().size.width
        assertEquals(type.toFloat(),2f*count,2f)
    }
    @Test fun largeLayoutIsCappedAt560Dp() {
        show(width=800)
        val group=compose.onNodeWithTag("log-event-controls").fetchSemanticsNode().size.width
        val type=compose.onNodeWithTag("log-event-type").fetchSemanticsNode().size.width
        val count=compose.onNodeWithTag("log-event-count").fetchSemanticsNode().size.width
        val scale=compose.density.density
        assertEquals(560f*scale,group.toFloat(),1f)
        assertEquals(type.toFloat(),2f*count,2f)
    }
}
"""
assert text.count('@Test')==5
t.write_text(text)
d=Path('docs/log.md');lines=d.read_text().splitlines()
assert sum(line.startswith('Events selector sizing uses the widest') for line in lines)==1
lines=[line for line in lines if not line.startswith('Events selector sizing uses the widest')]
d.write_text('\n'.join(lines)+'\nEvents selectors share one row with weights 2:1 after an 8 dp gap, inside a centered container capped at 560 dp. Each popup matches its own measured anchor width. Headers and options wrap without ellipses; option rows grow vertically with their text, including enlarged fonts. Previous overflow assertions remain mandatory, with per-type diagnostics. The former text-measured sizing and vertical control reflow are superseded.\n')
subprocess.run(['git','add','--',str(p),str(t),str(d)],check=True)
expected={str(p),str(t),str(d)}
assert set(subprocess.check_output(['git','diff','--cached','--name-only'],text=True).splitlines())==expected
