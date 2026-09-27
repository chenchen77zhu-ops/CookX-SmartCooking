package com.smartcooking.app.feature.live

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartcooking.app.R
import com.smartcooking.app.ui.components.MiniBar
import com.smartcooking.app.ui.components.NumberText
import com.smartcooking.app.ui.components.pressable
import com.smartcooking.app.ui.theme.KitchenColors
import kotlin.math.roundToInt

private val CardDark = Color(0xFF111110)
private val StatusGreen = Color(0xFF6EE7A0)

@Composable
fun SenseBrand(size: Float = 20f, modifier: Modifier = Modifier, suffix: String = " Sense") {
    Text(
        buildAnnotatedString {
            append("Cook")
            withStyle(SpanStyle(color = KitchenColors.Heat)) { append("X") }
            append(suffix)
        },
        modifier = modifier, color = KitchenColors.Text, fontSize = size.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp,
    )
}

@Composable
private fun StatusDot(state: LiveState, modifier: Modifier = Modifier) {
    val color = when {
        state.alert?.level == AlertLevel.Danger -> KitchenColors.Alert
        state.online -> StatusGreen
        else -> Color(0x80F6F3EE)
    }
    Box(modifier.size(7.dp).clip(CircleShape).background(color))
}

private fun tempText(t: Double?) = t?.roundToInt()?.toString() ?: "--"

/**
 * The home "CookX Sense" card (Apple Home reference): live pan temperature with a heat bar, what
 * is cooking, and the next suggestion. Turns red while the pan is overheating.
 */
@Composable
fun SenseCard(state: LiveState, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val alert = state.alert
    val danger = alert?.level == AlertLevel.Danger
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier.shadow(16.dp, shape, spotColor = Color(0x40000000), ambientColor = Color(0x26000000))
            .clip(shape).background(CardDark).pressable(onClick),
    ) {
        Image(
            painterResource(R.drawable.sense_pan), null,
            Modifier.fillMaxHeight().fillMaxWidth(0.86f).align(Alignment.TopEnd),
            contentScale = ContentScale.Crop, alignment = Alignment.CenterEnd,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to CardDark, 0.34f to CardDark.copy(alpha = 0.9f), 0.62f to CardDark.copy(alpha = 0.15f), 1f to Color.Transparent),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent, 0.45f to Color.Transparent,
                    1f to if (danger) Color(0xF0781C12) else Color(0xEB3A1F0C),
                ),
            ),
        )
        BoxWithConstraints(Modifier.fillMaxSize()) {
        // Short cards (small phones) drop the "cooking" chip so the suggestion always stays visible.
        val roomy = maxHeight >= 318.dp
        Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 12.dp, top = 18.dp, bottom = 12.dp)) {
            SenseBrand(20f)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                StatusDot(state)
                Spacer(Modifier.width(6.dp))
                Text(state.statusText, color = if (state.online) StatusGreen else KitchenColors.TextFaint, fontSize = 13.sp)
            }
            Spacer(Modifier.weight(0.6f))
            Text("当前锅内温度", color = KitchenColors.TextMuted, fontSize = 13.sp)
            NumberText(
                tempText(state.temperature), unit = if (state.temperature != null) "°C" else null, size = 44.sp,
                color = if (danger) KitchenColors.AlertSoft else KitchenColors.Text, superscriptUnit = true,
                modifier = Modifier.padding(top = 2.dp),
            )
            MiniBar(
                LiveRules.gaugeFraction(state.temperature), Modifier.padding(top = 8.dp).width(134.dp),
                fill = if (danger) KitchenColors.alertBrush else KitchenColors.heatBrush,
            )
            Spacer(Modifier.weight(0.5f))
            if (roomy) Row(
                Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x1FFFFFFF)).border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
                    .padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (danger) Icons.Outlined.WarningAmber else Icons.Outlined.LocalFireDepartment, null, tint = if (danger) KitchenColors.AlertSoft else KitchenColors.Heat, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(if (state.session != null) "正在烹饪中" else "待机中", color = KitchenColors.TextMuted, fontSize = 12.sp)
                    Text(state.session?.dish ?: "选择一道菜开始", color = KitchenColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
                }
            }
            Spacer(Modifier.height(if (roomy) 12.dp else 8.dp))
            val advice = state.advice
            val title = when {
                alert != null -> alert.text
                state.session != null -> state.session.nextTitle.takeIf { it.isNotBlank() }?.let { "接下来：$it" } ?: "最后一步，完成后核对库存"
                advice != null -> advice.text
                else -> "进入厨房连接测温设备"
            }
            val sub = when {
                alert != null -> "点按查看处理建议"
                state.session != null -> advice?.text ?: state.session.stepTitle
                state.online -> ""
                else -> "连接后实时查看锅温与火候建议"
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(if (danger) listOf(Color(0xCCA0201A), Color(0xB3781812)) else listOf(Color(0xB8A0521C), Color(0x99703A16))))
                    .border(1.dp, if (danger) Color(0x66FF6E5A) else Color(0x47FFAA6E), RoundedCornerShape(18.dp))
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(if (alert != null) alert.title else "下一步建议", color = Color(0xBFFFE6D2), fontSize = 12.sp)
                    Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    if (sub.isNotBlank()) Text(sub, color = Color(0xB8FFE6D2), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.size(42.dp).shadow(10.dp, CircleShape, spotColor = Color(0x99FF7A1E)).clip(CircleShape)
                        .background(if (danger) KitchenColors.Alert else Color(0xFFFF8A1F)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "进入厨房", tint = Color.White, modifier = Modifier.size(24.dp)) }
            }
        }
        }
    }
}

/** Compact floating version of the Sense card shown on other pages while cooking or connected. */
@Composable
fun SenseMiniCard(state: LiveState, modifier: Modifier = Modifier) {
    val danger = state.alert?.level == AlertLevel.Danger
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier.width(150.dp).shadow(18.dp, shape, spotColor = Color(0x59000000), ambientColor = Color(0x33000000))
            .clip(shape).background(CardDark),
    ) {
        Image(
            painterResource(R.drawable.sense_pan), null, Modifier.matchParentSize(),
            contentScale = ContentScale.Crop, alignment = Alignment.CenterEnd, alpha = 0.55f,
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(
                    if (danger) listOf(Color(0xF25A0E0A), Color(0x995A0E0A)) else listOf(Color(0xEB0E0F0E), Color(0x730E0F0E)),
                ),
            ),
        )
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SenseBrand(14f, suffix = "")
                Spacer(Modifier.weight(1f))
                StatusDot(state)
            }
            NumberText(
                tempText(state.temperature), unit = if (state.temperature != null) "°C" else null, size = 30.sp,
                color = if (danger) KitchenColors.AlertSoft else KitchenColors.Text, superscriptUnit = true,
            )
            MiniBar(LiveRules.gaugeFraction(state.temperature), Modifier.fillMaxWidth().padding(vertical = 4.dp), fill = if (danger) KitchenColors.alertBrush else KitchenColors.heatBrush, height = 4.dp)
            val line = state.alert?.title ?: state.session?.let { "${it.dish} · ${it.stepIndex + 1}/${it.total}" } ?: state.statusText
            Text(line, color = KitchenColors.TextMuted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
