package com.smartcooking.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.smartcooking.app.core.LocalAppContainer
import com.smartcooking.app.ui.CookXRoot
import com.smartcooking.app.ui.theme.CookXTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hero headers are dark, so status bar icons stay light; the bottom bar is light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        val container = (application as CookXApp).container
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                CookXTheme { CookXRoot() }
            }
        }
    }
}
