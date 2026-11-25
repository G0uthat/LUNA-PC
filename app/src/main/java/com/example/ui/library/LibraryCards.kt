package com.example.ui.library

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.LibraryDocumentItem
import com.example.data.local.PlaylistEntity
import com.example.engine.BitmapDecodeUtils
import com.example.ui.components.TactileCard
import com.example.ui.components.TactileProgressBar
import com.example.ui.theme.LocalFolioPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

private val PosterShape = RoundedCornerShape(18.dp)
private val PillBadgeShape = RoundedCornerShape(8.dp)
private val SeriesCardShape = RoundedCornerShape(20.dp)
private val SeriesBadgeShape = RoundedCornerShape(10.dp)
private val ContinueCoverShape = RoundedCornerShape(14.dp)

private val SeriesScrimBrush = Brush.verticalGradient(
    colors = listOf(Color.Black.copy(alpha = 0.16f), Color.Black.copy(alpha = 0.66f))
)

internal object CoverBitmapCache {
    private val cache = object : LruCache<String, Bitmap>(com.example.engine.MemoryPressureManager.coverCacheMaxKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    init {
        com.example.engine.MemoryPressureManager.registerTrimmer("cover_bitmap_cache") { level ->
            com.example.engine.MemoryPressureManager.trimLruCache(cache, level)
        }
        com.example.engine.LumaDiskCacheManager.registerRamCacheClearHook {
            clear()
        }
    }

    fun clear() {
        cache.evictAll()
    }

    fun get(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val bmp = cache.get(path)
        return if (bmp != null && !bmp.isRecycled) bmp else null
    }

    fun put(path: String, bitmap: Bitmap) {
        cache.put(path, bitmap)
    }

    fun preload(path: String?) {
        if (path.isNullOrBlank() || get(path) != null) return
        val resolvedPath = com.example.engine.CoverManager.resolveGridCoverPath(path) ?: return
        val file = File(resolvedPath)
        if (!file.exists()) return
        runCatching {
            BitmapDecodeUtils.decodeSampledFile(
                path = file.absolutePath,
                reqWidth = com.example.engine.MemoryPressureManager.coverDecodeWidth(),
                reqHeight = com.example.engine.MemoryPressureManager.coverDecodeHeight(),
                preferRgb565 = true
            )?.also { put(path, it) }
        }
    }
}

@Composable
internal fun rememberDecodedCoverBitmap(
    coverPath: String?,
    preferThumbnail: Boolean = true
): Bitmap? {
    val cached = remember(coverPath) { CoverBitmapCache.get(coverPath) }
    val bitmap by produceState<Bitmap?>(initialValue = cached, key1 = coverPath, key2 = preferThumbnail) {
        if (cached != null) {
            value = cached
            return@produceState
        }
        value = if (coverPath.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val effectivePath = if (preferThumbnail) {
                        com.example.engine.CoverManager.resolveGridCoverPath(coverPath) ?: coverPath
                    } else {
                        coverPath
                    }
                    val file = File(effectivePath)
                    if (file.exists()) {
                        BitmapDecodeUtils.decodeSampledFile(
                            path = file.absolutePath,
                            reqWidth = com.example.engine.MemoryPressureManager.coverDecodeWidth(),
                            reqHeight = com.example.engine.MemoryPressureManager.coverDecodeHeight(),
                            preferRgb565 = true
                        )?.also { decoded ->
                            CoverBitmapCache.put(coverPath, decoded)
                        }
                    } else {
                        null
                    }
                }.getOrNull()
            }
        }
    }
    return bitmap
}

