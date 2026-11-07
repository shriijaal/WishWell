package com.wishwell.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wishwell.app.data.model.PricePoint
import com.wishwell.app.ui.theme.LocalWishExtraColors
import com.wishwell.app.util.formatDateShort
import com.wishwell.app.util.formatDateWithYear
import com.wishwell.app.util.formatMoney
import java.util.concurrent.TimeUnit

@Composable
fun PriceHistoryChart(
    points: List<PricePoint>,
    currencyCode: String,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) {
        ChartPlaceholder(
            text = "Price history builds with each check. We'll chart every change here.",
            modifier = modifier
        )
        return
    }

    val extra = LocalWishExtraColors.current
    val lineColor = MaterialTheme.colorScheme.primary
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = extra.muted,
        letterSpacing = 0.1.sp
    )

    val minPrice = points.minOf { it.price }
    val maxPrice = points.maxOf { it.price }
    val average = points.map { it.price }.average()
    val flat = maxPrice - minPrice < 0.01
    val pad = if (flat) (maxPrice * 0.05).coerceAtLeast(1.0) else (maxPrice - minPrice) * 0.16
    val yMin = minPrice - pad
    val yMax = maxPrice + pad
    val startTime = points.first().recordedAt
    val endTime = points.last().recordedAt
    val longSpan = endTime - startTime > TimeUnit.DAYS.toMillis(300)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
    ) {
        val left = 56.dp.toPx()
        val right = size.width - 6.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = size.height - 28.dp.toPx()
        val plotWidth = right - left
        val plotHeight = bottom - top

        fun yFor(value: Double): Float =
            bottom - (((value - yMin) / (yMax - yMin)) * plotHeight).toFloat()

        fun xFor(time: Long): Float {
            if (endTime == startTime) return left + plotWidth / 2f
            return left + ((time - startTime).toFloat() / (endTime - startTime).toFloat()) * plotWidth
        }

        listOf(maxPrice, average, minPrice).forEach { value ->
            val y = yFor(value)
            drawLine(
                color = extra.hairline.copy(alpha = 0.9f),
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = if (value == average) dashedEffect(extra.hairline) else null
            )
            val layout = measurer.measure(
                text = AnnotatedString(formatMoney(value, currencyCode)),
                style = labelStyle
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(left - 8.dp.toPx() - layout.size.width, y - layout.size.height / 2f)
            )
        }

        val linePath = Path()
        var lastX = left
        var lastY = bottom
        points.forEachIndexed { index, point ->
            val x = xFor(point.recordedAt)
            val y = yFor(point.price)
            if (index == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
            lastX = x
            lastY = y
        }

        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(lastX, bottom)
            lineTo(xFor(points.first().recordedAt), bottom)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.20f), lineColor.copy(alpha = 0f)),
                startY = top,
                endY = bottom
            )
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        drawCircle(
            color = lineColor.copy(alpha = 0.18f),
            radius = 8.dp.toPx(),
            center = Offset(lastX, lastY)
        )
        drawCircle(
            color = lineColor,
            radius = 3.5.dp.toPx(),
            center = Offset(lastX, lastY)
        )

        val dateStyle = labelStyle.copy(letterSpacing = 0.4.sp)
        val startLabel = measurer.measure(
            AnnotatedString(
                if (longSpan) formatDateWithYear(startTime) else formatDateShort(startTime)
            ),
            dateStyle
        )
        drawText(startLabel, topLeft = Offset(left, bottom + 9.dp.toPx()))

        val endLabel = measurer.measure(
            AnnotatedString(
                if (longSpan) formatDateWithYear(endTime) else formatDateShort(endTime)
            ),
            dateStyle
        )
        drawText(
            endLabel,
            topLeft = Offset(right - endLabel.size.width, bottom + 9.dp.toPx())
        )
    }
}
