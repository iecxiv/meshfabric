package iecxiv.meshfabric.ui.devices

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DeviceGeneralFieldDialogUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun pencilOpensEditorAndApplyChangesDraft() {
        val open=mutableStateOf(false)
        val draft=mutableStateOf("Original")
        compose.setContent {MaterialTheme {
            GeneralValueRow("Nombre",draft.value,true,"general_name","Editar nombre") {open.value=true}
            if(open.value) GeneralFieldDialog("Nombre",draft.value,false,true,true,{open.value=false}) {value->draft.value=value;open.value=false;true}
        }}
        compose.onNodeWithTag("general_name_edit").performClick()
        compose.onNodeWithTag("general_field_input").performTextReplacement("Nuevo")
        compose.onNodeWithTag("general_field_apply").performClick()
        compose.runOnIdle {assertEquals("Nuevo",draft.value)}
        compose.onNodeWithTag("general_field_input").assertDoesNotExist()
    }
    @Test fun cancelDoesNotApplyDraft() {
        val open=mutableStateOf(true)
        var applied=false
        compose.setContent {MaterialTheme {if(open.value) GeneralFieldDialog("Nombre","Original",false,true,true,{open.value=false}) {applied=true;true}}}
        compose.onNodeWithTag("general_field_input").performTextReplacement("Nuevo")
        compose.onNodeWithTag("general_field_cancel").performClick()
        compose.runOnIdle {assertFalse(applied)}
        compose.onNodeWithTag("general_field_input").assertDoesNotExist()
    }
    @Test fun unverifiedPermissionsDisablePencilAndApply() {
        val open=mutableStateOf(false)
        var applied=false
        compose.setContent {MaterialTheme {
            GeneralValueRow("Nombre","Original",false,"general_name","Editar nombre") {}
            if(open.value) GeneralFieldDialog("Nombre","Original",false,false,true,{open.value=false}) {applied=true;true}
        }}
        compose.onNodeWithTag("general_name_edit").assertIsNotEnabled()
        compose.runOnIdle {open.value=true}
        compose.onNodeWithTag("general_field_apply").assertIsNotEnabled()
        compose.runOnIdle {assertFalse(applied)}
    }
    @Test fun invalidNameRemainsInEditor() {
        compose.setContent {MaterialTheme {GeneralFieldDialog("Nombre","Original",false,true,true,{}) {value->runCatching {generalDraft(value,"")}.isSuccess}}}
        compose.onNodeWithTag("general_field_input").performTextReplacement("")
        compose.onNodeWithTag("general_field_apply").performClick()
        compose.onNodeWithTag("general_field_error").assertExists()
        compose.onNodeWithTag("general_field_input").assertExists()
    }
    @Test fun tagsAreValidatedBeforeApplying() {
        var applied=false
        compose.setContent {MaterialTheme {GeneralFieldDialog("Etiquetas","original",true,true,true,{}) {value->
            runCatching {generalDraft("Equipo",value)}.onSuccess {applied=true}.isSuccess
        }}}
        compose.onNodeWithTag("general_field_input").performTextReplacement("x".repeat(64))
        compose.onNodeWithTag("general_field_apply").performClick()
        compose.onNodeWithTag("general_field_error").assertExists()
        compose.runOnIdle {assertFalse(applied)}
    }
}
