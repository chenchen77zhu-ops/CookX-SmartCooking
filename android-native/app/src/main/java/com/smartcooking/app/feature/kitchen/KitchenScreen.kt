package com.smartcooking.app.feature.kitchen

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartcooking.app.core.Time
import com.smartcooking.app.data.ChatMessage
import com.smartcooking.app.data.Recipe
import com.smartcooking.app.feature.cooking.CookingState
import com.smartcooking.app.ui.components.AccentButton
import com.smartcooking.app.ui.components.ActionRow
import com.smartcooking.app.ui.components.Banner
import com.smartcooking.app.ui.components.BannerKind
import com.smartcooking.app.ui.components.ConfirmDialog
import com.smartcooking.app.ui.components.CookXCard
import com.smartcooking.app.ui.components.EmptyState
import com.smartcooking.app.ui.components.GradientIcon
import com.smartcooking.app.ui.components.HeroHeader
import com.smartcooking.app.ui.components.IconBadge
import com.smartcooking.app.ui.components.Kicker
import com.smartcooking.app.ui.components.LinkButton
import com.smartcooking.app.ui.components.LocalMessenger
import com.smartcooking.app.ui.components.OutlineButton
import com.smartcooking.app.ui.components.PrimaryButton
import com.smartcooking.app.ui.components.SectionHeader
import com.smartcooking.app.ui.components.StatusChip
import com.smartcooking.app.ui.components.TonalButton
import com.smartcooking.app.ui.components.Tone
import com.smartcooking.app.ui.components.fieldColors
import com.smartcooking.app.ui.components.pressable
import com.smartcooking.app.ui.nav.Navigator
import com.smartcooking.app.ui.nav.cookxViewModel
import com.smartcooking.app.ui.theme.CookX
import com.smartcooking.app.ui.theme.CookXShapes

@Composable
fun KitchenScreen(navigator: Navigator) {
    val vm = cookxViewModel { KitchenViewModel(it) }
    val messenger = LocalMessenger.current
    val context = LocalContext.current
    val chat by vm.chat.collectAsStateWithLifecycle()
    val cooking by vm.cooking.collectAsStateWithLifecycle()
    val session by vm.engine.state.collectAsStateWithLifecycle()
    val connection by vm.bluetooth.connection.collectAsStateWithLifecycle()
    val replaceTarget by vm.confirmReplace.collectAsStateWithLifecycle()
    var showDevice by remember { mutableStateOf(false) }

    val btPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) { showDevice = true; vm.scan()?.let(messenger::show) } else messenger.show("请允许 CookX 使用附近设备权限")
    }
    fun openDevice() {
        if (!vm.bluetooth.supported) { messenger.show("此手机不支持蓝牙"); return }
        if (!vm.bluetooth.hasPermissions()) { btPermissions.launch(vm.bluetooth.requiredPermissions()); return }
        if (!vm.bluetooth.enabled) { messenger.show("请开启手机蓝牙后继续"); return }
        showDevice = true
        vm.scan()?.let(messenger::show)
    }
    var exportSaved by remember { mutableStateOf(false) }
    var exportKind by remember { mutableStateOf("temperature") }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) { messenger.show("已取消导出"); return@rememberLauncherForActivityResult }
        val json = if (exportKind == "device") vm.bluetooth.diagnostics().toString() else vm.temperature.exportJson(exportSaved)
        if (json == null) return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
            .onSuccess { messenger.show(if (exportKind == "device") "诊断文件已导出；包含设备信息、原始温度帧及连接事件。" else "温度记录已导出") }
            .onFailure { messenger.show("文件保存失败") }
    }
    val export: (String, Boolean) -> Unit = { kind, saved ->
        exportKind = kind; exportSaved = saved
        exporter.launch(if (kind == "device") "cookx-device-diagnostics-${System.currentTimeMillis()}.json" else "cookx-temperature-${System.currentTimeMillis()}.json")
    }

    if (cooking && session != null) CookingView(vm, session!!, onDevice = ::openDevice, onExport = export)
    else ChatView(vm, chat, session, onDevice = ::openDevice)

    val showCompletion by vm.showCompletion.collectAsStateWithLifecycle()
    session?.let { s ->
        if (showCompletion) CompletionSheet(s.id, s.completed, onComplete = vm::completed, onClose = vm::closeCompletion, onContinue = { vm.showCompletion.value = false })
    }

    if (showDevice) {
        val devices by vm.bluetooth.devices.collectAsStateWithLifecycle()
        val scanning by vm.bluetooth.scanning.collectAsStateWithLifecycle()
        val connecting by vm.connecting.collectAsStateWithLifecycle()
        DeviceSheet(devices, scanning, connecting, vm.bluetooth.savedAddress.orEmpty(), onScan = { vm.scan()?.let(messenger::show) },
            onConnect = { address -> vm.connect(address) { msg -> messenger.show(msg); if (vm.bluetooth.connection.value.connected) showDevice = false } },
            onDismiss = { vm.stopScan(); showDevice = false })
    }
    replaceTarget?.let { r ->
        ConfirmDialog("替换烹饪", "开始新的菜谱将替换当前烹饪记录，是否继续？", { vm.start(r) }, { vm.confirmReplace.value = null }, confirmText = "替换", dismissText = "保留当前")
    }
    LaunchedEffect(connection.state) { if (connection.errorCode.isNotEmpty() && connection.state.id == "error") messenger.show(connection.message) }
}

