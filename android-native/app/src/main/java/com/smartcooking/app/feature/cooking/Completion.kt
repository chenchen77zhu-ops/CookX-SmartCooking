package com.smartcooking.app.feature.cooking

import com.smartcooking.app.core.ApiClient
import com.smartcooking.app.core.ApiException
import com.smartcooking.app.core.BusinessApi
import com.smartcooking.app.core.KeyValueStore
import com.smartcooking.app.core.SessionManager
import com.smartcooking.app.core.Time
import com.smartcooking.app.core.asArray
import com.smartcooking.app.core.asObject
import com.smartcooking.app.core.jsonOf
import com.smartcooking.app.core.str
import com.smartcooking.app.data.InventoryItem
import com.smartcooking.app.data.InventoryRepository
import com.smartcooking.app.data.Recipe
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.util.UUID

/**
 * Queue of confirmed completions for the growth module; the idempotency key is derived from the
 * session id, so re-sending never counts a completion twice.
 */
class CompletionSync(private val store: KeyValueStore, private val business: BusinessApi, private val sessions: SessionManager) {
    private fun key(user: String) = "cookx:completion-queue:v1:$user"
    private val mutex = Mutex()

    fun list(user: String): List<JsonObject> = store.getJson(key(user)).asArray().orEmpty().mapNotNull { it.asObject() }

    fun enqueue(session: CookingState, provenance: String = "confirmed_session"): JsonObject {
        if (!session.completed || session.user != sessions.currentUserId) throw IllegalStateException("只能同步当前账号已确认的完成记录")
        val rows = list(session.user)
        rows.firstOrNull { it.str("session_id") == session.id }?.let { return it }
        val body = jsonOf(
            "idempotency_key" to "completion-${session.id}", "session_id" to session.id,
            "recipe" to Recipe.normalize(session.recipe).json, "recipe_version" to session.recipeVersion,
            "started_at" to Time.iso(Instant.ofEpochMilli(session.startedAt)),
            "completed_at" to Time.iso(Instant.ofEpochMilli(session.completedAt ?: System.currentTimeMillis())),
            "confirmed" to true, "provenance" to provenance,
        )
        store.putJsonSync(key(session.user), JsonArray(rows + body))
        return body
    }

    suspend fun sync(user: String) = mutex.withLock {
        for (body in list(user)) {
            if (user != sessions.currentUserId) throw IllegalStateException("账号已切换")
            business.call("/growth/completions", "POST", body)
            // Remove only the sent entry; entries added meanwhile are preserved.
            store.putJsonSync(key(user), JsonArray(list(user).filter { it.str("session_id") != body.str("session_id") }))
        }
    }
}

/** A batch the user ticked as used, with the (integer) count consumed. */
data class ConsumeSelection(val item: InventoryItem, val amount: Int)

/**
 * Personal inventory consumption (`/api/inventory/consume`). The transaction is saved before
 * sending; unknown outcomes are resolved through the receipt endpoint, never by re-deducting.
 */
class PersonalConsumption(private val api: ApiClient, private val inventory: InventoryRepository, private val store: KeyValueStore, private val sessions: SessionManager) {
    private fun key(user: String) = "cookx:consumption:v1:$user"
    private val busy = mutableSetOf<String>()

    fun pending(user: String): JsonObject? {
        if (!store.contains(key(user))) return null
        return store.getJson(key(user)).asObject() ?: throw IllegalStateException("待核对记录损坏，请在库存页手动检查，不能再次自动扣减")
    }

    private fun persist(user: String, tx: JsonObject) = store.putJsonSync(key(user), tx)

    suspend fun reconcile(user: String): List<InventoryItem> {
        val tx = pending(user) ?: throw IllegalStateException("没有待核对记录")
        val receipt = api.get("/inventory/consumption/${tx.str("idempotencyKey")}", mapOf("user_id" to user)).asObject()
        if (receipt?.str("status") != "success" || receipt.str("idempotency_key") != tx.str("idempotencyKey")) throw IllegalStateException("服务端尚未确认扣减凭证，请保留原凭证核对")
        persist(user, JsonObject(tx + mapOf("status" to kotlinx.serialization.json.JsonPrimitive("committed"), "receipt" to receipt)))
        val rows = inventory.read(user)
        persist(user, JsonObject(tx + mapOf("status" to kotlinx.serialization.json.JsonPrimitive("confirmed"), "receipt" to receipt)))
        inventory.notifyChanged(user)
        return rows
    }

