package com.smartcooking.app.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartcooking.app.core.LocalAppContainer
import com.smartcooking.app.core.UserSession
import com.smartcooking.app.core.str
import com.smartcooking.app.data.loadUploadJpeg
import com.smartcooking.app.device.DeviceConnection
import com.smartcooking.app.ui.components.AnimatedBanner
import com.smartcooking.app.ui.components.Avatar
import com.smartcooking.app.ui.components.BannerKind
import com.smartcooking.app.ui.components.Choice
import com.smartcooking.app.ui.components.ConfirmDialog
import com.smartcooking.app.ui.components.CookXCard
import com.smartcooking.app.ui.components.CookXSheet
import com.smartcooking.app.ui.components.CookXTextField
import com.smartcooking.app.ui.components.DarkPanel
import com.smartcooking.app.ui.components.EmptyState
import com.smartcooking.app.ui.components.HeroHeader
import com.smartcooking.app.ui.components.HeroIconButton
import com.smartcooking.app.ui.components.IconBadge
import com.smartcooking.app.ui.components.Kicker
import com.smartcooking.app.ui.components.ListRow
import com.smartcooking.app.ui.components.LocalMessenger
import com.smartcooking.app.ui.components.OutlineButton
import com.smartcooking.app.ui.components.PrimaryButton
import com.smartcooking.app.ui.components.SectionHeader
import com.smartcooking.app.ui.components.SegmentedTabs
import com.smartcooking.app.ui.components.StatusChip
import com.smartcooking.app.ui.components.TonalButton
import com.smartcooking.app.ui.components.Tone
import com.smartcooking.app.ui.components.overlapHero
import com.smartcooking.app.ui.components.pressable
import com.smartcooking.app.ui.nav.Navigator
import com.smartcooking.app.ui.nav.Routes
import com.smartcooking.app.ui.nav.cookxViewModel
import com.smartcooking.app.ui.theme.CookX
import com.smartcooking.app.ui.theme.CookXShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen(navigator: Navigator) {
    val vm = cookxViewModel { ProfileViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val container = LocalAppContainer.current
    val messenger = LocalMessenger.current
    val session by container.sessions.session.collectAsStateWithLifecycle()
    val connection by container.bluetooth.connection.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showNotices by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (container.events.editProfile.value) { container.events.editProfile.value = false; editing = true }
    }

    ProfileContent(
        session = session,
        state = state,
        connection = connection,
        savedDevice = container.bluetooth.savedAddress != null,
        onNotices = { showNotices = true; scope.launch { vm.loadNotices() } },
        onEdit = { editing = true },
        onOpen = navigator::open,
        onKitchen = { navigator.tab(Routes.KITCHEN) },
        onLogout = { confirmLogout = true },
    )
    if (showNotices) NotificationsSheet(state, { showNotices = false }) { id -> vm.markRead(id, messenger::show) }
    if (editing) session?.let { EditProfileSheet(it, vm, state) { editing = false } }
    if (confirmLogout) ConfirmDialog("退出登录", "确定要退出登录吗？", { confirmLogout = false; vm.logout(messenger::show) }, { confirmLogout = false }, confirmText = "退出")
}

