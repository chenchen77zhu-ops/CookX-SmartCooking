package com.smartcooking.app.temperature

import com.smartcooking.app.data.Recipe
import com.smartcooking.app.data.RecipeStep
import com.smartcooking.app.core.asDouble
import com.smartcooking.app.core.asText
import com.smartcooking.app.device.TemperatureSample
import kotlinx.serialization.json.JsonArray
import kotlin.math.abs
import kotlin.math.sqrt

val PHASES = listOf("preheat", "drop", "recovery", "steady", "cooling", "unknown")
val PHASE_LABELS = mapOf("preheat" to "预热", "drop" to "降温过渡", "recovery" to "回温", "steady" to "温度稳定", "cooling" to "冷却", "unknown" to "不确定")
val QUALITY_LABELS = mapOf("usable" to "测量可用", "suspect" to "测量待确认", "invalid" to "测量失效")
val EVENT_TYPES = listOf("ingredient_added", "probe_moved", "heat_off")

data class CookingContext(val recipeId: String = "", val stepId: String = "", val method: String = "unknown", val targetRange: Pair<Double, Double>? = null) {
    companion object {
        /** Parses a step's target range, e.g. "150-170℃" or [150,170]; anything implausible is ignored. */
        fun adapt(recipe: Recipe?, step: RecipeStep?, index: Int): CookingContext {
            val value = step?.raw?.get("temperature")
            var range: Pair<Double, Double>? = null
            if (value is JsonArray && value.size == 2) {
                val a = value[0].asDouble(); val b = value[1].asDouble()
                if (a != null && b != null) range = a to b
            } else value.asText()?.let { text ->
                Regex("^(\\d+(?:\\.\\d+)?)\\s*[-–~～至]\\s*(\\d+(?:\\.\\d+)?)\\s*(?:℃|°C|摄氏度)$", RegexOption.IGNORE_CASE).find(text.trim())?.let {
                    range = it.groupValues[1].toDouble() to it.groupValues[2].toDouble()
                }
            }
            range?.let { if (it.first < 0 || it.second > 300 || it.first >= it.second) range = null }
            return CookingContext(recipe?.json?.get("id").asText() ?: recipe?.dishName.orEmpty(), index.toString(), step?.raw?.get("method").asText() ?: "unknown", range)
        }
    }
}

data class Assessment(
    val quality: String = "invalid",
    val phase: String = "unknown",
    val risk: String = "unknown",
    val temperature: Double? = null,
    val slope: Double? = null,
    val reasons: List<String> = emptyList(),
    val suggestion: String? = null,
    val alert: Pair<Long, String>? = null,
    val updatedAt: Long? = null,
    val epoch: Int = 0,
    val modelVersion: String = "rules-v1",
) {
    val phaseLabel: String get() = PHASE_LABELS[phase] ?: "不确定"
    val qualityLabel: String get() = QUALITY_LABELS[quality] ?: quality
    companion object { fun blank(reason: String) = Assessment(reasons = listOf(reason)) }
}

private data class Seg(val sample: TemperatureSample, val segment: Int)

/** Pure causal rules engine; port of `temperature/engine.js`. */
class TemperatureEngine(private val qualityGate: Boolean = true, private val maxSampleGapMs: Long = 1500) {
    private var samples = mutableListOf<Seg>()
    private var context = CookingContext()
    private var previous: TemperatureSample? = null
    private var segment = 0
    private var acceptedPhase = "unknown"
    private var candidate = ""
    private var candidateSince = 0L
    private var stableSince: Long? = null
    private var highSince: Long? = null
    private var lastAlert = Long.MIN_VALUE / 2
    private var lastEvent: Pair<String, Long>? = null
    var epoch = 0
        private set
    var snapshot = Assessment.blank("等待连续温度数据")
        private set

    private fun clearSegment() { samples = mutableListOf(); segment++; acceptedPhase = "unknown"; candidate = ""; stableSince = null; highSince = null }

    fun reset(reason: String = "等待连续温度数据"): Assessment {
        clearSegment(); previous = null; lastEvent = null; epoch++
        snapshot = Assessment.blank(reason); return snapshot
    }

    fun confirm(type: String, at: Long) {
        if (type !in EVENT_TYPES) return
        clearSegment(); epoch++; lastEvent = type to at; snapshot = Assessment.blank("操作已记录，重新建立连续窗口")
    }

