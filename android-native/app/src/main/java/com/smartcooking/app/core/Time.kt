package com.smartcooking.app.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Date helpers that follow the JavaScript `Date.parse` rules the web client relied on:
 * a date-only string is UTC, a date-time without offset is local time.
 */
object Time {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val localInput = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    private val display = DateTimeFormatter.ofPattern("yyyy/M/d HH:mm")
    private val displayShort = DateTimeFormatter.ofPattern("M月d日 HH:mm")
    private val monthDay = DateTimeFormatter.ofPattern("M-d")
    private val clock = DateTimeFormatter.ofPattern("HH:mm")
    private val clockSeconds = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun parse(value: String?): Instant? {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return null
        runCatching { return Instant.parse(text) }
        runCatching { return OffsetDateTime.parse(text).toInstant() }
        val normalized = text.replace(' ', 'T')
        runCatching { return Instant.parse(normalized) }
        runCatching { return OffsetDateTime.parse(normalized).toInstant() }
        runCatching { return LocalDateTime.parse(normalized).atZone(zone).toInstant() }
        runCatching { return LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant() }
        return null
    }

    fun parseMillis(value: String?): Long? = parse(value)?.toEpochMilli()

    /** "yyyy-MM-ddTHH:mm" in local time, the format used by editable date fields. */
    fun localInput(value: String?): String = parse(value)?.let { localInput.format(it.atZone(zone)) } ?: ""
    fun localInput(millis: Long): String = localInput.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** Converts an editable local value to an ISO instant, rejecting DST gaps like the web version. */
    fun isoFromLocal(value: String?): String? {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (!Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}$").matches(text)) throw IllegalArgumentException("日期格式无效")
        val local = try { LocalDateTime.parse(text, localInput) } catch (e: DateTimeParseException) { throw IllegalArgumentException("日期无效") }
        val zoned = local.atZone(zone)
        if (zoned.toLocalDateTime() != local) throw IllegalArgumentException("日期无效或处于夏令时跳变区间")
        return iso(zoned.toInstant())
    }

    fun iso(instant: Instant): String = DateTimeFormatter.ISO_INSTANT.format(instant.truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
    fun isoNow(): String = iso(Instant.now())

    fun display(value: String?, fallback: String = "未知"): String = parse(value)?.let { display.format(it.atZone(zone)) } ?: fallback
    fun display(millis: Long): String = display.format(Instant.ofEpochMilli(millis).atZone(zone))
    fun displayShort(value: String?, fallback: String = "未知"): String = parse(value)?.let { displayShort.format(it.atZone(zone)) } ?: fallback
    fun monthDay(value: String?): String = parse(value)?.let { monthDay.format(it.atZone(zone)) } ?: "-"
    fun clock(millis: Long): String = clock.format(Instant.ofEpochMilli(millis).atZone(zone))
    fun clockSeconds(millis: Long): String = clockSeconds.format(Instant.ofEpochMilli(millis).atZone(zone))
    fun epochSecondsDisplay(seconds: Long): String = display(seconds * 1000)

    fun localDateText(offsetDays: Long = 0): String = LocalDate.now(zone).plusDays(offsetDays).toString()

    fun formatTimer(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        return "%02d:%02d".format(s / 60, s % 60)
    }

    fun greeting(hour: Int = LocalDateTime.now(zone).hour): String = when {
        hour < 11 -> "早上好"
        hour < 14 -> "中午好"
        hour < 18 -> "下午好"
        else -> "晚上好"
    }
}
