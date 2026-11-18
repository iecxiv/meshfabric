package iecxiv.meshfabric.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import iecxiv.meshfabric.R

private const val PREFERENCES = "disconnect_all_confirmation"
private const val CONFIRM = "confirm_disconnect_all"

@Composable
private fun rememberConfirmationPreferences(): SharedPreferences {
    val context = LocalContext.current.applicationContext
    return remember(context) { context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE) }
}

@Composable
private fun rememberConfirmationEnabled(preferences: SharedPreferences): State<Boolean> {
    val enabled = remember(preferences) { mutableStateOf(preferences.getBoolean(CONFIRM, true)) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { source, key ->
            if (key == CONFIRM || key == null) enabled.value = source.getBoolean(CONFIRM, true)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        enabled.value = preferences.getBoolean(CONFIRM, true)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return enabled
}

@Composable
internal fun rememberDisconnectAllAction(onDisconnect: () -> Unit): () -> Unit {
    val preferences = rememberConfirmationPreferences()
    var showConfirmation by rememberSaveable { mutableStateOf(false) }
    var doNotAskAgain by rememberSaveable { mutableStateOf(false) }
    val currentDisconnect by rememberUpdatedState(onDisconnect)
    if (showConfirmation) iecxiv.meshfabric.ui.components.AppAlertDialog(
        onDismissRequest = { showConfirmation = false },
        title = { Text(stringResource(R.string.confirm_disconnect_all_title), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.confirm_disconnect_all_msg))
                Row(
                    modifier = Modifier.fillMaxWidth().toggleable(
                        value = doNotAskAgain,
                        role = Role.Checkbox,
                        onValueChange = { doNotAskAgain = it }
                    ).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = doNotAskAgain, onCheckedChange = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.disconnect_all_do_not_ask_again))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                showConfirmation = false
                if (doNotAskAgain) preferences.edit().putBoolean(CONFIRM, false).apply()
                currentDisconnect()
            }) { Text(stringResource(R.string.disconnect_all), fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = { showConfirmation = false }) { Text(stringResource(R.string.cancel)) }
        }
    )
    return {
        if (preferences.getBoolean(CONFIRM, true)) {
            doNotAskAgain = false
            showConfirmation = true
        } else currentDisconnect()
    }
}

@Composable
internal fun DisconnectAllConfirmationSetting() {
    val preferences = rememberConfirmationPreferences()
    val enabled by rememberConfirmationEnabled(preferences)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = iecxiv.meshfabric.ui.theme.appCardColors(
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().toggleable(
                value = enabled,
                role = Role.Switch,
                onValueChange = { preferences.edit().putBoolean(CONFIRM, it).apply() }
            ).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.settings_confirm_disconnect_all_title), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(stringResource(R.string.settings_confirm_disconnect_all_desc), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = enabled, onCheckedChange = null)
        }
    }
}
