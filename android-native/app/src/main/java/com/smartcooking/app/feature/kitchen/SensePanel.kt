package com.smartcooking.app.feature.kitchen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartcooking.app.core.Time
import com.smartcooking.app.device.BtDevice
import com.smartcooking.app.device.DeviceConnection
import com.smartcooking.app.device.TemperatureSample
import com.smartcooking.app.temperature.Assessment
import com.smartcooking.app.temperature.Prediction
import com.smartcooking.app.ui.components.ActionRow
import com.smartcooking.app.ui.components.CookXSheet
import com.smartcooking.app.ui.components.CookXTextField
import com.smartcooking.app.ui.components.DarkPanel
import com.smartcooking.app.ui.components.OutlineButton
import com.smartcooking.app.ui.components.PrimaryButton
import com.smartcooking.app.ui.components.StatusChip
import com.smartcooking.app.ui.components.SwitchRow
import com.smartcooking.app.ui.components.TonalButton
import com.smartcooking.app.ui.components.Tone
import com.smartcooking.app.ui.components.AccentButton
import com.smartcooking.app.ui.components.pressable
import com.smartcooking.app.ui.theme.CookX
import com.smartcooking.app.ui.theme.CookXShapes

@Composable
fun SensePanel(
    connection: DeviceConnection,
    latest: TemperatureSample?,
    assessment: Assessment,
    history: List<TemperatureSample>,
    prediction: Prediction?,
    modelState: String,
    experimental: Boolean,
    replaying: Boolean,
    storageMessage: String,
    connecting: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onEvent: (String) -> Unit,
    onExperimental: (Boolean) -> Unit,
    onReplay: () -> Unit,
    onStopReplay: () -> Unit,
    onExport: (Boolean) -> Unit,
) {
    var details by remember { mutableStateOf(false) }
    val riskTone = when (assessment.risk) { "danger" -> Tone.Danger; "warning" -> Tone.Gold; else -> Tone.OnDark }
    DarkPanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (connection.connected) Icons.Outlined.BluetoothConnected else Icons.Outlined.Bluetooth, null, tint = CookX.Gold, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("CookX Sense 温度监控", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            StatusChip(if (replaying) "仿真回放" else connection.label, if (connection.connected) Tone.Fresh else Tone.OnDark, dot = true)
        }
        Spacer(Modifier.height(16.dp))
        Row {
            Reading("锅面温度", assessment.temperature ?: latest?.temperature, if (latest == null && assessment.temperature == null) "等待数据" else assessment.phaseLabel, Modifier.weight(1f), big = true)
            Reading("环境温度", latest?.ambientTemperature, if (latest?.ambientTemperature == null) "固件未上传环境温度" else "设备环境温度", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChip(assessment.qualityLabel, if (assessment.quality == "usable") Tone.Fresh else Tone.OnDark)
            StatusChip(assessment.phaseLabel, Tone.OnDark)
            if (assessment.risk == "warning" || assessment.risk == "danger") StatusChip(if (assessment.risk == "danger") "持续高温" else "温度偏高", riskTone)
        }
        Spacer(Modifier.height(12.dp))
        TemperatureChart(history)
        Text(if (replaying) "物理仿真回放 · 非设备实测" else "设备测温 · 移动识别研究版", color = CookX.OnDarkMuted, fontSize = 10.5.sp, modifier = Modifier.padding(top = 4.dp))
        assessment.reasons.forEach { Text("· $it", color = CookX.OnDarkMuted, fontSize = 12.sp, lineHeight = 18.sp) }
        assessment.suggestion?.let {
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().clip(CookXShapes.Small).background(if (riskTone == Tone.OnDark) Color.White.copy(alpha = 0.08f) else riskTone.fg.copy(alpha = 0.25f)).padding(10.dp)) {
                Text(it, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ingredient_added" to "刚投料", "probe_moved" to "移动了探头", "heat_off" to "已关火").forEach { (type, label) ->
                TonalButton(label, { onEvent(type) }, Modifier.weight(1f), tone = Tone.OnDark)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (!connection.connected) AccentButton(if (connecting) "连接中…" else "连接测温设备", onConnect, Modifier.fillMaxWidth(), icon = Icons.Outlined.Bluetooth, enabled = !connecting)
        else OutlineButton("断开设备", onDisconnect, Modifier.fillMaxWidth(), color = Color.White)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp).clip(CookXShapes.Small).clickable { details = !details }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("算法与记录", color = CookX.OnDarkMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(if (details) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = CookX.OnDarkMuted)
        }
        AnimatedVisibility(details) {
            Column(Modifier.clip(CookXShapes.Tile).background(Color.White).padding(12.dp)) {
                Text(modelState, style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary)
                SwitchRow("启用实验模型（仿真训练）", experimental, onExperimental, subtitle = "Transformer 仅在测量可用且置信时显示阶段与预测")
                if (prediction != null) {
                    Text("模型阶段估计：${prediction.phaseLabel}（概率 ${"%.0f".format(prediction.probability * 100)}%）", style = MaterialTheme.typography.titleSmall)
                    prediction.forecast.forEach { Text("${it.seconds} 秒后：${"%.0f".format(it.low)}–${"%.0f".format(it.high)} ℃", style = MaterialTheme.typography.bodySmall) }
                    Text(prediction.note, fontSize = 11.sp, color = CookX.TextTertiary)
                } else if (experimental) Text("证据不足或窗口正在恢复，暂不显示模型预测。", style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary)
                ActionRow(Modifier.padding(top = 10.dp)) {
                    if (!replaying) TonalButton("仿真回放", onReplay, enabled = !connection.connected) else TonalButton("停止回放", onStopReplay, tone = Tone.Warm)
                    OutlineButton("导出本次", { onExport(false) })
                    OutlineButton("导出保存记录", { onExport(true) })
                }
                if (connection.connected) Text("回放需先断开设备，避免混淆数据来源。", fontSize = 11.sp, color = CookX.TextTertiary)
                if (storageMessage.isNotBlank()) Text(storageMessage, fontSize = 11.sp, color = CookX.Danger)
            }
        }
    }
}

@Composable
private fun Reading(label: String, value: Double?, caption: String, modifier: Modifier, big: Boolean = false) {
    Column(modifier) {
        Text(label, color = CookX.OnDarkMuted, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value?.let { "%.1f".format(it) } ?: "--", color = Color.White, fontSize = if (big) 34.sp else 24.sp, fontWeight = FontWeight.Bold)
            Text("°C", color = CookX.OnDarkMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 3.dp, bottom = if (big) 7.dp else 4.dp))
        }
        Text(caption, color = CookX.OnDarkMuted, fontSize = 11.sp)
    }
}

