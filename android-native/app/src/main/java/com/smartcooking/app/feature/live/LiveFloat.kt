package com.smartcooking.app.feature.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.smartcooking.app.ui.components.pressable
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Floating mini CookX Sense card shown on other pages while cooking or connected. It can be dragged
 * vertically and snaps to the nearest side; tap opens the kitchen, × hides it until the session or
 * connection changes.
 */
@Composable
fun LiveFloat(state: LiveState, visible: Boolean, onOpen: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier, bottomReserve: Int = 96) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val cardW = with(density) { 150.dp.toPx() }
        val cardH = with(density) { 118.dp.toPx() }
        val margin = with(density) { 12.dp.toPx() }
        val maxX = constraints.maxWidth - cardW - margin
        val maxY = constraints.maxHeight - cardH - with(density) { bottomReserve.dp.toPx() }
        val minY = with(density) { 56.dp.toPx() }
        val x = remember { Animatable(maxX) }
        val y = remember { Animatable((constraints.maxHeight * 0.58f).coerceIn(minY, maxY.coerceAtLeast(minY))) }
        val scope = rememberCoroutineScope()
        AnimatedVisibility(
            visible,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier.offset { IntOffset(x.value.roundToInt(), y.value.roundToInt()) },
        ) {
            Box(
                Modifier.pointerInput(maxX, maxY) {
                    detectDragGestures(
                        onDragEnd = {
                            scope.launch { x.animateTo(if (x.value + cardW / 2 < (maxX + margin + cardW) / 2) margin else maxX, spring(dampingRatio = 0.75f)) }
                        },
                    ) { change, drag ->
                        change.consume()
                        scope.launch {
                            x.snapTo((x.value + drag.x).coerceIn(margin, maxX))
                            y.snapTo((y.value + drag.y).coerceIn(minY, maxY.coerceAtLeast(minY)))
                        }
                    }
                },
            ) {
                SenseMiniCard(state, Modifier.pressable(onOpen))
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 7.dp, y = (-7).dp).size(24.dp).clip(CircleShape)
                        .background(Color(0xE6302E2C)).pressable(onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Close, "隐藏悬浮窗", tint = Color.White, modifier = Modifier.size(13.dp)) }
            }
        }
    }
}
