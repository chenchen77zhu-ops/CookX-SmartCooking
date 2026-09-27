package com.smartcooking.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartcooking.app.ui.theme.CookX
import com.smartcooking.app.ui.theme.CookXShapes
import com.smartcooking.app.ui.theme.NumericFont

/** Deep forest background kept for the sign-in page. */
fun Modifier.heroBackground(): Modifier = this
    .background(CookX.heroBrush)
    .drawBehind {
        drawCircle(
            Brush.radialGradient(listOf(Color(0x334D8B69), Color.Transparent), center = Offset(size.width * 0.85f, 0f), radius = size.width * 0.55f),
            radius = size.width * 0.55f, center = Offset(size.width * 0.85f, 0f),
        )
    }

/**
 * Apple Home–style tab header: the CookX wordmark with trailing actions, then a large title and a
 * quiet subtitle. [bottomOverlap] is kept for source compatibility and only adds spacing.
 */
@Composable
fun HeroHeader(
    modifier: Modifier = Modifier,
    section: String? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomOverlap: Int = 12,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = bottomOverlap.coerceAtMost(16).dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Wordmark(26.sp, color = CookX.Text)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        if (section != null) {
            Spacer(Modifier.height(6.dp))
            LargeTitle(section, subtitle)
        }
        content()
    }
}

/** 34sp page title with an optional one-line subtitle, as on iOS large-title screens. */
@Composable
fun LargeTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.displayLarge, color = CookX.Text)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = CookX.TextSecondary, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * Scaffold for secondary pages (profile sub pages and the collaborative modules): a round back
 * button, a large title and subtitle on the page background, then the content cards.
 */
@Composable
fun SubpageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    listState: LazyListState = rememberLazyListState(),
    heroExtra: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().background(CookX.Bg)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).imePadding(),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item(key = "__hero") {
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(52.dp)) {
                        HeroIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", onBack)
                        Spacer(Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(title, color = CookX.Text, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(horizontal = 4.dp))
                    if (subtitle != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(subtitle, color = CookX.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                    heroExtra()
                }
            }
            item(key = "__overlap") { Spacer(Modifier.height(0.dp)) }
            content()
        }
        if (bottomBar != null) {
            Box(Modifier.fillMaxWidth().background(CookX.Surface).navigationBarsPadding()) { bottomBar() }
        }
    }
}

/** Former hero overlap; headers no longer overlap content, so this only keeps call sites valid. */
@Suppress("UNUSED_PARAMETER")
fun Modifier.overlapHero(amount: androidx.compose.ui.unit.Dp = 24.dp): Modifier = this

/** Standard horizontal page padding for list items inside scaffolds. */
fun Modifier.page(): Modifier = this.padding(horizontal = 16.dp)

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tone: Tone = Tone.Green,
    trailingText: String? = null,
    badge: Int? = null,
    chevron: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().clip(CookXShapes.Tile).pressable(onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconBadge(icon, tone, size = 40.dp, iconSize = 20.dp)
            Spacer(Modifier.width(13.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = CookX.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailingText != null) Text(trailingText, style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary)
        if (badge != null && badge > 0) {
            Box(Modifier.padding(start = 8.dp).clip(CookXShapes.Pill).background(CookX.Danger).padding(horizontal = 7.dp, vertical = 1.dp)) {
                Text("$badge", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (chevron && onClick != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = CookX.TextTertiary, modifier = Modifier.padding(start = 4.dp))
    }
}

/** Metric tile: icon, big number, caption. */
@Composable
fun MetricTile(icon: ImageVector, value: String, unit: String?, label: String, tone: Tone, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CookXShapes.Tile).background(CookX.Surface).then(Modifier).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tone, size = 42.dp, iconSize = 21.dp)
        Spacer(Modifier.width(11.dp))
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, fontFamily = NumericFont, color = CookX.Text, lineHeight = 26.sp)
                if (unit != null) Text(unit, fontSize = 11.sp, color = CookX.TextSecondary, modifier = Modifier.padding(start = 3.dp, bottom = 3.dp))
            }
            Text(label, style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary)
        }
    }
}

/** Bottom sheet with the CookX header style; used for forms that were dialogs on the web. */
@Composable
fun CookXSheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { dismissible })
    ModalBottomSheet(
        onDismissRequest = { if (dismissible) onDismiss() },
        sheetState = state,
        containerColor = CookX.Bg,
        shape = CookXShapes.Large,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 18.dp).padding(bottom = 18.dp)
                .then(Modifier.verticalScrollCompat()),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = CookX.Text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CookX.TextSecondary, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun Modifier.verticalScrollCompat(): Modifier = this.verticalScroll(rememberScrollState())
