package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.data.model.DevicePlatform

@Composable
internal fun DeviceBatchUploadOptionsForm(
    options: DeviceBatchUploadOptions,
    enabled: Boolean,
    onChange: (DeviceBatchUploadOptions) -> Unit
) {
    val es = LocalConfiguration.current.locales[0].language == "es"
    fun t(a: String, b: String) = if (es) a else b
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = options.windowsDirectory, onValueChange = { onChange(options.copy(windowsDirectory = it)) },
            enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text(t("Ruta de destino para Windows", "Destination path for Windows")) },
            isError = options.windowsDirectory.isNotBlank() && deviceBatchUploadRoute(options, DevicePlatform.WINDOWS) == null)
        OutlinedTextField(value = options.linuxDirectory, onValueChange = { onChange(options.copy(linuxDirectory = it)) },
            enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text(t("Ruta de destino para Linux", "Destination path for Linux")) },
            isError = options.linuxDirectory.isNotBlank() && deviceBatchUploadRoute(options, DevicePlatform.LINUX) == null)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = options.createDirectoryIfMissing, enabled = enabled,
                onCheckedChange = { onChange(options.copy(createDirectoryIfMissing = it)) })
            Text(t("Crear carpeta si no existe", "Create folder if it does not exist"), Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = options.overwriteFileIfExists, enabled = enabled,
                onCheckedChange = { onChange(options.copy(overwriteFileIfExists = it)) })
            Text(t("Sobrescribir fichero si existe", "Overwrite file if it exists"), Modifier.weight(1f))
        }
        Text(t("Una ruta vacía omite los dispositivos de ese sistema. Otros sistemas no se asignan automáticamente a la ruta de Linux.",
            "An empty path skips devices on that system. Other systems are not automatically assigned the Linux path."),
            style = MaterialTheme.typography.bodySmall)
    }
}
