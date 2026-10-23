package iecxiv.meshfabric.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import iecxiv.meshfabric.R
import java.io.File

@Composable
fun AppIconImage(
    customIconPath: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current

    val customBitmap = remember(customIconPath) {
        if (!customIconPath.isNullOrBlank()) {
            val file = File(customIconPath)
            if (file.exists()) {
                try {
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                } catch (_: Exception) {
                    null
                }
            } else null
        } else null
    }

    val appIconBitmap = remember(customBitmap, context) {
        if (customBitmap != null) null
        else {
            try {
                val drawable = context.packageManager.getApplicationIcon(context.packageName)
                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
                val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
                bmp.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }

    val finalModifier = if (onClick != null) {
        modifier.clickable { onClick() }
    } else {
        modifier
    }

    when {
        customBitmap != null -> {
            Image(
                bitmap = customBitmap,
                contentDescription = stringResource(R.string.app_logo_desc),
                modifier = finalModifier
            )
        }
        appIconBitmap != null -> {
            Image(
                bitmap = appIconBitmap,
                contentDescription = stringResource(R.string.app_logo_desc),
                modifier = finalModifier
            )
        }
        else -> {
            Icon(
                imageVector = Icons.Default.Computer,
                contentDescription = stringResource(R.string.app_logo_desc),
                modifier = finalModifier
            )
        }
    }
}
