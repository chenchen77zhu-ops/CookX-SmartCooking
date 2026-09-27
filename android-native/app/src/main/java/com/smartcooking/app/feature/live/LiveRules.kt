package com.smartcooking.app.feature.live

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Pure rules behind the live kitchen: heat stages, background state, target parsing, heating rate,
 * ETA and over-temperature alerts. They only drive presentation (copy, colours, backgrounds); the
 * temperature engine stays the source of truth for measurement quality.
 */
enum class HeatStage(val label: String, val min: Double, val title: String, val hint: String) {
    Preheat("预热", Double.NEGATIVE_INFINITY, "预热中", "锅还没热，保持中火"),
    Heating("升温", 90.0, "加热中", "接近煎香区，保持火力"),
    Sear("煎香", 150.0, "煎香区", "温度合适，可以放入食材"),
    Cook("烹饪", 200.0, "高温烹饪", "火力较大，注意快速翻动");

    companion object {
        fun of(temperature: Double?): HeatStage? {
            if (temperature == null || !temperature.isFinite()) return null
            return entries.last { temperature >= it.min }
        }
    }
}

/** The five kitchen photographs, from a cold pan to smoking oil. */
enum class Backdrop { Idle, Preheat, Heating, Sear, Overheat }

enum class AlertLevel { Warn, Danger }

data class LiveAlert(val level: AlertLevel, val title: String, val text: String)

data class LivePoint(val at: Long, val t: Double)

data class LiveAdvice(val title: String, val text: String, val tone: Tone) {
    enum class Tone { Normal, Good, Warn, Danger }
}

/** The part of the active cooking session the live surfaces show. */
data class LiveSession(
    val id: String,
    val dish: String,
    val imageUrl: String?,
    val stepIndex: Int,
    val total: Int,
    val stepTitle: String,
    val stepText: String,
    val nextTitle: String,
    val target: Pair<Double, Double>?,
    val remainingMs: Long,
    val paused: Boolean,
    val timed: Boolean,
    val totalSeconds: Double,
    val futureSeconds: Double,
)

object LiveRules {
    const val HISTORY_MS = 6 * 60 * 1000L
    const val MUTE_MS = 60 * 1000L
    const val GAUGE_MAX = 250.0
    const val DANGER_ABSOLUTE = 260.0
    const val WARN_ABSOLUTE = 235.0


    /** Reads a step's target zone, e.g. "160–180 ℃" or "约 160°C" (±10 °C). */
    fun parseTarget(value: String?): Pair<Double, Double>? {
        val text = value?.trim() ?: return null
        val range = Regex("""^(\d+(?:\.\d+)?)\s*[-–~～至]\s*(\d+(?:\.\d+)?)\s*(?:℃|°C|摄氏度)$""", RegexOption.IGNORE_CASE).matchEntire(text)
        if (range != null) {
            val lo = range.groupValues[1].toDouble(); val hi = range.groupValues[2].toDouble()
            return if (lo in 40.0..300.0 && hi in 40.0..300.0 && lo < hi) lo to hi else null
        }
        val single = Regex("""^(?:约\s*)?(\d+(?:\.\d+)?)\s*(?:℃|°C|摄氏度)$""", RegexOption.IGNORE_CASE).matchEntire(text)
        val t = single?.groupValues?.get(1)?.toDouble() ?: return null
        return if (t in 50.0..290.0) (t - 10) to (t + 10) else null
    }

    /** Least-squares heating rate over the last 20 s, in °C per second; null with too few points. */
    fun heatingRate(history: List<LivePoint>): Double? {
        val last = history.lastOrNull()?.at ?: return null
        val window = history.filter { last - it.at <= 20_000 }
        if (window.size < 5) return null
        val mx = window.sumOf { it.at.toDouble() } / window.size
        val my = window.sumOf { it.t } / window.size
        val num = window.sumOf { (it.at - mx) * (it.t - my) }
        val den = window.sumOf { (it.at - mx) * (it.at - mx) }
        return if (den == 0.0) null else num / den * 1000
    }

    /** Seconds until the lower bound of [target] is reached; 0 when already there. */
    fun targetEta(temperature: Double?, target: Pair<Double, Double>?, history: List<LivePoint>): Double? {
        if (temperature == null || target == null) return null
        if (temperature >= target.first) return 0.0
        val rate = heatingRate(history) ?: return null
        if (rate < 0.05) return null
        return (target.first - temperature) / rate
    }

