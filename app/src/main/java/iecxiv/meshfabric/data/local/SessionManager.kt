package iecxiv.meshfabric.data.local

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import iecxiv.meshfabric.data.model.SessionData
import iecxiv.meshfabric.data.remote.DesktopEncoding
import iecxiv.meshfabric.data.remote.DesktopSettings
import iecxiv.meshfabric.data.remote.FrameRateOption
import iecxiv.meshfabric.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        EncryptedSharedPreferences.create(
            "meshcentral_encrypted_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("meshcentral_fallback_prefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_AUTH_COOKIE = "auth_cookie"
        private const val KEY_IGNORE_SSL = "ignore_ssl"
        private const val KEY_IS_REMEMBERED = "is_remembered"

        private const val KEY_DESKTOP_QUALITY = "desktop_quality"
        private const val KEY_DESKTOP_SCALE = "desktop_scale"
        private const val KEY_DESKTOP_FPS = "desktop_fps"
        private const val KEY_DESKTOP_ENCODING = "desktop_encoding"
        private const val KEY_DESKTOP_SWAP_MOUSE = "desktop_swap_mouse"
        private const val KEY_DESKTOP_REVERSE_WHEEL = "desktop_reverse_wheel"
        private const val KEY_DESKTOP_REMOTE_KBD = "desktop_remote_kbd"
        private const val KEY_DESKTOP_AUTO_CLIPBOARD = "desktop_auto_clipboard"
        private const val KEY_DESKTOP_LOCK_DISCONNECT = "desktop_lock_disconnect"
        private const val KEY_DESKTOP_BAR_COLLAPSED = "desktop_bar_collapsed"

        private const val KEY_COLLAPSED_GROUPS = "collapsed_groups"
        private const val KEY_ALL_GROUPS_EXPANDED = "all_groups_expanded"

        private const val KEY_CUSTOM_APP_NAME = "custom_app_name"
        private const val KEY_CUSTOM_ICON_PATH = "custom_icon_path"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_HIDE_SETTINGS_ICON = "hide_settings_icon"
        private const val KEY_SAVE_TERMINAL_BUFFERS = "save_terminal_buffers"
    }

    private val _appNameFlow = MutableStateFlow(
        prefs.getString(KEY_CUSTOM_APP_NAME, "MeshFabric") ?: "MeshFabric"
    )
    val appNameFlow: StateFlow<String> = _appNameFlow.asStateFlow()

    private val _customIconPathFlow = MutableStateFlow(
        prefs.getString(KEY_CUSTOM_ICON_PATH, null)
    )
    val customIconPathFlow: StateFlow<String?> = _customIconPathFlow.asStateFlow()

    private val _themeModeFlow = MutableStateFlow(getSavedThemeMode())
    val themeModeFlow: StateFlow<AppThemeMode> = _themeModeFlow.asStateFlow()

    private val _hideSettingsIconFlow = MutableStateFlow(
        prefs.getBoolean(KEY_HIDE_SETTINGS_ICON, false)
    )
    val hideSettingsIconFlow: StateFlow<Boolean> = _hideSettingsIconFlow.asStateFlow()

    private fun getSavedThemeMode(): AppThemeMode {
        val savedName = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)
        return try {
            AppThemeMode.valueOf(savedName ?: AppThemeMode.SYSTEM.name)
        } catch (_: Exception) {
            AppThemeMode.SYSTEM
        }
    }

    fun saveCustomAppName(name: String) {
        val trimmed = name.ifBlank { "MeshFabric" }
        prefs.edit().putString(KEY_CUSTOM_APP_NAME, trimmed).apply()
        _appNameFlow.value = trimmed
    }

    fun resetAppName() {
        saveCustomAppName("MeshFabric")
    }

    fun setCustomIconFromUri(context: Context, uri: Uri): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return false
            val iconFile = File(context.filesDir, "custom_app_icon.png")
            iconFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            val path = iconFile.absolutePath
            prefs.edit().putString(KEY_CUSTOM_ICON_PATH, path).apply()
            _customIconPathFlow.value = path
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun clearCustomIcon(context: Context) {
        try {
            val iconFile = File(context.filesDir, "custom_app_icon.png")
            if (iconFile.exists()) {
                iconFile.delete()
            }
        } catch (_: Exception) {}
        prefs.edit().remove(KEY_CUSTOM_ICON_PATH).apply()
        _customIconPathFlow.value = null
    }

    fun saveThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeModeFlow.value = mode
    }

    fun saveHideSettingsIcon(hide: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_SETTINGS_ICON, hide).apply()
        _hideSettingsIconFlow.value = hide
    }

    fun saveSession(session: SessionData) {
        prefs.edit()
            .putString(KEY_SERVER_URL, session.serverUrl)
            .putString(KEY_USERNAME, session.username)
            .putString(KEY_AUTH_COOKIE, session.authCookie)
            .putBoolean(KEY_IGNORE_SSL, session.ignoreSsl)
            .putBoolean(KEY_IS_REMEMBERED, session.isRemembered)
            .apply()
    }

    fun getSession(): SessionData? {
        val isRemembered = prefs.getBoolean(KEY_IS_REMEMBERED, false)
        if (!isRemembered) return null

        val serverUrl = prefs.getString(KEY_SERVER_URL, "") ?: ""
        val username = prefs.getString(KEY_USERNAME, "") ?: ""
        val authCookie = prefs.getString(KEY_AUTH_COOKIE, "") ?: ""
        val ignoreSsl = prefs.getBoolean(KEY_IGNORE_SSL, false)

        if (serverUrl.isEmpty() || authCookie.isEmpty()) return null

        return SessionData(
            serverUrl = serverUrl,
            username = username,
            authCookie = authCookie,
            ignoreSsl = ignoreSsl,
            isRemembered = true
        )
    }

    fun clearSession() {
        val currentAppName = _appNameFlow.value
        val currentIconPath = _customIconPathFlow.value
        val currentTheme = _themeModeFlow.value

        prefs.edit().clear().apply()

        // Preserve app settings when clearing user session
        prefs.edit()
            .putString(KEY_CUSTOM_APP_NAME, currentAppName)
            .apply()
        if (currentIconPath != null) {
            prefs.edit().putString(KEY_CUSTOM_ICON_PATH, currentIconPath).apply()
        }
        prefs.edit().putString(KEY_THEME_MODE, currentTheme.name).apply()
    }

    fun saveDesktopSettings(settings: DesktopSettings) {
        prefs.edit()
            .putInt(KEY_DESKTOP_QUALITY, settings.quality)
            .putInt(KEY_DESKTOP_SCALE, settings.scale)
            .putString(KEY_DESKTOP_FPS, settings.frameRate.name)
            .putString(KEY_DESKTOP_ENCODING, settings.encoding.name)
            .putBoolean(KEY_DESKTOP_SWAP_MOUSE, settings.swapMouseButtons)
            .putBoolean(KEY_DESKTOP_REVERSE_WHEEL, settings.reverseMouseWheel)
            .putBoolean(KEY_DESKTOP_REMOTE_KBD, settings.useRemoteKeyboardMap)
            .putBoolean(KEY_DESKTOP_AUTO_CLIPBOARD, settings.autoClipboard)
            .putBoolean(KEY_DESKTOP_LOCK_DISCONNECT, settings.lockOnDisconnect)
            .apply()
    }

    fun getDesktopSettings(): DesktopSettings {
        val quality = prefs.getInt(KEY_DESKTOP_QUALITY, 50)
        val scale = prefs.getInt(KEY_DESKTOP_SCALE, 1024)
        val fpsStr = prefs.getString(KEY_DESKTOP_FPS, FrameRateOption.FAST.name) ?: FrameRateOption.FAST.name
        val encStr = prefs.getString(KEY_DESKTOP_ENCODING, DesktopEncoding.JPEG.name) ?: DesktopEncoding.JPEG.name
        val swapMouse = prefs.getBoolean(KEY_DESKTOP_SWAP_MOUSE, false)
        val reverseWheel = prefs.getBoolean(KEY_DESKTOP_REVERSE_WHEEL, false)
        val remoteKbd = prefs.getBoolean(KEY_DESKTOP_REMOTE_KBD, false)
        val autoClip = prefs.getBoolean(KEY_DESKTOP_AUTO_CLIPBOARD, false)
        val lockDisconnect = prefs.getBoolean(KEY_DESKTOP_LOCK_DISCONNECT, false)

        val fps = try { FrameRateOption.valueOf(fpsStr) } catch (e: Exception) { FrameRateOption.FAST }
        val enc = try { DesktopEncoding.valueOf(encStr) } catch (e: Exception) { DesktopEncoding.JPEG }

        return DesktopSettings(
            quality = quality,
            scale = scale,
            frameRate = fps,
            encoding = enc,
            swapMouseButtons = swapMouse,
            reverseMouseWheel = reverseWheel,
            useRemoteKeyboardMap = remoteKbd,
            autoClipboard = autoClip,
            lockOnDisconnect = lockDisconnect
        )
    }

    fun saveDesktopBarCollapsed(collapsed: Boolean) {
        prefs.edit().putBoolean(KEY_DESKTOP_BAR_COLLAPSED, collapsed).apply()
    }

    fun isDesktopBarCollapsed(): Boolean {
        return prefs.getBoolean(KEY_DESKTOP_BAR_COLLAPSED, false)
    }

    fun saveCollapsedGroups(collapsedGroupIds: Set<String>) {
        prefs.edit().putStringSet(KEY_COLLAPSED_GROUPS, collapsedGroupIds).apply()
    }

    fun getCollapsedGroups(): Set<String> {
        return prefs.getStringSet(KEY_COLLAPSED_GROUPS, emptySet()) ?: emptySet()
    }

    fun saveAllGroupsExpandedState(isExpanded: Boolean) {
        prefs.edit().putBoolean(KEY_ALL_GROUPS_EXPANDED, isExpanded).apply()
    }

    fun isAllGroupsExpanded(): Boolean {
        return prefs.getBoolean(KEY_ALL_GROUPS_EXPANDED, true)
    }

    private val _saveTerminalBuffersFlow = MutableStateFlow(
        prefs.getBoolean(KEY_SAVE_TERMINAL_BUFFERS, true)
    )
    val saveTerminalBuffersFlow: StateFlow<Boolean> = _saveTerminalBuffersFlow.asStateFlow()

    fun saveTerminalBuffersSetting(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SAVE_TERMINAL_BUFFERS, enabled).apply()
        _saveTerminalBuffersFlow.value = enabled
    }

    fun isSaveTerminalBuffersEnabled(): Boolean {
        return prefs.getBoolean(KEY_SAVE_TERMINAL_BUFFERS, true)
    }
}
