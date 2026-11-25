package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileDownloadDone
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalFolioPalette
import com.example.ui.theme.LocalLumaMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class LumaActionAnimType {
    BOOKMARK_ADDED,
    BOOKMARK_REMOVED,
    DELETED,
    SAVED,
    UPDATED,
    TRANSLATED,
    EXPORTED,
    IMPORTED,
    ERROR
}

data class LumaFeedbackEvent(
    val id: Long = System.nanoTime(),
    val type: LumaActionAnimType,
    val errorDetail: String? = null
)

/**
 * Replaces clunky Material Snackbars with tactile, aesthetic micro-animations:
 * - Bookmarking: silk notched ribbon gliding smoothly from the top-right page edge with a sparkle pulse.
 * - Deleting / Saving / Updating / Translating / Exporting / Importing: clean icon-only radial pulse emblem.
 * - Errors only: minimal frosted pill matching Luma's palette.
 */
@Composable
fun LumaActionFeedbackOverlay(
    event: LumaFeedbackEvent?,
    onConsumed: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (event == null) return

    Box(modifier = modifier.fillMaxSize()) {
        when (event.type) {
            LumaActionAnimType.BOOKMARK_ADDED,
            LumaActionAnimType.BOOKMARK_REMOVED -> {
                BookmarkRibbonDropAnimation(
                    eventId = event.id,
                    isAdded = event.type == LumaActionAnimType.BOOKMARK_ADDED,
                    onFinished = onConsumed,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(end = 22.dp)
                )
            }

            LumaActionAnimType.ERROR -> {
                ErrorFrostedPillAnimation(
                    eventId = event.id,
                    message = event.errorDetail ?: "No se pudo completar la acción",
                    onFinished = onConsumed,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 64.dp, start = 24.dp, end = 24.dp)
                )
            }

            else -> {
                val (icon, tint) = when (event.type) {
                    LumaActionAnimType.DELETED -> Icons.Rounded.DeleteSweep to Color(0xFFE11D48)
                    LumaActionAnimType.SAVED -> Icons.Rounded.Check to LocalFolioPalette.current.accent
                    LumaActionAnimType.UPDATED -> Icons.Rounded.AutoAwesome to LocalFolioPalette.current.accent
                    LumaActionAnimType.TRANSLATED -> Icons.Rounded.Translate to LocalFolioPalette.current.accent
                    LumaActionAnimType.EXPORTED,
                    LumaActionAnimType.IMPORTED -> Icons.Rounded.FileDownloadDone to Color(0xFF10B981)
                    else -> Icons.Rounded.Check to LocalFolioPalette.current.accent
                }
                TactileIconPulseAnimation(
                    eventId = event.id,
                    icon = icon,
                    accentTint = tint,
                    isDeleteStyle = event.type == LumaActionAnimType.DELETED,
                    onFinished = onConsumed,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 70.dp)
                )
            }
        }
    }
}

/**
 * Authentic silk bookmark ribbon animation dropping from the top-right edge of the page
 * with radial sparkles when bookmarking, and retracting gracefully upward when removing.
 */
