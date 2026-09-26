package com.smartcooking.app.feature.fridge

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccessAlarm
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartcooking.app.core.LocalAppContainer
import com.smartcooking.app.core.Time
import com.smartcooking.app.core.clean
import com.smartcooking.app.data.FoodCategory
import com.smartcooking.app.data.FreshnessState
import com.smartcooking.app.data.InventoryItem
import com.smartcooking.app.data.detail
import com.smartcooking.app.data.loadUploadJpeg
import com.smartcooking.app.ui.components.AccentButton
import com.smartcooking.app.ui.components.AnimatedBanner
import com.smartcooking.app.ui.components.Banner
import com.smartcooking.app.ui.components.BannerKind
import com.smartcooking.app.ui.components.ConfirmDialog
import com.smartcooking.app.ui.components.CookXCard
import com.smartcooking.app.ui.components.CookXSheet
import com.smartcooking.app.ui.components.CookXTextField
import com.smartcooking.app.ui.components.EmptyState
import com.smartcooking.app.ui.components.FilterChips
import com.smartcooking.app.ui.components.HeroHeader
import com.smartcooking.app.ui.components.HeroIconButton
import com.smartcooking.app.ui.components.IconBadge
import com.smartcooking.app.ui.components.IngredientImage
import com.smartcooking.app.ui.components.LinkButton
import com.smartcooking.app.ui.components.ListRow
import com.smartcooking.app.ui.components.LoadingBlock
import com.smartcooking.app.ui.components.LocalMessenger
import com.smartcooking.app.ui.components.OutlineButton
import com.smartcooking.app.ui.components.PrimaryButton
import com.smartcooking.app.ui.components.SectionHeader
import com.smartcooking.app.ui.components.Tone
import com.smartcooking.app.ui.components.pressable
import com.smartcooking.app.ui.nav.Navigator
import com.smartcooking.app.ui.nav.Routes
import com.smartcooking.app.ui.nav.cookxViewModel
import com.smartcooking.app.ui.theme.CookX
import com.smartcooking.app.ui.theme.CookXShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun FridgeScreen(navigator: Navigator) {
    val vm = cookxViewModel { FridgeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val container = LocalAppContainer.current
    val messenger = LocalMessenger.current
    val context = LocalContext.current
    var pickSource by remember { mutableStateOf(false) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    fun recognize(uri: Uri) = vm.recognize(
        loadBytes = { withContext(Dispatchers.IO) { loadUploadJpeg(context, uri) } },
        onRecognized = { navigator.open(Routes.CAPTURE_CONFIRM) },
    )
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::recognize) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraUri?.let(::recognize) }
    fun openCamera() {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(dir, "fridge-${System.currentTimeMillis()}.jpg"))
        cameraUri = uri
        runCatching { camera.launch(uri) }.onFailure { messenger.show("没有可用的相机应用，请从相册选择") }
    }

    val startRecognition by container.events.startRecognition.collectAsStateWithLifecycle()
    LaunchedEffect(startRecognition) {
        if (startRecognition) { container.events.startRecognition.value = false; pickSource = true }
    }

    FridgeContent(
        state = state,
        onRefresh = vm::refresh,
        onSearch = vm::setSearch,
        onCategory = vm::setCategory,
        onAdd = vm::openAdd,
        onEdit = vm::openEdit,
        onDelete = { item -> vm.delete(item, messenger::show) },
        onRecognize = { pickSource = true },
        onConsult = { dish -> container.events.pendingDish.value = dish; navigator.tab(Routes.KITCHEN) },
        recommendations = {
            RecommendationsSection(state.revision, state.ready, vm::openAdd, { navigator.open(Routes.LEARNING) }) { dish ->
                container.events.pendingDish.value = dish; navigator.tab(Routes.KITCHEN)
            }
        },
    )

    state.editor?.let { editor ->
        CookXSheet(if (editor.editing != null) "修改食材信息" else "添加食材", vm::closeEditor, dismissible = !editor.saving,
            subtitle = editor.editing?.let { "批次 ${it.id.takeLast(6)} · ${it.measureText}" }) {
            editor.editing?.let { item ->
                FreshnessPanel(state.freshness.detail(item.id), state.freshness)
                Spacer(Modifier.height(14.dp))
            }
            InventoryFields(editor.form, vm::updateForm, enabled = !editor.saving && !state.pendingWrite, original = editor.editing)
            AnimatedBanner(editor.error, BannerKind.Warning, Modifier.padding(top = 12.dp))
            Spacer(Modifier.height(10.dp))
            PendingWriteNotice(state.pendingWrite, editor.saving, vm::releasePending)
            Spacer(Modifier.height(16.dp))
            PrimaryButton(if (editor.editing != null) "保存修改" else "确认添加", { vm.save(messenger::show) }, Modifier.fillMaxWidth(), loading = editor.saving)
            Spacer(Modifier.height(8.dp))
            OutlineButton("取消", vm::closeEditor, Modifier.fillMaxWidth(), enabled = !editor.saving, color = CookX.TextSecondary)
        }
    }

    if (pickSource) CookXSheet("识别冰箱食材", { pickSource = false }, subtitle = "拍一张冰箱或食材照片，CookX 会识别后交给你确认") {
        ListRow("拍照识别", subtitle = "使用相机拍摄冰箱或台面上的食材", icon = Icons.Outlined.CameraAlt, onClick = { pickSource = false; openCamera() })
        ListRow("从相册选择", subtitle = "选择一张已有的食材照片", icon = Icons.Outlined.PhotoLibrary, tone = Tone.Warm,
            onClick = { pickSource = false; gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
        Spacer(Modifier.height(8.dp))
        Text("识别结果仅作为候选，确认后才会入库；图片在上传前会压缩。", style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary)
    }

    if (state.recognition != RecognitionStatus.IDLE) RecognitionOverlay(state, vm.lastRecognitionError, vm::resetRecognition)
}

@Composable
fun FridgeContent(
    state: FridgeState,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onCategory: (FoodCategory) -> Unit,
    onAdd: () -> Unit,
    onEdit: (InventoryItem) -> Unit,
    onDelete: (InventoryItem) -> Unit,
    onRecognize: () -> Unit,
    onConsult: (String) -> Unit,
    recommendations: @Composable () -> Unit,
) {
    var deleting by remember { mutableStateOf<InventoryItem?>(null) }
    Box(Modifier.fillMaxSize().background(CookX.Bg)) {
        LazyVerticalGrid(
            GridCells.Fixed(2), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            full { FridgeHero(state, onAdd) }
            full { AttentionCard(state, onEdit) }
            full {
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CookXTextField(state.search, onSearch, "搜索食材名称", Modifier.weight(1f), leadingIcon = Icons.Outlined.Search, clearable = true)
                        Spacer(Modifier.width(8.dp))
                        IconBadge(Icons.Outlined.Refresh, Tone.Green, size = 52.dp, modifier = Modifier.clip(CookXShapes.Input).pressable(onRefresh))
                    }
                    Spacer(Modifier.height(10.dp))
                    FilterChips(state.categories, state.category, onCategory)
                    if (state.error && state.inventory.isNotEmpty()) Banner("库存加载失败，当前显示上次库存；鲜度需重新评估。", BannerKind.Error, Modifier.padding(top = 10.dp))
                    if (state.freshness is FreshnessState.Failed) Banner(state.freshness.message, BannerKind.Warning, Modifier.padding(top = 10.dp))
                    Spacer(Modifier.height(12.dp))
                }
            }
            when {
                state.loading && state.inventory.isEmpty() -> full { LoadingBlock("正在同步库存…") }
                state.error && state.inventory.isEmpty() -> full {
                    EmptyState(Icons.Outlined.WarningAmber, "库存加载失败", "请检查网络连接后重新加载，已有库存数据不会因此被清空。", tone = Tone.Danger) {
                        PrimaryButton("重新加载", onRefresh, icon = Icons.Outlined.Refresh)
                    }
                }
                state.filtered.isEmpty() -> full {
                    EmptyState(Icons.Outlined.Kitchen, if (state.inventory.isEmpty()) "冰箱还是空的" else "没有匹配的食材",
                        if (state.inventory.isEmpty()) "拍照识别或手动添加食材，CookX 就能开始为你规划下一餐" else "换个关键词或分类看看吧") {
                        if (state.inventory.isEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrimaryButton("拍照识别", onRecognize, icon = Icons.Outlined.CameraAlt)
                            OutlineButton("手动添加", onAdd, icon = Icons.Outlined.Add)
                        }
                    }
                }
                else -> items(state.filtered, key = { it.id }) { item ->
                    val index = state.filtered.indexOf(item)
                    FoodCard(item, state.freshness, { onEdit(item) }, { deleting = item },
                        Modifier.padding(start = if (index % 2 == 0) 16.dp else 0.dp, end = if (index % 2 == 1) 16.dp else 0.dp, bottom = 12.dp))
                }
            }
            full { Box(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)) { recommendations() } }
            if (state.inventory.isNotEmpty()) full { InspirationCard(state, onConsult) }
            full {
                Row(Modifier.padding(16.dp).fillMaxWidth().clip(CookXShapes.Tile).background(CookX.Mint).padding(14.dp)) {
                    Icon(Icons.Outlined.Lightbulb, null, tint = CookX.Primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("CookX 小贴士：定期清理过期食材，保持冰箱整洁；合理搭配食材，吃得健康又美味。", style = MaterialTheme.typography.bodySmall, color = CookX.Primary)
                }
            }
        }
        AccentButton("识别食材", onRecognize, Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 18.dp), icon = Icons.Outlined.CameraAlt)
    }
    deleting?.let { item ->
        ConfirmDialog("移除食材", "确定要从冰箱移除「${item.info.cn}」这一批次吗？", { deleting = null; onDelete(item) }, { deleting = null }, confirmText = "移除", danger = true)
    }
}

