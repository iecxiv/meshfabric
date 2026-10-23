package iecxiv.meshfabric.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import iecxiv.meshfabric.MainActivity
import iecxiv.meshfabric.R
import iecxiv.meshfabric.data.local.SessionManager
import iecxiv.meshfabric.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class SettingsViewModel(
    val sessionManager: SessionManager
) : ViewModel() {

    val appName: StateFlow<String> = sessionManager.appNameFlow
    val customIconPath: StateFlow<String?> = sessionManager.customIconPathFlow
    val themeMode: StateFlow<AppThemeMode> = sessionManager.themeModeFlow
    val hideSettingsIcon: StateFlow<Boolean> = sessionManager.hideSettingsIconFlow
    val saveTerminalBuffers: StateFlow<Boolean> = sessionManager.saveTerminalBuffersFlow

    fun saveAppName(newName: String) {
        sessionManager.saveCustomAppName(newName)
    }

    fun resetAppName() {
        sessionManager.resetAppName()
    }

    fun setCustomIcon(context: Context, uri: Uri): Boolean {
        return sessionManager.setCustomIconFromUri(context, uri)
    }

    fun resetCustomIcon(context: Context) {
        sessionManager.clearCustomIcon(context)
    }

    fun setThemeMode(mode: AppThemeMode) {
        sessionManager.saveThemeMode(mode)
    }

    fun setHideSettingsIcon(hide: Boolean) {
        sessionManager.saveHideSettingsIcon(hide)
    }

    fun setSaveTerminalBuffers(enabled: Boolean) {
        sessionManager.saveTerminalBuffersSetting(enabled)
    }

    fun createHomeShortcut(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return false
            if (!shortcutManager.isRequestPinShortcutSupported) return false

            val currentAppName = appName.value
            val currentIconPath = customIconPath.value

            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val icon = if (!currentIconPath.isNullOrBlank()) {
                try {
                    val file = File(currentIconPath)
                    if (file.exists()) {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        if (bitmap != null) Icon.createWithBitmap(bitmap) else null
                    } else null
                } catch (_: Exception) { null }
            } else null

            val pinShortcutInfo = ShortcutInfo.Builder(context, "custom_app_launcher_shortcut")
                .setShortLabel(currentAppName)
                .setLongLabel(currentAppName)
                .setIcon(icon ?: Icon.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(intent)
                .build()

            return shortcutManager.requestPinShortcut(pinShortcutInfo, null)
        }
        return false
    }
}
