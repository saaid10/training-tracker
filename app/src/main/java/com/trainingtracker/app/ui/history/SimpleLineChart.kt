package com.trainingtracker.app.ui.history

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One plotted session: when it happened and the metric's score for it. */
data class ChartPoint(val loggedAt: Long, val value: Double)

/** Minimal dependency-free line chart with axes for the History & Graphs screen. */
@Composable
fun SimpleLineChart(pointsOldestFirst: List<ChartPoint>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current

    val axisLabelPaint = remember(labelColor) {
        Paint().apply {
            color = labelColor.toArgb()
            textSize = with(density) { 11.sp.toPx() }
            isAntiAlias = true
        }
    }
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

    Canvas(modifier = modifier.fillMaxWidth().height(220.dp)) {
        if (pointsOldestFirst.size < 2) return@Canvas

        val leftMargin = with(density) { 48.dp.toPx() }
        val bottomMargin = with(density) { 28.dp.toPx() }
        val topMargin = with(density) { 8.dp.toPx() }
        val rightMargin = with(density) { 8.dp.toPx() }

        val plotWidth = size.width - leftMargin - rightMargin
        val plotHeight = size.height - topMargin - bottomMargin

        val values = pointsOldestFirst.map { it.value }
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0.0 } ?: 1.0
        val stepX = if (pointsOldestFirst.size > 1) plotWidth / (pointsOldestFirst.size - 1) else 0f

        fun xFor(index: Int) = leftMargin + index * stepX
        fun yFor(value: Double) = topMargin + plotHeight - (((value - min) / range) * plotHeight).toFloat()

        // Axes
        drawLine(
            color = axisColor,
            start = Offset(leftMargin, topMargin),
            end = Offset(leftMargin, topMargin + plotHeight),
            strokeWidth = 2f,
        )
        drawLine(
            color = axisColor,
            start = Offset(leftMargin, topMargin + plotHeight),
            end = Offset(leftMargin + plotWidth, topMargin + plotHeight),
            strokeWidth = 2f,
        )

        // Y-axis labels: max, mid, min
        val yLabelValues = listOf(max, (max + min) / 2.0, min)
        yLabelValues.forEachIndexed { i, v ->
            val y = topMargin + (plotHeight * i / 2f)
            drawContext.canvas.nativeCanvas.drawText(
                formatAxisValue(v),
                0f,
                y + axisLabelPaint.textSize / 3f,
                axisLabelPaint,
            )
        }

        // X-axis labels: first, middle, last session dates
        val labelIndices = if (pointsOldestFirst.size <= 2) {
            listOf(0, pointsOldestFirst.lastIndex)
        } else {
            listOf(0, pointsOldestFirst.size / 2, pointsOldestFirst.lastIndex)
        }.distinct()
        labelIndices.forEach { index ->
            val label = dateFormat.format(Date(pointsOldestFirst[index].loggedAt))
            val textWidth = axisLabelPaint.measureText(label)
            val x = (xFor(index) - textWidth / 2f).coerceIn(0f, size.width - textWidth)
            drawContext.canvas.nativeCanvas.drawText(
                label,
                x,
                topMargin + plotHeight + with(density) { 18.dp.toPx() },
                axisLabelPaint,
            )
        }

        // Line + points
        val offsets = pointsOldestFirst.mapIndexed { index, point -> Offset(xFor(index), yFor(point.value)) }
        for (i in 0 until offsets.size - 1) {
            drawLine(color = lineColor, start = offsets[i], end = offsets[i + 1], strokeWidth = 5f)
        }
        offsets.forEach { offset ->
            drawCircle(color = Color.White, radius = 9f, center = offset)
            drawCircle(color = lineColor, radius = 7f, center = offset)
        }
    }
}

private fun formatAxisValue(value: Double): String = when {
    value >= 100 -> value.toInt().toString()
    else -> "%.1f".format(value)
}
