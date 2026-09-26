package com.smartcooking.app.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    isLenient = true
}

val EmptyObject = JsonObject(emptyMap())

fun JsonElement?.asObject(): JsonObject? = this as? JsonObject
fun JsonElement?.asArray(): JsonArray? = this as? JsonArray

/** Plain string content; numbers and booleans are rendered as text, null/objects yield null. */
fun JsonElement?.asText(): String? = (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull

fun JsonElement?.asDouble(): Double? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    if (p.isString) return p.content.trim().toDoubleOrNull()
    return p.doubleOrNull
}

fun JsonElement?.asLong(): Long? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.longOrNull ?: p.doubleOrNull?.toLong()
}

fun JsonElement?.asBool(): Boolean? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull || p.isString) return null
    return p.booleanOrNull
}

fun JsonObject.str(key: String): String? = this[key].asText()
fun JsonObject.num(key: String): Double? = this[key].asDouble()
fun JsonObject.long(key: String): Long? = this[key].asLong()
fun JsonObject.bool(key: String): Boolean = this[key].asBool() == true
fun JsonObject.obj(key: String): JsonObject? = this[key].asObject()
fun JsonObject.arr(key: String): JsonArray = this[key].asArray() ?: JsonArray(emptyList())
fun JsonObject.objects(key: String): List<JsonObject> = arr(key).mapNotNull { it.asObject() }
fun JsonObject.strings(key: String): List<String> = arr(key).mapNotNull { it.asText() }
fun JsonObject.has(key: String): Boolean = this[key] != null && this[key] !is JsonNull

/** JS-like truthiness for optional text fields. */
fun String?.orDash(fallback: String = "未知"): String = if (this.isNullOrBlank()) fallback else this

fun jsonOf(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
    for ((k, v) in pairs) put(k, v.toJsonElement())
}

fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.associate { (k, v) -> k.toString() to v.toJsonElement() })
    is Iterable<*> -> JsonArray(map { it.toJsonElement() })
    is Array<*> -> JsonArray(map { it.toJsonElement() })
    else -> JsonPrimitive(toString())
}

fun JsonObjectBuilder.put(key: String, value: Any?) {
    put(key, value.toJsonElement())
}

operator fun JsonObject.plus(other: Map<String, JsonElement>): JsonObject = JsonObject(this.toMap() + other)

fun JsonObject.with(vararg pairs: Pair<String, Any?>): JsonObject =
    JsonObject(this.toMap() + pairs.associate { (k, v) -> k to v.toJsonElement() })

fun JsonObject.without(vararg keys: String): JsonObject = JsonObject(this.toMap() - keys.toSet())

/** Numbers without a trailing ".0" – mirrors how the web client printed quantities. */
fun Double.clean(maxDecimals: Int = 2): String {
    if (this.isNaN() || this.isInfinite()) return "—"
    if (this == Math.floor(this) && kotlin.math.abs(this) < 1e15) return this.toLong().toString()
    return "%.${maxDecimals}f".format(this).trimEnd('0').trimEnd('.')
}
