package com.smartcooking.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartcooking.app.ui.theme.CookX
import com.smartcooking.app.ui.theme.CookXShapes
import com.smartcooking.app.ui.theme.KickerStyle

/** Press feedback shared by tappable cards: a subtle 0.98 scale like the web `:active` state. */
@Composable
fun Modifier.pressable(onClick: (() -> Unit)?, enabled: Boolean = true): Modifier {
    if (onClick == null) return this
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    return this.scale(if (pressed) 0.98f else 1f).clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), enabled = enabled, onClick = onClick)
}

/** White card with the CookX hairline border and soft green-tinted shadow. */
@Composable
fun CookXCard(
    modifier: Modifier = Modifier,
    shape: Shape = CookXShapes.Card,
    padding: PaddingValues = PaddingValues(18.dp),
    color: Color = CookX.Surface,
    elevation: Dp = 10.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .shadow(elevation, shape, ambientColor = Color(0x331C3028), spotColor = Color(0x1F1C3028))
            .clip(shape)
            .background(color)
            .border(1.dp, CookX.Border, shape)
            .pressable(onClick)
            .padding(padding),
        content = content,
    )
}

/** Dark forest surface used for hero headers and the CookX Sense card. */
@Composable
fun DarkPanel(
    modifier: Modifier = Modifier,
    brush: Brush = CookX.senseBrush,
    shape: Shape = CookXShapes.Card,
    padding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .shadow(14.dp, shape, ambientColor = Color(0x55073127), spotColor = Color(0x44073127))
            .clip(shape)
            .background(brush)
            .border(1.dp, CookX.OnDarkBorder.copy(alpha = 0.12f), shape)
            .padding(padding),
        content = content,
    )
}

@Composable
fun Wordmark(fontSize: TextUnit = 28.sp, color: Color = Color.White, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append("Cook")
            withStyle(SpanStyle(color = CookX.Accent)) { append("X") }
        },
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.8).sp,
    )
}

@Composable
fun Kicker(text: String, color: Color = CookX.Gold, modifier: Modifier = Modifier) {
    Text(text, style = KickerStyle, color = color, modifier = modifier)
}

enum class Tone(val fg: Color, val bg: Color) {
    Green(CookX.Primary, CookX.Mint),
    Fresh(CookX.SuccessBright, Color(0xFFE9F5E8)),
    Warm(CookX.Accent, CookX.AccentBg),
    Gold(Color(0xFFB7791F), CookX.WarningBg),
    Danger(CookX.Danger, Color(0xFFFFF0ED)),
    Neutral(CookX.TextSecondary, Color(0xFFF1F0EB)),
    OnDark(Color.White, CookX.OnDarkFaint),
}

/** Rounded-square icon container, e.g. the section icons in cards. */
@Composable
fun IconBadge(icon: ImageVector, tone: Tone = Tone.Green, size: Dp = 40.dp, iconSize: Dp = 20.dp, shape: Shape = CookXShapes.Icon, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(shape).background(tone.bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tone.fg, modifier = Modifier.size(iconSize))
    }
}

/** Gradient icon tile used on the home quick actions. */
@Composable
fun GradientIcon(icon: ImageVector, brush: Brush, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).shadow(6.dp, CookXShapes.Icon, spotColor = Color(0x331C3028)).clip(CookXShapes.Icon).background(brush),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.48f)) }
}

@Composable
fun StatusChip(text: String, tone: Tone = Tone.Green, icon: ImageVector? = null, modifier: Modifier = Modifier, dot: Boolean = false) {
    Row(
        modifier.clip(CookXShapes.Pill).background(tone.bg).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(tone.fg))
            Spacer(Modifier.width(6.dp))
        }
        if (icon != null) {
            Icon(icon, null, tint = tone.fg, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = tone.fg, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Title row for a card or section with an optional trailing action. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            IconBadge(icon, size = 32.dp, iconSize = 17.dp, shape = CookXShapes.Small)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            if (kicker != null) Kicker(kicker, color = CookX.Accent)
            Text(title, style = MaterialTheme.typography.titleLarge, color = CookX.Text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary, modifier = Modifier.padding(top = 2.dp))
        }
        trailing()
    }
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.padding(top = h))

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = CookX.Border) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun BoxWithBackground(modifier: Modifier = Modifier, brush: Brush = SolidColor(CookX.Bg), content: @Composable BoxScope.() -> Unit) {
    Box(modifier.background(brush), content = content)
}

@Composable
fun Labeled(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = CookX.Text) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary)
        Text(value, style = MaterialTheme.typography.titleSmall, color = valueColor)
    }
}
