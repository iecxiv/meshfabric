package iecxiv.meshfabric.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun logicalLineStarts(text: String): List<Int> = buildList {
    add(0)
    text.forEachIndexed { index, char -> if (char == '\n') add(index + 1) }
}

/** Numbers logical lines, positioning each label at its first visual baseline. */
@Composable
internal fun NumberedTextEditor(
    value: String,
    onValueChange: (String) -> Unit,
    wordWrap: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val density = LocalDensity.current
    val starts = remember(value) { logicalLineStarts(value) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val currentLayout = layout?.takeIf { it.layoutInput.text.text == value }
    val scheme = MaterialTheme.colorScheme
    val numberColor = scheme.onSurfaceVariant.copy(alpha = 0.6f)
    val fontPixels = with(density) { 13.sp.toPx() }
    val paint = remember(fontPixels, numberColor) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = fontPixels
            typeface = Typeface.MONOSPACE
            color = numberColor.toArgb()
            textAlign = Paint.Align.RIGHT
        }
    }
    val gutterWidth = with(density) {
        paint.measureText(starts.size.toString()).toDp() + 16.dp
    }
    val contentHeight = with(density) {
        (currentLayout?.size?.height ?: 0).toDp() + 16.dp
    }
    val shape = RoundedCornerShape(8.dp)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier.border(1.dp, scheme.outline, shape)
            .background(scheme.surfaceVariant.copy(alpha = 0.3f), shape).clip(shape)) {
            Row(Modifier.fillMaxSize().verticalScroll(verticalScroll)) {
                Canvas(Modifier.width(gutterWidth).height(contentHeight)
                    .background(scheme.surfaceVariant.copy(alpha = 0.6f))) {
                    val result = currentLayout
                    if (result != null) {
                        val padding = 8.dp.toPx()
                        starts.forEachIndexed { index, offset ->
                            val visualLine = result.getLineForOffset(offset)
                            drawContext.canvas.nativeCanvas.drawText(
                                (index + 1).toString(), size.width - padding,
                                padding + result.getLineBaseline(visualLine), paint
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).padding(8.dp).then(
                    if (wordWrap) Modifier else Modifier.horizontalScroll(horizontalScroll)
                )) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        modifier = if (wordWrap) Modifier.fillMaxWidth()
                            else Modifier.defaultMinSize(minWidth = 2000.dp),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp, lineHeight = 20.sp, color = scheme.onSurface),
                        cursorBrush = SolidColor(scheme.primary),
                        onTextLayout = { layout = it }
                    )
                }
            }
        }
    }
}
