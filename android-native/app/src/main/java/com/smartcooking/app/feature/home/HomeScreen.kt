package com.smartcooking.app.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccessAlarm
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Recycling
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SoupKitchen
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartcooking.app.R
import com.smartcooking.app.core.LocalAppContainer
import com.smartcooking.app.core.Time
import com.smartcooking.app.data.FreshnessState
import com.smartcooking.app.data.Recipe
import com.smartcooking.app.device.DeviceConnection
import com.smartcooking.app.device.TemperatureSample
import com.smartcooking.app.ui.components.AccentButton
import com.smartcooking.app.ui.components.CookXCard
import com.smartcooking.app.ui.components.DarkPanel
import com.smartcooking.app.ui.components.EmptyState
import com.smartcooking.app.ui.components.GradientIcon
import com.smartcooking.app.ui.components.HeroHeader
import com.smartcooking.app.ui.components.HeroIconButton
import com.smartcooking.app.ui.components.IconBadge
import com.smartcooking.app.ui.components.Kicker
import com.smartcooking.app.ui.components.LinkButton
import com.smartcooking.app.ui.components.LoadingBlock
import com.smartcooking.app.ui.components.MetricTile
import com.smartcooking.app.ui.components.OutlineButton
import com.smartcooking.app.ui.components.SectionHeader
import com.smartcooking.app.ui.components.StatusChip
import com.smartcooking.app.ui.components.Tone
import com.smartcooking.app.ui.components.pressable
import com.smartcooking.app.ui.nav.Navigator
import com.smartcooking.app.ui.nav.Routes
import com.smartcooking.app.ui.nav.cookxViewModel
import com.smartcooking.app.ui.theme.CookX
import com.smartcooking.app.ui.theme.CookXShapes

@Composable
fun HomeScreen(navigator: Navigator) {
    val vm = cookxViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val container = LocalAppContainer.current
    val session by container.sessions.session.collectAsStateWithLifecycle()
    val connection by container.bluetooth.connection.collectAsStateWithLifecycle()
    val latest by container.bluetooth.latest.collectAsStateWithLifecycle()
    HomeContent(
        name = session?.displayName.orEmpty(),
        state = state,
        connection = connection,
        latest = latest,
        onRefresh = vm::refresh,
        onOpen = navigator::open,
        onTab = navigator::tab,
        onRecognize = { container.events.startRecognition.value = true; navigator.tab(Routes.FRIDGE) },
        onRecipe = { dish -> container.events.pendingDish.value = dish; navigator.tab(Routes.KITCHEN) },
    )
}

@Composable
fun HomeContent(
    name: String,
    state: HomeState,
    connection: DeviceConnection,
    latest: TemperatureSample?,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onTab: (String) -> Unit,
    onRecognize: () -> Unit,
    onRecipe: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().background(CookX.Bg), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            HeroHeader(
                actions = { HeroIconButton(Icons.Outlined.NotificationsNone, "查看消息", { onTab(Routes.PROFILE) }, badge = state.unread > 0) },
                bottomOverlap = 30,
            ) {
                Spacer(Modifier.height(14.dp))
                Text("${Time.greeting()}${if (name.isNotBlank()) "，$name" else ""}", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("今天想吃点什么？", color = CookX.OnDarkMuted, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(18.dp))
                HeroRecommendCard { onTab(Routes.KITCHEN) }
            }
        }
        item { FridgePanel(state, onRefresh, { onTab(Routes.FRIDGE) }, onRecognize) }
        item { QuickActions(onRecognize) { onTab(Routes.KITCHEN) } }
        item { CollaborateGrid(onOpen) }
        item { SenseCard(connection, latest) { onTab(Routes.KITCHEN) } }
        item { InsightCard(state) { onTab(Routes.FRIDGE) } }
        item { DailyRecipe(state.latestRecipe, { onTab(Routes.KITCHEN) }, onRecipe) }
    }
}

@Composable
private fun HeroRecommendCard(onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(228.dp)
            .shadow(20.dp, CookXShapes.Large, spotColor = Color(0x66000000))
            .clip(CookXShapes.Large)
            .background(Color(0xFF0B362B))
            .border(1.dp, Color.White.copy(alpha = 0.16f), CookXShapes.Large),
    ) {
        Image(painterResource(R.drawable.home_hero_dish), "热气腾腾的鸡肉时蔬料理", Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.CenterEnd)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xFA052019), 0.45f to Color(0xD1052019), 0.85f to Color(0x1F052019))))
        Column(Modifier.fillMaxWidth(0.66f).padding(start = 20.dp, top = 22.dp, bottom = 18.dp, end = 4.dp)) {
            Kicker("COOKX AI")
            Text("CookX AI 智能推荐", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp, modifier = Modifier.padding(top = 6.dp))
            Text("根据冰箱现有食材\n为你生成今天最合适的菜谱", color = CookX.OnDarkMuted, fontSize = 12.5.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
            AccentButton("立即推荐", onClick, trailingIcon = Icons.AutoMirrored.Outlined.ArrowForward)
        }
    }
}

