package com.smartcooking.app.core

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.smartcooking.app.data.InventoryRepository
import com.smartcooking.app.data.PreferencesRepository
import com.smartcooking.app.data.RecognitionDrafts
import com.smartcooking.app.data.Recipe
import com.smartcooking.app.device.BluetoothService
import com.smartcooking.app.feature.cooking.CompletionSync
import com.smartcooking.app.feature.cooking.CookingEngine
import com.smartcooking.app.feature.cooking.CookingNotifications
import com.smartcooking.app.feature.cooking.PersonalConsumption
import com.smartcooking.app.feature.cooking.VoiceService
import com.smartcooking.app.temperature.TemperatureIntelligence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/** Hand-rolled dependency container; one instance per process. */
class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val store = KeyValueStore(context)
    val sessions = SessionManager(store)
    val api = ApiClient(sessions)
    val business = BusinessApi(api, sessions)
    val inventory = InventoryRepository(api, store)
    val events = AppEvents()
    val bluetooth = BluetoothService(context, store)
    val drafts = RecognitionDrafts(store)
    val preferences = PreferencesRepository(business)
    val completions = CompletionSync(store, business, sessions)
    val consumption = PersonalConsumption(api, inventory, store, sessions)
    val notifications = CookingNotifications(context, store)
    val voice by lazy { VoiceService(context) }
    val temperature = TemperatureIntelligence(context, store, appScope)

    private val engines = mutableMapOf<String, CookingEngine>()

    /** One cooking engine per account, like the web `cookingStore` map. */
    fun cooking(user: String): CookingEngine = engines.getOrPut(user) { CookingEngine(user, store) }

    fun pendingCommand(scope: String, onSuccess: suspend (JsonObject) -> Unit = {}) =
        PendingCommand(scope, business, store, sessions, onSuccess)

    private var foreground = true

    init {
        // Restoration after restart requires confirmation, so no stale alarm may fire.
        notifications.cancelAll()
        appScope.launch {
            sessions.session.map { it?.userId }.distinctUntilChanged().collect { user ->
                engines.values.forEach { it.ready = false }
                notifications.cancelAll()
                temperature.bindUser(user)
            }
        }
        appScope.launch {
            bluetooth.samples.collect { sample -> if (foreground) temperature.receive(sample) }
        }
        appScope.launch {
            bluetooth.connection.collect { if (!it.connected) temperature.invalidate(if (it.label == "未连接") "等待连续温度数据" else it.label) }
        }
        appScope.launch {
            while (isActive) { delay(1000); bluetooth.expireStale() }
        }
        appScope.launch {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    foreground = true
                    bluetooth.reconcile()
                    temperature.invalidate("恢复前台，等待新的连续测量")
                }
                override fun onStop(owner: LifecycleOwner) {
                    foreground = false
                    bluetooth.onBackground()
                    temperature.invalidate("应用在后台，不能保证连续采集")
                    runCatching { voice.stopAll(); voice.stopListening() }
                }
            })
        }
    }
}

/** Cross-screen hand-offs that the web client passed through query strings and sessionStorage. */
class AppEvents {
    /** A dish name the AI kitchen should ask for (from the fridge's "咨询教程"). */
    val pendingDish = MutableStateFlow<String?>(null)
    /** Set by the home screen to open the camera as soon as the fridge tab appears. */
    val startRecognition = MutableStateFlow(false)
    /** Opens the profile editor when "我的" appears (from 账号与安全). */
    val editProfile = MutableStateFlow(false)
    /** A verified recipe copy queued for the kitchen; cooking starts only when the user taps start. */
    val recipeDraft = MutableStateFlow<Recipe?>(null)
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
