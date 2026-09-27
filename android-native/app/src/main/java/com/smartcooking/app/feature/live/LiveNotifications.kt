package com.smartcooking.app.feature.live

import android.content.Context

/**
 * Decides when the live cooking notification exists:
 * - CookX Sense connected: always, as a foreground service (keeps the readings coming while the
 *   app is in the background);
 * - otherwise, while the app is in the background during a cooking session or a replay.
 * In the app the floating card shows the same information, so nothing else is posted.
 */
object LiveNotifications {
    fun payload(s: LiveState, now: Long = System.currentTimeMillis()): LiveNotifier.Payload = LiveNotifier.Payload().apply {
        connected = s.connected
        simulated = s.simulated
        temperature = s.temperature ?: Double.NaN
        cooking = s.session != null
        dish = s.session?.dish.orEmpty()
        step = s.session?.let { "第 ${it.stepIndex + 1}/${it.total} 步 · ${it.stepTitle}" }.orEmpty()
        val advice = s.advice
        adviceTitle = s.session?.nextTitle?.takeIf { it.isNotBlank() }?.let { "下一步：$it" } ?: advice?.title ?: "下一步建议"
        adviceText = advice?.text ?: s.session?.stepTitle.orEmpty()
        val alert = s.alert
        alertLevel = when (alert?.level) { AlertLevel.Danger -> "danger"; AlertLevel.Warn -> "warn"; null -> "" }
        alertTitle = alert?.title.orEmpty()
        alertText = alert?.text.orEmpty()
        timerEndsAt = s.session?.takeIf { !it.paused && it.remainingMs > 0 }?.let { now + it.remainingMs } ?: 0L
    }

    fun sync(context: Context, s: LiveState, foreground: Boolean) {
        val app = context.applicationContext
        val wantService = s.connected
        val wantPlain = !s.connected && !foreground && (s.session != null || s.simulated)
        if (!wantService) {
            if (LiveCookingService.isRunning()) LiveCookingService.stop(app)
            if (!wantPlain) { LiveNotifier.cancel(app); return }
        }
        val payload = payload(s)
        if (wantService && !LiveCookingService.isRunning()) {
            LiveNotifier.build(app, payload)
            // Android only allows starting it while the app is visible; otherwise post a plain one.
            if (foreground) LiveCookingService.start(app)
        }
        LiveNotifier.post(app, payload)
    }
}
