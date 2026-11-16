package iecxiv.meshfabric.ui.login

import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.platform.app.InstrumentationRegistry
import com.yubico.yubikit.fido.android.ui.R as YubiR
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class YubiKitLocalizationTest {
    private fun resources(language: String): Resources {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        return context.createConfigurationContext(configuration).resources
    }

    @Test fun spanishPromptsAndResultsUseLibraryResourceIds() {
        val es = resources("es")
        assertEquals("Conecta tu llave de seguridad", es.getString(YubiR.string.yk_fido_connect_your_key_title))
        assertEquals("Respuesta de la llave completada", es.getString(YubiR.string.yk_fido_login_successful))
        assertEquals("No se pudo completar la operación con la llave para server.example", es.getString(YubiR.string.yk_fido_error_login_failed, "server.example"))
        assertEquals("Clave de acceso creada en la llave", es.getString(YubiR.string.yk_fido_passkey_created))
        assertEquals("Cancelar", es.getString(YubiR.string.yk_fido_cancel))
        assertEquals("Reintentar", es.getString(YubiR.string.yk_fido_retry))
        assertEquals("Mostrar PIN", es.getString(YubiR.string.yk_fido_icon_content_description_show_pin))
        assertEquals("Ubicación de la antena NFC", es.getString(YubiR.string.yk_fido_content_description_nfc_antenna_location))
    }

    @Test fun spanishPinAndFingerprintAttemptsRetainPlurals() {
        val es = resources("es")
        assertEquals("PIN incorrecto. Queda 1 intento.", es.getQuantityString(YubiR.plurals.yk_fido_incorrect_pin_with_attempts, 1, 1))
        assertEquals("PIN incorrecto. Quedan 3 intentos.", es.getQuantityString(YubiR.plurals.yk_fido_incorrect_pin_with_attempts, 3, 3))
        assertEquals("Huella no reconocida. Vuelve a intentarlo.\nQueda 1 intento.", es.getQuantityString(YubiR.plurals.yk_fido_ctap_err_uv_invalid, 1, 1))
        assertEquals("Huella no reconocida. Vuelve a intentarlo.\nQuedan 2 intentos.", es.getQuantityString(YubiR.plurals.yk_fido_ctap_err_uv_invalid, 2, 2))
    }

    @Test fun spanishFormattedMessagesKeepHostCountsAndPinMinimum() {
        val es = resources("es")
        assertEquals("server.example necesita verificar tu identidad", es.getString(YubiR.string.yk_fido_enter_pin_subtitle, "server.example"))
        assertEquals("Selecciona una clave de acceso para entrar (3 disponibles)", es.getString(YubiR.string.yk_fido_select_passkey, 3))
        assertEquals("No hay claves de acceso para server.example en la llave de seguridad.", es.getString(YubiR.string.yk_fido_ctap_err_no_credentials, "server.example"))
        assertEquals("El PIN debe tener entre 4 y 63 caracteres, incluir al menos 2 caracteres distintos y no ser un PIN habitual, como «123456». Puede contener letras, números y caracteres especiales.", es.getString(YubiR.string.yk_fido_set_pin_requirements, 4))
    }

    @Test fun englishResultsDoNotAnnounceAnAuthenticatedSession() {
        val en = resources("en")
        assertEquals("Security key response completed", en.getString(YubiR.string.yk_fido_login_successful))
        assertEquals("Could not complete the security key operation for server.example", en.getString(YubiR.string.yk_fido_error_login_failed, "server.example"))
        assertEquals("Connect your security key", en.getString(YubiR.string.yk_fido_connect_your_key_title))
    }
}
