package com.smartcooking.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartcooking.app.ui.theme.CookX
import com.smartcooking.app.ui.theme.CookXShapes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** App-wide toast replacement for ElMessage. */
class Messenger(private val host: SnackbarHostState, private val scope: CoroutineScope) {
    fun show(text: String) {
        if (text.isBlank()) return
        scope.launch {
            host.currentSnackbarData?.dismiss()
            host.showSnackbar(text)
        }
    }
}

val LocalMessenger = staticCompositionLocalOf<Messenger> { error("Messenger not provided") }

enum class BannerKind(val tone: Tone, val icon: ImageVector) {
    Info(Tone.Green, Icons.Outlined.Info),
    Success(Tone.Fresh, Icons.Outlined.CheckCircle),
    Warning(Tone.Gold, Icons.Outlined.WarningAmber),
    Error(Tone.Danger, Icons.Outlined.ErrorOutline),
    Pending(Tone.Warm, Icons.Outlined.HourglassTop),
}

/** Inline status message; replaces the web's bare `<p class="error">` blocks. */
@Composable
fun Banner(
    text: String,
    kind: BannerKind = BannerKind.Info,
    modifier: Modifier = Modifier,
    title: String? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CookXShapes.Tile)
            .background(kind.tone.bg)
            .border(1.dp, kind.tone.fg.copy(alpha = 0.14f), CookXShapes.Tile)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(kind.icon, null, tint = kind.tone.fg, modifier = Modifier.padding(top = 1.dp).size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (title != null) Text(title, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = kind.tone.fg)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = if (kind == BannerKind.Error) Color(0xFF8C2A1E) else CookX.TextBody)
            }
        }
        if (actions != null) {
            Spacer(Modifier.height(10.dp))
            ActionRow(Modifier.padding(start = 28.dp)) { actions() }
        }
    }
}

@Composable
fun AnimatedBanner(text: String?, kind: BannerKind = BannerKind.Error, modifier: Modifier = Modifier) {
    AnimatedVisibility(!text.isNullOrBlank(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Banner(text.orEmpty(), kind, modifier)
    }
}

/** Shown while a write's outcome is unconfirmed; retry always reuses the original idempotency key. */
@Composable
fun PendingBanner(
    busy: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    message: String = "上次操作结果尚未确认。重试会使用原凭证，不会重复执行。",
    onDiscard: (() -> Unit)? = null,
) {
    Banner(message, BannerKind.Pending, modifier, title = "操作待确认") {
        TonalButton("使用原凭证重试", onRetry, loading = busy, tone = Tone.Warm)
        if (onDiscard != null) OutlineButton("已核对，清除", onDiscard, enabled = !busy, color = CookX.TextSecondary)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Green,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 26.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(icon, tone, size = 64.dp, iconSize = 28.dp, shape = CookXShapes.Card)
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = CookX.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp))
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun LoadingBlock(text: String = "正在加载…", modifier: Modifier = Modifier, minHeight: Int = 120) {
    Column(
        modifier.fillMaxWidth().heightIn(min = minHeight.dp).padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(Modifier.size(28.dp), color = CookX.Primary, strokeWidth = 2.5.dp, trackColor = CookX.Mint)
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary)
    }
}

@Composable
fun FullScreenLoading(text: String = "正在加载…") {
    Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) { LoadingBlock(text) }
}

/** Replacement for ElMessageBox.confirm. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "确认",
    dismissText: String = "取消",
    danger: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = CookXShapes.Large,
        containerColor = CookX.Surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium, color = CookX.TextBody) },
        confirmButton = {
            PrimaryButton(confirmText, onConfirm, container = if (danger) CookX.Danger else CookX.Primary)
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText, color = CookX.TextSecondary) } },
    )
}

/** Replacement for ElMessageBox.prompt with a validator returning an error message or null. */
@Composable
fun PromptDialog(
    title: String,
    label: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "确认",
    initial: String = "",
    numeric: Boolean = false,
    validate: (String) -> String? = { null },
) {
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = CookXShapes.Large,
        containerColor = CookX.Surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; error = null },
                    label = { Text(label) },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    shape = CookXShapes.Input,
                    keyboardOptions = if (numeric) androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal) else androidx.compose.foundation.text.KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            PrimaryButton(confirmText, {
                val problem = validate(value)
                if (problem != null) error = problem else onConfirm(value)
            })
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = CookX.TextSecondary) } },
    )
}
