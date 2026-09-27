package com.smartcooking.app.feature.live

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.smartcooking.app.ui.components.topInset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartcooking.app.ui.components.pressable
import com.smartcooking.app.ui.theme.KitchenColors
import com.smartcooking.app.ui.theme.NumericFont
import kotlin.math.roundToInt

/** Actions of the live kitchen page; every one is optional so previews can pass nothing. */
data class LiveKitchenActions(
    val onBell: () -> Unit = {},
    val onDevice: () -> Unit = {},
    val onTrend: () -> Unit = {},
    val onAdvice: () -> Unit = {},
    val onAlert: () -> Unit = {},
)

/**
 * Real-time kitchen, after the Apple Weather layout: the pan photograph fills the screen, one huge
 * reading, a stage pill, then three cards (stages, trend, CookX advice). Nothing else competes.
 */
@Composable
fun LiveKitchenContent(state: LiveState, modifier: Modifier = Modifier, actions: LiveKitchenActions = LiveKitchenActions(), unread: Boolean = false, animate: Boolean = true) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(0 to 0) }
    Box(
        modifier.fillMaxSize().background(KitchenColors.Base).onGloballyPositioned {
            origin = it.positionInRoot(); size = it.size.width to it.size.height
        },
    ) {
        StageBackdrop(state.backdrop, alert = state.alert?.level == AlertLevel.Danger, animate = animate)
        CompositionLocalProvider(LocalGlassSource provides GlassSource(state.backdrop, origin, size.first, size.second)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val compact = maxHeight < 700.dp
                val screenHeight = maxHeight
                // Reading on top, cards at the bottom, the pan in between. Only scrolls when a small
                // screen (or 3-button navigation) cannot fit everything.
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = screenHeight).topInset().padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            KitchenHeader(unread, actions.onBell, compact)
                            TitleRow(state, actions.onDevice, compact)
                            Spacer(Modifier.height(if (compact) 6.dp else 18.dp))
                            Reading(state, compact, actions.onAlert)
                        }
                        Column(Modifier.padding(top = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            StageCard(state, compact)
                            TrendCard(state, compact, actions.onTrend)
                            AdviceCard(state, if (state.alert != null) actions.onAlert else actions.onAdvice, compact)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KitchenHeader(unread: Boolean, onBell: () -> Unit, compact: Boolean) {
    Row(Modifier.fillMaxWidth().height(if (compact) 42.dp else 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            buildAnnotatedString { append("Cook"); withStyle(SpanStyle(color = KitchenColors.Heat)) { append("X") } },
            color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp,
        )
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(40.dp).clip(CircleShape).pressable(onBell), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.NotificationsNone, "消息", tint = Color.White, modifier = Modifier.size(25.dp))
            if (unread) Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp).size(8.dp).clip(CircleShape).background(KitchenColors.Heat))
        }
    }
}

@Composable
private fun TitleRow(state: LiveState, onDevice: () -> Unit, compact: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = if (compact) 2.dp else 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("实时厨房", color = Color.White, fontSize = if (compact) 24.sp else 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
            Text("好食材 · 更好味", color = KitchenColors.TextMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
        }
        GlassSurface(Modifier.pressable(onDevice), shape = RoundedCornerShape(16.dp), tint = Color(0x661C1B19)) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (state.online) Color(0xFF4ADE80) else Color(0x80F6F3EE)))
                Spacer(Modifier.width(9.dp))
                Column {
                    Text("CookX Sense", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(state.statusText, color = KitchenColors.TextMuted, fontSize = 11.5.sp)
                }
            }
        }
    }
}

@Composable
private fun Reading(state: LiveState, compact: Boolean, onAlert: () -> Unit) {
    val alert = state.alert
    val danger = alert?.level == AlertLevel.Danger
    val numberColor by animateColorAsState(if (danger) Color(0xFFFF5A4A) else if (alert != null) Color(0xFFFFC36B) else Color.White, tween(500), label = "reading")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("当前锅温", color = KitchenColors.TextMuted, fontSize = 15.sp)
        val t = state.temperature
        Row(verticalAlignment = Alignment.Top) {
            if (t != null) {
                Text(
                    "${t.roundToInt()}", color = numberColor, fontFamily = NumericFont, fontWeight = FontWeight.SemiBold,
                    fontSize = if (compact) 76.sp else 100.sp, lineHeight = if (compact) 78.sp else 100.sp, letterSpacing = (-3).sp,
                )
                Text("°C", color = numberColor, fontSize = if (compact) 26.sp else 32.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, top = if (compact) 12.dp else 16.dp))
            } else {
                Text(
                    "--", color = Color(0x80FFFFFF), fontFamily = NumericFont, fontWeight = FontWeight.Medium,
                    fontSize = if (compact) 56.sp else 68.sp, lineHeight = if (compact) 78.sp else 100.sp, letterSpacing = 6.sp,
                )
            }
        }
        val advice = state.advice
        val title = alert?.title ?: state.stage?.title ?: if (state.online) "等待读数" else "未连接"
        val hint = alert?.text ?: advice?.text ?: if (state.online) "保持探头贴近锅底" else "连接 CookX Sense 开始测温"
        GlassSurface(
            Modifier.padding(top = 4.dp).pressable(if (alert != null) onAlert else null),
            shape = RoundedCornerShape(26.dp),
            tint = if (danger) Color(0xB38C1A12) else KitchenColors.PillBg,
            border = if (danger) Color(0x80FF6E5A) else KitchenColors.PillBorder,
        ) {
            Column(Modifier.padding(horizontal = 26.dp, vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (alert != null) Icons.Outlined.WarningAmber else Icons.Outlined.LocalFireDepartment, null, tint = if (danger) Color(0xFFFFB3A8) else KitchenColors.Heat, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Text(hint, color = KitchenColors.TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
            }
        }
    }
}