    fun setContext(next: CookingContext) {
        if (next != context) { context = next; clearSegment(); lastEvent = null; epoch++; snapshot = Assessment.blank("步骤已更新，重新判断") }
    }

    val currentContext: CookingContext get() = context
    fun window(): List<TemperatureSample> = samples.map { it.sample }

    fun push(sample: TemperatureSample): Assessment {
        val t = sample.updatedAt
        val prev = previous
        if (prev != null && t <= prev.updatedAt) return snapshot
        val temp = sample.temperature
        if (!sample.valid || temp == null || !temp.isFinite() || temp < -70 || temp > 380) {
            reset("设备上报无效读数"); previous = sample; return snapshot
        }
        val dt = if (prev != null) (t - prev.updatedAt) / 1000.0 else 0.5
        val discontinuity = sample.discontinuity || (prev != null && (dt * 1000 > maxSampleGapMs || sample.source != prev.source))
        val jump = prev?.valid == true && prev.temperature != null && dt > 0 && abs(temp - prev.temperature) > maxOf(12.0, 12 * dt)
        if (discontinuity || (qualityGate && jump)) { clearSegment(); epoch++ }
        previous = sample
        samples.add(Seg(sample, segment))
        samples = samples.filter { t - it.sample.updatedAt <= 60_000 }.takeLast(120).toMutableList()
        val recent = samples.takeLast(20).map { it.sample }
        val last5 = samples.takeLast(5).map { it.sample.temperature!! }.sorted()
        val filtered = last5[last5.size / 2]
        val t0 = recent.first().updatedAt
        val span = (recent.last().updatedAt - t0) / 1000.0
        val meanX = recent.sumOf { (it.updatedAt - t0) / 1000.0 } / recent.size
        val meanY = recent.sumOf { it.temperature!! } / recent.size
        var num = 0.0; var den = 0.0
        for (x in recent) { val dx = (x.updatedAt - t0) / 1000.0 - meanX; num += dx * (x.temperature!! - meanY); den += dx * dx }
        val slope = if (den != 0.0) num / den else 0.0
        val residual = sqrt(recent.sumOf { val r = it.temperature!! - meanY - slope * ((it.updatedAt - t0) / 1000.0 - meanX); r * r } / recent.size)
        val stable = !discontinuity && !jump && residual < 4
        stableSince = if (stable) (stableSince ?: t) else null
        val quality = if (qualityGate && (span < 4.5 || stableSince == null || t - stableSince!! < 5000)) "suspect" else "usable"
        var phase = "unknown"
        val reasons = mutableListOf<String>()
        val event = lastEvent
        val eventFresh = event != null && t >= event.second && t - event.second < 60_000
        if (quality == "usable") {
            phase = when {
                slope > 0.3 -> if (eventFresh && event!!.first == "ingredient_added") "recovery" else "preheat"
                slope < -0.3 -> if (eventFresh && event!!.first == "heat_off") "cooling" else if (eventFresh && event!!.first == "ingredient_added") "drop" else "unknown"
                else -> "steady"
            }
            if (phase != candidate) { candidate = phase; candidateSince = t }
            if (phase == "unknown" || t - candidateSince >= 2000) acceptedPhase = phase
            phase = acceptedPhase
            reasons.add("近段温变 ${"%.2f".format(slope)} ℃/秒")
            if (phase == "unknown") reasons.add("阶段证据尚不明确，可确认投料、关火或探头移动")
        } else {
            acceptedPhase = "unknown"; candidate = ""; highSince = null
            reasons.add(if (jump) "温度突变：可能是投料、遮挡或测量目标变化" else if (discontinuity) "数据不连续，已重建窗口" else "正在积累稳定测量；移动时可能无法辨认阶段")
        }
        var risk = "unknown"; var suggestion: String? = null
        val range = context.targetRange
        if (quality == "usable") {
            val elevated = filtered > 230 || (range != null && filtered > range.second + 10)
            highSince = if (elevated) (highSince ?: t) else null
            risk = if (elevated) (if (t - highSince!! >= 10_000) "danger" else "warning") else "observing"
            suggestion = when {
                risk == "danger" -> "持续高温，请检查锅内情况与测温位置，必要时降低火力。"
                risk == "warning" -> "温度偏高，请核对当前步骤与测温位置。"
                phase != "unknown" && range != null -> when {
                    filtered < range.first -> "低于当前步骤目标温区，请结合操作状态确认。"
                    filtered > range.second -> "高于当前步骤目标温区，请结合操作状态确认。"
                    else -> "处于当前步骤目标温区，仍需观察锅内情况。"
                }
                else -> null
            }
            reasons.add(if (range != null) "步骤目标 ${"%.0f".format(range.first)}–${"%.0f".format(range.second)} ℃" else "缺少明确步骤温区，仅判断温度趋势")
        }
        var alert: Pair<Long, String>? = null
        if (risk in listOf("warning", "danger") && (risk != snapshot.risk || t - lastAlert >= 30_000)) { alert = t to risk; lastAlert = t }
        snapshot = Assessment(quality, phase, risk, filtered, slope, reasons, suggestion, alert, t, epoch)
        return snapshot
    }

