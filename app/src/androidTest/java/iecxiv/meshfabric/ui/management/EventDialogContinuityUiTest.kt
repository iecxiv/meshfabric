package iecxiv.meshfabric.ui.management
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.*
import okio.ByteString
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class EventDialogContinuityUiTest {
    @get:Rule val compose=createComposeRule()
    private val row=ServerEventRecord("runcommands","Command received","Admin","node//pc","PC",1000L,mapOf("cmds" to "whoami"))
    private fun ready()=ServerEventsSnapshot(ServerEventsPhase.READY,listOf(row),selection=legacyServerEventsQuery)
    @Test fun detailsSurviveVerifiedPauseResumeAndCloseOnRevocation() {
        val state=mutableStateOf(ready())
        compose.setContent {MaterialTheme {ServerEventsPanel(state.value,"",{},true,{},Modifier.fillMaxSize())}}
        compose.onNodeWithTag("log-event-row-0").performClick()
        compose.onNodeWithTag("log-event-command").assertTextEquals("whoami")
        compose.runOnIdle {state.value=ServerEventsSnapshot(ServerEventsPhase.INVALIDATED,selection=legacyServerEventsQuery,lifecyclePause=true)}
        compose.onNodeWithTag("log-event-command").assertTextEquals("whoami")
        compose.runOnIdle {state.value=ServerEventsSnapshot(ServerEventsPhase.LOADING,selection=legacyServerEventsQuery,lifecyclePause=true)}
        compose.onNodeWithTag("log-event-details").assertExists()
        compose.runOnIdle {state.value=ready()}
        compose.onNodeWithTag("log-event-details").assertExists()
        compose.runOnIdle {state.value=ServerEventsSnapshot(ServerEventsPhase.INVALIDATED)}
        compose.onNodeWithTag("log-event-details").assertDoesNotExist()
    }
    @Test fun removedEventDoesNotKeepOldDetails() {
        val state=mutableStateOf(ready())
        compose.setContent {MaterialTheme {ServerEventsPanel(state.value,"",{},true,{},Modifier.fillMaxSize())}}
        compose.onNodeWithTag("log-event-row-0").performClick()
        compose.runOnIdle {state.value=ready().copy(records=emptyList())}
        compose.onNodeWithTag("log-event-details").assertDoesNotExist()
    }
    private class Socket:WebSocket {
        val sent=mutableListOf<String>()
        override fun request()=Request.Builder().url("https://example.invalid/").build()
        override fun queueSize()=0L
        override fun send(text:String):Boolean {sent.add(text);return true}
        override fun send(bytes:ByteString)=false
        override fun close(code:Int,reason:String?)=true
        override fun cancel() {}
    }
    @Test fun readerRevalidatesAuthorityBeforeKeepingPausedDetails() {
        val b=LogEventsBinding("https://example.invalid","user//admin","cookie",false,2,"auth")
        var listener:WebSocketListener?=null
        val socket=Socket()
        val reader=LogEventsReader(current={b},open={_,l->listener=l;socket})
        fun user(links:JsonObject)=JsonObject().apply {addProperty("action","userinfo");add("userinfo",JsonObject().apply {addProperty("_id",b.actor);addProperty("siteadmin",2);add("links",links)})}.toString()
        try {
            reader.load()
            listener!!.onMessage(socket,user(JsonObject()))
            val tag=JsonParser.parseString(socket.sent.last()).asJsonObject.get("tag").asString
            val reply=JsonObject().apply {addProperty("action","events");addProperty("tag",tag);add("events",JsonArray())}
            listener!!.onMessage(socket,reply.toString())
            assertEquals(ServerEventsPhase.READY,reader.state.value.phase)
            reader.pause();assertTrue(reader.state.value.lifecyclePause)
            reader.load();assertTrue(reader.state.value.lifecyclePause)
            listener!!.onMessage(socket,user(JsonObject().apply {add("mesh//new",JsonObject())}))
            assertEquals(ServerEventsPhase.INVALIDATED,reader.state.value.phase)
            assertFalse(reader.state.value.lifecyclePause)
        } finally {reader.close()}
    }
}
