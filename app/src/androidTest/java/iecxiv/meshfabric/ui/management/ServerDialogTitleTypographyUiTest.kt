package iecxiv.meshfabric.ui.management
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import iecxiv.meshfabric.R
import iecxiv.meshfabric.ui.components.AppAlertDialog
import iecxiv.meshfabric.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class ServerDialogTitleTypographyUiTest {
    @get:Rule val compose=createComposeRule()
    private fun style(text:String):TextStyle {
        val layouts=mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text,useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        return layouts.single().layoutInput.style
    }
    private fun same(expected:TextStyle,actual:TextStyle) {
        assertEquals(expected.fontSize,actual.fontSize)
        assertEquals(expected.fontWeight,actual.fontWeight)
        assertEquals(expected.fontFamily,actual.fontFamily)
        assertEquals(expected.fontStyle,actual.fontStyle)
        assertEquals(expected.fontSynthesis,actual.fontSynthesis)
        assertEquals(expected.lineHeight,actual.lineHeight)
        assertEquals(expected.letterSpacing,actual.letterSpacing)
        assertEquals(expected.fontFeatureSettings,actual.fontFeatureSettings)
    }
    private fun verify(action:String,dark:Boolean,empty:Boolean=false,management:Boolean=false) {
        val reference=mutableStateOf(true)
        var title=""
        var managementExpected:TextStyle?=null
        compose.setContent {
            MeshCentralTheme(if(dark) AppThemeMode.DARK else AppThemeMode.LIGHT,dynamicColor=false) {
                val context=LocalContext.current
                title=if(management) "Other management dialog" else context.getString(if(action=="serverconfig") R.string.my_server_config else R.string.my_server_errors)
                managementExpected=MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.SemiBold)
                if(reference.value) AppAlertDialog(onDismissRequest={},title={Text("Reference title")},confirmButton={TextButton(onClick={}) {Text("OK")}})
                else if(management) ManagementDialog(onDismissRequest={},title={Text(title)},confirmButton={TextButton(onClick={}) {Text("OK")}})
                else {
                    val raw=if(empty) "{\"action\":\"servererrors\"}" else if(action=="serverconfig") "{\"action\":\"serverconfig\",\"data\":{}}" else "{\"action\":\"servererrors\",\"data\":\"Example log\"}"
                    ServerReadResultDialog(raw,action,{})
                }
            }
        }
        compose.waitForIdle()
        val expected=style("Reference title")
        compose.runOnIdle {reference.value=false}
        compose.waitForIdle()
        same(if(management) managementExpected!! else expected,style(title))
        if(!management && !empty) {
            compose.onNodeWithText("A+").performClick()
            same(expected,style(title))
        }
    }
    @Test fun configLight()=verify("serverconfig",false)
    @Test fun configDark()=verify("serverconfig",true)
    @Test fun errorsLight()=verify("servererrors",false)
    @Test fun errorsDark()=verify("servererrors",true)
    @Test fun absentLogLight()=verify("servererrors",false,true)
    @Test fun absentLogDark()=verify("servererrors",true,true)
    @Test fun otherManagementLightUnchanged()=verify("",false,management=true)
    @Test fun otherManagementDarkUnchanged()=verify("",true,management=true)
}
