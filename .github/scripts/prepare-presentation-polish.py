FILES={
'app/src/main/java/iecxiv/meshfabric/ui/management/PreviousLoginPresentation.kt':r"""package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import org.json.JSONArray
import org.json.JSONObject
internal fun previousLoginPalette(code:Any?,dark:Boolean,neutral:Color,foreground:Color):Pair<Color,Color> = when(accountLoginOutcome(code)) {
    true->(if(dark) Color(0xFF173B25) else Color(0xFFE2F4E7)) to (if(dark) Color(0xFFC5F2D1) else Color(0xFF163B24))
    false->(if(dark) Color(0xFF4A2025) else Color(0xFFFCE5E7)) to (if(dark) Color(0xFFFFDADD) else Color(0xFF64232B))
    null->neutral to foreground
}
@Composable internal fun PreviousLoginEntry(item:JSONObject,date:String,details:String,es:Boolean,modifier:Modifier=Modifier) {
    val scheme=MaterialTheme.colorScheme
    val colors=previousLoginPalette(item.opt("m"),scheme.surface.luminance()<0.5f,scheme.surfaceVariant,scheme.onSurfaceVariant)
    Column(modifier.fillMaxWidth().background(colors.first),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(date,style=MaterialTheme.typography.titleSmall,color=colors.second)
        Text(loginHistoryLabel(item.opt("m"),es)+" · "+details,style=MaterialTheme.typography.bodySmall,color=colors.second)
        HorizontalDivider(color=scheme.outlineVariant)
    }
}
@Composable internal fun OwnPreviousLoginsDialog(name:String,actor:String,events:JSONArray?,busy:Boolean,failed:Boolean,es:Boolean,onRefresh:()->Unit,onDismiss:()->Unit,error:String="") {
    fun t(a:String,b:String)=if(es) a else b
    val close={if(!busy) onDismiss()}
    ManagementDialog(onDismissRequest=close,modifier=Modifier.widthIn(max=640.dp).fillMaxWidth(0.94f).testTag("own-previous-logins-dialog"),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn,usePlatformDefaultWidth=false),title={Text(t("Inicios de sesión anteriores","Previous logins"))},text={
        Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*0.68f).dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(name,style=MaterialTheme.typography.titleSmall)
            ProfileDisclosure(t("Identificador","Identifier")) {Text(actor,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Text(t("Historial de accesos web registrado por el servidor; puede no incluir los accesos de la app.","Server-recorded web sign-ins; app sign-ins may not be included."),style=MaterialTheme.typography.bodySmall)
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(failed) Text(t("Historial no disponible o respuesta incompatible.","History unavailable or unsupported response."),color=MaterialTheme.colorScheme.error)
            if(events!=null && events.length()==0) Text(t("Sin registros.","No records."))
            if(events!=null) for(i in 0 until minOf(events.length(),200)) events.optJSONObject(i)?.let {event->
                val args=event.optJSONArray("a")
                val details=args?.let {(0 until it.length()).joinToString(" · ") {index->it.optString(index)}}.orEmpty()
                PreviousLoginEntry(event,userDate(event.opt("t")).ifBlank {t("Fecha no disponible","Date unavailable")},details,es)
            }
            TextButton(onClick=onRefresh,enabled=!busy) {Text(t("Actualizar","Refresh"))}
            if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={},dismissButton={Row {TextButton(onClick=close) {Text(t("Volver","Back"))};TextButton(onClick=close) {Text(t("Cerrar","Close"))}}})
}
""",
'app/src/main/java/iecxiv/meshfabric/ui/management/ServerStatisticsHeader.kt':r"""package iecxiv.meshfabric.ui.management
import androidx.compose.runtime.Composable
internal fun serverStatisticsRefreshEnabled(tab:Int,ready:Boolean,busy:Boolean)=tab==1 && ready && !busy
@Composable internal fun ServerStatisticsHeader(tab:Int,ready:Boolean,busy:Boolean,header:@Composable (@Composable ()->Unit)->Unit,onRefresh:()->Unit) {
    header {if(tab==1) HeaderRefreshButton(serverStatisticsRefreshEnabled(tab,ready,busy),"server-statistics-refresh",onRefresh)}
}
""",
'app/src/test/java/iecxiv/meshfabric/ui/management/PresentationPolishPolicyTest.kt':r"""package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class PresentationPolishPolicyTest {
    private fun row(name:String="surface",group:String="agent")=ServerEventRecord("manual","event","","node//x",name,1L,groupName=group)
    @Test fun deviceAndGroupHaveOneInsertedSpace() {assertEquals("surface (agent)",row().deviceLabel())}
    @Test fun absentGroupDoesNotAddWhitespaceOrParentheses() {assertEquals("surface",row(group="").deviceLabel())}
    @Test fun missingDeviceDoesNotInventAName() {assertEquals("",row(name="").deviceLabel())}
    @Test fun historyDateMatchesSecondsAndMilliseconds() {assertEquals(userDate(1700000000L),userDate(1700000000000L));assertTrue(userDate(1700000000L).isNotBlank())}
    @Test fun invalidHistoryDateIsNotInvented() {assertEquals("",userDate(null));assertEquals("",userDate("invalid"));assertEquals("",userDate(-1L))}
    @Test fun statisticsRefreshIsOnlyAvailableForReadyIdleStatistics() {
        for(tab in listOf(0,2,3)) assertFalse(serverStatisticsRefreshEnabled(tab,true,false))
        assertTrue(serverStatisticsRefreshEnabled(1,true,false));assertFalse(serverStatisticsRefreshEnabled(1,false,false));assertFalse(serverStatisticsRefreshEnabled(1,true,true))
    }
}
""",
'app/src/androidTest/java/iecxiv/meshfabric/ui/management/PresentationPolishUiTest.kt':r"""package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class PresentationPolishUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun cardUsesSpaceAndDetailsShowTheUnadornedName() {
        val row=ServerEventRecord("manual","entry","operator","node//x","surface",1L,meshId="mesh//x",groupName="agent")
        compose.setContent {MaterialTheme {ServerEventsPanel(ServerEventsSnapshot(ServerEventsPhase.READY,listOf(row)),"",{},true,{})}}
        compose.onNodeWithTag("log-event-device-0",useUnmergedTree=true).assertTextEquals("surface (agent)")
        compose.onNodeWithTag("log-event-row-0").performClick();compose.onNodeWithTag("log-event-details-device").assertTextEquals("surface");compose.onNodeWithText("agent").assertExists()
    }
    @Test fun coloredEntryKeepsTheReferenceRowGeometry() {
        val event=JSONObject().put("m",107)
        compose.setContent {MaterialTheme {Row {
            Column(Modifier.weight(1f).testTag("reference"),verticalArrangement=Arrangement.spacedBy(10.dp)) {Text("date",style=MaterialTheme.typography.titleSmall);Text(loginHistoryLabel(107,false)+" · details",style=MaterialTheme.typography.bodySmall);HorizontalDivider()}
            Box(Modifier.weight(1f)) {PreviousLoginEntry(event,"date","details",false,Modifier.testTag("colored"))}
        }}}
        assertEquals(compose.onNodeWithTag("reference").fetchSemanticsNode().size.height,compose.onNodeWithTag("colored").fetchSemanticsNode().size.height)
    }
    @Test fun loginHistoryColorsMatchAccountInBothThemes() {
        val neutral=Color.Gray;val foreground=Color.White
        assertEquals(Color(0xFFE2F4E7),previousLoginPalette(107,false,neutral,foreground).first)
        assertEquals(Color(0xFF173B25),previousLoginPalette(107,true,neutral,foreground).first)
        for(code in listOf(108,109,110)) {assertEquals(Color(0xFFFCE5E7),previousLoginPalette(code,false,neutral,foreground).first);assertEquals(Color(0xFF4A2025),previousLoginPalette(code,true,neutral,foreground).first)}
        assertEquals(neutral to foreground,previousLoginPalette(999,false,neutral,foreground));assertEquals(neutral to foreground,previousLoginPalette(null,true,neutral,foreground))
    }
    @Test fun ownHistoryDialogUsesTheReferenceTitleIdentityAndRows() {
        compose.setContent {MaterialTheme {OwnPreviousLoginsDialog("Alice","user//alice",JSONArray().put(JSONObject().put("m",107).put("t",1700000000L)),false,false,false,{},{})}}
        compose.onNodeWithTag("own-previous-logins-dialog").assertIsDisplayed();compose.onNodeWithText("Previous logins").assertExists();compose.onNodeWithText("Alice").assertExists();compose.onNodeWithText("Back").assertExists();compose.onNodeWithText("Close").assertExists()
        compose.onNodeWithText("Successful sign-in",substring=true).assertExists()
    }
    @Test fun statisticsRefreshTracksOnlyItsTabAndCurrentPeriod() {
        val tab=mutableIntStateOf(0);val period=mutableIntStateOf(3);val busy=mutableStateOf(false);val ready=mutableStateOf(true);val requests=mutableListOf<Int>()
        compose.setContent {MaterialTheme {ServerStatisticsHeader(tab.intValue,ready.value,busy.value,header={actions->Row {actions()}},onRefresh={requests.add(period.intValue)})}}
        compose.onNodeWithTag("server-statistics-refresh").assertDoesNotExist()
        compose.runOnIdle {tab.intValue=1;period.intValue=24};compose.onNodeWithTag("server-statistics-refresh").performClick();compose.runOnIdle {assertEquals(listOf(24),requests);busy.value=true}
        compose.onNodeWithTag("server-statistics-refresh").assertIsNotEnabled();compose.runOnIdle {busy.value=false;ready.value=false};compose.onNodeWithTag("server-statistics-refresh").assertIsNotEnabled()
        for(index in listOf(0,2,3)) {compose.runOnIdle {tab.intValue=index};compose.onNodeWithTag("server-statistics-refresh").assertDoesNotExist()}
    }
}
"""
}
from pathlib import Path
import ast,json,subprocess
root=Path('app/src/main/java/iecxiv/meshfabric/ui/management');changed=set()
def edit(path,old,new):
    p=Path(path);s=p.read_text();assert s.count(old)==1,(str(p),old);p.write_text(s.replace(old,new,1));changed.add(str(p))
