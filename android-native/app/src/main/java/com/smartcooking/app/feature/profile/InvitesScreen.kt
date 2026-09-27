package com.smartcooking.app.feature.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartcooking.app.core.LocalAppContainer
import com.smartcooking.app.core.asObject
import com.smartcooking.app.core.num
import com.smartcooking.app.core.str
import com.smartcooking.app.core.userMessage
import com.smartcooking.app.ui.components.AnimatedBanner
import com.smartcooking.app.ui.components.BannerKind
import com.smartcooking.app.ui.components.CookXCard
import com.smartcooking.app.ui.components.LocalMessenger
import com.smartcooking.app.ui.components.OutlineButton
import com.smartcooking.app.ui.components.PrimaryButton
import com.smartcooking.app.ui.components.SectionHeader
import com.smartcooking.app.ui.components.SubpageScaffold
import com.smartcooking.app.ui.components.TonalButton
import com.smartcooking.app.ui.nav.Navigator
import com.smartcooking.app.ui.theme.CookX
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 邀请内测成员 (administrators): creates single-use registration codes, valid for 7 days
 * (`POST /api/auth/invitations`). New members enter the code on the sign-up page.
 */
@Composable
fun InvitesScreen(navigator: Navigator) {
    val container = LocalAppContainer.current
    val messenger = LocalMessenger.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by container.sessions.session.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val codes = remember { mutableStateListOf<Pair<String, Int>>() }

    fun create() {
        busy = true; error = null
        scope.launch {
            try {
                val res = container.api.post("/auth/invitations").asObject()
                val code = res?.str("code") ?: throw IllegalStateException("未返回邀请码")
                codes.add(0, code to (res?.num("expires_in_days")?.toInt() ?: 7))
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                error = e.userMessage()
            } finally { busy = false }
        }
    }
    fun copy(code: String) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("CookX 邀请码", code))
        messenger.show("邀请码已复制")
    }
    fun share(code: String) {
        val text = "邀请你加入 CookX 智能厨房内测：注册时填写邀请码 $code（7 天内有效，仅可使用一次）。"
        runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "分享邀请码")) }
    }

    SubpageScaffold("邀请内测成员", navigator::back, subtitle = "内测采用邀请制注册。每个邀请码仅能使用一次，7 天后失效。") {
        item {
            CookXCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                SectionHeader("生成邀请码", icon = Icons.Outlined.GroupAdd, subtitle = if (session?.isAdmin == true) "你是管理员，可以邀请家人和朋友加入。" else "仅管理员可以创建邀请码；如需邀请他人，请联系管理员。")
                Spacer(Modifier.height(12.dp))
                PrimaryButton("生成一次性邀请码", ::create, Modifier.fillMaxWidth(), loading = busy, enabled = session?.isAdmin == true)
                AnimatedBanner(error, BannerKind.Warning, Modifier.padding(top = 10.dp))
            }
        }
        codes.forEach { (code, days) ->
            item {
                CookXCard(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp).fillMaxWidth()) {
                    Text("邀请码 · ${days} 天内有效", style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary)
                    Text(
                        code, fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = CookX.Text,
                        modifier = Modifier.padding(vertical = 10.dp).clip(RoundedCornerShape(12.dp)).background(CookX.SurfaceSunken).padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        TonalButton("复制", { copy(code) }, Modifier.weight(1f), icon = Icons.Outlined.ContentCopy)
                        OutlineButton("分享", { share(code) }, Modifier.weight(1f), icon = Icons.Outlined.Share)
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text("邀请码只在生成时显示一次，服务器仅保存其摘要；离开本页后无法再次查看。", style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary)
            }
        }
    }
}
