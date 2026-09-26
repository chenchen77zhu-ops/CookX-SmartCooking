package com.smartcooking.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartcooking.app.ui.theme.CookX
import com.smartcooking.app.ui.theme.CookXShapes

@Composable
private fun ButtonContent(text: String, icon: ImageVector?, loading: Boolean, trailingIcon: ImageVector?, color: Color) {
    if (loading) {
        CircularProgressIndicator(Modifier.size(16.dp), color = color, strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
    } else if (icon != null) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
    }
    Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (trailingIcon != null && !loading) {
        Spacer(Modifier.width(5.dp))
        Icon(trailingIcon, null, Modifier.size(16.dp))
    }
}

/** Filled forest-green action. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    container: Color = CookX.Primary,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = CookXShapes.Button,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = Color.White, disabledContainerColor = container.copy(alpha = 0.4f), disabledContentColor = Color.White.copy(alpha = 0.85f)),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    ) { ButtonContent(text, icon, loading, trailingIcon, Color.White) }
}

/** Warm orange call to action with gradient, used for the single most important action on a screen. */
@Composable
fun AccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Box(
        modifier
            .shadow(if (enabled) 10.dp else 0.dp, CookXShapes.Button, spotColor = Color(0x66D86B35), ambientColor = Color(0x33D86B35))
            .clip(CookXShapes.Button)
            .background(if (enabled) CookX.accentBrush else androidx.compose.ui.graphics.SolidColor(CookX.Accent.copy(alpha = 0.45f))),
    ) {
        Button(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            shape = CookXShapes.Button,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White, disabledContainerColor = Color.Transparent, disabledContentColor = Color.White),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        ) { ButtonContent(text, icon, loading, trailingIcon, Color.White) }
    }
}

/** Soft mint secondary action. */
@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    tone: Tone = Tone.Green,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.defaultMinSize(minHeight = 44.dp),
        shape = CookXShapes.Button,
        colors = ButtonDefaults.buttonColors(containerColor = tone.bg, contentColor = tone.fg, disabledContainerColor = tone.bg.copy(alpha = 0.5f), disabledContentColor = tone.fg.copy(alpha = 0.45f)),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        elevation = null,
    ) { ButtonContent(text, icon, loading, null, tone.fg) }
}

@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    color: Color = CookX.Primary,
    loading: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.defaultMinSize(minHeight = 44.dp),
        shape = CookXShapes.Button,
        border = BorderStroke(1.dp, if (enabled) color.copy(alpha = 0.32f) else color.copy(alpha = 0.12f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) { ButtonContent(text, icon, loading, trailingIcon, color) }
}

/** Compact text action such as "查看全部 →". */
@Composable
fun LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = CookX.TextSecondary, enabled: Boolean = true) {
    TextButton(onClick = onClick, modifier = modifier.height(36.dp), enabled = enabled, contentPadding = PaddingValues(horizontal = 6.dp), colors = ButtonDefaults.textButtonColors(contentColor = color)) {
        Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
        if (icon != null) {
            Spacer(Modifier.width(2.dp))
            Icon(icon, null, Modifier.size(14.dp))
        }
    }
}

/** Glassy icon button on dark hero backgrounds (back, notifications). */
@Composable
fun HeroIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, badge: Boolean = false) {
    Box(
        modifier
            .size(42.dp)
            .clip(CookXShapes.Input)
            .background(CookX.OnDarkFaint)
            .pressable(onClick)
            .then(Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(21.dp))
        if (badge) Box(Modifier.align(Alignment.TopEnd).padding(9.dp).size(8.dp).clip(CookXShapes.Pill).background(CookX.Accent))
    }
}

/** A horizontal row of buttons that wraps on narrow screens. */
@Composable
fun ActionRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
fun InlineRow(modifier: Modifier = Modifier, spacing: Int = 8, content: @Composable () -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(spacing.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}
