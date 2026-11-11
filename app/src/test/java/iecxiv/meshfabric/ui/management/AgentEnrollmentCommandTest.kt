package iecxiv.meshfabric.ui.management
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import org.junit.Assert.*
import org.junit.Test
class AgentEnrollmentCommandTest {
    private val base="https://example.test/domain"
    private fun expected(uninstall:Boolean):String {val args=(if(uninstall) "uninstall " else "")+"'https://example.test/domain' 'group'";return "(wget 'https://example.test/domain/meshagents?script=1' -O ./meshinstall.sh || wget 'https://example.test/domain/meshagents?script=1' --no-proxy -O ./meshinstall.sh) && chmod 755 ./meshinstall.sh && sudo -E ./meshinstall.sh "+args+" || ./meshinstall.sh "+args}
    @Test fun installHasCompleteUpstreamFallback() {assertEquals(expected(false),enrollmentLinuxCommand(base,"mesh//group",false,false))}
    @Test fun uninstallHasCompleteUpstreamFallback() {assertEquals(expected(true),enrollmentLinuxCommand(base,"mesh//group",true,false))}
    @Test fun fallbackReceivesIdenticalArguments() {for(u in listOf(false,true)) for(ssl in listOf(false,true)) {val c=enrollmentLinuxCommand(base,"mesh//group",u,ssl);val parts=c.substringAfter("sudo -E ./meshinstall.sh ").split(" || ./meshinstall.sh ");assertEquals(2,parts.size);assertEquals(parts[0],parts[1]);assertEquals(u,parts[1].startsWith("uninstall "))}}
    @Test fun certificateAndProxyOptionsArePreserved() {for(ssl in listOf(false,true)) {val c=enrollmentLinuxCommand(base,"mesh//group",false,ssl);assertEquals(if(ssl) 2 else 0,Regex("--no-check-certificate").findAll(c).count());assertEquals(1,Regex("--no-proxy").findAll(c).count())}}
    @Test fun literalGroupArgumentsAndExecutionFixtures() {
        val group="abc@${'$'}def";val rows=JsonArray()
        for(u in listOf(false,true)) for(ssl in listOf(false,true)) {
            val command=enrollmentLinuxCommand(base,"mesh//"+group,u,ssl)
            assertTrue(command.endsWith(" || ./meshinstall.sh "+(if(u) "uninstall " else "")+"'https://example.test/domain' 'abc@${'$'}def'"))
            rows.add(JsonObject().apply {addProperty("command",command);addProperty("server",base);addProperty("group",group);addProperty("uninstall",u)})
        }
        val target=System.getenv("AGENT_COMMAND_FIXTURES") ?: File(System.getProperty("java.io.tmpdir"),"meshfabric-agent-command-fixtures.json").path
        File(target).writeText(rows.toString())
    }
}
