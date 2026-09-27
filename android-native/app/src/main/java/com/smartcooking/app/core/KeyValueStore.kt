package com.smartcooking.app.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.json.JsonElement

/**
 * Durable key/value storage used where the web client used localStorage.
 * Writes that protect idempotency (pending commands, write transactions) use [putSync]
 * so the record is on disk before the request is sent.
 */
class KeyValueStore(context: Context, name: String = "cookx") {
    private val prefs: SharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun get(key: String): String? = prefs.getString(key, null)
    fun contains(key: String): Boolean = prefs.contains(key)

    fun put(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }

    /** Synchronous commit; returns false when the device could not persist the value. */
    fun putSync(key: String, value: String?): Boolean =
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.commit()

    fun remove(key: String) = put(key, null)
    fun removeSync(key: String) = putSync(key, null)

    fun getJson(key: String): JsonElement? = get(key)?.let { runCatching { AppJson.parseToJsonElement(it) }.getOrNull() }
    fun putJson(key: String, value: JsonElement?) = put(key, value?.toString())
    fun putJsonSync(key: String, value: JsonElement?): Boolean = putSync(key, value?.toString())

    fun keys(prefix: String): List<String> = prefs.all.keys.filter { it.startsWith(prefix) }
}
