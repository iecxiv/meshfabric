package iecxiv.meshfabric.ui.management
import org.junit.Assert.assertEquals
import org.junit.Test
class NoticeRecipientPresentationTest {
    @Test fun userShowsNameOnly() {assertEquals("Ana",noticeUserRecipientLabel(" Ana ",true))}
    @Test fun deviceShowsGroup() {assertEquals("Portatil (Oficina)",noticeDeviceRecipientLabel("Portatil","Oficina",true))}
    @Test fun homonymousDevicesKeepTheirGroup() {assertEquals("PC (A)",noticeDeviceRecipientLabel("PC","A",true));assertEquals("PC (B)",noticeDeviceRecipientLabel("PC","B",true))}
    @Test fun missingNamesNeverFallBackToIdentifiers() {assertEquals("Usuario sin nombre",noticeUserRecipientLabel("",true));assertEquals("Dispositivo sin nombre (Grupo no disponible)",noticeDeviceRecipientLabel("","",true))}
    @Test fun englishFallbacks() {assertEquals("Unnamed user",noticeUserRecipientLabel("",false));assertEquals("Unnamed device (Group unavailable)",noticeDeviceRecipientLabel("","",false))}
}
