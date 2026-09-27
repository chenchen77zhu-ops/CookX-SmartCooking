package com.smartcooking.app.feature.cooking

import com.smartcooking.app.core.AppJson
import com.smartcooking.app.core.KeyValueStore
import com.smartcooking.app.data.Recipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import java.util.UUID

@Serializable
data class StepTimer(
    val visited: Boolean = false,
    val remainingMs: Long = 0,
    val deadline: Long? = null,
    val round: Int = 0,
    val notificationKey: String? = null,
)

@Serializable
data class Patch(val index: Int, val before: String, val after: String)

@Serializable
data class Adjustment(
    val id: String = "",
    val sessionId: String,
    val baseVersion: Int,
    val type: String,
    val label: String,
    val reason: String,
    val advice: String,
    val patches: List<Patch>,
    val appliedAt: Long? = null,
    val version: Int = 0,
    val undone: Boolean = false,
    val undoneAt: Long? = null,
)

@Serializable
data class Reminder(
    val key: String,
    val type: String,
    val obj: String,
    val text: String,
    val risk: String = "info",
    val at: Long,
    val dismissed: Boolean = false,
)

/** Persisted cooking session (schema 1 of the web client, kept equivalent). */
@Serializable
data class CookingState(
    val schemaVersion: Int = 1,
    val user: String,
    val id: String,
    val recipe: JsonObject,
    val recipeVersion: Int = 1,
    val stepIndex: Int = 0,
    val status: String = "active",
    val startedAt: Long,
    val completedAt: Long? = null,
    val adjustments: List<Adjustment> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val consumption: String = "not_requested",
    val timers: List<StepTimer>,
) {
    val recipeModel: Recipe get() = Recipe(recipe)
    val active: Boolean get() = status == "active"
    val completed: Boolean get() = status == "completed"
    val timer: StepTimer get() = timers[stepIndex]
}

data class AdjustmentRule(val label: String, val reason: String, val advice: String)

val adjustmentRules = linkedMapOf(
    "seasoning" to AdjustmentRule("缺调料", "调料种类与配比未知，不能自动给出替代比例。", "开始后续操作前核对缺少的调料；可以暂缓可选调味，必要时停止本菜谱并选择材料齐全的做法。"),
    "salty" to AdjustmentRule("过咸", "无法从描述判断盐量或稀释比例。", "后续调味前先检查味道，暂缓额外加盐或含盐调味料；是否调整做法由你确认。"),
    "dry" to AdjustmentRule("偏干", "尚不清楚锅内油量、烹饪方式与食材状态，不能自动建议加水。", "继续后续操作前检查锅内状态和菜谱要求，必要时暂停加热；不要向热油中直接加水。"),
    "undercooked" to AdjustmentRule("未熟", "锅面温度不能代表食材内部熟度，也不能代替内部温度测量。", "进入后续操作前检查食材是否达到该食材要求的熟制条件；需要延长观察时，请自行设置计时，不把计时结束视为已熟。"),
)

/**
 * Port of `createCookingSession`. Timers are wall-clock deadlines, so the remaining time stays
 * correct across process death; only visiting a step starts its timer.
 */
class CookingEngine(private val user: String, private val store: KeyValueStore, private val now: () -> Long = System::currentTimeMillis) {
    private val key = "cookx:cooking:v1:$user"
    private val historyKey = "cookx:cooking-history:v1:$user"
    var warning: String = ""
        private set
    private val _state = MutableStateFlow(restore())
    val state: StateFlow<CookingState?> = _state.asStateFlow()
    /** False after an app restart until the user explicitly restores the session. */
    var ready = false

    private fun restore(): CookingState? {
        val raw = store.get(key) ?: return null
        return try {
            val s = AppJson.decodeFromString(CookingState.serializer(), raw)
            val steps = Recipe.normalize(s.recipe).steps.size
            require(s.status in listOf("active", "completed") && s.user == user && s.schemaVersion == 1)
            require(s.stepIndex in 0 until steps && s.timers.size == steps && s.timers.all { it.remainingMs >= 0 })
            s.copy(recipe = Recipe.normalize(s.recipe).json)
        } catch (e: Exception) {
            warning = "本地烹饪记录损坏或版本不兼容，请重新选择菜谱"
            null
        }
    }

