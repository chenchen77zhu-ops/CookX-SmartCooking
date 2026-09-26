package com.smartcooking.app.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.util.UUID

/** Client for the `/api/v3` multi-user modules (households, shopping, community …). */
class BusinessApi(private val api: ApiClient, private val sessions: SessionManager) {
    suspend fun call(path: String, method: String = "GET", body: JsonObject? = null, timeoutMs: Long = 20_000): JsonObject {
        val user = sessions.currentUserId
        val result = api.send(method.uppercase(), "/v3$path", body = body, timeoutMs = timeoutMs)
        if (sessions.currentUserId != user) throw IllegalStateException("账号已切换，请重新打开页面")
        val obj = result.asObject() ?: JsonObject(emptyMap())
        if (obj.str("status") == "error") throw ApiException(obj.str("message") ?: "操作未完成")
        return obj
    }

    suspend fun get(path: String) = call(path)
}

fun commandKey(): String = UUID.randomUUID().toString()

/** A write whose outcome is unknown until the server confirms it; retried with the same key. */
data class PendingWrite(val path: String, val method: String, val body: JsonObject) {
    fun toJson(): JsonObject = jsonOf("path" to path, "method" to method, "body" to body)

    companion object {
        fun from(json: JsonElement?): PendingWrite? {
            val obj = json.asObject() ?: return null
            return PendingWrite(obj.str("path") ?: return null, obj.str("method") ?: "post", obj.obj("body") ?: EmptyObject)
        }
    }
}

/**
 * Port of the web `usePendingCommand`: every business write carries an idempotency key that is
 * persisted before sending. A lost response is retried with the original key, never a new one.
 */
class PendingCommand(
    private val scope: String,
    private val business: BusinessApi,
    private val store: KeyValueStore,
    private val sessions: SessionManager,
    private val onSuccess: suspend (JsonObject) -> Unit = {},
) {
    private val user = sessions.currentUserId
    private val key = "cookx:pending:$scope:v1:$user"

    private val _pending = MutableStateFlow<PendingWrite?>(null)
    val pending: StateFlow<PendingWrite?> = _pending.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _error = MutableStateFlow("")
    val error: StateFlow<String> = _error.asStateFlow()

    init {
        val raw = store.get(key)
        if (raw != null) {
            _pending.value = PendingWrite.from(store.getJson(key))
            if (_pending.value == null) _error.value = "本地待确认记录损坏，请保留设备数据并联系维护者"
        }
    }

    fun setError(message: String) { _error.value = message }
    fun clearError() { _error.value = "" }

    suspend fun send(path: String, method: String, body: JsonObject = EmptyObject): Boolean {
        if (_busy.value || _pending.value != null || sessions.currentUserId != user) return false
        val write = PendingWrite(path, method, body.with("idempotency_key" to commandKey()))
        if (!store.putJsonSync(key, write.toJson())) {
            _error.value = "无法保存操作凭证，请释放设备存储后再试"
            return false
        }
        _pending.value = write
        return retry()
    }

    suspend fun retry(): Boolean {
        val write = _pending.value ?: return false
        if (_busy.value || sessions.currentUserId != user) return false
        _busy.value = true
        _error.value = ""
        try {
            val result = business.call(write.path, write.method, write.body)
            store.removeSync(key)
            _pending.value = null
            onSuccess(result)
            return true
        } catch (e: Throwable) {
            _error.value = e.userMessage()
            if (e is ApiException && e.isClientError) {
                store.removeSync(key)
                _pending.value = null
            }
            return false
        } finally {
            _busy.value = false
        }
    }

    /** Only clears the local guard after the user has checked the result; never undoes the write. */
    fun discard() {
        store.removeSync(key)
        _pending.value = null
    }
}
