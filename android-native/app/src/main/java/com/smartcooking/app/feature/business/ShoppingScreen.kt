package com.smartcooking.app.feature.business

import com.smartcooking.app.core.toFiniteOrNull
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartcooking.app.core.AppContainer
import com.smartcooking.app.core.Time
import com.smartcooking.app.core.clean
import com.smartcooking.app.core.jsonOf
import com.smartcooking.app.core.num
import com.smartcooking.app.core.objects
import com.smartcooking.app.core.str
import com.smartcooking.app.ui.components.Choice
import com.smartcooking.app.ui.components.CookXSheet
import com.smartcooking.app.ui.components.CookXTextField
import com.smartcooking.app.ui.components.DateTimeField
import com.smartcooking.app.ui.components.DropdownField
import com.smartcooking.app.ui.components.EmptyState
import com.smartcooking.app.ui.components.OutlineButton
import com.smartcooking.app.ui.components.PrimaryButton
import com.smartcooking.app.ui.components.SegmentedTabs
import com.smartcooking.app.ui.components.StatusChip
import com.smartcooking.app.ui.components.SubpageScaffold
import com.smartcooking.app.ui.components.TonalButton
import com.smartcooking.app.ui.components.Tone
import com.smartcooking.app.ui.components.overlapHero
import com.smartcooking.app.ui.nav.Navigator
import com.smartcooking.app.ui.nav.Routes
import com.smartcooking.app.ui.nav.cookxViewModel
import com.smartcooking.app.ui.theme.CookX
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject

private val shoppingUnits = listOf("库存计数", "克", "千克", "毫升", "升", "个", "根", "份", "瓣", "片", "袋", "包")
private val eventLabels = mapOf("added" to "添加需求", "claim" to "认领", "release" to "释放认领", "bought" to "标记购买", "cancel" to "取消需求", "stocked" to "确认入库")

data class StockIn(val item: JsonObject, val quantity: String, val unit: String, val storage: String? = null, val shelf: String = "", val purchase: String = "", val expiry: String = "")

class ShoppingViewModel(c: AppContainer) : BusinessViewModel(c, "shopping") {
    val families = MutableStateFlow<List<JsonObject>>(emptyList())
    val selected = MutableStateFlow("")
    val items = MutableStateFlow<List<JsonObject>>(emptyList())
    val sources = MutableStateFlow<JsonObject?>(null)
    val stockIn = MutableStateFlow<StockIn?>(null)
    val root get() = "/households/${selected.value}/shopping"

    init {
        launchLoad {
            families.value = c.business.get("/households").objects("items")
            selected.value = families.value.firstOrNull()?.str("id").orEmpty()
            load()
        }
    }

    fun select(id: String) { selected.value = id; launchLoad { load() } }
    fun refresh() = launchLoad { load() }

    private suspend fun load() {
        items.value = emptyList(); sources.value = null; stockIn.value = null
        if (selected.value.isEmpty()) return
        val family = selected.value
        coroutineScope {
            val list = async { c.business.get(root) }
            val catalog = async { c.business.get("$root/sources") }
            val (l, s) = list.await() to catalog.await()
            if (family == selected.value) { items.value = l.objects("items"); sources.value = s }
        }
    }

    override suspend fun onCommandSuccess(result: JsonObject) { stockIn.value = null; load() }

    fun change(item: JsonObject, action: String) = send("$root/${item.str("id")}/state", "post", jsonOf("expected_version" to item.num("version"), "action" to action))

    fun confirmStockIn() {
        val s = stockIn.value ?: return
        try {
            send("$root/${s.item.str("id")}/stock-in", "post", jsonOf(
                "expected_version" to s.item.num("version"), "name" to s.item.str("name"), "quantity" to s.quantity.toDouble(), "unit" to s.unit,
                "storage_type" to s.storage, "shelf_life" to s.shelf.toFiniteOrNull(), "purchase_time" to isoOrNull(s.purchase), "expiry_date" to isoOrNull(s.expiry),
            ))
        } catch (e: Exception) { command.setError(e.message ?: "输入无效") }
    }
}

fun stateLabel(item: JsonObject, user: String): String = when (item.str("state")) {
    "bought" -> "已购买，等待确认实际入库"
    "stocked" -> "已入库"
    "cancelled" -> "已取消"
    else -> item.str("claimed_by")?.let { if (it == user) "我已认领" else "其他成员已认领" } ?: "未认领"
}