@Composable
fun ProfileContent(
    session: UserSession?,
    state: ProfileState,
    connection: DeviceConnection,
    savedDevice: Boolean,
    onNotices: () -> Unit,
    onEdit: () -> Unit,
    onOpen: (String) -> Unit,
    onKitchen: () -> Unit,
    onLogout: () -> Unit,
) {
    val user = session?.user
    LazyColumn(Modifier.fillMaxSize().background(CookX.Bg), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            HeroHeader(section = "我的", actions = { HeroIconButton(Icons.Outlined.NotificationsNone, "打开消息中心", onNotices, badge = state.unread > 0) }, bottomOverlap = 46) {
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.pressable(onEdit)) {
                        Avatar(user?.str("avatar"), 80.dp, Modifier.border(3.dp, Color.White.copy(alpha = 0.25f), CircleShape))
                        Box(Modifier.align(Alignment.BottomEnd).size(26.dp).clip(CircleShape).background(CookX.Accent), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Edit, "编辑资料", tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Kicker("COOKX MEMBER")
                        Text(session?.displayName?.ifBlank { null } ?: "未设置昵称", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        user?.str("username")?.let { Text("@$it", color = CookX.OnDarkMuted, fontSize = 13.sp) }
                        Text("让烹饪更简单，让生活更美味", color = CookX.OnDarkMuted, fontSize = 11.5.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        item {
            val stats = listOfNotNull(
                state.recipeCount?.let { Triple("$it", "菜谱记录", "真实 AI 菜谱") },
                state.notices?.let { Triple("${state.unread}", "未读消息", "待查看提醒") },
                usageDays(user)?.let { Triple("$it", "使用天数", "自注册起") },
            )
            if (stats.isNotEmpty()) CookXCard(Modifier.padding(horizontal = 16.dp).padding(top = 0.dp).offsetUp()) {
                Row {
                    stats.forEachIndexed { i, (value, label, desc) ->
                        if (i > 0) Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(46.dp).background(CookX.Border).align(Alignment.CenterVertically))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = CookX.Primary)
                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CookX.Text)
                            Text(desc, fontSize = 10.5.sp, color = CookX.TextTertiary)
                        }
                    }
                }
            }
        }
        item {
            DarkPanel(Modifier.padding(horizontal = 16.dp).padding(top = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.Bluetooth, Tone.OnDark, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Kicker("COOKX SENSE")
                        Text("CookX Sense 测温", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(if (savedDevice) "已保存测温设备，可在 AI 厨房快速重连" else "尚未连接测温设备", color = CookX.OnDarkMuted, fontSize = 12.sp)
                    }
                    StatusChip(connection.label, if (connection.connected) Tone.Fresh else Tone.OnDark, dot = true)
                }
                Spacer(Modifier.height(14.dp))
                TonalButton("进入 AI 厨房", onKitchen, Modifier.fillMaxWidth(), tone = Tone.OnDark)
            }
        }
        item {
            CookXCard(Modifier.padding(horizontal = 16.dp).padding(top = 14.dp), padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                SectionHeader("功能入口", kicker = "账户服务", modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp))
                ListRow("我的收藏", subtitle = "查看我收藏的菜谱", icon = Icons.Outlined.StarOutline, tone = Tone.Gold, onClick = { onOpen(Routes.recipes(favorites = true)) })
                ListRow("烹饪记录", subtitle = "查看真实 AI 菜谱生成记录", icon = Icons.Outlined.History, tone = Tone.Warm, onClick = { onOpen(Routes.HISTORY) })
                ListRow("偏好设置", subtitle = "口味、辣度与食材禁忌", icon = Icons.Outlined.Settings, onClick = { onOpen(Routes.PREFERENCES) })
                ListRow("账号与安全", subtitle = "账号资料、后端地址与注销", icon = Icons.Outlined.Lock, onClick = { onOpen(Routes.ACCOUNT) })
                ListRow("消息中心", subtitle = "查看食材临期与系统通知", icon = Icons.Outlined.NotificationsNone, tone = Tone.Warm, badge = state.unread, onClick = onNotices)
                ListRow("关于 CookX", subtitle = "产品介绍、版本与隐私说明", icon = Icons.Outlined.Info, onClick = { onOpen(Routes.ABOUT) })
            }
        }
        item {
            AnimatedBanner(state.error, BannerKind.Warning, Modifier.padding(horizontal = 16.dp).padding(top = 12.dp))
            OutlineButton("退出登录", onLogout, Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp), icon = Icons.AutoMirrored.Outlined.Logout, color = CookX.Danger)
            Text("CookX · 感知每一度 · 智烹每一步", fontSize = 11.sp, color = CookX.TextTertiary, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

private fun Modifier.offsetUp() = this.overlapHero(26.dp)

@Composable
private fun NotificationsSheet(state: ProfileState, onDismiss: () -> Unit, onRead: (String) -> Unit) {
    var tab by remember { mutableStateOf("all") }
    CookXSheet("消息中心", onDismiss, subtitle = "食材临期与系统通知") {
        SegmentedTabs(listOf(Choice("all", "全部"), Choice("expire", "食材临期"), Choice("system", "系统通知")), tab, { tab = it })
        Spacer(Modifier.height(12.dp))
        val list = state.notices.orEmpty().filter { tab == "all" || it.type == tab }
        if (list.isEmpty()) EmptyState(Icons.Outlined.NotificationsNone, "暂无消息", "有新的提醒时会出现在这里")
        list.forEach { n ->
            Column(
                Modifier.padding(bottom = 10.dp).fillMaxWidth().clip(CookXShapes.Tile).background(if (n.read) CookX.SurfaceMuted else CookX.Surface)
                    .border(1.dp, if (n.read) CookX.Border else CookX.Accent.copy(alpha = 0.3f), CookXShapes.Tile).padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(n.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), color = if (n.read) CookX.TextSecondary else CookX.Text)
                    StatusChip(if (n.type == "expire") "食材临期" else "系统通知", if (n.type == "expire") Tone.Warm else Tone.Green)
                }
                Text(n.content, style = MaterialTheme.typography.bodySmall, color = CookX.TextBody, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(n.time, fontSize = 11.sp, color = CookX.TextTertiary, modifier = Modifier.weight(1f))
                    if (!n.read) TonalButton("标记已读", { onRead(n.id) })
                }
            }
        }
    }
}

