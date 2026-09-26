package com.smartcooking.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.smartcooking.app.core.LocalAppContainer
import com.smartcooking.app.data.FoodCatalog
import com.smartcooking.app.ui.theme.CookX
import com.smartcooking.app.ui.theme.CookXShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ingredient photo: server image when provided, otherwise the bundled illustration, else an icon. */
@Composable
fun IngredientImage(name: String?, imageUrl: String?, modifier: Modifier = Modifier, iconSize: Dp = 26.dp) {
    val container = LocalAppContainer.current
    val bundled = FoodCatalog.info(name).image
    Box(modifier.background(CookX.SurfaceMuted), contentAlignment = Alignment.Center) {
        val placeholder = @Composable { Icon(Icons.Outlined.Restaurant, null, tint = CookX.TextTertiary, modifier = Modifier.size(iconSize)) }
        val remote = container.sessions.resolveUrl(imageUrl)
        when {
            remote != null -> SubcomposeAsyncImage(
                model = remote, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                error = { if (bundled != null) Image(painterResource(bundled), name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else placeholder() },
                loading = { placeholder() },
            )
            bundled != null -> Image(painterResource(bundled), name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else -> placeholder()
        }
    }
}

/** Loads authorised media (avatars, community photos) through the API client with the bearer token. */
@Composable
fun rememberPrivateBitmap(path: String?): Pair<ImageBitmap?, Boolean> {
    val container = LocalAppContainer.current
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(path) { mutableStateOf(false) }
    LaunchedEffect(path) {
        if (path.isNullOrBlank()) return@LaunchedEffect
        try {
            val bytes = container.api.bytes(path)
            bitmap = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
            failed = bitmap == null
        } catch (e: Exception) { failed = true }
    }
    return bitmap to failed
}

/** Private avatars live behind `/api/v3/profile/media/{id}` or the legacy avatar endpoint. */
fun avatarMediaPath(path: String?): String? = when {
    path.isNullOrBlank() -> null
    path.startsWith("/api/v3/profile/media/") -> path
    path.startsWith("/static/uploads/avatars/") -> "/api/v3/profile/avatar/legacy"
    else -> null
}

@Composable
fun Avatar(path: String?, size: Dp, modifier: Modifier = Modifier) {
    val (bitmap, _) = rememberPrivateBitmap(avatarMediaPath(path))
    Box(modifier.size(size).clip(CookXShapes.Pill).background(CookX.Mint), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap, "头像", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Outlined.Person, null, tint = CookX.Primary, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun CommunityImage(id: String, modifier: Modifier = Modifier) {
    val (bitmap, failed) = rememberPrivateBitmap("/api/v3/community/media/$id")
    Box(modifier.clip(CookXShapes.Tile).background(CookX.SurfaceMuted), contentAlignment = Alignment.Center) {
        when {
            bitmap != null -> Image(bitmap, "成员主动分享的菜品图片", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            failed -> Icon(Icons.Outlined.BrokenImage, "图片读取失败", tint = CookX.TextTertiary)
            else -> CircularProgressIndicator(Modifier.size(22.dp), color = CookX.Primary, strokeWidth = 2.dp)
        }
    }
}