    private fun set(next: CookingState?) {
        _state.value = next
        val ok = store.putSync(key, next?.let { AppJson.encodeToString(CookingState.serializer(), it) })
        if (!ok) warning = "本地保存不可用，刷新后可能无法恢复"
    }

    fun persist() = set(_state.value)

    fun remaining(index: Int? = null): Long {
        val s = _state.value ?: return 0
        val t = s.timers.getOrNull(index ?: s.stepIndex) ?: return 0
        return maxOf(0, if (t.deadline == null) t.remainingMs else t.deadline - now())
    }

    private fun enter(s: CookingState, index: Int): CookingState {
        val t = s.timers[index]
        var timer = t
        if (!t.visited) {
            val ms = ((Recipe(s.recipe).steps[index].durationSeconds ?: 0.0) * 1000).toLong()
            timer = if (ms > 0) t.copy(visited = true, remainingMs = ms, deadline = now() + ms, round = t.round + 1) else t.copy(visited = true, remainingMs = 0)
        }
        return s.copy(stepIndex = index, timers = s.timers.toMutableList().also { it[index] = timer })
    }

    private fun paused(s: CookingState): CookingState {
        val t = s.timer
        return s.copy(timers = s.timers.toMutableList().also { it[s.stepIndex] = t.copy(remainingMs = remaining(s.stepIndex), deadline = null) })
    }

    fun pause() { _state.value?.let { set(paused(it)) } }

    fun resume() {
        val s = _state.value ?: return
        if (!s.active) return
        val t = s.timer
        if (t.deadline == null && t.remainingMs > 0) set(s.copy(timers = s.timers.toMutableList().also { it[s.stepIndex] = t.copy(deadline = now() + t.remainingMs) }))
    }

    fun start(recipe: Recipe, replace: Boolean = false) {
        val normalized = Recipe.normalize(recipe.json)
        val current = _state.value
        if (current?.active == true && !replace) throw IllegalStateException("已有未完成烹饪，请先确认替换")
        if (current?.completed == true) archive(current)
        val fresh = CookingState(user = user, id = UUID.randomUUID().toString(), recipe = normalized.json, startedAt = now(), timers = normalized.steps.map { StepTimer() })
        set(enter(fresh, 0))
    }

    private fun archive(s: CookingState) {
        val list = runCatching { AppJson.decodeFromString(ListSerializer(CookingState.serializer()), store.get(historyKey) ?: "[]") }.getOrDefault(emptyList())
        val next = (list.filter { it.id != s.id } + s).takeLast(50)
        if (!store.putSync(historyKey, AppJson.encodeToString(ListSerializer(CookingState.serializer()), next))) throw IllegalStateException("完成记录归档失败，请释放本地空间后再开始")
    }

    fun history(): List<CookingState> = runCatching { AppJson.decodeFromString(ListSerializer(CookingState.serializer()), store.get(historyKey) ?: "[]") }.getOrDefault(emptyList())

    fun move(delta: Int): Boolean {
        val s = _state.value ?: return false
        if (!s.active) return false
        val target = s.stepIndex + delta
        if (target !in s.timers.indices) return false
        set(enter(paused(s), target))
        return true
    }

    fun setTimer(seconds: Double) {
        if (!seconds.isFinite() || seconds <= 0 || seconds > 86400) throw IllegalArgumentException("计时秒数须大于 0 且不超过 24 小时")
        val s = _state.value ?: return
        if (!s.active) return
        val ms = (seconds * 1000).toLong()
        set(s.copy(timers = s.timers.toMutableList().also { it[s.stepIndex] = s.timer.copy(remainingMs = ms, deadline = now() + ms, round = s.timer.round + 1) }))
    }

    fun finish() {
        val s = _state.value ?: return
        set(paused(s).copy(status = "completed", completedAt = now()))
    }

    fun markConsumption(value: String) { _state.value?.let { set(it.copy(consumption = value)) } }

    fun setNotificationKey(key: String?) {
        val s = _state.value ?: return
        set(s.copy(timers = s.timers.toMutableList().also { it[s.stepIndex] = s.timer.copy(notificationKey = key) }))
    }

    // ---- adjustments: only unvisited later steps are patched, with preview and undo

