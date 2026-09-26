package com.smartcooking.app.feature.business

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.smartcooking.app.core.AppContainer
import com.smartcooking.app.core.PendingCommand
import com.smartcooking.app.core.userMessage
import com.smartcooking.app.ui.components.AnimatedBanner
import com.smartcooking.app.ui.components.BannerKind
import com.smartcooking.app.ui.components.ConfirmDialog
import com.smartcooking.app.ui.components.CookXCard
import com.smartcooking.app.ui.components.PendingBanner
import com.smartcooking.app.ui.components.PromptDialog
import com.smartcooking.app.ui.components.SectionHeader
import com.smartcooking.app.ui.theme.CookX
import com.smartcooking.app.ui.theme.CookXShapes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/** Base for the collaborative modules: one idempotent command channel plus a load error. */
abstract class BusinessViewModel(protected val c: AppContainer, scope: String) : ViewModel() {
    val user = c.sessions.currentUserId.orEmpty()
    val loadError = MutableStateFlow<String?>(null)
    val command: PendingCommand = c.pendingCommand(scope) { result -> onCommandSuccess(result) }

    protected open suspend fun onCommandSuccess(result: JsonObject) {}

    fun send(path: String, method: String, body: JsonObject = JsonObject(emptyMap())) {
        viewModelScope.launch { command.send(path, method, body) }
    }

    fun retry() { viewModelScope.launch { command.retry() } }

    protected fun launchLoad(block: suspend () -> Unit) = viewModelScope.launch {
        try { loadError.value = null; block() }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { loadError.value = e.userMessage() }
    }
}

/** Pending-write and error banners shown at the top of every business page. */
@Composable
fun CommandStatus(vm: BusinessViewModel, modifier: Modifier = Modifier, onDiscard: (() -> Unit)? = null) {
    val pending by vm.command.pending.collectAsStateWithLifecycle()
    val busy by vm.command.busy.collectAsStateWithLifecycle()
    val error by vm.command.error.collectAsStateWithLifecycle()
    val loadError by vm.loadError.collectAsStateWithLifecycle()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (pending != null) PendingBanner(busy, vm::retry, onDiscard = onDiscard)
        AnimatedBanner(error.ifBlank { null }, BannerKind.Error)
        AnimatedBanner(loadError, BannerKind.Error)
    }
}

@Composable
fun rememberBusy(vm: BusinessViewModel): Boolean {
    val busy by vm.command.busy.collectAsStateWithLifecycle()
    val pending by vm.command.pending.collectAsStateWithLifecycle()
    return busy || pending != null
}

/** Card with a section header; the building block of every business page. */
@Composable
fun Section(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    CookXCard(modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        SectionHeader(title, subtitle = subtitle, icon = icon) { trailing() }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

/** Compact list item with a title, secondary lines and trailing actions. */
@Composable
fun ItemBlock(
    title: String,
    modifier: Modifier = Modifier,
    lines: List<String> = emptyList(),
    badge: @Composable (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    Column(modifier.padding(bottom = 10.dp).fillMaxWidth().clip(CookXShapes.Tile).background(CookX.SurfaceMuted).border(1.dp, CookX.Border, CookXShapes.Tile).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (badge != null) { Spacer(Modifier.width(8.dp)); badge() }
        }
        lines.filter { it.isNotBlank() }.forEach { Text(it, fontSize = 12.sp, color = CookX.TextSecondary, lineHeight = 18.sp, modifier = Modifier.padding(top = 3.dp)) }
        extra()
        if (actions != null) {
            Spacer(Modifier.height(10.dp))
            com.smartcooking.app.ui.components.ActionRow { actions() }
        }
    }
}

/** Holds one pending confirmation; call `ask` to show it and render `Host()` once per screen. */
class Confirmer {
    var request by mutableStateOf<Triple<String, String, () -> Unit>?>(null)
    var prompt by mutableStateOf<Pair<Pair<String, String>, (String) -> Unit>?>(null)
    fun ask(title: String, text: String, action: () -> Unit) { request = Triple(title, text, action) }
    fun askText(title: String, label: String, action: (String) -> Unit) { prompt = (title to label) to action }

    @Composable
    fun Host() {
        request?.let { (title, text, action) ->
            ConfirmDialog(title, text, { request = null; action() }, { request = null })
        }
        prompt?.let { (labels, action) ->
            PromptDialog(labels.first, labels.second, { prompt = null; action(it) }, { prompt = null }, confirmText = "提交",
                validate = { if (it.trim().length < 3) "请补充原因（至少 3 个字）" else null })
        }
    }
}

@Composable
fun rememberConfirmer() = remember { Confirmer() }

val storageChoices = listOf(com.smartcooking.app.ui.components.Choice<String?>(null, "未知"), com.smartcooking.app.ui.components.Choice<String?>("常温", "常温"),
    com.smartcooking.app.ui.components.Choice<String?>("冷藏", "冷藏"), com.smartcooking.app.ui.components.Choice<String?>("冷冻", "冷冻"))

fun isoOrNull(local: String): String? = if (local.isBlank()) null else com.smartcooking.app.core.Time.isoFromLocal(local)
