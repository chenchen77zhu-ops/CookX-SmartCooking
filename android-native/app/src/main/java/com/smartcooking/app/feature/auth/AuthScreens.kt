package com.smartcooking.app.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartcooking.app.R
import com.smartcooking.app.core.LocalAppContainer
import com.smartcooking.app.core.asObject
import com.smartcooking.app.core.jsonOf
import com.smartcooking.app.core.str
import com.smartcooking.app.core.userMessage
import com.smartcooking.app.ui.components.AnimatedBanner
import com.smartcooking.app.ui.components.Banner
import com.smartcooking.app.ui.components.BannerKind
import com.smartcooking.app.ui.components.CookXCard
import com.smartcooking.app.ui.components.CookXSheet
import com.smartcooking.app.ui.components.CookXTextField
import com.smartcooking.app.ui.components.HeroIconButton
import com.smartcooking.app.ui.components.LinkButton
import com.smartcooking.app.ui.components.LocalMessenger
import com.smartcooking.app.ui.components.OutlineButton
import com.smartcooking.app.ui.components.PrimaryButton
import com.smartcooking.app.ui.components.Wordmark
import com.smartcooking.app.ui.components.heroBackground
import com.smartcooking.app.ui.components.pressable
import com.smartcooking.app.ui.theme.CookX
import com.smartcooking.app.ui.theme.CookXShapes
import kotlinx.coroutines.launch

@Composable
private fun AuthLayout(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(CookX.Bg).verticalScroll(rememberScrollState()).imePadding()) {
        Column(
            Modifier.fillMaxWidth().heroBackground().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(48.dp)) {
                if (onBack != null) HeroIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", onBack, Modifier.align(Alignment.CenterStart))
            }
            Box(Modifier.size(88.dp).clip(CookXShapes.Large).background(CookX.OnDarkFaint), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.cookx_mark), null, Modifier.size(70.dp))
            }
            Spacer(Modifier.height(14.dp))
            Wordmark(34.sp)
            Text("AI 智能烹饪助手", color = CookX.OnDarkMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(10.dp))
            Box(Modifier.size(width = 28.dp, height = 2.dp).background(CookX.Gold))
            Spacer(Modifier.height(10.dp))
            Text("感知每一度 · 智烹每一步", color = CookX.OnDarkMuted, fontSize = 12.sp, letterSpacing = 2.sp)
        }
        CookXCard(Modifier.padding(horizontal = 18.dp).offset(y = (-36).dp), padding = androidx.compose.foundation.layout.PaddingValues(22.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
            content()
        }
    }
}

@Composable
fun LoginScreen(onLoggedIn: () -> Unit, onRegister: () -> Unit) {
    val container = LocalAppContainer.current
    val messenger = LocalMessenger.current
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showServer by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }

    fun submit() {
        attempted = true
        if (username.isBlank() || password.length < 6 || loading) return
        loading = true; error = null
        scope.launch {
            try {
                container.sessions.clear()
                val response = container.api.post("/login", jsonOf("username" to username.trim(), "password" to password)).asObject()
                if (response?.str("status") == "success") {
                    container.sessions.save(response)
                    val user = container.sessions.session.value
                    messenger.show("欢迎回来，${user?.displayName.orEmpty()}！")
                    onLoggedIn()
                } else error = response?.str("message") ?: "登录失败，请检查账号密码"
            } catch (e: Throwable) {
                error = e.userMessage()
            } finally { loading = false }
        }
    }

    AuthLayout("欢迎回来", "使用内测账号登录，继续你的智能烹饪") {
        CookXTextField(username, { username = it }, "昵称 / 用户名", leadingIcon = Icons.Outlined.Person, clearable = true,
            error = if (attempted && username.isBlank()) "请输入昵称" else null)
        Spacer(Modifier.height(10.dp))
        CookXTextField(password, { password = it }, "密码", leadingIcon = Icons.Outlined.Lock, password = true, imeAction = ImeAction.Done, onImeAction = ::submit,
            error = when { attempted && password.isBlank() -> "请输入密码"; attempted && password.length < 6 -> "密码长度至少 6 位"; else -> null })
        AnimatedBanner(error, BannerKind.Error, Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(18.dp))
        PrimaryButton("登录", ::submit, Modifier.fillMaxWidth(), loading = loading)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("还没有账号？", color = CookX.TextSecondary, fontSize = 13.sp)
            LinkButton("立即注册", onRegister, color = CookX.Accent)
        }
        Spacer(Modifier.height(6.dp))
        ServerRow(container.sessions.backendOrigin) { showServer = true }
    }
    if (showServer) ServerSettingsSheet(onDismiss = { showServer = false })
}

