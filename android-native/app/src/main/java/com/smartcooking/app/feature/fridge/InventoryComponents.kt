package com.smartcooking.app.feature.fridge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartcooking.app.core.Time
import com.smartcooking.app.core.str
import com.smartcooking.app.data.FreshLevel
import com.smartcooking.app.data.Freshness
import com.smartcooking.app.data.FreshnessState
import com.smartcooking.app.data.FreshnessStatus
import com.smartcooking.app.data.InventoryForm
import com.smartcooking.app.data.InventoryItem
import com.smartcooking.app.data.InventoryRules
import com.smartcooking.app.ui.components.Banner
import com.smartcooking.app.ui.components.BannerKind
import com.smartcooking.app.ui.components.Choice
import com.smartcooking.app.ui.components.ConfirmDialog
import com.smartcooking.app.ui.components.CookXTextField
import com.smartcooking.app.ui.components.DateTimeField
import com.smartcooking.app.ui.components.DropdownField
import com.smartcooking.app.ui.components.OutlineButton
import com.smartcooking.app.ui.components.StatusChip
import com.smartcooking.app.ui.components.Tone
import com.smartcooking.app.ui.theme.CookX
import com.smartcooking.app.ui.theme.CookXShapes

fun FreshLevel.tone(): Tone = when (this) {
    FreshLevel.FRESH -> Tone.Fresh
    FreshLevel.SOON -> Tone.Gold
    FreshLevel.EXPIRED -> Tone.Danger
    FreshLevel.UNKNOWN -> Tone.Neutral
}

@Composable
fun FreshnessBadge(detail: Freshness?, state: FreshnessState, modifier: Modifier = Modifier) {
    val status = when (state) {
        FreshnessState.Loading -> FreshnessStatus(FreshLevel.UNKNOWN, "评估中", "")
        is FreshnessState.Failed -> FreshnessStatus(FreshLevel.UNKNOWN, "评估失败", "")
        else -> FreshnessStatus.of(detail)
    }
    StatusChip(status.label, status.level.tone(), modifier = modifier, dot = true)
}

/** FreshFusion result: score, confidence, risks and the collapsible evidence (weights come from the server). */
@Composable
fun FreshnessPanel(detail: Freshness?, state: FreshnessState, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().clip(CookXShapes.Small).background(CookX.SurfaceMuted).padding(10.dp)) {
        when {
            state == FreshnessState.Loading -> Text("鲜度评估中…", style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary)
            state is FreshnessState.Failed -> Text("鲜度评估失败，库存仍保留", style = MaterialTheme.typography.bodySmall, color = CookX.Danger)
            detail == null -> Text("鲜度未知，尚无可用评估", style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary)
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(detail.status.description, style = MaterialTheme.typography.labelLarge, color = detail.status.level.tone().fg, modifier = Modifier.weight(1f))
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, "查看鲜度依据", tint = CookX.TextSecondary,
                        modifier = Modifier.size(20.dp).clip(CookXShapes.Pill).clickable { expanded = !expanded })
                }
                val score = detail.freshScore
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("FreshScore", fontSize = 11.sp, color = CookX.TextSecondary, modifier = Modifier.width(72.dp))
                    if (score != null) {
                        LinearProgressIndicator(
                            progress = { score.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.weight(1f).height(5.dp).clip(CookXShapes.Pill),
                            color = detail.status.level.tone().fg, trackColor = CookX.Border, drawStopIndicator = {},
                        )
                        Text("  ${"%.2f".format(score)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    } else Text("数据不足", fontSize = 11.sp, color = CookX.TextSecondary)
                }
                Text(
                    "置信度 ${detail.confidence?.let { "${Math.round(it * 100)}%" } ?: "数据不足"}${detail.confidenceLevel?.let { " · $it" }.orEmpty()}",
                    fontSize = 11.sp, color = CookX.TextSecondary, modifier = Modifier.padding(top = 3.dp),
                )
                AnimatedVisibility(expanded) {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        val small = @Composable { t: String -> Text(t, fontSize = 11.5.sp, color = CookX.TextBody, lineHeight = 17.sp) }
                        small("风险：" + (detail.riskFlags.takeIf { it.isNotEmpty() }?.joinToString("；") ?: "未返回风险标记（不代表安全）"))
                        detail.reasons.forEach { small("· $it") }
                        listOf("T" to "时间", "S" to "储存", "V" to "视觉", "H" to "历史").forEach { (k, label) ->
                            val s = detail.componentScore(k); val w = detail.effectiveWeight(k)
                            small("$k $label：${s?.let { "%.2f（0–1）".format(it) } ?: "数据不足"}；权重 ${w?.takeIf { it > 0 }?.let { "${Math.round(it * 100)}%" } ?: "未参与"}")
                        }
                        detail.notes.forEach { small(it) }
                        small("起始：${Time.display(detail.timeDetails?.str("start_time"))} · 到期：${Time.display(detail.timeDetails?.str("expiry_time"))}")
                        small("储存依据：${detail.storageDetails?.str("reason") ?: detail.storageDetails?.str("status") ?: "数据不足"}")
                        small("评估时间 ${Time.display(detail.evaluatedAt)} · 算法 ${detail.algorithmVersion ?: "未提供"}")
                        small("权重和分项直接来自服务端；未提供的视觉与历史数据不作推测。")
                    }
                }
                detail.disclaimer?.let { Text(it, fontSize = 10.5.sp, color = CookX.TextTertiary, lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
        }
    }
}

