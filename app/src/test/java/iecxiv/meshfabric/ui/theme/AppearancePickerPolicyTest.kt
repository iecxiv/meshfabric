package iecxiv.meshfabric.ui.theme
import org.junit.Assert.*
import org.junit.Test
class AppearancePickerPolicyTest {
    @Test fun validHourCanBeSaved() {assertTrue(appearanceTimeCanSave(7,0,1200))}
    @Test fun equalTimesAreRejected() {assertFalse(appearanceTimeCanSave(20,0,1200))}
    @Test fun invalidHourAndMinuteAreRejected() {assertFalse(appearanceTimeCanSave(24,0,0));assertFalse(appearanceTimeCanSave(0,60,0));assertFalse(appearanceTimeCanSave(-1,0,0))}
    @Test fun invalidOppositeTimeIsRejected() {assertFalse(appearanceTimeCanSave(7,0,1440));assertFalse(appearanceTimeCanSave(7,0,-1))}
    @Test fun shortMenuFitsContent() {assertEquals(112f,appearanceCityPopupWidth(80f,280f),0f)}
    @Test fun longMenuDoesNotOverflow() {assertEquals(280f,appearanceCityPopupWidth(800f,280f),0f)}
    @Test fun veryWideWindowIsBounded() {assertEquals(480f,appearanceCityPopupWidth(1000f,1000f),0f);assertEquals(480f,appearanceCityPopupWidth(1000f,Float.POSITIVE_INFINITY),0f)}
    @Test fun invalidDimensionsAreSafe() {assertEquals(0f,appearanceCityPopupWidth(80f,Float.NaN),0f);assertEquals(0f,appearanceCityPopupWidth(80f,-1f),0f);assertEquals(280f,appearanceCityPopupWidth(Float.NaN,280f),0f)}
}
