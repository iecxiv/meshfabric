package iecxiv.meshfabric.ui.theme

import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val LocalAppDarkTheme = staticCompositionLocalOf { false }

internal fun resolveAppCardBackground(dark: Boolean, original: Color, events: Color): Color =
    if (dark) events else original

internal fun resolveAppDarkTheme(mode: AppThemeMode, systemDark: Boolean): Boolean = when (mode) {
    AppThemeMode.SYSTEM -> systemDark
    AppThemeMode.LIGHT -> false
    AppThemeMode.DARK -> true
}

@Composable internal fun appCardBackground(original: Color): Color = resolveAppCardBackground(
    LocalAppDarkTheme.current, original, CardDefaults.cardColors().containerColor
)

@Composable internal fun appCardColors(original: CardColors): CardColors {
    if (!LocalAppDarkTheme.current) return original
    val background = CardDefaults.cardColors().containerColor
    return CardDefaults.cardColors(
        containerColor = background,
        contentColor = original.contentColor,
        disabledContainerColor = background,
        disabledContentColor = original.disabledContentColor
    )
}

@Composable internal fun appCardTonalElevation(original: Dp): Dp =
    if (LocalAppDarkTheme.current) 0.dp else original
