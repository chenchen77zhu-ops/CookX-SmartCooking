package com.smartcooking.app.core

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.smartcooking.app.data.InventoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow

/** Hand-rolled dependency container; one instance per process. */
class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val store = KeyValueStore(context)
    val sessions = SessionManager(store)
    val api = ApiClient(sessions)
    val business = BusinessApi(api, sessions)
    val inventory = InventoryRepository(api, store)
    val events = AppEvents()
    val bluetooth = com.smartcooking.app.device.BluetoothService(context, store)
    val drafts = com.smartcooking.app.data.RecognitionDrafts(store)
    val preferences = com.smartcooking.app.data.PreferencesRepository(business)

    fun pendingCommand(scope: String, onSuccess: suspend (kotlinx.serialization.json.JsonObject) -> Unit = {}) =
        PendingCommand(scope, business, store, sessions, onSuccess)
}

/** Cross-screen hand-offs that the web client passed through query strings and sessionStorage. */
class AppEvents {
    /** A dish name the AI kitchen should ask for (from the fridge's "咨询教程"). */
    val pendingDish = MutableStateFlow<String?>(null)
    /** Set by the home screen to open the camera as soon as the fridge tab appears. */
    val startRecognition = MutableStateFlow(false)
    /** Opens the profile editor when "我的" appears (from 账号与安全). */
    val editProfile = MutableStateFlow(false)
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
