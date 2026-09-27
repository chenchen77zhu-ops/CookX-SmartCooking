package com.smartcooking.app.logic

import com.smartcooking.app.feature.live.AlertLevel
import com.smartcooking.app.feature.live.Backdrop
import com.smartcooking.app.feature.live.HeatStage
import com.smartcooking.app.feature.live.LivePoint
import com.smartcooking.app.feature.live.LiveRules
import com.smartcooking.app.feature.live.LiveSession
import com.smartcooking.app.feature.live.LiveState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Live kitchen presentation rules (plain JVM, no Android). */
class LiveRulesTest {
    private val session = LiveSession(
        id = "s", dish = "香煎鸡胸肉", imageUrl = null, stepIndex = 1, total = 4, stepTitle = "热锅", stepText = "",
        nextTitle = "煎制", target = 170.0 to 190.0, remainingMs = 60_000, paused = false, timed = true, totalSeconds = 600.0, futureSeconds = 300.0,
    )

    @Test fun parsesTargetZones() {
        assertEquals(160.0 to 180.0, LiveRules.parseTarget("160–180 ℃"))
        assertEquals(150.0 to 170.0, LiveRules.parseTarget("约 160°C"))
        assertNull(LiveRules.parseTarget("中火"))
        assertNull(LiveRules.parseTarget(null))
        // Numbers outside a cooking range (e.g. step counts) are ignored.
        assertNull(LiveRules.parseTarget("第 3 步"))
    }

    @Test fun heatStagesFollowTemperature() {
        assertNull(HeatStage.of(null))
        assertEquals(HeatStage.Preheat, HeatStage.of(40.0))
        assertEquals(HeatStage.Heating, HeatStage.of(90.0))
        assertEquals(HeatStage.Sear, HeatStage.of(182.0))
        assertEquals(HeatStage.Cook, HeatStage.of(230.0))
    }

    @Test fun backdropsSwitchWithTemperatureAndAlerts() {
        assertEquals(Backdrop.Idle, LiveRules.backdrop(null, null))
        assertEquals(Backdrop.Idle, LiveRules.backdrop(30.0, null))
        assertEquals(Backdrop.Preheat, LiveRules.backdrop(70.0, null))
        assertEquals(Backdrop.Heating, LiveRules.backdrop(120.0, null))
        assertEquals(Backdrop.Sear, LiveRules.backdrop(180.0, null))
        assertEquals(Backdrop.Overheat, LiveRules.backdrop(180.0, LiveRules.alert(270.0, null, null)))
    }

    @Test fun alertsUseAbsoluteLimitsTargetsAndEngineRisk() {
        assertNull(LiveRules.alert(null, null, null))
        assertNull(LiveRules.alert(200.0, null, null))
        assertEquals(AlertLevel.Warn, LiveRules.alert(236.0, null, null)?.level)
        assertEquals(AlertLevel.Danger, LiveRules.alert(261.0, null, null)?.level)
        assertEquals(AlertLevel.Warn, LiveRules.alert(206.0, 170.0 to 190.0, null)?.level)
        assertEquals(AlertLevel.Danger, LiveRules.alert(231.0, 170.0 to 190.0, null)?.level)
        assertEquals(AlertLevel.Danger, LiveRules.alert(null, null, "danger")?.level)
        assertEquals(AlertLevel.Warn, LiveRules.alert(120.0, null, "warning")?.level)
    }

    @Test fun etaUsesTheRecentHeatingRate() {
        val history = (0..20).map { LivePoint(it * 1000L, 100.0 + it) } // 1 °C per second
        val rate = LiveRules.heatingRate(history)
        assertNotNull(rate)
        assertEquals(1.0, rate!!, 0.01)
        assertEquals(50.0, LiveRules.targetEta(120.0, 170.0 to 190.0, history)!!, 0.5)
        assertEquals(0.0, LiveRules.targetEta(175.0, 170.0 to 190.0, history)!!, 0.0)
        assertNull(LiveRules.heatingRate(history.take(3)))
    }

    @Test fun adviceFollowsTargetAndAlerts() {
        val flat = (0..10).map { LivePoint(it * 1000L, 150.0) }
        assertEquals("加热中", LiveRules.advice(150.0, session, null, null, flat)?.title)
        assertEquals("温度合适", LiveRules.advice(180.0, session, null, null, flat)?.title)
        assertEquals("温度偏高", LiveRules.advice(195.0, session, null, null, flat)?.title)
        assertEquals("锅温过高", LiveRules.advice(280.0, session, LiveRules.alert(280.0, session.target, null), null, flat)?.title)
        assertEquals("预热中", LiveRules.advice(60.0, null, null, null, emptyList())?.title)
    }

    @Test fun stateDerivesEverythingFromOneSnapshot() {
        val state = LiveState(connected = true, temperature = 182.0, measurementUsable = true, session = session, now = 10_000, mutedUntil = 20_000)
        assertEquals(Backdrop.Sear, state.backdrop)
        assertEquals(2, state.stepperIndex)
        assertTrue(state.muted)
        assertEquals("已连接", state.statusText)
        assertEquals("01:05", LiveRules.clock(65.0))
        assertEquals("--:--", LiveRules.clock(null))
    }
    @Test fun measurementQualitySuppressesOperationalAdviceAndEta() {
        val state = LiveState(connected = true, temperature = 280.0, risk = "danger", session = session)
        assertNull(state.alert)
        assertNull(state.eta)
        assertNull(state.stage)
        assertEquals("测量待确认", state.advice?.title)
        assertEquals(AlertLevel.Danger, state.copy(measurementUsable = true).alert?.level)
    }

    @Test fun durationAndFahrenheitAreNeverCelsiusTargets() {
        assertNull(LiveRules.parseTarget("加热 180 秒"))
        assertNull(LiveRules.parseTarget("180°F"))
        assertNull(LiveRules.parseTarget("180"))
        assertNull(LiveRules.parseTarget("190–170℃"))
        assertNull(LiveRules.parseTarget("加热 60 秒至 180℃"))
    }
}
