package com.smartcooking.app.ui

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.smartcooking.app.core.LocalAppContainer
import com.smartcooking.app.feature.live.LiveFloat
import com.smartcooking.app.ui.components.LocalMessenger
import com.smartcooking.app.ui.components.Messenger
import com.smartcooking.app.ui.nav.CookXBottomBar
import com.smartcooking.app.ui.nav.CookXNavHost
import com.smartcooking.app.ui.nav.Navigator
import com.smartcooking.app.ui.nav.Routes
import com.smartcooking.app.ui.theme.CookX
import com.smartcooking.app.ui.theme.CookXShapes

/** Pages drawn on dark photography: the status bar icons turn light there. */
private val darkRoutes = setOf(Routes.KITCHEN, Routes.LOGIN, Routes.REGISTER)

@Composable
fun CookXRoot() {
    val container = LocalAppContainer.current
    val nav = rememberNavController()
    val navigator = remember(nav) { Navigator(nav) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val messenger = remember { Messenger(snackbar, scope) }
    val session by container.sessions.session.collectAsStateWithLifecycle()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val immersive by container.events.immersive.collectAsStateWithLifecycle()
    val live by container.live.state.collectAsStateWithLifecycle()
    val launchRoute by container.events.launchRoute.collectAsStateWithLifecycle()

    // Auth gate: signing out anywhere (including a 401 from the server) returns to login.
    LaunchedEffect(session?.userId) {
        val current = nav.currentBackStackEntry?.destination?.route
        if (session == null && current != null && current !in Routes.public) nav.goToLogin()
    }
    LaunchedEffect(Unit) {
        container.api.unauthorized.collect { messenger.show("登录已失效，请重新登录") }
    }
    // Opened from the live notification: go straight to the kitchen.
    LaunchedEffect(launchRoute, route) {
        val target = launchRoute
        if (target != null && route != null && route !in Routes.public && session != null) {
            container.events.launchRoute.value = null
            if (target == Routes.KITCHEN) container.events.openCooking.value = true
            navigator.tab(target)
        }
    }

    // Status bar icons: light on the dark kitchen/sign-in pages and in dark mode, dark otherwise.
    val view = LocalView.current
    val lightIcons = CookX.isDark || route in darkRoutes
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !lightIcons
            isAppearanceLightNavigationBars = !CookX.isDark
        }
    }

    // Floating CookX Sense card on every other page while cooking or connected.
    val floatKey = "${live.session?.id}|${live.online}"
    var dismissedKey by remember { mutableStateOf<String?>(null) }
    val floatVisible = session != null && (live.session != null || live.online) && dismissedKey != floatKey &&
        route != null && route !in darkRoutes && route != Routes.CAPTURE_CONFIRM && !immersive

    CompositionLocalProvider(LocalMessenger provides messenger) {
        Scaffold(
            containerColor = CookX.Bg,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    Snackbar(Modifier.padding(horizontal = 16.dp), shape = CookXShapes.Tile, containerColor = Color(0xFF1F2320), contentColor = Color.White) {
                        Text(data.visuals.message)
                    }
                }
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = route in Routes.tabs && !immersive,
                    enter = slideInVertically(tween(220)) { it } + fadeIn(),
                    exit = slideOutVertically(tween(180)) { it } + fadeOut(),
                ) { CookXBottomBar(nav, route) }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().background(CookX.Bg).padding(bottom = padding.calculateBottomPadding())) {
                CookXNavHost(nav, startDestination = if (session == null) Routes.LOGIN else Routes.HOME)
                LiveFloat(
                    live, floatVisible,
                    onOpen = { container.events.openCooking.value = live.session != null; navigator.tab(Routes.KITCHEN) },
                    onClose = { dismissedKey = floatKey },
                    bottomReserve = if (route in Routes.tabs) 24 else 96,
                )
            }
        }
    }
}

fun NavHostController.goToLogin() {
    navigate(Routes.LOGIN) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
