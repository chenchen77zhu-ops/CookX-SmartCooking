package com.smartcooking.app.feature.business

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartcooking.app.core.AppContainer
import com.smartcooking.app.core.clean
import com.smartcooking.app.core.jsonOf
import com.smartcooking.app.core.num
import com.smartcooking.app.core.obj
import com.smartcooking.app.core.objects
import com.smartcooking.app.core.str
import com.smartcooking.app.core.with
import com.smartcooking.app.data.Recipe
import com.smartcooking.app.ui.components.Banner
import com.smartcooking.app.ui.components.BannerKind
import com.smartcooking.app.ui.components.CookXCard
import com.smartcooking.app.ui.components.CookXSheet
import com.smartcooking.app.ui.components.EmptyState
import com.smartcooking.app.ui.components.FilterChips
import com.smartcooking.app.ui.components.IconBadge
import com.smartcooking.app.ui.components.LoadingBlock
import com.smartcooking.app.ui.components.OutlineButton
import com.smartcooking.app.ui.components.PrimaryButton
import com.smartcooking.app.ui.components.StatusChip
import com.smartcooking.app.ui.components.SubpageScaffold
import com.smartcooking.app.ui.components.TonalButton
import com.smartcooking.app.ui.components.Tone
import com.smartcooking.app.ui.components.overlapHero
import com.smartcooking.app.ui.nav.Navigator
import com.smartcooking.app.ui.nav.Routes
import com.smartcooking.app.ui.nav.cookxViewModel
import com.smartcooking.app.ui.theme.CookX
import com.smartcooking.app.ui.theme.CookXShapes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject

private val kindLabels = linkedMapOf("standard" to "标准菜谱", "history" to "我的历史", "favorite" to "我的收藏", "copy" to "独立副本")
private val sourceLabels = kindLabels + mapOf("community" to "社区菜谱", "menu" to "已保存菜单")
private val checkStatuses = mapOf(
    "missing" to "库存未找到该食材，请准备后再开始",
    "needs_freshness_check" to "存在同名库存，但日期或鲜度需核查，请检查实物",
    "needs_quantity_check" to "找到可评估批次，仍需手动核对数量与单位",
)

class RecipeLibraryViewModel(c: AppContainer, initialKind: String) : BusinessViewModel(c, "recipes") {
    val kind = MutableStateFlow(initialKind)
    val items = MutableStateFlow<List<JsonObject>?>(null)
    val checked = MutableStateFlow<JsonObject?>(null)

    init { load() }

    fun setKind(k: String) { kind.value = k; load() }
    fun load() = launchLoad { items.value = null; checked.value = null; items.value = c.business.get("/recipes/sources?kind=${kind.value}").objects("items") }

    private fun source(item: JsonObject) = jsonOf("source_type" to kind.value, "source_id" to item.str("id"), "expected_source_version" to item.str("source_version"))
    fun clone(item: JsonObject) = send("/recipes/copies", "post", source(item))
    fun favorite(item: JsonObject) = send("/recipes/favorites", "post", source(item))
    fun unfavorite(item: JsonObject) = send("/recipes/favorites/${item.str("id")}", "delete", jsonOf("expected_version" to item.num("version")))
    fun check(id: String) = launchLoad { checked.value = c.business.get("/recipes/copies/$id/check") }

    override suspend fun onCommandSuccess(result: JsonObject) {
        items.value = c.business.get("/recipes/sources?kind=${kind.value}").objects("items")
        result.obj("copy")?.str("id")?.let { checked.value = c.business.get("/recipes/copies/$it/check") }
    }

    /** Hands the verified copy to the kitchen; cooking starts only when the user taps start there. */
    fun prepare(): Boolean {
        val copy = checked.value?.obj("copy") ?: return false
        val recipe = runCatching {
            Recipe.normalize(copy.obj("recipe")!!.with("cookx_copy_id" to copy.str("id"), "source_meta" to copy.obj("source"), "copy_version" to copy.num("version")))
        }.getOrElse { command.setError(it.message ?: "菜谱结构异常"); return false }
        c.events.recipeDraft.value = recipe
        return true
    }
}

