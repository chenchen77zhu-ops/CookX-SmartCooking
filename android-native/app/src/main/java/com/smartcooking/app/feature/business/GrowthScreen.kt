package com.smartcooking.app.feature.business

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.smartcooking.app.core.AppContainer
import com.smartcooking.app.core.Time
import com.smartcooking.app.core.asDouble
import com.smartcooking.app.core.bool
import com.smartcooking.app.core.jsonOf
import com.smartcooking.app.core.num
import com.smartcooking.app.core.obj
import com.smartcooking.app.core.objects
import com.smartcooking.app.core.str
import com.smartcooking.app.core.strings
import com.smartcooking.app.core.userMessage
import com.smartcooking.app.ui.components.CookXCard
import com.smartcooking.app.ui.components.IconBadge
import com.smartcooking.app.ui.components.LoadingBlock
import com.smartcooking.app.ui.components.OutlineButton
import com.smartcooking.app.ui.components.PrimaryButton
import com.smartcooking.app.ui.components.StatusChip
import com.smartcooking.app.ui.components.SubpageScaffold
import com.smartcooking.app.ui.components.SwitchRow
import com.smartcooking.app.ui.components.TonalButton
import com.smartcooking.app.ui.components.Tone
import com.smartcooking.app.ui.components.overlapHero
import com.smartcooking.app.ui.nav.Navigator
import com.smartcooking.app.ui.nav.Routes
import com.smartcooking.app.ui.nav.cookxViewModel
import com.smartcooking.app.ui.theme.CookX
import com.smartcooking.app.ui.theme.CookXShapes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

class GrowthViewModel(c: AppContainer) : BusinessViewModel(c, "growth") {
    val summary = MutableStateFlow<JsonObject?>(null)
    val challenges = MutableStateFlow<JsonObject?>(null)
    val badges = MutableStateFlow<JsonObject?>(null)
    val pendingCount = MutableStateFlow(c.completions.list(user).size)
    val syncing = MutableStateFlow(false)
    val navigateCopy = MutableStateFlow<String?>(null)
    val importCandidates = MutableStateFlow(0)

    init { load() }

    fun load() = launchLoad {
        coroutineScope {
            val s = async { c.business.get("/growth") }
            val ch = async { c.business.get("/challenges") }
            val b = async { c.business.get("/badges") }
            summary.value = s.await(); challenges.value = ch.await(); badges.value = b.await()
        }
        pendingCount.value = c.completions.list(user).size
    }

    fun sync() = viewModelScope.launch {
        if (syncing.value) return@launch
        syncing.value = true
        try { c.completions.sync(user); load() } catch (e: CancellationException) { throw e } catch (e: Exception) { loadError.value = e.userMessage() }
        finally { syncing.value = false; pendingCount.value = c.completions.list(user).size }
    }

    /** Completed local sessions for this account not yet on the server; imported only after confirmation. */
    private fun eligibleLocal() = (c.cooking(user).history() + listOfNotNull(c.cooking(user).state.value))
        .filter { it.user == user && it.completed }
        .filter { s -> summary.value?.objects("history")?.none { it.str("session_id") == s.id } != false }
        .distinctBy { it.id }

    fun checkLocal() { importCandidates.value = eligibleLocal().size.also { if (it == 0) loadError.value = "当前账号没有尚未同步的本机完成记录" } }

    fun importLocal() {
        eligibleLocal().forEach { runCatching { c.completions.enqueue(it, "confirmed_local_import") } }
        importCandidates.value = 0
        pendingCount.value = c.completions.list(user).size
        sync()
    }

    fun join(id: String) = send("/challenges/$id/join", "post")

    fun clone(record: JsonObject) = launchLoad {
        val source = c.business.get("/recipes/sources?kind=history").objects("items").firstOrNull { it.str("id") == record.str("id") }
            ?: throw IllegalStateException("菜谱记录已变化，请刷新")
        command.send("/recipes/copies", "post", jsonOf("source_type" to "history", "source_id" to record.str("id"), "expected_source_version" to source.str("source_version")))
    }

    override suspend fun onCommandSuccess(result: JsonObject) {
        result.obj("copy")?.str("id")?.let { navigateCopy.value = it; return }
        load()
    }
}