for name,sha in [('ServerEventIdentity.kt','658d9b00440eb36dc99fd93028805f9300e3cd99'),('NativeAccount.kt','95455524d930affbc6c07ca6ecf5dbb408d255a8'),('UserAdministration.kt','e3bb572c8205cc55312f02d07a49c2a4e2e83ad6'),('MyServerScreen.kt','d5853da782e5744edf625625c468fd591ab2d846'),('MeshFabricHome.kt','30b18ba4de1524ce4d4f8fbbae26554caf8e69e3')]:
    assert subprocess.check_output(['git','hash-object',str(root/name)],text=True).strip()==sha,name
for path,content in FILES.items():
    p=Path(path);assert not p.exists(),path;p.write_text(content);changed.add(path)
edit(root/'ServerEventIdentity.kt','else "($groupName)"','else " ($groupName)"')
edit(root/'ServerEventDetails.kt','Text(row.deviceLabel().ifBlank {missing},Modifier.testTag("log-event-details-device")','Text(row.nodeName.ifBlank {missing},Modifier.testTag("log-event-details-device")')
p=root/'UserAdministration.kt';original=p.read_text();s=original
assert 'Modifier.widthIn(max=640.dp).fillMaxWidth(0.94f)' in s and '(LocalConfiguration.current.screenHeightDp*0.68f).dp' in s
assert 'confirmButton={ if(mode!="history")' in s and 'Text(t("Volver","Back"))' in s and 'Text(t("Cerrar","Close"))' in s
start=s.index('Text(userDate(event.opt("t"))');end=s.index('\n}',start);old=s[start:end]
assert old.count('\n')==2 and 'MaterialTheme.typography.titleSmall' in old and 'MaterialTheme.typography.bodySmall' in old and 'HorizontalDivider' in old
new='PreviousLoginEntry(event,userDate(event.opt("t")).ifBlank { t("Fecha no disponible","Date unavailable") },event.optJSONArray("a")?.let {args->(0 until args.length()).joinToString(" · ") {args.optString(it)}}.orEmpty(),es)'
s=s[:start]+new+s[end:];assert s.count('private fun userDate(')==1
s=s.replace('private fun userDate(','internal fun userDate(',1)
assert s.replace(new,old,1).replace('internal fun userDate(','private fun userDate(',1)==original
p.write_text(s);changed.add(str(p))
p=root/'NativeAccount.kt';original=p.read_text()
needle='ManagementDialog(onDismissRequest={ if(!state.busy) { wire.clearResult(); mode=null } },properties='
prefix='if(selected==3) OwnPreviousLoginsDialog(state.user?.optString("name").orEmpty(),actor,reply?.optJSONArray("events"),state.busy,state.status in setOf("incompatible","rejected","unknown","malformed","timeout"),es,onRefresh={run("previousLogins")},onDismiss={wire.clearResult();mode=null},error=feedback) else '
assert original.count(needle)==1 and prefix not in original
s=original.replace(needle,prefix+needle,1);assert s.replace(prefix,'',1)==original
p.write_text(s);changed.add(str(p))
p=root/'MyServerScreen.kt';original=p.read_text();reader=original[original.index('private class ServerReader('):original.index('@OptIn(ExperimentalMaterial3Api::class)')]
edit(p,'MyServerScreen(source:MeshCentralClient?,modifier:Modifier=Modifier)','MyServerScreen(source:MeshCentralClient?,modifier:Modifier=Modifier,header:@Composable (@Composable ()->Unit)->Unit={})')
edit(p,'    Column(modifier) {\n        ServerSectionTabs','    fun refreshHistory(period:Int) {\n        if(period in serverHistoryPeriods && state.ready && state.pending.isEmpty()) {preferences.saveHours(period);reader?.request("servertimelinestats",period)}\n    }\n    Column(modifier) {\n        ServerStatisticsHeader(tab,state.ready,state.pending.isNotEmpty(),header) {refreshHistory(state.hours)}\n        ServerSectionTabs')
old="""                1->HistoryPanel(state.history,state.hours,state.ready,state.pending.isNotEmpty()) { period->
                    if(period in serverHistoryPeriods && state.ready && state.pending.isEmpty()) {
                        preferences.saveHours(period)
                        reader?.request("servertimelinestats",period)
                    }
                }"""