@Composable
fun ShoppingScreen(navigator: Navigator) {
    val vm = cookxViewModel { ShoppingViewModel(it) }
    val families by vm.families.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val stockIn by vm.stockIn.collectAsStateWithLifecycle()
    val busy = rememberBusy(vm)
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var sourceType by remember { mutableStateOf("recipe") }
    var sourceId by remember { mutableStateOf("") }
    var multiplier by remember { mutableStateOf("1") }
    val active = items.filter { it.str("state") in listOf("open", "bought") }
    val finished = items.filter { it.str("state") !in listOf("open", "bought") }

    SubpageScaffold("共同采购清单", navigator::back, subtitle = "认领后由认领成员标记购买。购买不会自动入库，收到食材后还需确认实际数量与储存信息。") {
        item {
            Column(Modifier.overlapHero(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CommandStatus(vm, Modifier.padding(horizontal = 16.dp))
                Section("选择家庭", icon = Icons.Outlined.ShoppingCart) {
                    if (families.isEmpty()) EmptyState(Icons.Outlined.ShoppingCart, "还没有家庭", "共同采购基于家庭共享冰箱，请先创建或加入家庭。") {
                        PrimaryButton("管理家庭", { navigator.open(Routes.HOUSEHOLD) })
                    } else {
                        DropdownField("家庭", selected, families.map { Choice(it.str("id").orEmpty(), it.str("name").orEmpty()) }, vm::select)
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlineButton("刷新清单", vm::refresh, enabled = !busy)
                            OutlineButton("管理家庭", { navigator.open(Routes.HOUSEHOLD) })
                        }
                    }
                }
            }
        }
        if (selected.isNotEmpty()) {
            item {
                Section("添加采购需求", Modifier.padding(top = 12.dp), icon = Icons.Outlined.AddShoppingCart) {
                    CookXTextField(name, { name = it }, "食材名称", maxLength = 80)
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CookXTextField(quantity, { quantity = it }, "需求数量（可未知）", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                        CookXTextField(unit, { unit = it }, "单位", Modifier.weight(1f), placeholder = "克、个；未知填待确认", maxLength = 16)
                    }
                    Text("仅同名、同单位的未认领需求合并。个、根、份与克数不自动换算。", fontSize = 12.sp, color = CookX.TextSecondary, modifier = Modifier.padding(vertical = 8.dp))
                    PrimaryButton("添加到共同清单", {
                        vm.send(vm.root, "post", jsonOf("name" to name.trim(), "quantity" to quantity.toFiniteOrNull(), "unit" to unit.trim()))
                        name = ""; quantity = ""
                    }, Modifier.fillMaxWidth(), enabled = !busy && name.isNotBlank() && unit.isNotBlank())
                }
            }
            item {
                Section("从菜谱或菜单生成", Modifier.padding(top = 12.dp), icon = Icons.Outlined.AutoAwesome,
                    subtitle = "菜谱份量不明时按原配方倍数计算，不推测人数。菜单使用已核对的补购需求。") {
                    SegmentedTabs(listOf(Choice("recipe", "标准菜谱"), Choice("menu", "已保存菜单")), sourceType, { sourceType = it; sourceId = "" })
                    Spacer(Modifier.height(10.dp))
                    val options = sources?.objects(if (sourceType == "recipe") "recipes" else "menus").orEmpty()
                    DropdownField("选择来源", sourceId, listOf(Choice("", "请选择")) + options.map { Choice(it.str("id").orEmpty(), it.str("name").orEmpty()) }, { sourceId = it })
                    if (sourceType == "recipe") CookXTextField(multiplier, { multiplier = it }, "原配方倍数", Modifier.padding(top = 10.dp), keyboardType = KeyboardType.Decimal)
                    Spacer(Modifier.height(10.dp))
                    TonalButton("确认生成采购需求", {
                        vm.send("${vm.root}/generate", "post", jsonOf("source_type" to sourceType, "source_id" to sourceId, "multiplier" to if (sourceType == "recipe") (multiplier.toFiniteOrNull() ?: 1.0) else 1))
                    }, Modifier.fillMaxWidth(), enabled = !busy && sourceId.isNotBlank(), tone = Tone.Warm)
                }
            }
            item {
                Section("待采购与待入库", Modifier.padding(top = 12.dp), icon = Icons.Outlined.Inventory, trailing = { StatusChip("${active.size} 项", Tone.Warm) }) {
                    if (active.isEmpty()) Text("暂无待处理项目。", style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary)
                    active.forEach { item -> ShoppingItem(vm, item, busy) }
                }
            }
            item {
                Section("采购历史", Modifier.padding(top = 12.dp), icon = Icons.Outlined.History) {
                    if (finished.isEmpty()) Text("暂无已结束项目。", style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary)
                    finished.forEach { item ->
                        ItemBlock("${item.str("name")} · ${stateLabel(item, vm.user)}", lines = item.objects("history").map { e ->
                            "${eventLabels[e.str("type")] ?: e.str("type")} · ${Time.display(e.str("at"))}" + if (e.str("type") == "stocked") " · 实际 ${e.num("quantity")?.clean(3)} ${e.str("unit")}" else ""
                        })
                    }
                }
            }
        }
    }
    stockIn?.let { s ->
        CookXSheet("确认「${s.item.str("name")}」实际入库", { vm.stockIn.value = null }, dismissible = !busy, subtitle = "填写实际收到的数量与储存信息，写入家庭冰箱") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CookXTextField(s.quantity, { vm.stockIn.value = s.copy(quantity = it) }, "实际数量", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                    DropdownField("实际单位", s.unit, listOf(Choice("", "请选择")) + shoppingUnits.map { Choice(it, it) }, { vm.stockIn.value = s.copy(unit = it) }, Modifier.weight(1f))
                }
                DropdownField("储存方式", s.storage, storageChoices, { vm.stockIn.value = s.copy(storage = it) })
                CookXTextField(s.shelf, { vm.stockIn.value = s.copy(shelf = it) }, "保质期（天，可未知）", keyboardType = KeyboardType.Decimal)
                DateTimeField("购买时间（可未知）", s.purchase, { vm.stockIn.value = s.copy(purchase = it) })
                DateTimeField("到期时间（可未知）", s.expiry, { vm.stockIn.value = s.copy(expiry = it) })
                PrimaryButton("确认入家庭冰箱", vm::confirmStockIn, Modifier.fillMaxWidth(), loading = busy, enabled = s.unit.isNotBlank() && (s.quantity.toFiniteOrNull() ?: 0.0) > 0)
            }
        }
    }
}

