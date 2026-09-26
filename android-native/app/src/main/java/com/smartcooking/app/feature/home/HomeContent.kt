package com.smartcooking.app.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
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
import com.smartcooking.app.feature.live.LiveState
import com.smartcooking.app.feature.live.SenseCard
import com.smartcooking.app.ui.components.CardTitle
import com.smartcooking.app.ui.components.HeroIconButton
import com.smartcooking.app.ui.components.SoftCard
import com.smartcooking.app.ui.components.Wordmark
import com.smartcooking.app.ui.components.pressable
import com.smartcooking.app.ui.components.topInset
import com.smartcooking.app.ui.theme.CookX

/** Today's pick on the home screen: the latest AI recipe, or an invitation to create one. */
data class TodayPick(val title: String, val minutes: Int?, val detail: String, val fromHistory: Boolean)

/** The most urgent fridge reminder, e.g. 牛奶 · 还有 1 天过期. */
data class ExpiryHint(val name: String, val text: String, val count: Int, val urgent: Boolean)

data class HomeUi(
    val greeting: String,
    val name: String,
    val unread: Int = 0,
    val pick: TodayPick? = null,
    val expiry: ExpiryHint? = null,
    val fridgeEmpty: Boolean = false,
    val loading: Boolean = false,
)

data class HomeActions(
    val onBell: () -> Unit = {},
    val onServices: () -> Unit = {},
    val onSense: () -> Unit = {},
    val onPick: () -> Unit = {},
    val onAllRecipes: () -> Unit = {},
    val onExpiry: () -> Unit = {},
)

/**
 * Home, after the Apple Home reference: large greeting, the live CookX Sense card, today's pick and
 * one fridge reminder. Everything fits one phone screen; the Sense card absorbs spare height.
 */
@Composable
fun HomeContent(ui: HomeUi, live: LiveState, modifier: Modifier = Modifier, actions: HomeActions = HomeActions(), pickImage: Painter? = null) {
    BoxWithConstraints(modifier.fillMaxSize().background(CookX.Bg)) {
        val roomy = maxHeight >= 640.dp
        val body: @Composable (Modifier) -> Unit = { senseModifier ->
            HomeHeader(ui, actions)
            SenseCard(live, senseModifier.fillMaxWidth(), onClick = actions.onSense)
            Spacer(Modifier.height(12.dp))
            PickCard(ui.pick, pickImage, actions)
            Spacer(Modifier.height(12.dp))
            ExpiryCard(ui, actions.onExpiry)
            Spacer(Modifier.height(14.dp))
        }
        if (roomy) {
            Column(Modifier.fillMaxSize().topInset().padding(horizontal = 20.dp)) {
                body(Modifier.weight(1f).heightIn(max = 380.dp))
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).topInset().padding(horizontal = 20.dp)) {
                body(Modifier.height(300.dp))
            }
        }
    }
}

@Composable
private fun HomeHeader(ui: HomeUi, actions: HomeActions) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Wordmark(26.sp, color = CookX.Text)
        Spacer(Modifier.weight(1f))
        HeroIconButton(Icons.Outlined.GridView, "全部服务", actions.onServices)
        Spacer(Modifier.width(10.dp))
        HeroIconButton(Icons.Outlined.NotificationsNone, "消息", actions.onBell, badge = ui.unread > 0)
    }
    Text(
        if (ui.name.isNotBlank()) "${ui.greeting}，${ui.name}" else ui.greeting,
        fontSize = 30.sp, fontWeight = FontWeight.Bold, color = CookX.Text, letterSpacing = (-0.6).sp,
        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
    )
    Text("智能烹饪，轻松享受每一餐", fontSize = 14.sp, color = CookX.TextSecondary, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp))
}

@Composable
private fun PickCard(pick: TodayPick?, image: Painter?, actions: HomeActions) {
    SoftCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(14.dp), onClick = actions.onPick) {
        CardTitle("今日推荐", action = "查看全部", onAction = actions.onAllRecipes)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                image ?: painterResource(R.drawable.today_dish), null,
                Modifier.size(width = 92.dp, height = 68.dp).clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(pick?.title ?: "彩椒西兰花炒鸡胸肉", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = CookX.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AccessTime, null, tint = CookX.TextSecondary, modifier = Modifier.size(15.dp))
                    Text(pick?.minutes?.let { " $it 分钟" } ?: " 15 分钟", fontSize = 12.5.sp, color = CookX.TextSecondary)
                    Spacer(Modifier.width(14.dp))
                    Icon(if (pick?.fromHistory == true) Icons.Outlined.SignalCellularAlt else Icons.Outlined.AutoAwesome, null, tint = CookX.TextSecondary, modifier = Modifier.size(15.dp))
                    Text(" ${pick?.detail ?: "AI 按冰箱食材推荐"}", fontSize = 12.5.sp, color = CookX.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
            }
            Icon(Icons.Outlined.BookmarkBorder, null, tint = CookX.TextSecondary, modifier = Modifier.padding(start = 6.dp).size(22.dp))
        }
    }
}

@Composable
private fun ExpiryCard(ui: HomeUi, onClick: () -> Unit) {
    val hint = ui.expiry
    val urgent = hint?.urgent == true
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (CookX.isDark) CookX.Mint else Color(0xFFEAF5EC))
            .pressable(onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (CookX.isDark) CookX.MintDeep else Color(0xFFD3EBD8)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Eco, null, tint = CookX.SuccessBright, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("临期食材提醒", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = CookX.Text)
            Text(
                when {
                    hint != null -> buildAnnotatedString {
                        withStyle(SpanStyle(color = CookX.Text, fontWeight = FontWeight.Medium)) { append(hint.name) }
                        append("  ")
                        withStyle(SpanStyle(color = if (urgent) CookX.Danger else CookX.TextSecondary)) { append(hint.text) }
                        if (hint.count > 1) append("  等 ${hint.count} 种")
                    }
                    ui.loading -> buildAnnotatedString { append("正在核对库存…") }
                    ui.fridgeEmpty -> buildAnnotatedString { append("冰箱还是空的，拍一张照片识别食材") }
                    else -> buildAnnotatedString { append("暂无临期食材，查看评估依据") }
                },
                fontSize = 13.sp, color = CookX.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = CookX.Success, modifier = Modifier.size(22.dp))
    }
}

/** Service shortcuts that used to crowd the home page; now in a sheet behind the grid button. */
data class ServiceItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val tint: Color)

@Composable
fun ServicesGrid(items: List<ServiceItem>, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { item ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).pressable({ onOpen(item.route) }).padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(item.tint.copy(alpha = if (CookX.isDark) 0.22f else 0.12f)), contentAlignment = Alignment.Center) {
                            Icon(item.icon, null, tint = item.tint, modifier = Modifier.size(26.dp))
                        }
                        Text(item.label, fontSize = 12.5.sp, color = CookX.Text, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
