package com.litter.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

private val BrandBackground = Color(0xFF0A0F1C)
private val BrandCoral = Color(0xFFFF6B4A)
private val BrandAmber = Color(0xFFFFB86B)
private val BrandText = Color(0xFFF8FAFC)

@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    showWordmark: Boolean = false,
) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = modifier.size(size)) {
        val edge = min(this.size.width, this.size.height)
        val unit = edge / 96f
        val radius = 20f * unit
        val iconSize = Size(edge, edge)
        drawRoundRect(
            color = BrandBackground,
            size = iconSize,
            cornerRadius = CornerRadius(radius, radius),
        )
        drawLine(BrandCoral, Offset(30f * unit, 24f * unit), Offset(30f * unit, 69f * unit), 10f * unit, cap = StrokeCap.Round)
        drawLine(BrandAmber, Offset(30f * unit, 24f * unit), Offset(66f * unit, 69f * unit), 10f * unit, cap = StrokeCap.Round)
        drawLine(BrandCoral, Offset(66f * unit, 24f * unit), Offset(66f * unit, 69f * unit), 10f * unit, cap = StrokeCap.Round)
        if (showWordmark) {
            val layout = textMeasurer.measure(
                text = "NeCode",
                style = TextStyle(
                    color = BrandText,
                    fontFamily = LitterTheme.monoFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            drawText(
                layout,
                topLeft = Offset((edge - layout.size.width) / 2f, 73f * unit),
            )
        }
    }
}

@Composable
fun AnimatedLogo(size: Dp = 44.dp) {
    BrandLogo(size = size)
}
