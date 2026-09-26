package com.smartcooking.app.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.SoupKitchen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.SoupKitchen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartcooking.app.ui.theme.CookX

data class TabSpec(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector, val green: Boolean = false)

/** 首页 · 冰箱 · 厨房 · AI 菜谱 · 我的 — the kitchen is the live temperature page. */
val cookxTabs = listOf(
    TabSpec("home", "首页", Icons.Outlined.Home, Icons.Filled.Home),
    TabSpec("fridge", "冰箱", Icons.Outlined.Kitchen, Icons.Filled.Kitchen, green = true),
    TabSpec("kitchen", "厨房", Icons.Outlined.SoupKitchen, Icons.Filled.SoupKitchen),
    TabSpec("chef", "AI 菜谱", Icons.Outlined.RestaurantMenu, Icons.Filled.RestaurantMenu),
    TabSpec("profile", "我的", Icons.Outlined.Person, Icons.Filled.Person),
)

/**
 * Translucent tab bar with a hairline top edge. The selected tab gets a soft tinted capsule
 * behind its icon (orange; green for the fridge), like the Apple Home reference.
 */
@Composable
fun CookXTabBar(current: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(CookX.TabBar)) {
        Box(Modifier.fillMaxWidth().height(0.6.dp).background(CookX.Border))
        Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            cookxTabs.forEach { tab ->
                val active = current == tab.route
                val accent = if (tab.green) CookX.Primary else CookX.Accent
                val tint by animateColorAsState(if (active) accent else CookX.TextSecondary, tween(180), label = "tab")
                Column(
                    Modifier.weight(1f).clickable(remember { MutableInteractionSource() }, indication = null) { if (!active) onSelect(tab.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(width = 46.dp, height = 30.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (active) accent.copy(alpha = if (CookX.isDark) 0.2f else 0.12f) else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) { Icon(if (active) tab.selectedIcon else tab.icon, tab.label, tint = tint, modifier = Modifier.size(23.dp)) }
                    Text(tab.label, fontSize = 11.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, color = tint, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}