@Composable
private fun ShoppingItem(vm: ShoppingViewModel, item: JsonObject, busy: Boolean) {
    var history by remember { mutableStateOf(false) }
    val state = item.str("state")
    val claimed = item.str("claimed_by")
    ItemBlock(
        "${item.str("name")} · ${item.num("quantity")?.clean(3) ?: "数量待确认"} ${item.str("unit").orEmpty()}",
        badge = { StatusChip(stateLabel(item, vm.user), if (state == "bought") Tone.Gold else if (claimed != null) Tone.Warm else Tone.Neutral) },
        actions = {
            if (state == "open") {
                if (claimed == null) TonalButton("我来采购", { vm.change(item, "claim") }, enabled = !busy, tone = Tone.Warm)
                if (claimed == null || claimed == vm.user) {
                    TonalButton("标记已购买", { vm.change(item, "bought") }, enabled = !busy)
                    if (claimed != null) OutlineButton("释放认领", { vm.change(item, "release") }, enabled = !busy)
                    OutlineButton("取消需求", { vm.change(item, "cancel") }, enabled = !busy, color = CookX.Danger)
                }
            } else if (state == "bought") {
                PrimaryButton("确认实际入库信息", {
                    vm.stockIn.value = StockIn(item, item.num("quantity")?.clean(3).orEmpty(), item.str("unit")?.takeIf { it in shoppingUnits }.orEmpty())
                }, enabled = !busy)
            }
        },
    ) {
        Text(if (history) "收起操作历史" else "操作历史", fontSize = 12.sp, color = CookX.Primary, modifier = Modifier.padding(top = 6.dp).clickable { history = !history })
        AnimatedVisibility(history) {
            Column { item.objects("history").forEach { e -> Text("${eventLabels[e.str("type")] ?: e.str("type")} · ${Time.display(e.str("at"))}", fontSize = 11.5.sp, color = CookX.TextSecondary) } }
        }
    }
}
