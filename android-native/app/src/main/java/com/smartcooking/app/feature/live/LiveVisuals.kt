package com.smartcooking.app.feature.live

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.smartcooking.app.R
import com.smartcooking.app.ui.theme.KitchenColors
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

internal val stageImages = mapOf(
    Backdrop.Idle to R.drawable.kitchen_stage_idle,
    Backdrop.Preheat to R.drawable.kitchen_stage_preheat,
    Backdrop.Heating to R.drawable.kitchen_stage_heating,
    Backdrop.Sear to R.drawable.kitchen_stage_sear,
    Backdrop.Overheat to R.drawable.kitchen_stage_overheat,
)

/**
 * Full-screen kitchen photograph for the current [state]. The five photos share one camera
 * position, so a 1.6 s crossfade reads as the pan heating up rather than a slide change.
 * [cooking] blurs and darkens the photo behind the denser cooking dashboard.
 */
@Composable
fun StageBackdrop(state: Backdrop, modifier: Modifier = Modifier, cooking: Boolean = false, alert: Boolean = false, animate: Boolean = true) {
    Box(modifier.fillMaxSize().background(KitchenColors.Base)) {
        Backdrop.entries.forEach { layer ->
            val target = if (layer == state) 1f else 0f
            val alpha by animateFloatAsState(target, if (animate) tween(1600, easing = FastOutSlowInEasing) else tween(0), label = "stage-$layer")
            if (alpha > 0.001f) {
                Image(
                    painterResource(stageImages.getValue(layer)), null,
                    Modifier.fillMaxSize()
                        .graphicsLayer { this.alpha = alpha }
                        .stagePlacement(cooking)
                        .then(if (cooking) Modifier.blur(22.dp) else Modifier),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                )
            }
        }
        Steam(
            intensity = when (state) { Backdrop.Idle -> 0.05f; Backdrop.Preheat -> 0.2f; Backdrop.Heating -> 0.45f; Backdrop.Sear -> 0.8f; Backdrop.Overheat -> 1f },
            smoke = state == Backdrop.Overheat,
            originY = if (cooking) 0.36f else 0.46f,
            animate = animate,
        )
        // Soft edges hide where the slightly reduced photo ends.
        if (!cooking) Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to KitchenColors.Base, 0.075f to KitchenColors.Base.copy(alpha = 0.85f), 0.16f to Color.Transparent, 0.84f to Color.Transparent, 0.925f to KitchenColors.Base.copy(alpha = 0.85f), 1f to KitchenColors.Base),
            ),
        )
        // Readability: dark top for the large reading, dark bottom for the glass cards.
        Box(
            Modifier.fillMaxSize().background(
                if (cooking) Brush.verticalGradient(0f to Color(0x8C080908), 0.5f to Color(0x66080908), 1f to Color(0xE6080908))
                else Brush.verticalGradient(
                    0f to Color(0xB8080908), 0.2f to Color(0x2E080908), 0.34f to Color(0x00080908),
                    0.55f to Color(0x24080908), 0.78f to Color(0xC7080908), 1f to Color(0xFF0B0C0B),
                ),
            ),
        )
        AlertVignette(alert, animate)
    }
}

/** Pulsing red edge glow while the pan is overheating. */
@Composable
fun AlertVignette(active: Boolean, animate: Boolean = true) {
    val shown by animateFloatAsState(if (active) 1f else 0f, tween(600), label = "alert")
    if (shown <= 0.001f) return
    val pulse = if (animate) {
        val transition = rememberInfiniteTransition(label = "alert-pulse")
        transition.animateFloat(0.55f, 1f, infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse), label = "pulse").value
    } else 1f
    Box(
        Modifier.fillMaxSize().alpha(shown * pulse).drawBehind {
            val r = size.maxDimension * 0.75f
            drawRect(Brush.radialGradient(0.55f to Color.Transparent, 1f to Color(0xB3FF281E), center = center, radius = r))
            drawRect(Brush.verticalGradient(0f to Color(0x59FF281E), 0.18f to Color.Transparent, 0.82f to Color.Transparent, 1f to Color(0x59FF281E)))
        },
    )
}

private class Puff(var x: Float, var y: Float, var r: Float, var vx: Float, var vy: Float, var life: Float, val max: Float, val seed: Float)

/**
 * Soft steam (or grey smoke) rising from the pan. Particles are drawn as radial gradients on a
 * canvas; [intensity] 0…1 controls how many puffs rise and how opaque they are.
 */
