package com.smartcooking.app.feature.live

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartcooking.app.ui.theme.KitchenColors
import com.smartcooking.app.ui.theme.NumericFont
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val gridColor = Color(0x1FFFFFFF)
private val labelColor = Color(0x99F6F3EE)

private fun hm(at: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = at }
    return "%d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}

/**
 * Temperature trend on a 50–250 °C scale: measured line (orange gradient), glowing end point with
 * its value, optional dashed prediction. With no data a faint placeholder curve keeps the card
 * from looking broken.
 */
@Composable
fun TempChart(
    points: List<LivePoint>,
    now: Long,
    modifier: Modifier = Modifier,
    prediction: List<LivePoint> = emptyList(),
    alert: Boolean = false,
    height: Dp = 96.dp,
    showValue: Boolean = true,
    emptyText: String = "连接 CookX Sense 后显示实时曲线",
) {
    val measurer = rememberTextMeasurer()
    val lo = 50.0; val hi = 250.0
    val end = max(now, points.lastOrNull()?.at ?: now)
    val span = when {
        points.size < 2 -> 6 * 60_000L
        else -> (end - points.first().at).coerceIn(60_000L, LiveRules.HISTORY_MS)
    }
    val predictionSpan = if (prediction.isNotEmpty()) (prediction.last().at - end).coerceAtLeast(0) else 0L
    val start = end - span
    val total = (span + predictionSpan).toFloat()
    val lineColors = if (alert) listOf(KitchenColors.AlertSoft, KitchenColors.Alert) else listOf(Color(0xFFFFC27A), KitchenColors.Heat)
    val labelStyle = TextStyle(color = labelColor, fontSize = 10.5.sp, fontFamily = NumericFont, fontWeight = FontWeight.Medium)

    Canvas(modifier.fillMaxWidth().height(height + 18.dp)) {
        val axisW = 30.dp.toPx()
        val plotW = size.width - axisW - 4.dp.toPx()
        val plotH = height.toPx() - 6.dp.toPx()
        val top = 6.dp.toPx()
        fun x(at: Long) = axisW + ((at - start) / total).coerceIn(0f, 1f) * plotW
        fun y(t: Double) = top + (1f - ((t - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f)) * plotH

        // Grid and Y labels
        listOf(250.0, 150.0, 50.0).forEach { v ->
            val yy = y(v)
            drawLine(gridColor, Offset(axisW, yy), Offset(axisW + plotW, yy), strokeWidth = 1f)
            val layout = measurer.measure("${v.roundToInt()}", labelStyle)
            drawText(layout, topLeft = Offset(0f, yy - layout.size.height / 2f))
        }
        // X labels: four clock ticks, the last one "现在"
        val ticks = 4
        for (i in 0 until ticks) {
            val at = start + span * i / (ticks - 1)
            val text = if (i == ticks - 1) "现在" else if (span < 150_000) "-${((end - at) / 1000)}秒" else hm(at)
            val layout = measurer.measure(text, labelStyle)
            val cx = x(at) - layout.size.width / 2f
            drawText(layout, topLeft = Offset(cx.coerceIn(axisW - 6.dp.toPx(), size.width - layout.size.width.toFloat()), top + plotH + 6.dp.toPx()))
        }
        if (points.size < 2) {
            // Placeholder curve
            val path = Path()
            for (i in 0..40) {
                val f = i / 40f
                val px = axisW + f * plotW
                val py = top + plotH * (0.78f - 0.5f * (1f - (1f - f) * (1f - f)) + 0.03f * sin(f * 9f))
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, Color(0x33FFFFFF), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
            val layout = measurer.measure(emptyText, labelStyle.copy(fontFamily = null, color = Color(0xB3F6F3EE), fontSize = 12.sp))
            drawText(layout, topLeft = Offset(axisW + (plotW - layout.size.width) / 2f, top + plotH * 0.36f))
            return@Canvas
        }
        val visible = points.filter { it.at >= start }
        val path = Path()
        visible.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.at), y(p.t)) else path.lineTo(x(p.at), y(p.t)) }
        drawPath(path, Brush.horizontalGradient(lineColors, startX = axisW, endX = axisW + plotW), style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round))
        if (prediction.isNotEmpty()) {
            val p2 = Path().apply {
                moveTo(x(visible.last().at), y(visible.last().t))
                prediction.forEach { lineTo(axisW + ((it.at - start) / total).coerceIn(0f, 1f) * plotW, y(it.t)) }
            }
            drawPath(p2, Color(0xB3F6F3EE), style = Stroke(1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 9f))))
        }
        val last = visible.last()
        val c = Offset(x(last.at), y(last.t))
        val dotColor = if (alert) KitchenColors.Alert else KitchenColors.Heat
        drawCircle(dotColor.copy(alpha = 0.28f), radius = 9.dp.toPx(), center = c)
        drawCircle(Color.White, radius = 5.dp.toPx(), center = c)
        drawCircle(dotColor, radius = 3.2.dp.toPx(), center = c)
        if (showValue) {
            val layout = measurer.measure("${last.t.roundToInt()}°C", TextStyle(color = if (alert) KitchenColors.AlertSoft else Color(0xFFFFA24A), fontSize = 13.sp, fontFamily = NumericFont, fontWeight = FontWeight.SemiBold))
            val lx = (c.x - layout.size.width + 6.dp.toPx()).coerceIn(axisW + 2.dp.toPx(), size.width - layout.size.width.toFloat())
            val ly = (c.y - layout.size.height - 8.dp.toPx()).coerceAtLeast(0f)
            drawText(layout, topLeft = Offset(lx, ly))
        }
    }
}