@Composable
fun RecipeLibraryScreen(navigator: Navigator, initialCopy: String?, onlyFavorites: Boolean) {
    val vm = cookxViewModel(key = if (onlyFavorites) "fav" else "all") { RecipeLibraryViewModel(it, if (onlyFavorites) "favorite" else "standard") }
    val kind by vm.kind.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val checked by vm.checked.collectAsStateWithLifecycle()
    val busy = rememberBusy(vm)
    LaunchedEffect(initialCopy) { initialCopy?.let(vm::check) }

    SubpageScaffold(if (onlyFavorites) "我的收藏" else "菜谱复刻", navigator::back,
        subtitle = "复刻会创建独立副本并保留来源。核对食材后送到烹饪页，由你决定何时开始；不会直接计时或扣库存。") {
        item {
            Column(Modifier.overlapHero(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CommandStatus(vm, Modifier.padding(horizontal = 16.dp))
                CookXCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Text("菜谱来源", style = MaterialTheme.typography.labelLarge, color = CookX.TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    FilterChips(kindLabels.map { Triple(it.key, it.value, null) }, kind, vm::setKind)
                }
            }
        }
        val list = items
        when {
            list == null -> item { LoadingBlock("正在读取菜谱…") }
            list.isEmpty() -> item { EmptyState(Icons.AutoMirrored.Outlined.MenuBook, "当前来源暂无可执行菜谱", "换个来源看看，或在 AI 厨房生成新的菜谱。") }
            else -> items(list, key = { it.str("id").orEmpty() }) { item -> RecipeItem(vm, item, kind, busy) }
        }
    }
    checked?.let { c ->
        val copy = c.obj("copy")
        CookXSheet("核对：${copy?.obj("recipe")?.str("dish_name").orEmpty()}", { vm.checked.value = null }, subtitle = c.str("note")) {
            val ingredients = c.objects("ingredients")
            if (ingredients.isEmpty()) Banner("来源未提供结构化食材清单，请手动检查原菜谱。", BannerKind.Info)
            ingredients.forEach { i ->
                val status = i.str("status")
                ItemBlock("${i.str("name")} · ${i.num("required_amount")?.clean(3) ?: "用量未知"} ${i.str("unit").orEmpty()}",
                    lines = listOf(checkStatuses[status] ?: status.orEmpty()),
                    badge = { StatusChip(if (status == "missing") "缺少" else "需核查", if (status == "missing") Tone.Danger else Tone.Gold) })
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton("已核对，送到烹饪页", { if (vm.prepare()) { vm.checked.value = null; navigator.tab(Routes.KITCHEN) } }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RecipeItem(vm: RecipeLibraryViewModel, item: JsonObject, kind: String, busy: Boolean) {
    var open by remember { mutableStateOf(false) }
    val recipe = item.obj("recipe")
    val steps = recipe?.objects("steps").orEmpty()
    CookXCard(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp).fillMaxWidth()) {
        Row(Modifier.clip(CookXShapes.Small).clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.AutoMirrored.Outlined.MenuBook, Tone.Gold, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(recipe?.str("dish_name").orEmpty(), style = MaterialTheme.typography.titleMedium)
                Text("${steps.size} 个步骤 · ${recipe?.str("method") ?: "烹饪方法未标注"}", fontSize = 12.sp, color = CookX.TextSecondary)
            }
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, "查看步骤与来源", tint = CookX.TextSecondary)
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = 12.dp)) {
                steps.forEachIndexed { i, s ->
                    Row(Modifier.padding(bottom = 6.dp)) {
                        Box(Modifier.size(20.dp).clip(CookXShapes.Pill).background(CookX.Mint), contentAlignment = Alignment.Center) { Text("${i + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CookX.Primary) }
                        Spacer(Modifier.width(8.dp))
                        Text("${s.str("text")}（${s.num("time_estimate")?.let { "${it.clean()} 秒" } ?: "时长未提供"}）", style = MaterialTheme.typography.bodySmall, color = CookX.TextBody)
                    }
                }
                Text("来源：${sourceLabels[item.obj("source")?.str("type") ?: kind] ?: item.obj("source")?.str("type")} · 版本 ${item.obj("source")?.str("version") ?: item.str("source_version")}", fontSize = 11.sp, color = CookX.TextTertiary)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(if (kind == "copy") "再创建副本" else "一键复刻", { vm.clone(item) }, enabled = !busy, icon = Icons.Outlined.ContentCopy)
            if (kind != "favorite") TonalButton("收藏此版本", { vm.favorite(item) }, enabled = !busy, icon = Icons.Outlined.StarOutline, tone = Tone.Gold)
            else OutlineButton("取消收藏", { vm.unfavorite(item) }, enabled = !busy, icon = Icons.Outlined.Star, color = CookX.Accent)
            if (kind == "copy") OutlineButton("核对食材", { vm.check(item.str("id").orEmpty()) }, enabled = !busy, icon = Icons.Outlined.FactCheck)
        }
    }
}
