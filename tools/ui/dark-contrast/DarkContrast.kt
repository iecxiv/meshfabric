package iecxiv.meshfabric.ui.theme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal fun darkContrastScheme(scheme:ColorScheme,dark:Boolean):ColorScheme =
    if(dark) scheme.copy(outline=DarkOutline,outlineVariant=DarkOutlineVariant) else scheme
internal fun readableActivityColor(original:Color,primary:Color,dark:Boolean):Color =
    if(dark) primary else original
@Composable internal fun activityCardTextColor(original:Color):Color =
    readableActivityColor(original,MaterialTheme.colorScheme.onSurface,LocalAppDarkTheme.current)