    fun expire(now: Long): Assessment {
        val prev = previous
        if (prev != null && now - prev.updatedAt >= 5000 && snapshot.quality != "invalid") reset("温度已超时，不能继续判断")
        return snapshot
    }
}

const val WINDOW = 120
const val CHANNELS = 8

/** Feature contract shared with the research pipeline: 120 × 8, 0.5 s resampling. */
fun buildFeatures(samples: List<TemperatureSample>, context: CookingContext): FloatArray {
    val out = FloatArray(WINDOW * CHANNELS)
    if (samples.isEmpty()) return out
    val end = samples.last().updatedAt
    var cursor = -1
    var previous: TemperatureSample? = null
    for (i in 0 until WINDOW) {
        val at = end - (WINDOW - 1 - i) * 500L
        while (cursor + 1 < samples.size && samples[cursor + 1].updatedAt <= at) cursor++
        val s = samples.getOrNull(cursor)
        val temp = s?.temperature
        val valid = s != null && s.valid && temp != null && temp.isFinite() && at - s.updatedAt < 750
        if (!valid) { previous = null; continue }
        val dt = if (previous != null) (s!!.updatedAt - previous.updatedAt) / 1000.0 else 0.5
        val range = context.targetRange
        val rate = if (previous != null && dt > 0) ((temp!! - previous.temperature!!) / dt).coerceIn(-5.0, 5.0) / 5 else 0.0
        val values = doubleArrayOf(temp!! / 300, rate, dt.coerceIn(0.0, 5.0) / 5, 1.0, (s!!.ambientTemperature ?: 0.0) / 100,
            (range?.first ?: 0.0) / 300, (range?.second ?: 0.0) / 300, if (range != null) 1.0 else 0.0)
        for (c in 0 until CHANNELS) out[i * CHANNELS + c] = values[c].toFloat()
        previous = s
    }
    return out
}

data class ForecastPoint(val seconds: Int, val low: Double, val median: Double, val high: Double)
data class Prediction(val phase: String, val probability: Double, val forecast: List<ForecastPoint>, val at: Long) {
    val phaseLabel: String get() = PHASE_LABELS[phase] ?: "不确定"
    val note: String get() = "仿真训练模型估计，条件变化后失效；模型概率不是实机准确率。"
}

/** Accepts a model output only for the current window, usable quality and a confident phase. */
fun acceptPrediction(logits: FloatArray, forecast: FloatArray, qualityLogit: Float, epoch: Int, currentEpoch: Int, quality: String, at: Long, now: Long): Prediction? {
    if (epoch != currentEpoch || quality != "usable" || now - at > 2500 || now < at) return null
    if (forecast.size != 9 || logits.size != 6 || !forecast.all { it.isFinite() } || !logits.all { it.isFinite() } || !qualityLogit.isFinite()) return null
    val max = logits.max()
    val exp = logits.map { kotlin.math.exp((it - max).toDouble()) }
    val probability = exp.max() / exp.sum()
    if (probability < .55 || qualityLogit < 0) return null
    val points = listOf(5, 15, 30).mapIndexed { i, s -> ForecastPoint(s, forecast[i * 3] * 300.0, forecast[i * 3 + 1] * 300.0, forecast[i * 3 + 2] * 300.0) }
    if (points.any { it.low > it.median || it.median > it.high || it.low < -70 || it.high > 380 }) return null
    return Prediction(PHASES[logits.indexOfFirst { it == max }], probability, points, at)
}