@Composable
fun Steam(intensity: Float, smoke: Boolean, originY: Float, modifier: Modifier = Modifier, animate: Boolean = true) {
    val level by rememberUpdatedState(intensity)
    val puffs = remember { ArrayList<Puff>() }
    val tick = remember { mutableLongStateOf(0L) }
    val random = remember { Random(7) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        fun spawn(age: Float = 0f) {
            val max = 3.5f + random.nextFloat() * 3f
            puffs += Puff(
                x = w * (0.28f + random.nextFloat() * 0.44f), y = h * (originY + random.nextFloat() * 0.05f),
                r = w * (0.05f + random.nextFloat() * 0.06f), vx = w * (random.nextFloat() - 0.5f) * 0.02f,
                vy = -h * (0.035f + random.nextFloat() * 0.03f), life = age, max = max, seed = random.nextFloat() * 6f,
            )
        }
        if (!animate) {
            // Static preview: a settled plume, generated once.
            if (puffs.isEmpty()) {
                repeat((level * 26).toInt()) { spawn(random.nextFloat() * 3f) }
                puffs.forEach { it.y += it.vy * it.life; it.x += it.vx * it.life; it.r *= 1f + it.life * 0.35f }
            }
        } else {
            LaunchedEffect(Unit) {
                var last = 0L
                var carry = 0f
                while (true) {
                    withFrameNanos { now ->
                        val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                        last = now
                        carry += dt * (1f + level * 9f)
                        while (carry >= 1f && level > 0.02f) { spawn(); carry -= 1f }
                        val it = puffs.iterator()
                        while (it.hasNext()) {
                            val p = it.next()
                            p.life += dt
                            p.x += (p.vx + sin((p.life + p.seed) * 1.7f) * w * 0.012f) * dt
                            p.y += p.vy * dt
                            p.r += w * 0.018f * dt
                            if (p.life > p.max) it.remove()
                        }
                        tick.longValue = now
                    }
                }
            }
        }
        Canvas(Modifier.fillMaxSize().blur(10.dp)) {
            tick.longValue // redraw every frame
            val base = if (smoke) Color(0xFF9A948E) else Color.White
            puffs.forEach { p ->
                val t = p.life / p.max
                val fade = sin((t.coerceIn(0f, 1f)) * PI.toFloat())
                val a = fade * (if (smoke) 0.34f else 0.16f) * (0.35f + level * 0.65f)
                if (a > 0.005f) drawCircle(
                    Brush.radialGradient(listOf(base.copy(alpha = a), base.copy(alpha = 0f)), center = Offset(p.x, p.y), radius = p.r),
                    radius = p.r, center = Offset(p.x, p.y),
                )
            }
        }
    }
}

/** What frosted-glass cards need to re-draw the photograph behind themselves. */
data class GlassSource(val backdrop: Backdrop, val origin: Offset, val width: Int, val height: Int, val cooking: Boolean = false)

val LocalGlassSource = compositionLocalOf<GlassSource?> { null }

/**
 * Frosted glass over the kitchen photo: the photo is re-drawn behind the card, aligned to the
 * screen, blurred and tinted (like a UIVisualEffectView). Without a [LocalGlassSource] it falls
 * back to a plain translucent tint.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    tint: Color = KitchenColors.Glass,
    border: Color = KitchenColors.GlassBorder,
    blur: Dp = 26.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val source = LocalGlassSource.current
    var position by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.onGloballyPositioned { position = it.positionInRoot() }.clip(shape)) {
        if (source != null && source.width > 0 && source.height > 0) {
            Box(
                Modifier.matchParentSize().clipToBounds().layout { measurable, constraints ->
                    val placeable = measurable.measure(Constraints.fixed(source.width, source.height))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place((source.origin.x - position.x).roundToInt(), (source.origin.y - position.y).roundToInt())
                    }
                },
            ) {
                Image(
                    painterResource(stageImages.getValue(source.backdrop)), null,
                    Modifier.fillMaxSize().stagePlacement(source.cooking).blur(blur),
                    contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
                )
            }
        }
        Box(Modifier.matchParentSize().background(tint))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0x14FFFFFF), Color(0x00FFFFFF)))).border(1.dp, border, shape))
        content()
    }
}

/** Aspect ratio (width / height) of the kitchen photographs. */
private const val PHOTO_RATIO = 853f / 1844f
/** Vertical position of the pan's centre inside the photographs. */
private const val PAN_CENTER = 0.49f

/**
 * Places a kitchen photo (drawn with Crop + TopCenter) so the pan sits in the free band between
 * the reading and the cards: slightly reduced, pan centre at 51 % of the screen. The cooking view
 * instead zooms in behind its blur. Glass cards use the same placement so their blur lines up.
 */
fun Modifier.stagePlacement(cooking: Boolean): Modifier = this.graphicsLayer {
    if (cooking) {
        scaleX = 1.18f; scaleY = 1.18f
    } else {
        val w = size.width; val h = size.height
        if (w > 0f && h > 0f) {
            val imageHeight = maxOf(h, w / PHOTO_RATIO)
            val scale = 0.86f
            transformOrigin = TransformOrigin(0.5f, 0f)
            scaleX = scale; scaleY = scale
            translationY = 0.51f * h - scale * PAN_CENTER * imageHeight
        }
    }
}
