package com.smartcooking.app.device

/** One temperature reading; mirrors the web `TemperatureSample` contract. */
data class TemperatureSample(
    val temperature: Double?,
    val ambientTemperature: Double?,
    val valid: Boolean,
    val updatedAt: Long,
    val receivedAt: Long = updatedAt,
    val protocolVersion: Int = 1,
    val bootId: String? = null,
    val sequence: Long? = null,
    val deviceTimeMs: Long? = null,
    val discontinuity: Boolean = false,
    val source: String = "device",
)

const val MIN_TEMPERATURE = -50.0
const val MAX_TEMPERATURE = 500.0
const val TEMPERATURE_STALE_MS = 5000L

private val decimal = Regex("^[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)$")
private val uint = Regex("^\\d+$")
private fun bounded(s: String, lo: Double, hi: Double) = decimal.matches(s) && s.toDouble() in lo..hi

/** Parses one firmware line: protocol v2 `CX2,boot,seq,ms,raw,ambient,valid` or legacy `TEMP:xx.x`. */
fun parseTemperatureFrame(line: String, receivedAt: Long): TemperatureSample? {
    val text = line.trim()
    if (text.startsWith("CX2,")) {
        val parts = text.split(",")
        if (parts.size != 7) return null
        val (_, bootId, seq, ms, raw, ambient, valid) = Seven(parts)
        if (!Regex("^[0-9a-fA-F]{8}$").matches(bootId) || !uint.matches(seq) || !uint.matches(ms)) return null
        if ((seq.toBigInteger() > 0xffffffffL.toBigInteger()) || ms.toBigInteger() > 0xffffffffL.toBigInteger() || valid !in listOf("0", "1")) return null
        if (ambient.isNotEmpty() && !bounded(ambient, -40.0, 125.0)) return null
        if (valid == "1" && !bounded(raw, -70.0, 380.0)) return null
        if (valid == "0" && raw.isNotEmpty()) return null
        return TemperatureSample(
            temperature = if (valid == "1") raw.toDouble() else null,
            ambientTemperature = ambient.takeIf { it.isNotEmpty() }?.toDouble(),
            valid = valid == "1", updatedAt = receivedAt, receivedAt = receivedAt, protocolVersion = 2,
            bootId = bootId, sequence = seq.toLong(), deviceTimeMs = ms.toLong(),
        )
    }
    val raw = if (text.startsWith("TEMP:")) text.substring(5) else text
    if (!bounded(raw, MIN_TEMPERATURE, MAX_TEMPERATURE)) return null
    return TemperatureSample(raw.toDouble(), null, true, receivedAt, receivedAt)
}

private class Seven(val p: List<String>) {
    operator fun component1() = p[0]; operator fun component2() = p[1]; operator fun component3() = p[2]
    operator fun component4() = p[3]; operator fun component5() = p[4]; operator fun component6() = p[5]; operator fun component7() = p[6]
}

/** Line-buffered stream parser with continuity checks for protocol v2 frames. */
class TemperatureStreamParser(
    private val onSample: (TemperatureSample) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    private val onReject: (String) -> Unit = {},
) {
    private val buffer = StringBuilder()
    private var dropping = false
    private var previous: TemperatureSample? = null

    private fun emit(line: String) {
        var sample = parseTemperatureFrame(line, now())
        if (sample == null) { if (line.isNotBlank()) onReject("malformed"); return }
        val prev = previous
        if (sample.protocolVersion == 2 && prev?.protocolVersion == 2) {
            if (sample.bootId != prev.bootId) sample = sample.copy(discontinuity = true)
            else {
                if (sample.sequence!! <= prev.sequence!! || sample.deviceTimeMs!! <= prev.deviceTimeMs!!) {
                    if (prev.deviceTimeMs!! > 0xffff0000L && sample.deviceTimeMs!! < 60000) sample = sample.copy(discontinuity = true)
                    else { onReject("out-of-order"); return }
                }
                if (sample.sequence != prev.sequence!! + 1 || sample.deviceTimeMs!! - prev.deviceTimeMs!! > 1500) sample = sample.copy(discontinuity = true)
            }
        } else if (prev != null && prev.protocolVersion != sample.protocolVersion) sample = sample.copy(discontinuity = true)
        previous = sample
        onSample(sample)
    }

    fun append(chunk: String) {
        for (c in chunk) {
            if (c == '\n') {
                if (!dropping) emit(buffer.toString())
                buffer.clear(); dropping = false
            } else if (!dropping) {
                buffer.append(c)
                if (buffer.length > 256) { buffer.clear(); dropping = true; onReject("oversize") }
            }
        }
    }

    fun reset() { buffer.clear(); dropping = false; previous = null }
}