@Composable
private fun KitchenHero(vm: KitchenViewModel, session: CookingState?, cooking: Boolean, remaining: Long, overall: Int) {
    val connection by vm.bluetooth.connection.collectAsStateWithLifecycle()
    val playback by vm.voice.playback.collectAsStateWithLifecycle()
    HeroHeader(section = "AI 厨房", bottomOverlap = 26) {
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip("CookX Sense ${connection.label}", if (connection.connected) Tone.Fresh else Tone.OnDark, icon = Icons.Outlined.Bluetooth)
            StatusChip(if (cooking) "语音 · ${playback.name.lowercase().let { mapOf("idle" to "待播放", "loading" to "准备中", "playing" to "播报中", "paused" to "已暂停")[it] }}" else "语音待命", Tone.OnDark, icon = Icons.Outlined.Mic)
        }
        Spacer(Modifier.height(16.dp))
        if (cooking && session != null) {
            val recipe = session.recipeModel
            Row(verticalAlignment = Alignment.CenterVertically) {
                GradientIcon(Icons.Outlined.Restaurant, CookX.accentBrush, size = 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Kicker("COOKX COOKING")
                    Text(recipe.dishName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${if (session.timer.deadline == null) "已暂停" else "烹饪中"}${if (recipe.totalSeconds > 0) " · 总时长约 ${maxOf(1, Math.round(recipe.totalSeconds / 60))} 分钟" else ""}", color = CookX.OnDarkMuted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth().clip(CookXShapes.Tile).background(Color.White.copy(alpha = 0.08f)).padding(14.dp)) {
                Row {
                    Text("整体进度", color = CookX.OnDarkMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("${session.stepIndex + 1} / ${recipe.steps.size} 步", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { overall / 100f }, Modifier.fillMaxWidth().height(8.dp).clip(CookXShapes.Pill), color = CookX.Accent, trackColor = Color.White.copy(alpha = 0.14f), drawStopIndicator = {})
                Spacer(Modifier.height(8.dp))
                val future = recipe.steps.drop(session.stepIndex + 1).sumOf { it.durationSeconds?.takeIf { d -> d > 0 } ?: 0.0 }
                val left = remaining + future.toLong()
                Text("预计完成 ${if (left > 0) Time.clock(System.currentTimeMillis() + left * 1000) else "--:--"}", color = CookX.OnDarkMuted, fontSize = 12.sp)
            }
        } else {
            Kicker("COOKX INTELLIGENCE")
            Text("CookX AI 厨房", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            Text("从菜谱推荐到语音步骤指导，让每一步都更从容。", color = CookX.OnDarkMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// ---------------------------------------------------------------------------- chat mode

@Composable
private fun ChatView(vm: KitchenViewModel, chat: ChatState, session: CookingState?, onDevice: () -> Unit) {
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val latest = chat.messages.lastOrNull { it.recipe != null }?.recipe
    var deliveryDish by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(chat.messages.size, chat.loading) { if (chat.messages.isNotEmpty()) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1) }

    Column(Modifier.fillMaxSize().background(CookX.Bg)) {
        LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { KitchenHero(vm, session, false, 0, 0) }
            if (session?.active == true) item {
                Banner("发现未完成的烹饪「${session.recipeModel.dishName}」，计时按实际经过时间核对。", BannerKind.Pending, Modifier.padding(horizontal = 16.dp), title = "可恢复烹饪") {
                    TonalButton("恢复烹饪", vm::restore, tone = Tone.Warm)
                }
            }
            if (session?.completed == true && session.consumption != "confirmed") item {
                Banner("上次烹饪已完成，可核对实际使用的食材。", BannerKind.Info, Modifier.padding(horizontal = 16.dp)) {
                    TonalButton("查看完成与库存核对", { vm.showCompletion.value = true })
                }
            }
            chat.error?.let { e -> item {
                Banner(e, BannerKind.Error, Modifier.padding(horizontal = 16.dp)) { OutlineButton("重新生成菜谱", { vm.send(chat.lastPrompt) }, enabled = chat.lastPrompt.isNotBlank()) }
            } }
            item {
                if (latest != null) ReadyCard(latest) { vm.requestStart(latest) }
                else CookXCard(Modifier.padding(horizontal = 16.dp)) {
                    EmptyState(Icons.Outlined.Restaurant, "还没有开始烹饪", "告诉 CookX 你想做什么，生成菜谱后可一步步语音指导。", tone = Tone.Warm)
                    Text("试试这样问", style = MaterialTheme.typography.labelLarge, color = CookX.TextSecondary)
                    ActionRow(Modifier.padding(top = 8.dp)) {
                        listOf("用冰箱里的食材推荐一道菜", "30 分钟内的快手晚餐", "清淡少油的家常菜").forEach { prompt ->
                            TonalButton(prompt, { vm.send(prompt) }, enabled = !chat.loading)
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("AI 菜谱对话", Modifier.weight(1f))
                    LinkButton("连接测温设备", onDevice, icon = Icons.Outlined.Bluetooth, color = CookX.Primary)
                }
            }
            items(chat.messages.size) { i -> MessageBubble(chat.messages[i], onStart = { r -> vm.requestStart(r) }, onMarket = { openMarket(context) }, onDelivery = { deliveryDish = it }) }
            if (chat.loading) item {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.Restaurant, Tone.Warm, size = 34.dp, iconSize = 17.dp, shape = CircleShape)
                    Spacer(Modifier.width(10.dp))
                    Row(Modifier.clip(CookXShapes.Tile).background(CookX.Surface).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        LoadingDots(); Text("  CookX 正在生成菜谱…", style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(CookX.Surface).border(1.dp, CookX.Border).imePadding().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                input, { input = it }, Modifier.weight(1f), placeholder = { Text("告诉 CookX 你想做什么…", color = CookX.TextTertiary) },
                shape = RoundedCornerShape(24.dp), colors = fieldColors(), maxLines = 3,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(50.dp).clip(CircleShape).background(if (input.isNotBlank() && !chat.loading) CookX.accentBrush else androidx.compose.ui.graphics.SolidColor(CookX.Accent.copy(alpha = 0.4f)))
                    .pressable({ if (input.isNotBlank() && !chat.loading) { vm.send(input.trim()); input = "" } }),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Outlined.Send, "发送", tint = Color.White) }
        }
    }
    deliveryDish?.let { dish ->
        ConfirmDialog("外卖下单", "这就去为您搜索「$dish」的外卖吗？", {
            deliveryDish = null
            openDelivery(context, dish) { messenger.show(it) }
        }, { deliveryDish = null }, confirmText = "出发", dismissText = "再想想")
    }
}

@Composable
private fun ReadyCard(recipe: Recipe, onStart: () -> Unit) {
    CookXCard(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Kicker("READY TO COOK", color = CookX.Accent)
                Text(recipe.dishName, style = MaterialTheme.typography.titleLarge)
                Text("${recipe.steps.size} 个步骤 · 菜谱已准备好，可以开启语音步骤指导", style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary)
            }
        }
        Spacer(Modifier.height(12.dp))
        AccentButton("开始烹饪", onStart, Modifier.fillMaxWidth(), icon = Icons.Outlined.PlayCircle)
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, onStart: (Recipe) -> Unit, onMarket: () -> Unit, onDelivery: (String) -> Unit) {
    val mine = message.role == "user"
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top) {
        if (!mine) { IconBadge(Icons.Outlined.Restaurant, Tone.Warm, size = 34.dp, iconSize = 17.dp, shape = CircleShape); Spacer(Modifier.width(8.dp)) }
        Column(Modifier.widthIn(max = 320.dp), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            if (message.content.isNotBlank()) Box(
                Modifier.clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp))
                    .background(if (mine) CookX.Primary else CookX.Surface)
                    .then(if (mine) Modifier else Modifier.border(1.dp, CookX.Border, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) { Text(message.content, color = if (mine) Color.White else CookX.Text, style = MaterialTheme.typography.bodyMedium) }
            message.recipe?.let { r -> RecipeCard(r, { onStart(r) }, onMarket, { onDelivery(r.dishName) }) }
        }
    }
}

@Composable
private fun RecipeCard(recipe: Recipe, onStart: () -> Unit, onMarket: () -> Unit, onDelivery: () -> Unit) {
    Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(CookXShapes.Card).background(CookX.Surface).border(1.dp, CookX.Border, CookXShapes.Card)) {
        Row(Modifier.fillMaxWidth().background(CookX.heroBrush).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Kicker("COOKX RECIPE")
                Text(recipe.dishName, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            AccentButton("开始指导", onStart, icon = Icons.Outlined.Mic)
        }
        Column(Modifier.padding(14.dp)) {
            if (recipe.ingredients.isNotEmpty()) {
                ActionRow { recipe.ingredients.forEach { StatusChip("${it.item} ${it.amount.orEmpty()}".trim(), Tone.Green) } }
                Spacer(Modifier.height(12.dp))
            }
            recipe.steps.take(3).forEachIndexed { i, s ->
                Row(Modifier.padding(bottom = 8.dp)) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(CookX.AccentBg), contentAlignment = Alignment.Center) { Text("${i + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CookX.Accent) }
                    Spacer(Modifier.width(8.dp))
                    Text(s.text, style = MaterialTheme.typography.bodySmall, color = CookX.TextBody, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            if (recipe.steps.size > 3) Text("还有 ${recipe.steps.size - 3} 步，开始指导后逐步展示", fontSize = 11.sp, color = CookX.TextTertiary)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton("买食材", onMarket, Modifier.weight(1f), icon = Icons.Outlined.Storefront)
                OutlineButton("点外卖", onDelivery, Modifier.weight(1f), icon = Icons.Outlined.DeliveryDining, color = CookX.Accent)
            }
        }
    }
}

private fun openMarket(context: android.content.Context) {
    val scheme = Intent(Intent.ACTION_VIEW, Uri.parse("androidamap://arroundpoi?sourceApplication=smart_cooking&keywords=${Uri.encode("菜市场")}&dev=0"))
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://uri.amap.com/search?keyword=${Uri.encode("菜市场")}&view=map&src=smart_cooking&coordinate=gaode"))
    runCatching { context.startActivity(scheme) }.onFailure { runCatching { context.startActivity(web) } }
}

private fun openDelivery(context: android.content.Context, dish: String, toast: (String) -> Unit) {
    val app = Intent(Intent.ACTION_VIEW, Uri.parse("imeituan://www.meituan.com/s/${Uri.encode(dish)}"))
    runCatching { context.startActivity(app) }.onFailure {
        (context.getSystemService(android.content.ClipboardManager::class.java))?.setPrimaryClip(android.content.ClipData.newPlainText("dish", dish))
        toast("请在美团外卖中搜索「$dish」（已复制菜名）")
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://h5.waimai.meituan.com/waimai/mindex/home"))) }
    }
}

// ---------------------------------------------------------------------------- cooking mode

@Composable
private fun CookingView(vm: KitchenViewModel, session: CookingState, onDevice: () -> Unit, onExport: (String, Boolean) -> Unit) {
    val context = LocalContext.current
    val now by vm.now.collectAsStateWithLifecycle()
    val playback by vm.voice.playback.collectAsStateWithLifecycle()
    val progress by vm.voice.progress.collectAsStateWithLifecycle()
    val voiceUi by vm.voiceUi.collectAsStateWithLifecycle()
    val sessionMessage by vm.sessionMessage.collectAsStateWithLifecycle()
    val remindersOn by vm.remindersOn.collectAsStateWithLifecycle()
    val notificationMessage by vm.notificationMessage.collectAsStateWithLifecycle()
    val checkError by vm.checkError.collectAsStateWithLifecycle()
    val preview by vm.adjustmentPreview.collectAsStateWithLifecycle()
    val adjustmentError by vm.adjustmentError.collectAsStateWithLifecycle()
    val connection by vm.bluetooth.connection.collectAsStateWithLifecycle()
    val latest by vm.bluetooth.latest.collectAsStateWithLifecycle()
    val assessment by vm.temperature.assessment.collectAsStateWithLifecycle()
    val history by vm.temperature.history.collectAsStateWithLifecycle()
    val prediction by vm.temperature.prediction.collectAsStateWithLifecycle()
    val modelState by vm.temperature.modelState.collectAsStateWithLifecycle()
    val experimental by vm.temperature.experimental.collectAsStateWithLifecycle()
    val replaying by vm.temperature.replaying.collectAsStateWithLifecycle()
    val storageMessage by vm.temperature.storageMessage.collectAsStateWithLifecycle()
    val connecting by vm.connecting.collectAsStateWithLifecycle()
    val deviceMessage by vm.deviceMessage.collectAsStateWithLifecycle()
    val remaining = remember(now, session) { (vm.engine.remaining() + 999) / 1000 }
    val steps = session.recipeModel.steps
    val duration = steps[session.stepIndex].durationSeconds?.coerceAtLeast(1.0) ?: 1.0
    val stepPct = ((duration - remaining) / duration).coerceIn(0.0, 1.0)
    val overall = minOf(100, Math.round((session.stepIndex + stepPct) / steps.size * 100).toInt())

    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) vm.listen() else vm.voiceUi.value = vm.voiceUi.value.copy(commandStatus = "麦克风权限未授予，请使用文字或按钮") }
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> vm.setReminders(true, ok) }
    val exact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.onExactSettingsReturned() }

    LazyColumn(Modifier.fillMaxSize().background(CookX.Bg), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { KitchenHero(vm, session, true, remaining, overall) }
        if (sessionMessage.isNotBlank()) section { Banner(sessionMessage, BannerKind.Warning) }
        section {
            CurrentStepCard(session, remaining, playback, progress, voiceUi.message, vm::toggleVoice, vm::runStep, vm::runCloudStep, vm::previous, vm::next, vm::pauseTimer, vm::resumeTimer, vm::setManualTimer)
        }
        section {
            VoiceCommandCard(voiceUi,
                onListen = { if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) vm.listen() else mic.launch(Manifest.permission.RECORD_AUDIO) },
                onCancel = vm::cancelListening, onText = vm::setCommandText, onRun = { vm.interpret(voiceUi.commandText, 1f) }, onExecute = vm::execute,
                onDismiss = { vm.voiceUi.value = voiceUi.copy(pendingCommands = emptyList()) })
        }
        section {
            SensePanel(connection, latest, assessment, history, prediction, modelState, experimental, replaying, storageMessage, connecting,
                onConnect = onDevice, onDisconnect = vm::disconnect, onEvent = vm.temperature::confirm, onExperimental = vm.temperature::setExperimental,
                onReplay = vm.temperature::startReplay, onStopReplay = vm.temperature::stopReplay, onExport = { onExport("temperature", it) })
        }
        section { StepsTrack(session, overall) }
        section { SupportCards(session) }
        section {
            RemindersCard(session, remindersOn, notificationMessage, checkError,
                onToggle = { on ->
                    val granted = Build.VERSION.SDK_INT < 33 || androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (on && !granted) notify.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else vm.setReminders(on, true)
                },
                onExact = { vm.exactSettingsIntent()?.let { runCatching { exact.launch(it) } } ?: vm.onExactSettingsReturned() },
                onRetryCheck = vm::checkInventory, onDismiss = vm::dismissReminder)
        }
        section {
            AdjustmentsCard(session, preview, adjustmentError, vm.engine::canUndo, vm::previewAdjustment, vm::applyAdjustment, { vm.adjustmentPreview.value = null }, vm::undoAdjustment)
        }
        section {
            CookXCard(Modifier.fillMaxWidth()) {
                SectionHeader("设备状态与诊断", icon = Icons.Outlined.Settings, subtitle = "恢复页面后等待新测量；后台连续采集能力待真机验证。")
                Spacer(Modifier.height(10.dp))
                ActionRow {
                    TonalButton("扫描与连接设备", onDevice)
                    OutlineButton("重新核对设备状态", vm::refreshDevice)
                    OutlineButton("导出设备诊断", { onExport("device", false) })
                }
                if (deviceMessage.isNotBlank()) Text(deviceMessage, style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary, modifier = Modifier.padding(top = 8.dp))
            }
        }
        section {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton("返回对话", vm::exitCooking, Modifier.weight(1f), color = CookX.TextSecondary)
                PrimaryButton("完成与库存核对", { vm.showCompletion.value = true }, Modifier.weight(1f))
            }
        }
    }
}

private fun LazyListScope.section(content: @Composable () -> Unit) = item { Box(Modifier.padding(horizontal = 16.dp)) { content() } }
