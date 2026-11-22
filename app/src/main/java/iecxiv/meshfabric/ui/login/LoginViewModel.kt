package iecxiv.meshfabric.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import iecxiv.meshfabric.R
import iecxiv.meshfabric.data.local.SessionManager
import iecxiv.meshfabric.data.remote.AuthResult
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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

    internal val fidoLogin=FidoLoginFlow(viewModelScope,{ FidoLoginFlow.Config(serverUrl.value,username.value,ignoreSsl.value) })
    internal fun startFidoLogin() {
        if(!isLoading.value && !_loginSuccess.value) {
            _errorMessage.value=null
            fidoLogin.start(password.value)
        }
    }
    private fun completeFidoLogin(result:iecxiv.meshfabric.data.remote.FidoLoginHttpTransport.PendingLogin) {
        FidoLoginCompletion.complete(result,rememberMe.value,
            persist={ sessionManager.saveSession(it) },
            forget={ sessionManager.forgetAuthentication() },
            connect={ meshClient.connectControlSocket(it.serverUrl,it.authCookie,false) },
            disconnect={ meshClient.disconnect() },
            success={
                password.value=""; otpToken.value=""
                _requires2FA.value=false; _errorMessage.value=null
                _loginSuccess.value=true
            })
    }
    override fun onCleared() { fidoLogin.close(); super.onCleared() }

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
        viewModelScope.launch {
            combine(serverUrl,username,ignoreSsl) { _,_,_ -> Unit }.collect {
                fidoLogin.configurationChanged()
            }
        }
        viewModelScope.launch {
            fidoLogin.state.collect { state ->
                if(state.phase==FidoLoginFlow.Phase.PENDING) {
                    fidoLogin.verify(state.id,::completeFidoLogin)
                }
            }
        }
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

    private suspend fun verifyControlSession(url: String, cookie: String, ssl: Boolean): Boolean {
        meshClient.connectControlSocket(
            rawServerUrl = url,
            cookie = cookie,
            ignoreSslCert = ssl
        )
        return withTimeoutOrNull(15_000L) {
            meshClient.userInfo.first { it?.id?.isNotBlank() == true }
        } != null
    }

    private fun verificationError(): String =
        if (getApplication<Application>().resources.configuration.locales[0].language == "es")
            "No se pudo verificar la sesión con el servidor. Comprueba las credenciales y vuelve a intentarlo."
        else "The session could not be verified with the server. Check your credentials and try again."

    fun login() {
        fidoLogin.cancel()
        val url = serverUrl.value.trim()
        val user = username.value.trim()
        val pass = password.value
        val otp = otpToken.value.trim()
        val ssl = ignoreSsl.value

        if (url.isEmpty()) {
            _errorMessage.value = getApplication<Application>().getString(R.string.app_l10n_login_missing_url)
            return
        }
        if (user.isEmpty() || pass.isEmpty()) {
            _errorMessage.value = getApplication<Application>().getString(R.string.app_l10n_login_missing_credentials)
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
                when (result) {
                    is AuthResult.Success -> {
                        val verified = result.authCookie.isNotBlank() &&
                            verifyControlSession(url, result.authCookie, ssl)
                        _isLoading.value = false
                        if (!verified) {
                            meshClient.disconnect()
                            _errorMessage.value = verificationError()
                            return@launch
                        }
                        _requires2FA.value = false

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
                        _loginSuccess.value = true
                    }
                    is AuthResult.Requires2FA -> {
                        _isLoading.value = false
                        _requires2FA.value = true
                        _errorMessage.value = result.message
                    }
                    is AuthResult.Error -> {
                        _isLoading.value = false
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
        fidoLogin.cancel()
        sessionManager.clearSession()
        meshClient.disconnect()
        _loginSuccess.value = false
        password.value = ""
        otpToken.value = ""
        _requires2FA.value = false
    }
}