@Composable
private fun FridgePanel(state: HomeState, onRetry: () -> Unit, onOpen: () -> Unit, onRecognize: () -> Unit) {
    CookXCard(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
        SectionHeader("我的冰箱", icon = Icons.Outlined.Kitchen) { LinkButton("查看全部", onOpen, icon = Icons.AutoMirrored.Outlined.ArrowForward) }
        Spacer(Modifier.height(14.dp))
        when {
            state.loading && state.inventory.isEmpty() -> LoadingBlock("正在同步冰箱状态…", minHeight = 90)
            state.error -> EmptyState(Icons.Outlined.Refresh, "暂时无法读取库存", "请检查后端地址与网络连接，已有数据不会被清空。", tone = Tone.Danger) {
                OutlineButton("重新加载", onRetry, icon = Icons.Outlined.Refresh)
            }
            state.inventory.isEmpty() -> Row(
                Modifier.fillMaxWidth().clip(CookXShapes.Tile).background(CookX.SurfaceMuted).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(Icons.Outlined.Kitchen, size = 48.dp, iconSize = 23.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("冰箱还是空的", style = MaterialTheme.typography.titleSmall)
                    Text("识别食材后，CookX 才能为你推荐菜谱", style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    OutlineButton("去识别食材", onRecognize, trailingIcon = Icons.AutoMirrored.Outlined.ArrowForward)
                }
            }
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile(Icons.Outlined.Restaurant, "${state.kindCount}", "种", "食材种类", Tone.Fresh, Modifier.weight(1f).border(1.dp, CookX.Border, CookXShapes.Tile))
                    MetricTile(Icons.Outlined.AccessAlarm, state.expiringCountText, "种", "即将过期", Tone.Danger, Modifier.weight(1f).border(1.dp, CookX.Border, CookXShapes.Tile))
                }
                Spacer(Modifier.height(10.dp))
                val names = state.expiring.take(3).joinToString(" · ") { it.info.cn }
                Row(
                    Modifier.fillMaxWidth().clip(CookXShapes.Small).pressable(onOpen).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.AccessAlarm, null, tint = if (names.isNotEmpty()) CookX.Danger else CookX.TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            names.isNotEmpty() -> "建议优先食用：$names"
                            state.freshness is FreshnessState.Failed -> "鲜度暂不可用，请进入冰箱重试"
                            state.freshness is FreshnessState.Loading -> "正在评估食材鲜度…"
                            else -> "暂无已评估的临期项；未知与过期项请查看冰箱详情"
                        },
                        style = MaterialTheme.typography.bodySmall, color = CookX.TextBody, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = CookX.TextTertiary, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

@Composable
private fun QuickActions(onRecognize: () -> Unit, onKitchen: () -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickCard("拍照识别", "AI 识别冰箱食材", Icons.Outlined.PhotoCamera, CookX.quickGreen, Modifier.weight(1f), onRecognize)
        QuickCard("AI 厨房", "语音指导 · 实时控温", Icons.Outlined.SoupKitchen, CookX.quickOrange, Modifier.weight(1f), onKitchen)
    }
}

@Composable
private fun QuickCard(title: String, subtitle: String, icon: ImageVector, brush: Brush, modifier: Modifier, onClick: () -> Unit) {
    CookXCard(modifier, padding = PaddingValues(14.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GradientIcon(icon, brush, size = 42.dp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private data class Feature(val label: String, val icon: ImageVector, val route: String, val tone: Tone)

private val features = listOf(
    Feature("家庭冰箱", Icons.Outlined.Groups, Routes.HOUSEHOLD, Tone.Green),
    Feature("共同采购", Icons.Outlined.ShoppingCart, Routes.SHOPPING, Tone.Warm),
    Feature("菜谱复刻", Icons.AutoMirrored.Outlined.MenuBook, Routes.recipes(), Tone.Gold),
    Feature("一起晒菜", Icons.Outlined.CameraAlt, Routes.COMMUNITY, Tone.Fresh),
    Feature("剩菜改造", Icons.Outlined.Recycling, Routes.LEFTOVERS, Tone.Green),
    Feature("厨艺成长", Icons.Outlined.EmojiEvents, Routes.GROWTH, Tone.Gold),
    Feature("七日菜单", Icons.Outlined.CalendarMonth, Routes.MENUS, Tone.Warm),
    Feature("偏好学习", Icons.Outlined.Psychology, Routes.LEARNING, Tone.Fresh),
)

@Composable
private fun CollaborateGrid(onOpen: (String) -> Unit) {
    CookXCard(Modifier.padding(horizontal = 16.dp).padding(top = 14.dp)) {
        SectionHeader("一起做饭", subtitle = "个人数据保持私有，确认后再与家人和内测成员分享")
        Spacer(Modifier.height(14.dp))
        features.chunked(4).forEachIndexed { row, items ->
            if (row > 0) Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                items.forEach { f ->
                    Column(
                        Modifier.weight(1f).clip(CookXShapes.Tile).pressable({ onOpen(f.route) }).padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        IconBadge(f.icon, f.tone, size = 46.dp, iconSize = 22.dp, shape = CookXShapes.Tile)
                        Spacer(Modifier.height(7.dp))
                        Text(f.label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = CookX.TextBody)
                    }
                }
            }
        }
    }
}

@Composable
private fun SenseCard(connection: DeviceConnection, latest: TemperatureSample?, onOpen: () -> Unit) {
    DarkPanel(Modifier.padding(horizontal = 16.dp).padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CookX Sense", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(" 智能状态", color = CookX.OnDarkMuted, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            StatusChip(connection.label, if (connection.connected) Tone.Fresh else Tone.OnDark, dot = true)
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("实时锅温", color = CookX.OnDarkMuted, fontSize = 11.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(latest?.temperature?.let { "%.1f".format(it) } ?: "--.-", color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Bold)
                    Text("°C", color = CookX.OnDarkMuted, fontSize = 15.sp, modifier = Modifier.padding(start = 3.dp, bottom = 5.dp))
                }
                Text(if (latest != null) "设备实测读数" else if (connection.connected) "已连接 · 等待新数据" else "等待连接", color = CookX.OnDarkMuted, fontSize = 11.sp)
            }
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.05f))
                    .border(5.dp, if (connection.connected) CookX.Success else Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (connection.connected) Icons.Outlined.Thermostat else Icons.Outlined.Bluetooth, null, tint = if (connection.connected) Color.White else CookX.Gold, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("测温设备", color = CookX.OnDarkMuted, fontSize = 11.sp)
                Text(connection.device?.name ?: "未连接", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        AccentButton(if (connection.connected) "进入 AI 厨房查看" else "进入 AI 厨房连接", onOpen, Modifier.fillMaxWidth(), trailingIcon = Icons.AutoMirrored.Outlined.ArrowForward)
    }
}

@Composable
private fun InsightCard(state: HomeState, onOpen: () -> Unit) {
    val (title, body) = when {
        state.loading -> "正在分析" to "同步冰箱中的真实食材状态…"
        state.error -> "暂时无法分析" to "库存恢复后将自动生成今日洞察。"
        state.inventory.isEmpty() -> "先添加食材" to "CookX 才能为你生成智能建议。"
        state.expiring.isNotEmpty() -> "建议优先食用" to "冰箱中有 ${state.expiring.size} 种食材需要尽快使用。"
        else -> "查看评估依据" to "未显示临期项不代表食品安全；请核对日期、储存条件与数据不足项。"
    }
    CookXCard(Modifier.padding(horizontal = 16.dp).padding(top = 14.dp), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Kicker("COOKX INSIGHT", color = CookX.Accent)
                Text("今日洞察", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = CookX.Primary)
                Text(body, style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary)
            }
            Spacer(Modifier.width(12.dp))
            IconBadge(Icons.Outlined.AutoAwesome, Tone.Gold, size = 56.dp, iconSize = 28.dp, shape = CookXShapes.Card)
        }
    }
}

@Composable
private fun DailyRecipe(recipe: Recipe?, onKitchen: () -> Unit, onRecipe: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).padding(top = 20.dp)) {
        SectionHeader("今日推荐菜谱", kicker = "DAILY RECIPE") { LinkButton("换一换", onKitchen, icon = Icons.Outlined.Refresh) }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(172.dp).clip(CookXShapes.Card).pressable(if (recipe != null) ({ onRecipe(recipe.dishName) }) else onKitchen)) {
            Image(painterResource(R.drawable.home_recipe_empty), "新鲜蔬菜食材背景", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x33092A22), Color(0xE6092A22)))))
            Row(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                GradientIcon(Icons.Outlined.Restaurant, CookX.accentBrush, size = 42.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(recipe?.dishName ?: "还没有今日推荐", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (recipe != null) "最近生成 · ${recipe.steps.size} 个步骤，点击继续咨询" else "根据冰箱真实食材生成一份吧", color = CookX.OnDarkMuted, fontSize = 12.sp)
                }
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = Color.White)
            }
        }
    }
}
