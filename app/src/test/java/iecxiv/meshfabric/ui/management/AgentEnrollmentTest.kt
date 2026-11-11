package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
import okhttp3.HttpUrl.Companion.toHttpUrl
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
class AgentEnrollmentTest {
    private val base="https://example.test/proxy/domain";private val mesh="mesh//group"
    @Test fun nineFamilies() {assertEquals(listOf(0,1,5,2,6,7,3,4,8),enrollmentFamilies)}
    @Test fun fifteenBinaryArchitectures() {assertEquals(15,enrollmentArchitectures.size);for(id in enrollmentArchitectures.keys) assertEquals(id.toString(),enrollmentArtifacts(base,mesh,AgentEnrollmentOptions(5,id)).single().url.toHttpUrl().queryParameter("meshinstall"))}
    @Test fun windowsArchitecturesAndMode() {val rows=enrollmentArtifacts(base,mesh,AgentEnrollmentOptions(mode=2));assertEquals(listOf("3","4","43"),rows.map {it.url.toHttpUrl().queryParameter("id")});assertTrue(rows.all {it.url.toHttpUrl().queryParameter("installflags")=="2"})}
    @Test fun macArchitectures() {assertEquals(listOf("16","29","10005"),enrollmentArtifacts(base,mesh,AgentEnrollmentOptions(family=2)).map {it.url.toHttpUrl().queryParameter("id")})}
    @Test fun assistantUsesFiveIndependentModes() {for(mode in 0..4) {val u=enrollmentArtifacts(base,mesh,AgentEnrollmentOptions(family=7,assistant=mode)).single().url.toHttpUrl();assertEquals(mode.toString(),u.queryParameter("ac"));assertNull(u.queryParameter("installflags"))}}
    @Test fun typeVisibilityMatchesPlatform() {for(f in enrollmentFamilies) assertEquals(f in setOf(0,5),AgentEnrollmentOptions(family=f).showsType)}
    @Test fun linuxCommandsHaveGroupAndNoCookie() {val c=enrollmentLinuxCommand(base,mesh,false,false);assertTrue(c.contains("/proxy/domain/meshagents?script=1"));assertTrue(c.contains("'group'"));assertFalse(c.contains("auth="));assertFalse(c.contains("--no-check-certificate"));assertTrue(enrollmentLinuxCommand(base,mesh,true,true).contains("uninstall"))}
    @Test fun baseCannotCarryAuthenticationOrFragment() {for(b in listOf("https://user:pw@example.test","https://example.test?auth=secret","https://example.test/#secret")) assertThrows(IllegalArgumentException::class.java) {enrollmentBase(b)}}
    @Test fun binaryHeaderValidation() {assertTrue(enrollmentValidHeader("pe",byteArrayOf(77,90,0,0)));assertTrue(enrollmentValidHeader("zip",byteArrayOf(80,75,3,4)));assertTrue(enrollmentValidHeader("bin",byteArrayOf(127,69,76,70)));assertFalse(enrollmentValidHeader("pe","<htm".toByteArray()))}
    @Test fun qrRoundTripsExactMobilePayloadLocally() {val text="mc://agent.example/domain,"+"a".repeat(64)+",group";val m=enrollmentQr(text);val pixels=IntArray(256*256) {i->if(m[i%256,i/256]) 0xff000000.toInt() else 0xffffffff.toInt()};val result=QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(256,256,pixels))));assertEquals(text,result.text)}
}
