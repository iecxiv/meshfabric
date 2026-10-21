package iecxiv.meshfabric.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import iecxiv.meshfabric.data.local.SessionManager
import iecxiv.meshfabric.data.remote.AuthResult
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    application: Application,
    val meshClient: MeshCentralClient,
    val sessionManager: SessionManager
) : AndroidViewModel(application) {

    var serverUrl = MutableStateFlow("")
    var username = MutableStateFlow("")
    var password = MutableStateFlow("")
    var otpToken = MutableStateFlow("")
    var ignoreSsl = MutableStateFlow(false)
    var rememberMe = MutableStateFlow(true)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _requires2FA = MutableStateFlow(false)
    val requires2FA: StateFlow<Boolean> = _requires2FA.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _loginSuccess = MutableStateFlow(false)
    val loginSuccess: StateFlow<Boolean> = _loginSuccess.asStateFlow()

    init {
        checkSavedSession()
    }

    private fun checkSavedSession() {
        val session = sessionManager.getSession()
        if (session != null) {
            serverUrl.value = session.serverUrl
            username.value = session.username
            ignoreSsl.value = session.ignoreSsl
            rememberMe.value = true

            // Connect using saved auth cookie
            meshClient.connectControlSocket(
                rawServerUrl = session.serverUrl,
                cookie = session.authCookie,
                ignoreSslCert = session.ignoreSsl
            )
            _loginSuccess.value = true
        }
    }

    fun login() {
        val url = serverUrl.value.trim()
        val user = username.value.trim()
        val pass = password.value
        val otp = otpToken.value.trim()
        val ssl = ignoreSsl.value

        if (url.isEmpty()) {
            _errorMessage.value = "Por favor ingrese la URL del servidor MeshCentral."
            return
        }
        if (user.isEmpty() || pass.isEmpty()) {
            _errorMessage.value = "Por favor ingrese usuario y contraseña."
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        meshClient.login(
            rawServerUrl = url,
            username = user,
            pass = pass,
            otpToken = otp,
            ignoreSslCert = ssl
        ) { result ->
            viewModelScope.launch {
                _isLoading.value = false
                when (result) {
                    is AuthResult.Success -> {
                        _requires2FA.value = false
                        _loginSuccess.value = true

                        if (rememberMe.value) {
                            sessionManager.saveSession(
                                iecxiv.meshfabric.data.model.SessionData(
                                    serverUrl = url,
                                    username = user,
                                    authCookie = result.authCookie,
                                    ignoreSsl = ssl,
                                    isRemembered = true
                                )
                            )
                        }

                        // Connect control WebSocket
                        meshClient.connectControlSocket(
                            rawServerUrl = url,
                            cookie = result.authCookie,
                            ignoreSslCert = ssl
                        )
                    }
                    is AuthResult.Requires2FA -> {
                        _requires2FA.value = true
                        _errorMessage.value = result.message
                    }
                    is AuthResult.Error -> {
                        _errorMessage.value = result.message
                    }
                }
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun logout() {
        sessionManager.clearSession()
        meshClient.disconnect()
        _loginSuccess.value = false
        password.value = ""
        otpToken.value = ""
        _requires2FA.value = false
    }
}
