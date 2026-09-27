package com.smartcooking.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartcooking.app.ui.theme.CookX
import com.smartcooking.app.ui.theme.NumericFont

/*
 * Apple-style building blocks shared by the redesigned screens: soft white cards, grouped list
 * sections, pills and rounded numerals. They only use theme tokens, so they work in light and dark.
 */

/** A white rounded card with a very soft shadow, the base surface of the light theme. */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    color: Color = CookX.Surface,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .shadow(if (CookX.isDark) 0.dp else 8.dp, shape, ambientColor = CookX.Shadow, spotColor = CookX.Shadow)
            .clip(shape)
            .background(color)
            .then(if (CookX.isDark) Modifier.border(1.dp, CookX.Border, shape) else Modifier)
            .pressable(onClick)
            .padding(padding),
        content = content,
    )
}

/** Card title row with an optional "查看全部 >" link, as in the Apple Home reference. */
@Composable
fun CardTitle(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CookX.Text, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Row(Modifier.clip(RoundedCornerShape(8.dp)).pressable(onAction).padding(vertical = 4.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(action, fontSize = 13.sp, color = CookX.TextSecondary)
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = CookX.TextTertiary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Rounded geometric numerals with a small unit, e.g. "180°C", "6 种". */
@Composable
fun NumberText(
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    size: TextUnit = 24.sp,
    color: Color = CookX.Text,
    unitColor: Color = color,
    weight: FontWeight = FontWeight.SemiBold,
    superscriptUnit: Boolean = false,
) {
    Row(modifier, verticalAlignment = if (superscriptUnit) Alignment.Top else Alignment.Bottom) {
        if (value == "--") {
            // Unknown reading: a quiet placeholder instead of two heavy bars.
            Text("— —", fontSize = size * 0.5f, fontWeight = FontWeight.Light, color = color.copy(alpha = 0.5f), lineHeight = size * 1.05f, letterSpacing = 2.sp)
            return@Row
        }
        Text(value, fontFamily = NumericFont, fontSize = size, fontWeight = weight, color = color, lineHeight = size * 1.05f, letterSpacing = (-0.5).sp)
        if (unit != null) {
            Text(
                unit,
                fontSize = size * (if (superscriptUnit) 0.42f else 0.46f),
                fontWeight = FontWeight.Medium,
                color = unitColor,
                modifier = Modifier.padding(start = 2.dp, top = if (superscriptUnit) (size.value * 0.12f).dp else 0.dp, bottom = if (superscriptUnit) 0.dp else (size.value * 0.1f).dp),
            )
        }
    }
}

/** Small rounded label, used for status and day counters ("1 天"). */
@Composable
fun Pill(text: String, fg: Color, bg: Color, modifier: Modifier = Modifier, icon: ImageVector? = null, dot: Boolean = false, fontSize: TextUnit = 12.sp) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(fg))
            Spacer(Modifier.width(6.dp))
        }
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = fg, fontSize = fontSize, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Thin rounded progress track. */
@Composable
fun MiniBar(fraction: Float, modifier: Modifier = Modifier, track: Color = Color(0x29FFFFFF), fill: Brush, height: Dp = 6.dp) {
    Box(modifier.height(height).clip(RoundedCornerShape(50)).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(RoundedCornerShape(50)).background(fill))
    }
}

/** Circle with an icon, e.g. the orange "go" button or the advice icon. */
@Composable
fun IconCircle(icon: ImageVector, size: Dp, bg: Color, fg: Color, modifier: Modifier = Modifier, iconSize: Dp = size * 0.5f, contentDescription: String? = null) {
    Box(modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription, tint = fg, modifier = Modifier.size(iconSize))
    }
}

/** An iOS-style grouped list: white rounded container with hairline separators between rows. */
@Composable
fun GroupedList(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    SoftCard(modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp), content = content)
}

/** One row of a [GroupedList]: tinted icon, title, subtitle, trailing value and chevron. */
@Composable
fun GroupedRow(
    title: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    badge: Int? = null,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).pressable(onClick).padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = if (CookX.isDark) 0.22f else 0.13f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = CookX.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = CookX.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (value != null) Text(value, fontSize = 13.sp, color = CookX.TextSecondary, maxLines = 1)
            if (badge != null && badge > 0) {
                Box(Modifier.padding(start = 6.dp).clip(RoundedCornerShape(50)).background(CookX.Danger).padding(horizontal = 7.dp, vertical = 1.dp)) {
                    Text("$badge", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (onClick != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = CookX.TextTertiary, modifier = Modifier.padding(start = 4.dp).size(20.dp))
        }
        if (divider) Box(Modifier.padding(start = 46.dp).fillMaxWidth().height(0.6.dp).background(CookX.Border))
    }
}

/** Horizontal gap helper that reads better in dense rows. */
@Composable
fun Gap(width: Dp) = Spacer(Modifier.width(width))

/** Vertical gap helper. */
@Composable
fun VGap(height: Dp) = Spacer(Modifier.height(height))

/** Section heading outside cards ("食材分类"), left aligned with card content. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CookX.TextSecondary, modifier = modifier.padding(start = 6.dp, bottom = 6.dp))
}

/** Box that fills its parent width with a fixed aspect, handy for image headers. */
@Composable
fun AspectBox(ratio: Float, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxWidth().aspectRatio(ratio), content = content)
}

/** Arrangement shortcut used by stacked cards. */
val CardSpacing: Arrangement.Vertical = Arrangement.spacedBy(12.dp)
