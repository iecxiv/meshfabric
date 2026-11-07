package iecxiv.meshfabric.ui.management

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URLEncoder

@RunWith(AndroidJUnit4::class)
class AccountWebStateBootstrapTest {
    @Test fun readsEncodedStateWithoutExecutingScripts() {
        val original=JSONObject().put("notifications","31").put("search","a+b")
        val encoded=URLEncoder.encode(original.toString(),"UTF-8").replace("+","%20")
        val result=embeddedWebState("<script>var webState = '"+encoded+"';</script>")!!
        assertEquals(31L,webNoticeMask(result))
        assertEquals("a+b",result.getString("search"))
    }
    @Test fun onlyExplicitEmptyObjectIsKnownEmpty() {
        assertEquals(0L,webNoticeMask(embeddedWebState("var webState = '%7B%7D';")!!))
        assertNull(embeddedWebState("<html>Login required</html>"))
        assertNull(embeddedWebState("var webState = '';"))
    }
    @Test fun rejectsMalformedEncodingAndInvalidFlags() {
        assertNull(embeddedWebState("var webState = '%ZZ';"))
        val encoded=URLEncoder.encode(JSONObject().put("notifications",false).toString(),"UTF-8")
        assertNull(embeddedWebState("var webState = '"+encoded+"';"))
    }
}
