package iecxiv.meshfabric.ui.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable internal fun DesktopRightClickHeaderGlyph(active: Boolean, label: String?, rightButtonBlue: Boolean = true, iconSize: Dp = 24.dp) {
    val blue = Color(0xFF2196F3)
    val body = if (active) blue else LocalContentColor.current
    val accessibility = if (label != null) Modifier.semantics { contentDescription = label } else Modifier
    Canvas(Modifier.size(iconSize).then(accessibility)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val rightButton = Path().apply {
                moveTo(13f, 3f)
                cubicTo(16.2f, 3.4f, 18f, 6f, 18f, 9f)
                lineTo(13f, 9f)
                close()
            }
            drawPath(rightButton, if (rightButtonBlue) blue else body)
            drawRoundRect(body, Offset(5f, 2f), Size(14f, 20f), CornerRadius(7f, 7f), style = Stroke(1.7f))
            drawLine(body, Offset(5f, 10f), Offset(19f, 10f), strokeWidth = 1.5f)
            drawLine(body, Offset(12f, 2f), Offset(12f, 10f), strokeWidth = 1.5f)
        }
    }
}