@Composable
fun GrowthScreen(navigator: Navigator) {
    val vm = cookxViewModel { GrowthViewModel(it) }
    val summary by vm.summary.collectAsStateWithLifecycle()
    val challenges by vm.challenges.collectAsStateWithLifecycle()
    val badges by vm.badges.collectAsStateWithLifecycle()
    val pending by vm.pendingCount.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val candidates by vm.importCandidates.collectAsStateWithLifecycle()
    val navigateCopy by vm.navigateCopy.collectAsStateWithLifecycle()
    val busy = rememberBusy(vm)
    val confirm = rememberConfirmer()
    navigateCopy?.let { vm.navigateCopy.value = null; navigator.open(Routes.recipes(copy = it)) }
    androidx.compose.runtime.LaunchedEffect(candidates) {
        if (candidates > 0) {
            confirm.ask("核对旧记录", "导入当前账号 $candidates 次已完成记录？将标记为本机旧记录导入，不会公开到社区。", vm::importLocal)
            vm.importCandidates.value = 0
        }
    }

    SubpageScaffold("厨艺成长", navigator::back, subtitle = "只统计你确认完成的烹饪。生成菜谱、暂停计时和扣库存都不会单独增加次数。",
        heroExtra = {
            summary?.let { s ->
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    HeroStat("${s.num("confirmed_count")?.toInt() ?: 0}", "已确认完成")
                    HeroStat("${s.num("recipe_count")?.toInt() ?: 0}", "尝试菜谱")
                    HeroStat("${badges?.objects("items")?.count { it.obj("award") != null } ?: 0}", "获得徽章")
                }
            }
        }) {
        item {
            Column(Modifier.overlapHero(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CommandStatus(vm, Modifier.padding(horizontal = 16.dp))
                Section("成长记录", icon = Icons.Outlined.EmojiEvents, trailing = { TonalButton("刷新", vm::load, enabled = !busy) }) {
                    if (summary == null) LoadingBlock(minHeight = 60)
                    summary?.let { s ->
                        s.str("note")?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary) }
                        val methods = s.obj("methods")?.entries.orEmpty()
                        if (methods.isNotEmpty()) {
                            Text("烹饪方法", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                            val max = methods.maxOf { it.value.asDouble() ?: 0.0 }.coerceAtLeast(1.0)
                            methods.forEach { (m, v) -> Bar(m, v.asDouble() ?: 0.0, max) }
                        }
                        val timeline = s.obj("timeline")?.entries.orEmpty()
                        if (timeline.isNotEmpty()) {
                            Text("历史变化", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                            val max = timeline.maxOf { it.value.asDouble() ?: 0.0 }.coerceAtLeast(1.0)
                            timeline.toList().takeLast(8).forEach { (d, v) -> Bar(d, v.asDouble() ?: 0.0, max, CookX.Accent) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (pending > 0) Text("本机有 $pending 次完成待同步，原会话凭证不会重复计次。", style = MaterialTheme.typography.bodySmall, color = CookX.Accent)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (pending > 0) PrimaryButton("同步待确认完成", vm::sync, loading = syncing, icon = Icons.Outlined.Sync)
                        OutlineButton("核对本机旧完成记录", vm::checkLocal, enabled = !syncing)
                    }
                }
                challenges?.let { data ->
                    Section("烹饪挑战", icon = Icons.Outlined.Flag, subtitle = data.str("note")) {
                        data.objects("items").forEach { ch ->
                            val p = ch.obj("participation")
                            val target = ch.num("target") ?: 1.0
                            ItemBlock(ch.str("name").orEmpty(), lines = listOf(ch.str("description").orEmpty()),
                                badge = { if (p != null) StatusChip(mapOf("active" to "进行中", "completed" to "已完成", "ended" to "已结束")[p.str("status")] ?: p.str("status").orEmpty(), if (p.str("status") == "completed") Tone.Fresh else Tone.Warm) },
                                actions = if (p == null) { { TonalButton("加入挑战", { vm.join(ch.str("id").orEmpty()) }, enabled = !busy, tone = Tone.Warm) } } else null) {
                                if (p != null) {
                                    val progress = minOf(target, p.num("progress") ?: 0.0)
                                    LinearProgressIndicator(progress = { (progress / target).toFloat() }, Modifier.padding(top = 8.dp).fillMaxWidth().height(6.dp).clip(CookXShapes.Pill),
                                        color = CookX.Accent, trackColor = CookX.Border, drawStopIndicator = {})
                                    Text("${progress.toInt()} / ${target.toInt()}${p.str("ends_at")?.let { " · 截止 ${Time.display(it)}" }.orEmpty()}", fontSize = 11.sp, color = CookX.TextSecondary, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                }
                badges?.let { data ->
                    Section("我的徽章", icon = Icons.Outlined.MilitaryTech, subtitle = data.str("note")) {
                        data.objects("items").chunked(2).forEach { row ->
                            Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { b -> BadgeTile(b, Modifier.weight(1f)) }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        items(summary?.objects("history").orEmpty()) { r ->
            CookXCard(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.obj("recipe")?.str("dish_name").orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Text("${Time.display(r.str("completed_at"))} · ${if (r.str("provenance") == "confirmed_local_import") "已确认导入的本机记录" else "烹饪会话确认"}", fontSize = 11.5.sp, color = CookX.TextSecondary)
                        Text("版本 ${r.str("recipe_version")} · ${r.obj("recipe")?.str("method") ?: "烹饪方法未标注"}", fontSize = 11.sp, color = CookX.TextTertiary)
                    }
                    TonalButton("复刻", { vm.clone(r) }, enabled = !busy, icon = Icons.Outlined.ContentCopy)
                }
            }
        }
    }
    confirm.Host()
}

@Composable
private fun HeroStat(value: String, label: String) = Column {
    Text(value, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    Text(label, color = CookX.OnDarkMuted, fontSize = 11.sp)
}

@Composable
private fun Bar(label: String, value: Double, max: Double, color: Color = CookX.Primary) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = CookX.TextBody, modifier = Modifier.width(88.dp), maxLines = 1)
        Box(Modifier.weight(1f).height(10.dp).clip(CookXShapes.Pill).background(CookX.Border)) {
            Box(Modifier.fillMaxWidth((value / max).toFloat()).fillMaxHeight().clip(CookXShapes.Pill).background(color))
        }
        Text("${value.toInt()} 次", fontSize = 11.sp, color = CookX.TextSecondary, modifier = Modifier.padding(start = 8.dp).width(40.dp))
    }
}

@Composable
private fun BadgeTile(b: JsonObject, modifier: Modifier) {
    val award = b.obj("award")
    Column(
        modifier.clip(CookXShapes.Tile).background(if (award != null) CookX.WarningBg else CookX.SurfaceMuted)
            .border(1.dp, if (award != null) CookX.Gold.copy(alpha = 0.5f) else CookX.Border, CookXShapes.Tile).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(Icons.Outlined.MilitaryTech, if (award != null) Tone.Gold else Tone.Neutral, size = 46.dp, iconSize = 24.dp, shape = androidx.compose.foundation.shape.CircleShape)
        Text(b.str("name").orEmpty(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp), color = if (award != null) CookX.Text else CookX.TextSecondary)
        Text(b.str("description").orEmpty(), fontSize = 11.sp, color = CookX.TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(award?.let { "已获得 · ${Time.displayShort(it.str("awarded_at"))}" } ?: "尚未获得", fontSize = 10.5.sp, color = if (award != null) Color(0xFFB7791F) else CookX.TextTertiary, modifier = Modifier.padding(top = 4.dp))
        award?.let { Text("规则 ${it.str("rule_version")} · 依据 ${it.strings("evidence_ids").size} 项", fontSize = 10.sp, color = CookX.TextTertiary) }
    }
}

// ---------------------------------------------------------------- 偏好学习

class LearningViewModel(c: AppContainer) : BusinessViewModel(c, "learning") {
    val data = MutableStateFlow<JsonObject?>(null)
    val recipes = MutableStateFlow<List<JsonObject>>(emptyList())

    init { load() }

    fun load() = launchLoad {
        coroutineScope {
            val a = async { c.business.get("/learning") }
            val b = async { c.business.get("/recipes/sources") }
            data.value = a.await(); recipes.value = b.await().objects("items")
        }
    }

    fun vote(id: String): JsonObject? = data.value?.objects("feedback")?.firstOrNull { it.str("recipe_id") == id }
    fun toggle() { val s = data.value?.obj("settings") ?: return; send("/learning/settings", "put", jsonOf("enabled" to !s.bool("enabled"), "expected_version" to s.num("version"))) }
    fun train() = send("/learning/train", "post")
    fun reset(action: String) = send("/learning/reset", "post", jsonOf("action" to action))
    fun feedback(id: String, liked: Boolean) = send("/learning/feedback/$id", "put", jsonOf("liked" to liked, "expected_version" to (vote(id)?.num("version") ?: 0)))

    override suspend fun onCommandSuccess(result: JsonObject) = load().join()
}

@Composable
fun LearningScreen(navigator: Navigator) {
    val vm = cookxViewModel { LearningViewModel(it) }
    val data by vm.data.collectAsStateWithLifecycle()
    val recipes by vm.recipes.collectAsStateWithLifecycle()
    val busy = rememberBusy(vm)
    val confirm = rememberConfirmer()
    val enabled = data?.obj("settings")?.bool("enabled") == true

    SubpageScaffold("偏好学习", navigator::back, subtitle = "主动开启后，只用你明确选择的喜欢与不喜欢学习菜谱标签，在原本合格的候选内调整顺序，不突破忌口、食材风险或菜单硬约束。") {
        item {
            Column(Modifier.overlapHero(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CommandStatus(vm, Modifier.padding(horizontal = 16.dp))
                Section("学习开关", icon = Icons.Outlined.ThumbUp) {
                    if (data == null) LoadingBlock(minHeight = 60)
                    data?.let { d ->
                        SwitchRow(if (enabled) "已开启偏好学习" else "偏好学习已关闭", enabled, { vm.toggle() }, subtitle = d.str("note"), enabled = !busy)
                        Text("明确反馈 ${d.objects("feedback").size} 道菜；独立行为记录 ${d.num("activity_count")?.toInt() ?: 0} 条。", style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary)
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrimaryButton("训练并评估", vm::train, enabled = !busy && enabled)
                            OutlineButton("重置模型", { confirm.ask("核对学习数据", "删除当前模型并回到原排序？明确反馈会保留。") { vm.reset("reset_model") } }, enabled = !busy)
                        }
                        OutlineButton("清除反馈和行为记录", { confirm.ask("核对学习数据", "清除当前账号全部反馈、行为记录与模型？操作后回到原排序。") { vm.reset("clear_feedback") } },
                            Modifier.padding(top = 8.dp), enabled = !busy, color = CookX.Danger)
                    }
                }
                data?.obj("model")?.let { m ->
                    val accepted = m.bool("accepted")
                    Section(if (accepted) "时间对照评估通过" else "继续使用原排序", icon = Icons.Outlined.Sync, trailing = { StatusChip(if (accepted) "已启用" else "未采用", if (accepted) Tone.Fresh else Tone.Neutral) }) {
                        Text(m.str("reason").orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        Text("训练时间 ${Time.display(m.str("trained_at"))} · ${m.str("model_version") ?: m.str("algorithm")}", fontSize = 11.sp, color = CookX.TextTertiary, modifier = Modifier.padding(top = 4.dp))
                        m.obj("metrics")?.let { x ->
                            ItemBlock("时间对照结果", Modifier.padding(top = 10.dp), lines = listOf(
                                "训练 ${x.num("train_count")?.toInt()} 条，测试 ${x.num("test_count")?.toInt()} 条",
                                "训练截止 ${x.str("train_end")} · 测试开始 ${x.str("test_start")}",
                                "原排序成对 AUC ${x.num("original_pairwise_auc")?.let { "%.3f".format(it) }}；个性化 ${x.num("personalized_pairwise_auc")?.let { "%.3f".format(it) }}",
                                "这些是有限历史反馈上的离线指标，不是识别准确率或长期收益承诺。",
                            ))
                        }
                    }
                }
                Section("明确表达菜谱偏好", icon = Icons.Outlined.ThumbUp, subtitle = if (enabled) "每道菜只保留一份当前反馈；更新反馈会要求重新评估模型。" else "开启后可以提交反馈。浏览列表不会产生负反馈。") {
                    recipes.forEach { r ->
                        val id = r.str("id").orEmpty()
                        val v = vm.vote(id)
                        ItemBlock(r.obj("recipe")?.str("dish_name").orEmpty(), lines = listOf("${r.obj("recipe")?.strings("tags")?.joinToString("、").orEmpty()} · ${r.obj("recipe")?.str("method") ?: "方法未标注"}"),
                            badge = { v?.let { StatusChip(if (it.bool("liked")) "喜欢" else "不喜欢", if (it.bool("liked")) Tone.Fresh else Tone.Neutral) } },
                            actions = {
                                TonalButton("喜欢", { vm.feedback(id, true) }, enabled = !busy && enabled, icon = Icons.Outlined.ThumbUp, tone = if (v?.bool("liked") == true) Tone.Fresh else Tone.Neutral)
                                TonalButton("不喜欢", { vm.feedback(id, false) }, enabled = !busy && enabled, icon = Icons.Outlined.ThumbDown, tone = if (v != null && !v.bool("liked")) Tone.Danger else Tone.Neutral)
                            })
                    }
                }
            }
        }
    }
    confirm.Host()
}
