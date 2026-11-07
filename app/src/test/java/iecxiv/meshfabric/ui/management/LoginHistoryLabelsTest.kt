package iecxiv.meshfabric.ui.management

import org.junit.Assert.assertEquals
import org.junit.Test

class LoginHistoryLabelsTest {
@Test fun spanishLabels() {
assertEquals("Inicio de sesión válido",loginHistoryLabel(107,true))
assertEquals("Segundo factor de autenticación no válido",loginHistoryLabel(108,true))
assertEquals("Cuenta bloqueada",loginHistoryLabel(109,true))
assertEquals("Contraseña no válida",loginHistoryLabel(110,true))
}
@Test fun englishLabels() {
assertEquals("Successful sign-in",loginHistoryLabel(107,false))
assertEquals("Invalid second authentication factor",loginHistoryLabel(108,false))
assertEquals("Account locked",loginHistoryLabel(109,false))
assertEquals("Invalid password",loginHistoryLabel(110,false))
}
@Test fun unknownCodesKeepTheirNumber() {
assertEquals("Evento desconocido (código 999)",loginHistoryLabel(999,true))
assertEquals("Unknown event (code -1)",loginHistoryLabel(-1,false))
}
@Test fun numericRepresentations() {
assertEquals(loginHistoryLabel(107,true),loginHistoryLabel("107",true))
assertEquals(loginHistoryLabel(110,false),loginHistoryLabel(110L,false))
}
@Test fun malformedValuesAreNotConvertedToKnownEvents() {
for(value in listOf(null,"invalid",107.5,Double.NaN,Double.POSITIVE_INFINITY,Long.MAX_VALUE,true)) assertEquals("Evento sin código válido",loginHistoryLabel(value,true))
}
}