private fun LazyGridScope.full(content: @Composable () -> Unit) = item(span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun FridgeHero(state: FridgeState, onAdd: () -> Unit) {
    HeroHeader(section = "我的冰箱", actions = { HeroIconButton(Icons.Outlined.Add, "添加食材", onAdd) }, bottomOverlap = 22) {
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clip(CookXShapes.Card).background(Color.White.copy(alpha = 0.07f)).border(1.dp, Color.White.copy(alpha = 0.1f), CookXShapes.Card).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("库存概览", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("最后更新 ${state.lastUpdated?.let(Time::clock) ?: "等待同步"}", color = CookX.OnDarkMuted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(14.dp))
            Row {
                HeroMetric("食材种类", "${state.kindCount}", "种", Modifier.weight(1f))
                HeroMetric("库存总量", state.totalQuantity.clean(), "计数", Modifier.weight(1f))
                HeroMetric("即将过期", state.expiringText, "种", Modifier.weight(1f), CookX.Gold)
                HeroMetric("已过期", state.expiredText, "种", Modifier.weight(1f), Color(0xFFFF9A8A))
            }
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, unit: String, modifier: Modifier, color: Color = Color.White) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(unit, color = CookX.OnDarkMuted, fontSize = 10.sp, modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
        }
        Text(label, color = CookX.OnDarkMuted, fontSize = 11.sp)
    }
}

@Composable
private fun AttentionCard(state: FridgeState, onEdit: (InventoryItem) -> Unit) {
    val items = state.attention.take(3)
    if (items.isEmpty()) return
    CookXCard(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
        SectionHeader("临期食材提醒", icon = Icons.Outlined.AccessAlarm) { Text("最近 ${items.size} 项", fontSize = 12.sp, color = CookX.TextSecondary) }
        Spacer(Modifier.height(8.dp))
        items.forEach { item ->
            val status = com.smartcooking.app.data.FreshnessStatus.of(state.freshness.detail(item.id))
            Row(Modifier.fillMaxWidth().clip(CookXShapes.Small).pressable({ onEdit(item) }).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                IngredientImage(item.name, item.imageUrl, Modifier.size(40.dp).clip(CookXShapes.Small), iconSize = 18.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.info.cn, style = MaterialTheme.typography.titleSmall)
                    Text(status.description, style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary)
                }
                FreshnessBadge(state.freshness.detail(item.id), state.freshness)
            }
        }
    }
}

