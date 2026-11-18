package it.registrovoti.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

@Composable
internal fun GradeChart(means: List<Double>, dates: List<String>, selected: Int, grades: List<Double> = emptyList(), select: (Int) -> Unit) {
    if (means.isEmpty()) return
    val fg = MaterialTheme.colorScheme.onSurface
    val line = MaterialTheme.colorScheme.primary
    val low = floor(minOf(means.min(), grades.minOrNull() ?: means.min()))
    val high = maxOf(low + 1, ceil(maxOf(means.max(), grades.maxOrNull() ?: means.max())))
    val labels = if (grades.isEmpty()) means else grades
    if (grades.isNotEmpty()) Text("Punti: voti · Linea: media progressiva", style = MaterialTheme.typography.labelMedium)
    val density = LocalDensity.current
    val paint = remember(fg, density) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = fg.toArgb()
            textSize = with(density) { 12.sp.toPx() }
        }
    }
    val ticks = listOf(high, (high + low) / 2, low)
    val tickLabels = ticks.map { display(it).removeSuffix(",00") }
    val dateLabels = dates.map { date ->
        if (date.length == 10 && date[4] == '-' && date[7] == '-') {
            date.substring(8) + "/" + date.substring(5, 7)
        } else date
    }
    val gap = with(density) { 6.dp.toPx() }
    val leftInset = maxOf(tickLabels.maxOf(paint::measureText) + gap, paint.measureText(dateLabels.first()) / 2)
    val rightInset = maxOf(gap, paint.measureText(dateLabels.last()) / 2)
    Canvas(Modifier.fillMaxWidth().height(148.dp).pointerInput(means, leftInset, rightInset) {
        detectTapGestures { tap ->
            val fraction = (tap.x - leftInset) / (size.width - leftInset - rightInset).coerceAtLeast(1f)
            select((fraction * means.lastIndex).roundToInt().coerceIn(means.indices))
        }
    }) {
        val right = (size.width - rightInset).coerceAtLeast(leftInset)
        val top = -paint.ascent()
        val bottom = size.height - (paint.descent() - paint.ascent()) - gap
        fun y(value: Double) = bottom - ((value - low) / (high - low)).toFloat() * (bottom - top)
        fun x(index: Int) = if (means.size == 1) (leftInset + right) / 2
            else leftInset + (right - leftInset) * index / means.lastIndex
        val canvas = drawContext.canvas.nativeCanvas
        paint.textAlign = android.graphics.Paint.Align.RIGHT
        ticks.forEachIndexed { i, tick ->
            val h = y(tick)
            drawLine(line.copy(alpha = .25f), Offset(leftInset, h), Offset(right, h))
            canvas.drawText(tickLabels[i], leftInset - gap, h - (paint.ascent() + paint.descent()) / 2, paint)
        }
        paint.textAlign = android.graphics.Paint.Align.CENTER
        var previousEnd = Float.NEGATIVE_INFINITY
        val lastStart = x(means.lastIndex) - paint.measureText(dateLabels.last()) / 2
        var previousDate = ""
        means.forEachIndexed { i, mean ->
            val px = x(i)
            if (i > 0) drawLine(line, Offset(x(i - 1), y(means[i - 1])), Offset(px, y(mean)), 2.dp.toPx())
            drawCircle(fg, if (i == selected) 4.dp.toPx() else 2.5.dp.toPx(), Offset(px, y(labels[i])))
            val half = paint.measureText(dateLabels[i]) / 2
            if (dates[i] != previousDate && px - half >= previousEnd + gap &&
                (i == means.lastIndex || px + half + gap <= lastStart)) {
                canvas.drawText(dateLabels[i], px, size.height - paint.descent(), paint)
                previousEnd = px + half
                previousDate = dates[i]
            }
        }
    }
}
