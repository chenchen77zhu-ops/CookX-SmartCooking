package com.smartcooking.app.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.smartcooking.app.core.LocalAppContainer
import com.smartcooking.app.ui.components.LocalMessenger
import com.smartcooking.app.ui.components.Messenger
import com.smartcooking.app.ui.nav.CookXBottomBar
import com.smartcooking.app.ui.nav.CookXNavHost
import com.smartcooking.app.ui.nav.Routes
import com.smartcooking.app.ui.theme.CookX
import com.smartcooking.app.ui.theme.CookXShapes

@Composable
fun CookXRoot() {
    val container = LocalAppContainer.current
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val messenger = remember { Messenger(snackbar, scope) }
    val session by container.sessions.session.collectAsStateWithLifecycle()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    // Auth gate: signing out anywhere (including a 401 from the server) returns to login.
    LaunchedEffect(session?.userId) {
        val current = nav.currentBackStackEntry?.destination?.route
        if (session == null && current != null && current !in Routes.public) nav.goToLogin()
    }
    LaunchedEffect(Unit) {
        container.api.unauthorized.collect { messenger.show("登录已失效，请重新登录") }
    }

    CompositionLocalProvider(LocalMessenger provides messenger) {
        Scaffold(
            containerColor = CookX.Bg,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    Snackbar(Modifier.padding(horizontal = 16.dp), shape = CookXShapes.Tile, containerColor = CookX.PrimaryDark, contentColor = Color.White) {
                        Text(data.visuals.message)
                    }
                }
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = route in Routes.tabs,
                    enter = slideInVertically(tween(220)) { it } + fadeIn(),
                    exit = slideOutVertically(tween(180)) { it } + fadeOut(),
                ) { CookXBottomBar(nav, route) }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().background(CookX.Bg).padding(bottom = padding.calculateBottomPadding())) {
                CookXNavHost(nav, startDestination = if (session == null) Routes.LOGIN else Routes.HOME)
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