@Composable
private fun FoodCard(item: InventoryItem, freshness: FreshnessState, onEdit: () -> Unit, onDelete: () -> Unit, modifier: Modifier) {
    CookXCard(modifier, padding = PaddingValues(0.dp), shape = CookXShapes.Tile, elevation = 6.dp, onClick = onEdit) {
        Box {
            IngredientImage(item.name, item.imageUrl, Modifier.fillMaxWidth().aspectRatio(1.45f))
            FreshnessBadge(freshness.detail(item.id), freshness, Modifier.align(Alignment.TopStart).padding(8.dp))
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.info.cn, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                CategoryTag(item.category.label)
            }
            Text(item.measureText + (item.storageType?.let { " · $it" } ?: ""), fontSize = 11.5.sp, color = CookX.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("添加于 ${Time.monthDay(item.addTime)}", fontSize = 11.sp, color = CookX.TextTertiary, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.EditNote, "编辑食材", tint = CookX.Primary, modifier = Modifier.size(28.dp).clip(CircleShape).pressable(onEdit).padding(4.dp))
                Icon(Icons.Outlined.DeleteOutline, "删除食材", tint = CookX.Danger, modifier = Modifier.size(28.dp).clip(CircleShape).pressable(onDelete).padding(4.dp))
            }
        }
    }
}

