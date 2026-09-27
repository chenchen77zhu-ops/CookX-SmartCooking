package com.smartcooking.app.ui.components

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Top system-bar padding for full-bleed pages (kept in one place so previews can simulate it). */
@Composable
fun Modifier.topInset(): Modifier = this.statusBarsPadding()
