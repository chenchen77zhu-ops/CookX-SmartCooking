package com.smartcooking.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.smartcooking.app.core.LocalAppContainer
import com.smartcooking.app.ui.CookXRoot
import com.smartcooking.app.ui.theme.Appearance
import com.smartcooking.app.ui.theme.CookXTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as CookXApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Transparent system bars; CookXRoot switches the icon colour per page (dark kitchen, light pages).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        Appearance.preference = container.store.get(Appearance.KEY)?.takeIf { v -> Appearance.options.any { it.first == v } } ?: "light"
        handleLaunch(intent)
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                CookXTheme { CookXRoot() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLaunch(intent)
    }

    /** The live cooking notification opens the kitchen. */
    private fun handleLaunch(intent: Intent?) {
        intent?.getStringExtra("cookx_route")?.let { container.events.launchRoute.value = it }
    }
}