@Composable
private fun InspirationCard(state: FridgeState, onConsult: (String) -> Unit) {
    CookXCard(Modifier.padding(horizontal = 16.dp).padding(top = 14.dp)) {
        SectionHeader("AI 生成灵感", subtitle = "由大模型根据库存构思新的菜谱方案", icon = Icons.Outlined.AutoAwesome)
        Spacer(Modifier.height(10.dp))
        when {
            state.quickLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), color = CookX.Accent, strokeWidth = 2.dp)
                Text("  构思中…", style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary)
            }
            state.quickDishes.isEmpty() -> Text("暂未获得灵感（云端生成服务可能未配置）", style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary)
            else -> state.quickDishes.forEach { dish ->
                Row(Modifier.fillMaxWidth().clip(CookXShapes.Tile).background(CookX.SurfaceMuted).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.Restaurant, Tone.Warm, size = 36.dp, iconSize = 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(dish, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    LinkButton("咨询教程", { onConsult(dish) }, icon = Icons.AutoMirrored.Outlined.ArrowForward, color = CookX.Accent)
                }
            }
        }
    }
}

@Composable
private fun RecognitionOverlay(state: FridgeState, error: String, onReset: () -> Unit) {
    val busy = state.recognition == RecognitionStatus.UPLOADING || state.recognition == RecognitionStatus.ANALYZING
    Dialog(onDismissRequest = { if (!busy) onReset() }, properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = false)) {
        Column(
            Modifier.fillMaxWidth().shadow(24.dp, CookXShapes.Large).clip(CookXShapes.Large).background(CookX.Surface).padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val (icon, tone) = when (state.recognition) {
                RecognitionStatus.SUCCESS -> Icons.Outlined.CheckCircle to Tone.Fresh
                RecognitionStatus.ERROR -> Icons.Outlined.ErrorOutline to Tone.Danger
                else -> Icons.Outlined.CameraAlt to Tone.Green
            }
            Box(contentAlignment = Alignment.Center) {
                if (busy) {
                    val spin = rememberInfiniteTransition(label = "scan").animateFloat(0f, 360f, infiniteRepeatable(tween(1400)), label = "spin")
                    CircularProgressIndicator(progress = { 0.28f }, Modifier.size(86.dp).rotate(spin.value), color = CookX.Accent, strokeWidth = 3.dp, trackColor = CookX.Mint)
                }
                IconBadge(icon, tone, size = 66.dp, iconSize = 30.dp, shape = CircleShape)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                when (state.recognition) {
                    RecognitionStatus.UPLOADING -> "正在上传图片"; RecognitionStatus.ANALYZING -> "CookX 正在识别食材"
                    RecognitionStatus.SUCCESS -> "识别完成"; else -> "识别失败"
                }, style = MaterialTheme.typography.titleLarge,
            )
            Text(
                when {
                    state.recognition == RecognitionStatus.SUCCESS -> "发现 ${state.recognizedCount} 项食材，即将进入确认页"
                    state.recognition == RecognitionStatus.ERROR -> error.ifBlank { "请检查网络后重新尝试" }
                    state.recognition == RecognitionStatus.UPLOADING -> "正在安全上传图片，请稍候…"
                    state.elapsed >= 30 -> "仍在识别中，请保持网络连接"
                    state.elapsed >= 15 -> "图片中的食材较多，本次识别需要一点时间"
                    state.elapsed >= 9 -> "正在整理识别结果…"
                    state.elapsed >= 4 -> "AI 正在定位图片中的食材…"
                    else -> "AI 正在分析图片，请稍候…"
                },
                style = MaterialTheme.typography.bodyMedium, color = CookX.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
            )
            if (state.recognition == RecognitionStatus.UPLOADING) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(progress = { state.uploadPercent / 100f }, Modifier.fillMaxWidth().height(6.dp).clip(CookXShapes.Pill), color = CookX.Accent, trackColor = CookX.Mint, drawStopIndicator = {})
                Text("${state.uploadPercent}%", fontSize = 12.sp, color = CookX.TextSecondary, modifier = Modifier.padding(top = 4.dp))
            } else if (state.recognition == RecognitionStatus.ANALYZING) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).clip(CookXShapes.Pill), color = CookX.Accent, trackColor = CookX.Mint)
            }
            if (busy) {
                Text("已等待 ${state.elapsed} 秒 · 请不要关闭页面或重复选择图片", fontSize = 11.5.sp, color = CookX.TextTertiary, modifier = Modifier.padding(top = 10.dp))
            }
            if (state.recognition == RecognitionStatus.ERROR) {
                Spacer(Modifier.height(16.dp))
                PrimaryButton("返回重试", onReset, Modifier.fillMaxWidth())
            }
        }
    }
}
