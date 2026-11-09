package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class) class ProfilePolishPresentationTest {
    @get:Rule val compose=createComposeRule()
    private fun group(id:String,name:String,desc:String="")=JSONObject().put("_id",id).put("name",name).put("desc",desc)
    @Test fun namesReplaceIdsAndDescriptionsDisambiguateOnlyWhenUnique() {
        val a=group("mesh//a","Oficina","Madrid");val b=group("mesh//b","Oficina","Barcelona")
        val all=listOf(a,b)
        assertEquals("Oficina · Madrid",profileGroupChoiceLabel(a,all,true))
        assertTrue(profileGroupChoiceUsable(a,all));assertTrue(profileGroupChoiceUsable(b,all))
        assertEquals("Oficina",profileGroupChoiceLabel(a,listOf(a),true))
        assertFalse(profileGroupChoiceLabel(a,all,true).contains("mesh//"))
        assertFalse(profileGroupChoiceUsable(a,listOf(a,group("mesh//c","Oficina","Madrid"))))
        assertFalse(profileGroupChoiceUsable(group("mesh//empty",""),emptyList()))
        assertFalse(profileGroupChoiceUsable(null,all))
        assertFalse(profileGroupChoiceUsable(group("mesh//x","Oficina"),listOf(a,group("mesh//x","Oficina"))))
    }
    @Test fun disclosureStillExpandsAndCollapsesWithItsTextTarget() {
        compose.setContent {MaterialTheme {Column {ProfileDisclosure("Información adicional") {Text("Detalle visible")}}}}
        compose.onNodeWithText("Detalle visible").assertDoesNotExist()
        compose.onNodeWithText("Información adicional").performClick()
        compose.onNodeWithText("Detalle visible").assertExists()
        compose.onNodeWithText("Información adicional").performClick()
        compose.onNodeWithText("Detalle visible").assertDoesNotExist()
    }
}