private val stepLabels = listOf("预热", "升温", "煎香", "烹饪", "完成")

@Composable
private fun StageCard(state: LiveState, compact: Boolean) {
    GlassSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = if (compact) 10.dp else 12.dp, bottom = if (compact) 8.dp else 10.dp)) {
            Text("烹饪阶段", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
            Stepper(state.stepperIndex, state.alert?.level == AlertLevel.Danger)
        }
    }
}

@Composable
fun Stepper(current: Int, alert: Boolean, modifier: Modifier = Modifier) {
    val accent = if (alert) KitchenColors.Alert else KitchenColors.Heat
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(22.dp)) {
            // Track and progress behind the nodes (nodes sit at 10 %, 30 %, … 90 %).
            Row(Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal = 0.dp)) {
                repeat(5) { i ->
                    Box(Modifier.weight(1f).height(22.dp)) {
                        if (i > 0) Box(
                            Modifier.align(Alignment.CenterStart).fillMaxWidth(0.5f).height(2.dp)
                                .background(if (i <= current) accent else Color(0x33FFFFFF)),
                        )
                        if (i < 4) Box(
                            Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.5f).height(2.dp)
                                .background(if (i < current) accent else Color(0x33FFFFFF)),
                        )
                        val done = i < current
                        val now = i == current
                        Box(
                            Modifier.align(Alignment.Center).size(if (now) 20.dp else 15.dp).clip(CircleShape)
                                .background(if (done || now) accent else Color(0xFF2A2826))
                                .border(if (now) 3.dp else 1.5.dp, if (now) Color.White else if (done) accent else Color(0x66FFFFFF), CircleShape),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            stepLabels.forEachIndexed { i, label ->
                Text(
                    label, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = if (i == current) Color.White else KitchenColors.TextMuted, fontSize = 12.5.sp,
                    fontWeight = if (i == current) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun TrendCard(state: LiveState, compact: Boolean, onTrend: () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth().pressable(onTrend)) {
        Column(Modifier.padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("温度趋势", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "查看温度趋势", tint = KitchenColors.TextMuted, modifier = Modifier.size(20.dp))
            }
            TempChart(state.history, state.now, Modifier.padding(top = 4.dp), alert = state.alert?.level == AlertLevel.Danger, height = if (compact) 50.dp else 72.dp)
        }
    }
}

@Composable
fun AdviceCard(state: LiveState, onClick: () -> Unit, compact: Boolean = false, modifier: Modifier = Modifier) {
    val alert = state.alert
    val danger = alert?.level == AlertLevel.Danger
    val advice = state.advice
    val text = when {
        alert != null -> "${alert.text}，点按查看处理建议"
        state.session != null && advice != null -> advice.text
        state.session != null -> "第 ${state.session.stepIndex + 1} 步：${state.session.stepTitle}"
        advice != null -> advice.text
        state.online -> "选一道菜，CookX 会按锅温一步步提醒你"
        else -> "连接 CookX Sense，实时掌握火候"
    }
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.fillMaxWidth().clip(shape)
            .background(if (danger) Color(0xFFFFE9E5) else KitchenColors.Cream)
            .pressable(onClick)
            .padding(horizontal = 16.dp, vertical = if (compact) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(if (danger) Color(0xFFFFD2CA) else Color(0xFFFFE3C7)), contentAlignment = Alignment.Center) {
            Icon(if (danger) Icons.Outlined.WarningAmber else Icons.Outlined.Restaurant, null, tint = if (danger) Color(0xFFD9342A) else Color(0xFFF08A24), modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (danger) "锅温预警" else "CookX 建议", color = if (danger) Color(0xFFB4281E) else KitchenColors.CreamMuted, fontSize = 12.5.sp)
            Text(text, color = KitchenColors.CreamText, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = KitchenColors.CreamMuted, modifier = Modifier.size(22.dp))
    }
}

/** Overheat guidance shown from the red pill or advice card. */
@Composable
fun OverheatGuide(state: LiveState, muted: Boolean, onMute: () -> Unit, onSpeak: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val alert = state.alert
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0x33FF4A3D)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.WarningAmber, null, tint = KitchenColors.Alert, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(alert?.title ?: "锅温已回落", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    state.temperature?.let { "当前 ${it.roundToInt()}°C" + (state.target?.let { t -> " · 目标 ${t.first.roundToInt()}–${t.second.roundToInt()}°C" } ?: "") } ?: "暂无读数",
                    color = KitchenColors.TextMuted, fontSize = 13.sp,
                )
            }
        }
        listOf(
            "1" to "立即调小火力，或把锅移离火源",
            "2" to "油开始冒烟时不要加水，也不要急于翻动",
            "3" to "万一起火：关火，盖上锅盖或湿毛巾隔绝空气，切勿用水浇",
            "4" to "温度回到目标区间后，再继续下一步",
        ).forEach { (n, text) ->
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(Color(0x26FFFFFF)), contentAlignment = Alignment.Center) {
                    Text(n, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Text(text, color = KitchenColors.Text, fontSize = 14.5.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
            GuideButton(if (muted) "已静音 60 秒" else "静音提醒", Modifier.weight(1f), onClick = onMute)
            GuideButton("语音播报", Modifier.weight(1f), onClick = onSpeak)
            GuideButton("我知道了", Modifier.weight(1f), primary = true, onClick = onClose)
        }
    }
}

@Composable
private fun GuideButton(text: String, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.height(46.dp).clip(RoundedCornerShape(14.dp)).background(if (primary) KitchenColors.Alert else Color(0x1FFFFFFF)).pressable(onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}
