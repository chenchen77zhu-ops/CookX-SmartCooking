package com.smartcooking.app.feature.fridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartcooking.app.core.LocalAppContainer
import com.smartcooking.app.core.Time
import com.smartcooking.app.core.asObject
import com.smartcooking.app.core.jsonOf
import com.smartcooking.app.core.str
import com.smartcooking.app.core.toFiniteOrNull
import com.smartcooking.app.core.userMessage
import com.smartcooking.app.data.Freshness
import com.smartcooking.app.data.FreshnessState
import com.smartcooking.app.data.InventoryForm
import com.smartcooking.app.ui.components.AnimatedBanner
import com.smartcooking.app.ui.components.BannerKind
import com.smartcooking.app.ui.components.CookXCard
import com.smartcooking.app.ui.components.PrimaryButton
import com.smartcooking.app.ui.components.SectionHeader
import com.smartcooking.app.ui.components.SubpageScaffold
import com.smartcooking.app.ui.nav.Navigator
import com.smartcooking.app.ui.theme.CookX
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 鲜度检测: evaluates one ingredient with the FreshFusion algorithm without saving it
 * (`POST /api/freshness/evaluate`), e.g. before buying or for something not in the fridge yet.
 */
@Composable
fun FreshnessCheckScreen(navigator: Navigator) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf(InventoryForm(quantity = "1")) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<Pair<Freshness, String?>?>(null) }

    fun evaluate() {
        if (form.name.isBlank()) { error = "请填写食材名称"; return }
        busy = true; error = null
        scope.launch {
            try {
                val body = jsonOf(
                    "ingredient_name" to form.name.trim(),
                    "storage_type" to form.storageType.ifBlank { null },
                    "shelf_life" to form.shelfLife.toFiniteOrNull(),
                    "purchase_time" to Time.isoFromLocal(form.purchaseTime),
                    "add_time" to Time.isoFromLocal(form.addTime),
                    "expiry_date" to Time.isoFromLocal(form.expiryDate),
                )
                val res = container.api.post("/freshness/evaluate", body, timeoutMs = 20_000).asObject() ?: throw IllegalStateException("评估服务未返回结果")
                result = Freshness(res) to res.str("evaluated_at")
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                error = e.userMessage()
            } finally { busy = false }
        }
    }

    SubpageScaffold("鲜度检测", navigator::back, subtitle = "输入一件食材的购买时间、保质期与储存方式，即时查看 FreshFusion 评估；不会加入冰箱。") {
        item {
            CookXCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                SectionHeader("食材信息", icon = Icons.Outlined.Spa, subtitle = "未知的信息可以留空，评估会标注数据不足。")
                Spacer(Modifier.height(12.dp))
                InventoryFields(form, { form = it; result = null }, enabled = !busy)
                AnimatedBanner(error, BannerKind.Warning, Modifier.padding(top = 12.dp))
                Spacer(Modifier.height(14.dp))
                PrimaryButton("开始评估", ::evaluate, Modifier.fillMaxWidth(), loading = busy, enabled = form.name.isNotBlank())
            }
        }
        result?.let { (detail, at) ->
            item {
                CookXCard(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp).fillMaxWidth()) {
                    SectionHeader("评估结果", subtitle = "评估时间 ${Time.display(at)}")
                    Spacer(Modifier.height(12.dp))
                    FreshnessPanel(detail, FreshnessState.Ready(mapOf(detail.itemId to detail), at))
                    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        detail.reasons.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall, color = CookX.TextBody) }
                        detail.disclaimer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = CookX.TextTertiary) }
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text("鲜度评估仅根据你提供的时间与储存信息推算，不能代替实际检查；闻、看、摸有异常请不要食用。", style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary)
            }
        }
    }
}