edit(p,old,'                1->HistoryPanel(state.history,state.hours,state.ready,state.pending.isNotEmpty(),onLoad=::refreshHistory)')
edit(p,'            TextButton(onClick={ onLoad(hours) },enabled=ready && !busy) { Text(if(es) "Actualizar" else "Refresh") }\n','')
assert reader in p.read_text()
edit(root/'MeshFabricHome.kt','topBar={ if(page==2 || page==4) MeshFabricAppBar(viewModel,onOpenSettings) }','topBar={ if(page==4) MeshFabricAppBar(viewModel,onOpenSettings) }')
edit(root/'MeshFabricHome.kt','MyServerScreen(viewModel.meshClient,Modifier.fillMaxSize())','MyServerScreen(viewModel.meshClient,Modifier.fillMaxSize(),header={actions->MeshFabricAppBar(viewModel,onOpenSettings,actions={actions()})})')
tests=Path('app/src/androidTest/java/iecxiv/meshfabric/ui/management')
p=Path('app/src/test/java/iecxiv/meshfabric/ui/management/EventContentTest.kt');s=p.read_text();assert s.count('"surface(agent)"')==2 and s.count('"old surface(old agent)"')==1
p.write_text(s.replace('"surface(agent)"','"surface (agent)"').replace('"old surface(old agent)"','"old surface (old agent)"'));changed.add(str(p))
edit(tests/'EventContentUiTest.kt','onNodeWithTag("log-event-device-0",useUnmergedTree=true).assertTextEquals("surface(agent)")','onNodeWithTag("log-event-device-0",useUnmergedTree=true).assertTextEquals("surface (agent)")')
p=tests/'EventContentUiTest.kt';s=p.read_text();old='onNodeWithTag("log-event-details-device").assertTextEquals("surface(agent)")';assert s.count(old)==2
p.write_text(s.replace(old,'onNodeWithTag("log-event-details-device").assertTextEquals("surface")'));changed.add(str(p))
edit(tests/'HeaderRefreshUiTest.kt','onNodeWithTag("log-event-details-device").assertTextEquals("surface(agent)")','onNodeWithTag("log-event-details-device").assertTextEquals("surface")')
p=Path('docs/log.md');p.write_text(p.read_text()+'\nPresentation refinement: event cards use device (group) with an inserted space; the details Device value is the unadorned node name, with Group separate. User-edit login history retains its existing dialog modifier, properties, content maximum height, typography, spacing and navigation, adding only row colors from the account palette. The account previous-logins view alone uses a dedicated read-only dialog with the user-editor reference geometry (max 640 dp, 94% width, 68% screen content-height cap, 10 dp gaps), date/message/divider hierarchy and Back/Close actions. All other account dialog code and wire behavior are unchanged. Both login views share the colored entry renderer and existing date formatter, use server-order records, preserve the 200-row display bound, and keep unknown outcomes neutral. Server Statistics moves its existing refresh callback into the app bar only on tab 1, preserving selected hours and busy/connection gating; period selectors, charts, exports, reader and other server actions are unchanged. Validation is focused and builds/installs APKs once; physical visual review and server verification remain required.\n');changed.add(str(p))
assert len(changed)==14,changed
subprocess.run(['git','add','--',*sorted(changed)],check=True)
assert set(subprocess.check_output(['git','diff','--cached','--name-only'],text=True).splitlines())==changed
Path('validation/presentation-polish-expected.json').write_text(json.dumps(sorted(changed),indent=2)+'\n')
source=Path('.github/scripts/validate-trace-sources-focused.py');assert subprocess.check_output(['git','hash-object',str(source)],text=True).strip()=='dc9647024d8532acaab5c0ba41cf3186216f25c0'
v=source.read_text();start=v.index('ANDROID={');end=v.index('\ndef scope():',start)
v=v[:start]+"""ANDROID={
'PresentationPolishUiTest':{'cardUsesSpaceAndDetailsShowTheUnadornedName','coloredEntryKeepsTheReferenceRowGeometry','loginHistoryColorsMatchAccountInBothThemes','ownHistoryDialogUsesTheReferenceTitleIdentityAndRows','statisticsRefreshTracksOnlyItsTabAndCurrentPeriod'},
'EventContentUiTest':{'cardHidesIdentifiersWhileDetailsKeepThem','longCommandIsNotTruncatedInReadOnlyDetails','groupAndCommandAreSearchable','inventoryChangeRemovesOpenResolvedDetails'},
'AccountPresentationPolicyTest':{'unknownLoginEventsAreNotMarkedInvalid'}}
JVM=['PresentationPolishPolicyTest','LoginHistoryLabelsTest','EventContentTest','ServerHistoryTest','ServerHistoryCsvSelectionTest']
"""+v[end:]
v=v.replace('validation/trace-focused','validation/presentation-polish').replace('validation/trace-sources-expected.json','validation/presentation-polish-expected.json').replace('len(expected)==5','len(expected)==14')
v=v.replace("assert sum(c.get('classname')==PREFIX+'TraceSourcesPolicyTest' for c in cases)==8","assert sum(c.get('classname')==PREFIX+'PresentationPolishPolicyTest' for c in cases)==6")
v=v.replace('len(results)==2','len(results)==3').replace("for r in results)==7","for r in results)==10").replace("'android_classes':2,'android_methods':7","'android_classes':3,'android_methods':10")
old="            process=subprocess.run(adb+['shell','am','instrument','-w','-r','-e','class',classname,"
new="            test_filter=classname+'#unknownLoginEventsAreNotMarkedInvalid' if name=='AccountPresentationPolicyTest' else classname\n            process=subprocess.run(adb+['shell','am','instrument','-w','-r','-e','class',test_filter,"
assert v.count(old)==1;v=v.replace(old,new,1)
assert "for r in results)==10" in v
ast.parse(v);Path('validation/run-presentation-polish.py').write_text(v)