@Composable
private fun EditProfileSheet(session: UserSession, vm: ProfileViewModel, state: ProfileState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val scope = rememberCoroutineScope()
    var nickname by remember { mutableStateOf(session.user.str("nickname").orEmpty()) }
    var phone by remember { mutableStateOf(session.user.str("phone").orEmpty()) }
    var avatar by remember { mutableStateOf(session.user.str("avatar")) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val bytes = runCatching { withContext(Dispatchers.IO) { loadUploadJpeg(context, uri, 800) } }.getOrNull()
            if (bytes == null) { messenger.show("图片读取失败"); return@launch }
            if (bytes.size > 5 * 1024 * 1024) { messenger.show("图片大小不能超过 5MB"); return@launch }
            vm.uploadAvatar(bytes, { avatar = it; messenger.show("头像预览已更新，请点击保存") }, { messenger.show("上传失败：$it") })
        }
    }
    CookXSheet("编辑个人信息", onDismiss, dismissible = !state.updating) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(avatar, 72.dp)
            Spacer(Modifier.width(16.dp))
            TonalButton(if (state.uploading) "上传中…" else "更换头像", { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, loading = state.uploading)
        }
        Spacer(Modifier.height(16.dp))
        CookXTextField(nickname, { nickname = it; error = null }, "昵称", clearable = true)
        Spacer(Modifier.height(10.dp))
        CookXTextField(phone, { phone = it.filter(Char::isDigit).take(11); error = null }, "手机号", keyboardType = KeyboardType.Phone, clearable = true)
        AnimatedBanner(error, BannerKind.Error, Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(16.dp))
        PrimaryButton("保存修改", {
            when {
                nickname.isBlank() -> error = "请输入昵称"
                phone.isNotEmpty() && !Regex("^1[3-9]\\d{9}$").matches(phone) -> error = "请输入正确的手机号"
                else -> vm.updateProfile(nickname.trim(), phone, avatar) { problem ->
                    if (problem == null) { messenger.show("更新成功"); onDismiss() } else error = problem
                }
            }
        }, Modifier.fillMaxWidth(), loading = state.updating)
    }
}