@Composable
private fun BookmarkRibbonDropAnimation(
    eventId: Long,
    isAdded: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val motion = LocalLumaMotion.current
    val slideY = remember(eventId) { Animatable(if (isAdded) -90f else 0f) }
    val alphaAnim = remember(eventId) { Animatable(if (isAdded) 0f else 1f) }
    val burstProgress = remember(eventId) { Animatable(0f) }

    LaunchedEffect(eventId) {
        if (isAdded) {
            launch { alphaAnim.animateTo(1f, motion.tweenSpec(145)) }
            launch {
                slideY.animateTo(
                    targetValue = 0f,
                    animationSpec = motion.expressiveSpring()
                )
            }
            launch {
                delay(motion.duration(80, minFrames = 2).toLong())
                burstProgress.animateTo(1f, motion.emphasizedTween(480))
            }
            delay(800)
            launch { slideY.animateTo(-72f, motion.exitTween(240)) }
            alphaAnim.animateTo(0f, motion.exitTween(210))
        } else {
            launch {
                burstProgress.animateTo(1f, motion.tweenSpec(340))
            }
            launch { slideY.animateTo(-86f, motion.exitTween(320)) }
            alphaAnim.animateTo(0f, motion.exitTween(290))
        }
        onFinished()
    }

    val ribbonColorTop = if (isAdded) palette.accent else palette.inkMuted
    val ribbonColorBottom = if (isAdded) palette.accentShadow else palette.inkMuted.copy(alpha = 0.7f)

    Box(
        modifier = modifier
            .size(width = 58.dp, height = 84.dp)
            .graphicsLayer {
                translationY = slideY.value
                alpha = alphaAnim.value
            }
            .testTag("bookmark_ribbon_animation"),
        contentAlignment = Alignment.TopCenter
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val ribW = w * 0.56f
            val ribH = h * 0.76f
            val left = (w - ribW) / 2f
            val right = left + ribW
            val notchY = ribH - ribW * 0.34f

            // Notched silk ribbon silhouette
            val ribbonPath = Path().apply {
                moveTo(left, 0f)
                lineTo(right, 0f)
                lineTo(right, ribH)
                lineTo(w / 2f, notchY)
                lineTo(left, ribH)
                close()
            }

            // Subtle ribbon drop shadow
            drawContext.canvas.save()
            drawContext.canvas.translate(0f, 3.dp.toPx())
            drawPath(path = ribbonPath, color = Color.Black.copy(alpha = 0.16f))
            drawContext.canvas.restore()

            drawPath(
                path = ribbonPath,
                brush = Brush.verticalGradient(listOf(ribbonColorTop, ribbonColorBottom))
            )

            // Crisp radial sparkle ticks when added
            val bp = burstProgress.value
            if (isAdded && bp > 0.02f && bp < 0.98f) {
                val center = Offset(w / 2f, ribH * 0.48f)
                val fade = (1f - bp).coerceIn(0f, 1f)
                val innerR = (10.dp.toPx() + bp * 16.dp.toPx())
                val outerR = innerR + (6.dp.toPx() * fade)
                for (i in 0 until 6) {
                    val angle = Math.toRadians((i * 60.0) + 15.0)
                    val sx = center.x + (cos(angle) * innerR).toFloat()
                    val sy = center.y + (sin(angle) * innerR).toFloat()
                    val ex = center.x + (cos(angle) * outerR).toFloat()
                    val ey = center.y + (sin(angle) * outerR).toFloat()
                    drawLine(
                        color = palette.accent.copy(alpha = fade * 0.85f),
                        start = Offset(sx, sy),
                        end = Offset(ex, ey),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.Rounded.Bookmark,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier
                .padding(top = 14.dp)
                .size(16.dp)
        )
    }
}

/**
 * Pure icon-driven tactile micro-animation (expanding soft radial wave + spring-pop icon).
 * Zero text banner chrome!
 */
@Composable
private fun TactileIconPulseAnimation(
    eventId: Long,
    icon: ImageVector,
    accentTint: Color,
    isDeleteStyle: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val motion = LocalLumaMotion.current
    val scaleAnim = remember(eventId) { Animatable(0.48f) }
    val alphaAnim = remember(eventId) { Animatable(0f) }
    val rippleProgress = remember(eventId) { Animatable(0f) }

    LaunchedEffect(eventId) {
        launch { alphaAnim.animateTo(1f, motion.tweenSpec(145)) }
        launch {
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec = motion.expressiveSpring()
            )
        }
        launch {
            rippleProgress.animateTo(1f, motion.emphasizedTween(620))
        }
        delay(660)
        launch {
            scaleAnim.animateTo(
                targetValue = if (isDeleteStyle) 0.58f else 0.84f,
                animationSpec = motion.exitTween(210)
            )
        }
        alphaAnim.animateTo(0f, motion.exitTween(200))
        onFinished()
    }

    Box(
        modifier = modifier
            .size(86.dp)
            .graphicsLayer { alpha = alphaAnim.value }
            .testTag("luma_micro_feedback_pulse"),
        contentAlignment = Alignment.Center
    ) {
        // Expanding radial ripple ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            val rp = rippleProgress.value
            val radius = (size.minDimension * 0.24f) + (size.minDimension * 0.26f * rp)
            val ringAlpha = (1f - rp).coerceIn(0f, 1f) * 0.45f
            drawCircle(
                color = accentTint.copy(alpha = ringAlpha),
                radius = radius,
                style = Stroke(width = (2.4.dp.toPx() * (1f - rp * 0.5f)))
            )
        }

        // Tactile frosted emblem circle
        Box(
            modifier = Modifier
                .size(52.dp)
                .graphicsLayer {
                    val s = scaleAnim.value
                    scaleX = s
                    scaleY = s
                }
                .shadow(12.dp, CircleShape, ambientColor = accentTint.copy(alpha = 0.35f))
                .clip(CircleShape)
                .background(palette.surface.copy(alpha = 0.96f))
                .border(1.4.dp, accentTint.copy(alpha = 0.42f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentTint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Minimal frosted floating pill used ONLY for actual errors (replaces grey Android Snackbars).
 */
@Composable
private fun ErrorFrostedPillAnimation(
    eventId: Long,
    message: String,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val motion = LocalLumaMotion.current
    val scaleAnim = remember(eventId) { Animatable(0.88f) }
    val alphaAnim = remember(eventId) { Animatable(0f) }

    LaunchedEffect(eventId) {
        launch { alphaAnim.animateTo(1f, motion.tweenSpec(165)) }
        scaleAnim.animateTo(
            targetValue = 1f,
            animationSpec = motion.modalSpring()
        )
        delay(2300)
        launch { scaleAnim.animateTo(0.92f, motion.exitTween(180)) }
        alphaAnim.animateTo(0f, motion.exitTween(180))
        onFinished()
    }

    val pillShape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .graphicsLayer {
                val s = scaleAnim.value
                scaleX = s
                scaleY = s
                alpha = alphaAnim.value
            }
            .widthIn(max = 320.dp)
            .shadow(12.dp, pillShape, ambientColor = Color.Black.copy(alpha = 0.22f))
            .clip(pillShape)
            .background(palette.surface.copy(alpha = 0.96f))
            .border(1.dp, Color(0xFFE11D48).copy(alpha = 0.38f), pillShape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("luma_error_feedback_pill"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = Color(0xFFE11D48),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = palette.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
