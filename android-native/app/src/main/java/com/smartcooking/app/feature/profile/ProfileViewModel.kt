package com.smartcooking.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcooking.app.core.AppContainer
import com.smartcooking.app.core.ApiException
import com.smartcooking.app.core.Time
import com.smartcooking.app.core.asArray
import com.smartcooking.app.core.asBool
import com.smartcooking.app.core.asObject
import com.smartcooking.app.core.obj
import com.smartcooking.app.core.str
import com.smartcooking.app.core.userMessage
import com.smartcooking.app.data.safeHistory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

data class Notice(val id: String, val type: String, val title: String, val content: String, val time: String, val read: Boolean)

data class ProfileState(
    val recipeCount: Int? = null,
    val notices: List<Notice>? = null,
    val error: String? = null,
    val updating: Boolean = false,
    val uploading: Boolean = false,
) {
    val unread: Int get() = notices?.count { !it.read } ?: 0
}

class ProfileViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ProfileState())
    val state = _state.asStateFlow()
    private val user get() = c.sessions.currentUserId

    init { refresh() }

    fun refresh() {
        val id = user ?: return
        viewModelScope.launch {
            try {
                val res = c.api.get("/user/$id").asObject()
                if (res?.str("status") == "success") res.obj("user")?.let(c.sessions::updateUser)
                else if (res?.str("message") == "用户不存在") { logout {} ; return@launch }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                _state.update { it.copy(error = "用户信息同步失败：${e.userMessage()}") }
            }
        }
        viewModelScope.launch { loadNotices() }
        viewModelScope.launch {
            runCatching { safeHistory(c.api.get("/chat-history", mapOf("user_id" to id))).count { it.recipe != null } }
                .onSuccess { n -> _state.update { it.copy(recipeCount = n) } }
        }
    }

    suspend fun loadNotices() {
        val id = user ?: return
        runCatching {
            c.api.get("/notifications", mapOf("user_id" to id)).asArray().orEmpty().mapNotNull { it.asObject() }.map {
                Notice(it.str("id").orEmpty(), it.str("type") ?: "system", it.str("title").orEmpty(), it.str("content").orEmpty(), it.str("time").orEmpty(),
                    it["isRead"].asBool() == true || it["is_read"].asBool() == true)
            }
        }.onSuccess { list -> _state.update { it.copy(notices = list) } }
    }

    fun markRead(id: String, onResult: (String) -> Unit) {
        val uid = user ?: return
        viewModelScope.launch {
            try {
                val res = c.api.post("/notifications/read", query = mapOf("user_id" to uid, "msg_id" to id)).asObject()
                if (res?.str("status") == "success") {
                    _state.update { s -> s.copy(notices = s.notices?.map { if (it.id == id) it.copy(read = true) else it }) }
                    onResult("已标记已读")
                } else onResult("操作失败")
            } catch (e: CancellationException) { throw e } catch (e: Exception) { onResult("操作失败") }
        }
    }

    /** Uploads a new avatar; the returned private media path is saved with the profile. */
    fun uploadAvatar(bytes: ByteArray, onUploaded: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(uploading = true) }
            try {
                val res = c.api.upload("/upload-avatar", "file", "avatar.jpg", "image/jpeg", bytes).asObject()
                val url = res?.str("avatar_url")
                if (res?.str("status") == "success" && url != null) onUploaded(url) else onError(res?.str("message") ?: "上传失败")
            } catch (e: CancellationException) { throw e } catch (e: Exception) { onError(e.userMessage()) }
            finally { _state.update { it.copy(uploading = false) } }
        }
    }

    fun updateProfile(nickname: String, phone: String, avatar: String?, onDone: (String?) -> Unit) {
        val id = user ?: return
        viewModelScope.launch {
            _state.update { it.copy(updating = true) }
            try {
                val res = c.api.put("/user/$id", query = mapOf("nickname" to nickname, "phone" to phone, "avatar" to avatar)).asObject()
                if (res?.str("status") == "success") { res.obj("user")?.let(c.sessions::updateUser); onDone(null) }
                else onDone(res?.str("message") ?: "更新失败，请稍后重试")
            } catch (e: CancellationException) { throw e } catch (e: Exception) { onDone(e.userMessage()) }
            finally { _state.update { it.copy(updating = false) } }
        }
    }

    /** Revokes the server session first; a failure keeps the user signed in so it can be retried. */
    fun logout(onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                if (c.sessions.session.value?.token != null) c.api.post("/auth/logout", timeoutMs = 10_000)
                c.sessions.clear()
            } catch (e: ApiException) {
                if (e.status == 401) c.sessions.clear() else onError("退出未确认，请恢复网络后重试")
            }
        }
    }

    fun deleteAccount(onDone: (String) -> Unit) {
        val id = user ?: return
        viewModelScope.launch {
            try {
                val res = c.api.delete("/user/$id").asObject()
                if (res?.str("status") != "success") throw ApiException(res?.str("message") ?: "注销失败")
                c.sessions.clear()
                onDone("账户已注销")
            } catch (e: CancellationException) { throw e } catch (e: Exception) { onDone("注销失败：${e.userMessage()}") }
        }
    }
}

/** Days since registration, counting the first day. */
fun usageDays(user: JsonObject?): Int? {
    val created = Time.parseMillis(user?.str("created_at")) ?: return null
    return maxOf(1, ((System.currentTimeMillis() - created) / 86_400_000L + 1).toInt())
}