    fun alert(temperature: Double?, target: Pair<Double, Double>?, risk: String?): LiveAlert? {
        if (temperature == null && risk != "danger") return null
        val t = temperature ?: Double.NaN
        val hi = target?.second
        if (risk == "danger" || t >= DANGER_ABSOLUTE || (hi != null && t > hi + 40)) {
            return LiveAlert(AlertLevel.Danger, "锅温过高", "已超出安全范围，请立即调小火力或离火")
        }
        if (risk == "warning" || t >= WARN_ABSOLUTE || (hi != null && t > hi + 15)) {
            return LiveAlert(AlertLevel.Warn, "温度偏高", "建议调小火力，避免油冒烟")
        }
        return null
    }

    fun backdrop(temperature: Double?, alert: LiveAlert?): Backdrop = when {
        alert != null -> Backdrop.Overheat
        temperature == null || temperature < 50 -> Backdrop.Idle
        temperature < 90 -> Backdrop.Preheat
        temperature < 150 -> Backdrop.Heating
        else -> Backdrop.Sear
    }

    /** Copy for the stage pill and the "CookX 建议" card. */
    fun advice(temperature: Double?, session: LiveSession?, alert: LiveAlert?, suggestion: String?, history: List<LivePoint>): LiveAdvice? {
        if (alert != null) return LiveAdvice(alert.title, alert.text, if (alert.level == AlertLevel.Danger) LiveAdvice.Tone.Danger else LiveAdvice.Tone.Warn)
        val stage = HeatStage.of(temperature)
        if (!suggestion.isNullOrBlank()) return LiveAdvice(stage?.title ?: "温度分析", suggestion, LiveAdvice.Tone.Normal)
        val target = session?.target
        if (temperature != null && target != null) {
            val lo = target.first.roundToInt(); val hi = target.second.roundToInt()
            return when {
                temperature < target.first -> {
                    val eta = targetEta(temperature, target, history)
                    LiveAdvice("加热中", if (eta != null) "再等 ${max(1, eta.roundToInt())} 秒，达到 $lo°C 后放入食材" else "加热到 $lo°C 后放入食材", LiveAdvice.Tone.Normal)
                }
                temperature <= target.second -> LiveAdvice("温度合适", "已达到目标温度，可以放入食材", LiveAdvice.Tone.Good)
                else -> LiveAdvice("温度偏高", "目标 $lo–$hi°C，建议调小火力", LiveAdvice.Tone.Warn)
            }
        }
        if (stage != null) return LiveAdvice(stage.title, stage.hint, LiveAdvice.Tone.Normal)
        return null
    }

    /** "mm:ss", or "--:--" for unknown values. */
    fun clock(seconds: Double?): String {
        if (seconds == null || !seconds.isFinite() || seconds < 0) return "--:--"
        val s = seconds.roundToLong()
        return "%02d:%02d".format(s / 60, s % 60)
    }

    fun gaugeFraction(temperature: Double?): Float =
        if (temperature == null) 0f else (temperature / GAUGE_MAX).coerceIn(0.02, 1.0).toFloat()
}

/** Snapshot shared by the home card, kitchen, floating card and notification. */
data class LiveState(
    val connected: Boolean = false,
    val simulated: Boolean = false,
    val supported: Boolean = true,
    val deviceLabel: String = "未连接",
    val temperature: Double? = null,
    val history: List<LivePoint> = emptyList(),
    val session: LiveSession? = null,
    val risk: String? = null,
    val suggestion: String? = null,
    val qualityLabel: String = "",
    val measurementUsable: Boolean = false,
    val mutedUntil: Long = 0,
    val now: Long = 0,
) {
    val target: Pair<Double, Double>? get() = session?.target
    val alert: LiveAlert? get() = if (measurementUsable) LiveRules.alert(temperature, target, risk) else null
    val stage: HeatStage? get() = if (measurementUsable) HeatStage.of(temperature) else null
    val backdrop: Backdrop get() = LiveRules.backdrop(temperature, alert)
    val advice: LiveAdvice? get() = if (measurementUsable) LiveRules.advice(temperature, session, alert, suggestion, history) else if (temperature != null) LiveAdvice("测量待确认", "请保持探头稳定，等待连续有效测量后再判断", LiveAdvice.Tone.Normal) else null
    val eta: Double? get() = if (measurementUsable) LiveRules.targetEta(temperature, target, history) else null
    val muted: Boolean get() = now < mutedUntil
    val online: Boolean get() = connected || simulated

    /** Status line for the Sense card: "已连接", "仿真回放" … */
    val statusText: String get() = when {
        simulated -> "仿真回放"
        connected -> "已连接"
        !supported -> "不支持蓝牙"
        else -> deviceLabel
    }

    /** Index in the five-step stepper 预热 · 升温 · 煎香 · 烹饪 · 完成. */
    val stepperIndex: Int get() = stage?.ordinal ?: -1
}