    fun previewAdjustment(type: String): Adjustment {
        val s = _state.value
        val rule = adjustmentRules[type]
        if (rule == null || s?.active != true) throw IllegalStateException("当前没有可调整的烹饪会话")
        val steps = Recipe(s.recipe).steps
        val index = steps.indices.firstOrNull { it > s.stepIndex && !s.timers[it].visited }
        val patches = if (index == null) emptyList() else listOf(Patch(index, steps[index].text, "${steps[index].text}\n调整提示：${rule.advice}"))
        return Adjustment(sessionId = s.id, baseVersion = s.recipeVersion, type = type, label = rule.label, reason = rule.reason, advice = rule.advice, patches = patches)
    }

    fun applyAdjustment(preview: Adjustment): Adjustment {
        val s = _state.value
        if (s?.active != true || preview.sessionId != s.id || preview.baseVersion != s.recipeVersion) throw IllegalStateException("菜谱已变化，请重新预览")
        val expected = previewAdjustment(preview.type)
        if (expected.patches != preview.patches || expected.patches.isEmpty()) throw IllegalStateException("没有可修改的后续步骤，请使用检查建议和手动计时")
        if (expected.patches.any { s.timers[it.index].visited || it.index <= s.stepIndex }) throw IllegalStateException("已进入的步骤不能修改")
        val record = expected.copy(id = "${s.id}:${s.recipeVersion + 1}", appliedAt = now(), version = s.recipeVersion + 1)
        var recipe = Recipe(s.recipe)
        record.patches.forEach { recipe = recipe.withStepText(it.index, it.after) }
        set(s.copy(recipe = recipe.json, recipeVersion = s.recipeVersion + 1, adjustments = s.adjustments + record))
        return record
    }

    fun canUndo(record: Adjustment): Boolean {
        val s = _state.value ?: return false
        val steps = Recipe(s.recipe).steps
        return !record.undone && record.patches.all { !s.timers[it.index].visited && it.index > s.stepIndex && steps[it.index].text == it.after }
    }

    fun undoAdjustment(id: String) {
        val s = _state.value
        val record = s?.adjustments?.firstOrNull { it.id == id }
        if (s?.active != true || record == null || !canUndo(record)) throw IllegalStateException("相关步骤已进入或有后续修改，不能撤销")
        var recipe = Recipe(s.recipe)
        record.patches.forEach { recipe = recipe.withStepText(it.index, it.before) }
        set(s.copy(recipe = recipe.json, recipeVersion = s.recipeVersion + 1,
            adjustments = s.adjustments.map { if (it.id == id) it.copy(undone = true, undoneAt = now()) else it }))
    }

    // ---- reminders: de-duplicated per object; escalation or 5 minutes allows a repeat

    fun recordReminder(type: String, obj: String, text: String, risk: String = "info", once: Boolean = false): Reminder? {
        val s = _state.value ?: return null
        val key = "${s.id}:$type:$obj"
        val previous = s.reminders.firstOrNull { it.key == key }
        val rank = mapOf("info" to 0, "warning" to 1, "danger" to 2)
        if (previous != null && (once || (now() - previous.at < 300_000 && (rank[risk] ?: 0) <= (rank[previous.risk] ?: 0)))) return null
        val event = Reminder(key, type, obj, text, risk, now())
        set(s.copy(reminders = (s.reminders.filter { it.key != key } + event).takeLast(100)))
        return event
    }

    fun timerReminder(): Reminder? {
        val s = _state.value ?: return null
        val t = s.timer
        if (!s.active || t.deadline == null || t.deadline > now()) return null
        return recordReminder("timer", "${s.stepIndex}:${t.round}", "第 ${s.stepIndex + 1} 步计时结束，请检查烹饪状态；不会自动跳步。", once = true)
    }

    fun dismissReminder(key: String) {
        val s = _state.value ?: return
        set(s.copy(reminders = s.reminders.map { if (it.key == key) it.copy(dismissed = true) else it }))
    }
}

fun notificationId(key: String): Int {
    var hash = 2166136261L.toInt()
    for (c in key) { hash = hash xor c.code; hash *= 16777619 }
    return ((hash.toLong() and 0xffffffffL) % 2_000_000_000L + 1).toInt()
}