@Composable
private fun ServerRow(origin: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(CookXShapes.Tile).background(CookX.Mint).pressable(onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Dns, null, tint = CookX.Primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("局域网后端地址", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = CookX.Primary)
            Text(origin, fontSize = 12.sp, color = CookX.TextSecondary)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = CookX.Primary)
    }
}

@Composable
fun ServerSettingsSheet(onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val messenger = LocalMessenger.current
    var address by remember { mutableStateOf(container.sessions.savedBackendOrigin ?: container.sessions.backendOrigin) }
    var error by remember { mutableStateOf<String?>(null) }
    CookXSheet("局域网后端地址", onDismiss, subtitle = "手机与运行后端的电脑需处于同一网络。") {
        Banner("填写电脑的局域网 IPv4 和端口，例如 http://192.168.1.20:8000。更换地址后需要重新登录；留空恢复安装包默认配置。", BannerKind.Info)
        Spacer(Modifier.height(14.dp))
        CookXTextField(address, { address = it; error = null }, "后端地址", placeholder = "http://192.168.1.20:8000", error = error,
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri, imeAction = ImeAction.Done)
        Spacer(Modifier.height(14.dp))
        PrimaryButton("保存地址并在本机退出账号", {
            try {
                container.sessions.setBackendOrigin(address)
                messenger.show("后端地址已保存，请重新登录")
                onDismiss()
            } catch (e: IllegalArgumentException) { error = e.message }
        }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlineButton("取消", onDismiss, Modifier.fillMaxWidth(), color = CookX.TextSecondary)
    }
}

@Composable
fun RegisterScreen(onBack: () -> Unit, onRegistered: () -> Unit) {
    val container = LocalAppContainer.current
    val messenger = LocalMessenger.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val passwordError = when { !attempted -> null; password.isBlank() -> "请输入密码"; password.length !in 8..64 -> "密码长度在 8-64 个字符"; else -> null }
    val confirmError = when { !attempted -> null; confirm.isBlank() -> "请再次输入密码"; confirm != password -> "两次输入的密码不一致"; else -> null }

    fun submit() {
        attempted = true
        if (code.isBlank() || nickname.isBlank() || passwordError != null || confirmError != null || password.length !in 8..64 || confirm != password || loading) return
        loading = true; error = null
        scope.launch {
            try {
                val response = container.api.post("/register", jsonOf("invitation_code" to code.trim(), "nickname" to nickname.trim(), "phone" to "", "password" to password, "sms_code" to "")).asObject()
                if (response?.str("status") == "success") {
                    messenger.show("注册成功，欢迎加入！")
                    onRegistered()
                } else error = response?.str("message") ?: "注册失败"
            } catch (e: Throwable) { error = e.userMessage() } finally { loading = false }
        }
    }

    AuthLayout("加入 CookX 内测", "注册需要管理员提供的内测邀请码", onBack = onBack) {
        CookXTextField(code, { code = it }, "内测邀请码", leadingIcon = Icons.Outlined.ConfirmationNumber, error = if (attempted && code.isBlank()) "请输入内测邀请码" else null)
        Spacer(Modifier.height(10.dp))
        CookXTextField(nickname, { nickname = it }, "昵称", leadingIcon = Icons.Outlined.Person, clearable = true, error = if (attempted && nickname.isBlank()) "请输入昵称" else null)
        Spacer(Modifier.height(10.dp))
        CookXTextField(password, { password = it }, "密码（8-64 位）", leadingIcon = Icons.Outlined.Lock, password = true, error = passwordError)
        Spacer(Modifier.height(10.dp))
        CookXTextField(confirm, { confirm = it }, "确认密码", leadingIcon = Icons.Outlined.Lock, password = true, imeAction = ImeAction.Done, onImeAction = ::submit, error = confirmError)
        AnimatedBanner(error, BannerKind.Error, Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(18.dp))
        PrimaryButton("立即注册", ::submit, Modifier.fillMaxWidth(), loading = loading)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("已有账号？", color = CookX.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
            LinkButton("立即登录", onBack, color = CookX.Accent)
        }
    }
}