/** Legend row under the cooking chart: ● 当前温度  - - 预测曲线. */
@Composable
fun ChartLegend(modifier: Modifier = Modifier, alert: Boolean = false) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(8.dp)) { drawCircle(if (alert) KitchenColors.Alert else KitchenColors.Heat) }
        Spacer(Modifier.width(6.dp))
        Text("当前温度", color = labelColor, fontSize = 11.sp)
        Spacer(Modifier.width(22.dp))
        Canvas(Modifier.size(width = 18.dp, height = 2.dp)) {
            drawLine(Color(0xB3F6F3EE), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f)))
        }
        Spacer(Modifier.width(6.dp))
        Text("预测曲线", color = labelColor, fontSize = 11.sp)
    }
}

/**
 * 270° arc gauge opening at the bottom, with a thick orange gradient value arc and a knob at its
 * start; the centre slot holds the reading. Red when [alert].
 */
@Composable
fun ArcGauge(fraction: Float, modifier: Modifier = Modifier, alert: Boolean = false, stroke: Dp = 13.dp, content: @Composable () -> Unit) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(800), label = "gauge")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val sw = stroke.toPx()
            val d = min(size.width, size.height) - sw
            val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val arcSize = Size(d, d)
            drawArc(Color(0x29FFFFFF), 135f, 270f, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            if (animated > 0f) {
                val colors = if (alert) listOf(KitchenColors.AlertSoft, KitchenColors.Alert, Color(0xFFD1170C)) else listOf(KitchenColors.HeatSoft, KitchenColors.Heat, KitchenColors.HeatDeep)
                drawArc(
                    Brush.sweepGradient(listOf(colors[2], colors[0], colors[1], colors[2]), center = center),
                    135f, 270f * animated, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round),
                )
                val a = Math.toRadians(135.0)
                val knob = Offset(center.x + (d / 2f) * cos(a).toFloat(), center.y + (d / 2f) * sin(a).toFloat())
                drawCircle(colors[0], radius = sw * 0.42f, center = knob)
            }
        }
        content()
    }
}

/** Column helper so chart cards share paddings. */
@Composable
fun ChartColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.padding(top = 2.dp)) { content() }
}