    private suspend fun send(user: String, tx: JsonObject): List<InventoryItem> {
        if (sessions.currentUserId != user) throw IllegalStateException("登录用户已变化，已取消本次扣减")
        try {
            val items = tx["items"].asArray().orEmpty().mapNotNull { it.asObject() }.map { i ->
                jsonOf("item_id" to i.str("id"), "quantity" to i.str("consume")?.toInt(), "expected_quantity" to i.str("quantity")?.toDouble()?.toInt(),
                    "expected_revision" to i.str("revision")).let { o -> JsonObject(o.filterValues { v -> v !is kotlinx.serialization.json.JsonNull }) }
            }
            val response = api.post("/inventory/consume", jsonOf("user_id" to user, "idempotency_key" to tx.str("idempotencyKey"), "items" to JsonArray(items))).asObject()
            if (response?.str("status") != "success") throw ApiException(response?.str("message") ?: "扣减未确认，请查询原凭证")
        } catch (e: ApiException) {
            // Only a definitive rejection with no receipt allows a fresh, reviewed transaction.
            if (e.status in listOf(409, 422, 428)) {
                try { api.get("/inventory/consumption/${tx.str("idempotencyKey")}", mapOf("user_id" to user)) }
                catch (check: ApiException) { if (check.status == 404) persist(user, JsonObject(tx + ("status" to kotlinx.serialization.json.JsonPrimitive("rejected")))) }
            }
            throw e
        }
        return reconcile(user)
    }

    suspend fun submit(user: String, sessionId: String, selected: List<ConsumeSelection>): List<InventoryItem> {
        if (!busy.add(user)) throw IllegalStateException("正在核对，请勿重复提交")
        try {
            val old = pending(user)
            if (old != null && old.str("status") != "rejected" && (old.str("status") != "confirmed" || old.str("sessionId") == sessionId)) {
                throw IllegalStateException("本次扣减已提交，请核对已有结果，不要重复扣减")
            }
            if (selected.isEmpty()) throw IllegalArgumentException("请先选择实际使用的食材")
            val latest = inventory.read(user)
            val items = selected.map { sel ->
                val row = latest.firstOrNull { it.id == sel.item.id }
                val q = row?.quantity
                if (row == null || q == null || q != Math.floor(q) || q < 1 || q != sel.item.quantity || row.name != sel.item.name ||
                    row.revision != sel.item.revision || sel.amount < 1 || sel.amount > q) throw IllegalStateException("库存或使用数量已变化，请重新读取清单")
                jsonOf("id" to row.id, "name" to row.name, "quantity" to q.toLong().toString(), "consume" to sel.amount.toString(),
                    "after" to (q.toLong() - sel.amount).toString(), "revision" to row.revision)
            }
            if (sessions.currentUserId != user) throw IllegalStateException("登录用户已变化，已取消本次扣减")
            val tx = jsonOf("schemaVersion" to 2, "user" to user, "sessionId" to sessionId, "items" to JsonArray(items),
                "idempotencyKey" to UUID.randomUUID().toString(), "status" to "uncertain", "at" to System.currentTimeMillis())
            persist(user, tx)
            return send(user, tx)
        } finally { busy.remove(user) }
    }

    suspend fun retry(user: String): List<InventoryItem> {
        if (!busy.add(user)) throw IllegalStateException("正在核对，请勿重复提交")
        try {
            val tx = pending(user)
            if (tx == null || tx.str("schemaVersion") != "2" || tx.str("status") in listOf("confirmed", "rejected")) throw IllegalStateException("没有可安全重试的原凭证")
            return send(user, tx)
        } finally { busy.remove(user) }
    }
}

/** Legacy counts are whole batches; decimals or zero cannot be deducted automatically. */
fun consumable(item: InventoryItem): Boolean = item.quantity >= 1 && item.quantity == Math.floor(item.quantity)