/**
 * Pure Steam-style vertical cover poster tile (2:3 portrait aspect ratio).
 * Tap to open; Long-press to launch the floating book options window.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SteamCoverBookTile(
    doc: LibraryDocumentItem,
    onOpen: () -> Unit,
    onLongPress: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val coverBitmap = rememberDecodedCoverBitmap(doc.coverImagePath)
    val coverGradient = remember(doc.coverColorHex) {
        val coverColor = Color(doc.coverColorHex.toInt())
        val coverColorDeep = lerp(coverColor, Color.Black, 0.36f)
        Brush.verticalGradient(colors = listOf(coverColor, coverColorDeep))
    }
    val imageBitmap = remember(coverBitmap) { coverBitmap?.asImageBitmap() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("book_card_${doc.id}"),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .shadow(
                    elevation = when {
                        com.example.engine.MemoryPressureManager.isLowRamDevice -> 2.dp
                        palette.isDark -> 4.dp
                        else -> 8.dp
                    },
                    shape = PosterShape,
                    ambientColor = Color.Black.copy(alpha = 0.24f)
                )
                .clip(PosterShape)
                .background(coverGradient)
                .border(1.dp, palette.border.copy(alpha = 0.7f), PosterShape)
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = onLongPress
                )
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Carátula de ${doc.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = doc.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Discreet format / genre pill top-left
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(9.dp)
                    .clip(PillBadgeShape)
                    .background(Color.Black.copy(alpha = 0.56f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = doc.genre.ifBlank { doc.badgeTag.ifBlank { doc.format } },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Sleek reading progress strip along bottom of poster
            if (doc.progressPercent > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(doc.progressPercent.coerceIn(0f, 1f))
                            .height(5.dp)
                            .background(palette.accent)
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 2.dp)) {
            Text(
                text = doc.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = doc.author,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (doc.progressPercent > 0f) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${(doc.progressPercent * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent
                    )
                }
            }
        }
    }
}

/**
 * Spotify/Steam inspired Series card showing preview artwork from its books + title + book count.
 */
@Composable
internal fun SeriesSteamCard(
    series: PlaylistEntity,
    memberCount: Int,
    leadCoverPath: String?,
    onClick: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val accentGradient = remember(series.accentColorHex) {
        val accent = Color(series.accentColorHex.toInt())
        val accentDeep = lerp(accent, Color.Black, 0.42f)
        Brush.linearGradient(colors = listOf(accent, accentDeep))
    }
    val leadCover = rememberDecodedCoverBitmap(leadCoverPath)
    val leadImageBitmap = remember(leadCover) { leadCover?.asImageBitmap() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("playlist_card_${series.id}"),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.86f)
                .shadow(if (palette.isDark) 4.dp else 7.dp, SeriesCardShape)
                .clip(SeriesCardShape)
                .background(accentGradient)
                .border(1.dp, palette.border.copy(alpha = 0.65f), SeriesCardShape)
                .clickable(onClick = onClick)
        ) {
            if (leadImageBitmap != null) {
                Image(
                    bitmap = leadImageBitmap,
                    contentDescription = series.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SeriesScrimBrush)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier
                        .size(56.dp)
                        .align(Alignment.Center)
                )
            }

            // Book count badge top-left
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(SeriesBadgeShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "$memberCount ${if (memberCount == 1) "libro" else "libros"}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = series.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (series.description.isNotBlank()) {
                    Text(
                        text = series.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun ContinueReadingSoftBanner(
    doc: LibraryDocumentItem,
    onClick: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val coverBitmap = rememberDecodedCoverBitmap(doc.coverImagePath)
    val coverImageBitmap = remember(coverBitmap) { coverBitmap?.asImageBitmap() }
    val coverColor = remember(doc.coverColorHex) { Color(doc.coverColorHex.toInt()) }
    val remainingWords = (doc.totalWords * (1f - doc.progressPercent)).roundToInt().coerceAtLeast(0)
    val minsLeft = max(1, remainingWords / 210)

    TactileCard(
        onClick = onClick,
        containerColor = palette.surface,
        borderColor = palette.border,
        shadowColor = palette.borderShadow,
        cornerRadius = 24.dp,
        lipDepth = 2.dp,
        modifier = Modifier.testTag("continue_reading_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 60.dp, height = 86.dp)
                    .clip(ContinueCoverShape)
                    .background(coverColor)
            ) {
                if (coverImageBitmap != null) {
                    Image(
                        bitmap = coverImageBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "CONTINUAR LEYENDO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.accent,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = doc.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${doc.author} · ~${minsLeft} min restantes",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TactileProgressBar(
                        progress = doc.progressPercent,
                        height = 6.dp,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${(doc.progressPercent * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.inkMuted
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(palette.accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Continuar lectura",
                    tint = palette.onAccent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