/** Last 60 s of readings; gaps and discontinuities break the line rather than being interpolated. */
@Composable
fun TemperatureChart(history: List<TemperatureSample>) {
    val values = history.filter { it.valid && it.temperature != null }.map { it.temperature!! }
    val lo = (values.minOrNull() ?: 0.0) - 5
    val hi = (values.maxOrNull() ?: 100.0) + 5
    Column(Modifier.fillMaxWidth().clip(CookXShapes.Tile).background(Color.White.copy(alpha = 0.05f)).padding(10.dp)) {
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            for (f in listOf(0.1f, 0.5f, 0.9f)) drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, size.height * f), Offset(size.width, size.height * f), 1f, pathEffect = dash)
            val end = history.lastOrNull()?.updatedAt ?: return@Canvas
            var path = Path(); var started = false; var previous: TemperatureSample? = null
            fun flush() { if (started) drawPath(path, CookX.AccentLight, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round)); path = Path(); started = false }
            for (s in history) {
                if (!s.valid || s.discontinuity || (previous != null && s.updatedAt - previous.updatedAt > 1500)) flush()
                val t = s.temperature
                if (s.valid && t != null) {
                    val x = ((s.updatedAt - end + 60_000).toFloat() / 60_000f) * size.width
                    val y = size.height * 0.9f - ((t - lo) / (hi - lo)).toFloat() * size.height * 0.8f
                    if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                }
                previous = s
            }
            flush()
        }
        Text("${"%.0f".format(lo)}–${"%.0f".format(hi)} ℃ · 最近 60 秒", color = CookX.OnDarkMuted, fontSize = 10.sp)
    }
}

@Composable
fun DeviceSheet(
    devices: List<BtDevice>,
    scanning: Boolean,
    connecting: Boolean,
    initialAddress: String,
    onScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(initialAddress) }
    var advanced by remember { mutableStateOf(false) }
    CookXSheet("连接 CookX Sense", onDismiss, subtitle = "扫描最多约 25 秒。已配对设备也会列出，是否在线需点击连接确认。") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (scanning) CircularProgressIndicator(Modifier.size(16.dp), color = CookX.Accent, strokeWidth = 2.dp)
            Text(if (scanning) "  正在扫描 Classic Bluetooth 设备" else "发现 ${devices.size} 个设备", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TonalButton("重新扫描", onScan, icon = Icons.Outlined.Refresh, enabled = !scanning)
        }
        Spacer(Modifier.height(12.dp))
        devices.forEach { d ->
            val active = d.address.equals(selected, true)
            Row(
                Modifier.padding(bottom = 8.dp).fillMaxWidth().clip(CookXShapes.Tile)
                    .background(if (active) CookX.Mint else CookX.Surface)
                    .border(1.dp, if (active) CookX.Primary else if (d.isJdy31) CookX.Accent.copy(alpha = 0.4f) else CookX.Border, CookXShapes.Tile)
                    .pressable({ selected = d.address }).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(d.name, style = MaterialTheme.typography.titleSmall)
                    Text(d.address, fontSize = 11.sp, color = CookX.TextSecondary)
                }
                StatusChip(if (d.bonded) "已配对" else if (d.isJdy31) "CookX 设备" else "Classic", if (d.isJdy31) Tone.Warm else Tone.Neutral)
            }
        }
        if (devices.isEmpty()) Text(if (scanning) "正在扫描附近的 JDY-31…" else "暂未发现设备，请确认模块已上电。", style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary)
        Row(Modifier.clip(CookXShapes.Small).clickable { advanced = !advanced }.padding(vertical = 8.dp)) {
            Text("高级调试：手工地址", fontSize = 12.sp, color = CookX.TextSecondary)
        }
        if (advanced) CookXTextField(selected, { selected = it.uppercase().take(17) }, "设备地址", placeholder = "00:11:22:33:44:55")
        Spacer(Modifier.height(14.dp))
        PrimaryButton(if (connecting) "正在连接…" else "连接", { onConnect(selected) }, Modifier.fillMaxWidth(), loading = connecting, enabled = selected.isNotBlank())
    }
}

fun lastUpdateText(latest: TemperatureSample?) = latest?.let { Time.clockSeconds(it.receivedAt) } ?: "--"