/** Inventory batch fields; dates are optional and blank means unknown. */
@Composable
fun InventoryFields(form: InventoryForm, onChange: (InventoryForm) -> Unit, enabled: Boolean, original: InventoryItem? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CookXTextField(form.name, { onChange(form.copy(name = it)) }, "名称", placeholder = "如：牛肉", enabled = enabled)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CookXTextField(form.quantity, { onChange(form.copy(quantity = it.filter(Char::isDigit))) }, "数量（库存计数）", Modifier.weight(1f), enabled = enabled, keyboardType = KeyboardType.Number)
            CookXTextField(form.shelfLife, { onChange(form.copy(shelfLife = it)) }, "保质期（天）", Modifier.weight(1f), placeholder = "未知", enabled = enabled, keyboardType = KeyboardType.Decimal)
        }
        val storageOptions = buildList {
            add(Choice("", "未填写"))
            InventoryRules.storageOptions.forEach { add(Choice(it, it)) }
            if (form.storageType.isNotBlank() && form.storageType !in InventoryRules.storageOptions) add(Choice(form.storageType, "${form.storageType}（原值）"))
        }
        DropdownField("储存方式", form.storageType, storageOptions, { onChange(form.copy(storageType = it)) }, enabled = enabled)
        DateTimeField("购买时间（可未知）", form.purchaseTime, { onChange(form.copy(purchaseTime = it)) }, enabled = enabled)
        DateTimeField("入库时间（可未知）", form.addTime, { onChange(form.copy(addTime = it)) }, enabled = enabled)
        DateTimeField("到期时间（可未知）", form.expiryDate, { onChange(form.copy(expiryDate = it)) }, enabled = enabled)
        Text(
            if (original == null) "未填写的购买/到期信息保持未知；入库时间留空时由服务器记录。"
            else "只保存修改过的字段；清空代表未知。原入库时间：${Time.display(original.addTime)}",
            style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary,
        )
    }
}

/** Shown when a previous write's outcome is unknown; releasing only drops the local guard. */
@Composable
fun PendingWriteNotice(pending: Boolean, disabled: Boolean, onRelease: () -> Unit) {
    if (!pending) return
    var confirm by remember { mutableStateOf(false) }
    Banner(
        "上次写入结果待确认。再次保存只会重新读取库存，不会重复发送入库请求。请先核对库存，避免重复添加。",
        BannerKind.Pending, title = "写入待确认",
    ) { OutlineButton("已核对库存，解除待确认", { confirm = true }, enabled = !disabled, color = CookX.Accent) }
    if (confirm) ConfirmDialog(
        "核对保存结果", "仅解除本地防重复保护，不撤销或重发上次写入。确认已查看库存并核对数量和日期？",
        onConfirm = { confirm = false; onRelease() }, onDismiss = { confirm = false }, confirmText = "已核对", dismissText = "继续核对",
    )
}

@Composable
fun SmallMuted(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary, modifier = modifier)

@Composable
fun CategoryTag(text: String) = Box(Modifier.clip(CookXShapes.Pill).background(CookX.Mint).padding(horizontal = 8.dp, vertical = 2.dp)) {
    Text(text, fontSize = 10.5.sp, color = CookX.Primary, fontWeight = FontWeight.SemiBold)
}
