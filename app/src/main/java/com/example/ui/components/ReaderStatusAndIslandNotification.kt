package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.CriticalEventKind
import com.example.engine.DynamicIslandEvent
import com.example.engine.DynamicIslandEventManager
import com.example.ui.theme.LocalFolioPalette
import com.example.ui.theme.LocalLumaMotion
import kotlinx.coroutines.launch

private val BatteryPillShape = RoundedCornerShape(50)

/**
 * Ultra-minimal, distraction-free battery-only indicator shown in the reader while
 * Android's system status bar & notification icons are hidden.
 * Observes centralized live battery telemetry from [DynamicIslandEventManager].
 */
@Composable
fun ReaderBatteryOnlyIndicator(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val palette = LocalFolioPalette.current
    val battery by DynamicIslandEventManager.batteryState.collectAsStateWithLifecycle()
    val batteryPct = battery.levelPct
    val isCharging = battery.isCharging

    val fillColor = when {
        isCharging -> Color(0xFF22C55E)
        batteryPct <= 15 -> Color(0xFFEF4444)
        else -> palette.ink.copy(alpha = 0.78f)
    }
    val outlineColor = palette.ink.copy(alpha = 0.54f)

    Row(
        modifier = modifier
            .then(
                if (compact) Modifier else Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
            .testTag("reader_battery_only_indicator"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = "$batteryPct%",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (!isCharging && batteryPct <= 15) Color(0xFFEF4444) else palette.ink.copy(alpha = 0.80f),
            maxLines = 1,
            softWrap = false
        )
        Box(
            modifier = Modifier.size(width = 21.dp, height = 10.5.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val bodyW = size.width - 2.6.dp.toPx()
                val bodyH = size.height
                val strokePx = 1.2.dp.toPx()

                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset(strokePx / 2f, strokePx / 2f),
                    size = Size(bodyW - strokePx, bodyH - strokePx),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    style = Stroke(width = strokePx)
                )

                val capW = 1.8.dp.toPx()
                val capH = bodyH * 0.44f
                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset(bodyW + 0.8.dp.toPx(), (bodyH - capH) / 2f),
                    size = Size(capW, capH),
                    cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                )

                val pad = 2.dp.toPx()
                val innerMaxW = (bodyW - pad * 2f).coerceAtLeast(1f)
                val fillW = (innerMaxW * (batteryPct / 100f)).coerceAtLeast(if (batteryPct > 0) 1.5.dp.toPx() else 0f)
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(pad, pad),
                    size = Size(fillW, (bodyH - pad * 2f).coerceAtLeast(1f)),
                    cornerRadius = CornerRadius(1.6.dp.toPx(), 1.6.dp.toPx())
                )
            }
        }
        if (isCharging) {
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = "Cargando",
                tint = Color(0xFF22C55E),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

/**
 * Self-contained floating capsule bubble for battery telemetry that matches the reader top bar
 * aesthetic and sits beside other floating pills with zero overlap.
 */
@Composable
fun ReaderBatteryPillBubble(
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    Box(
        modifier = modifier
            .shadow(8.dp, BatteryPillShape, ambientColor = Color.Black.copy(alpha = 0.22f))
            .clip(BatteryPillShape)
            .background(palette.surface.copy(alpha = 0.96f))
            .border(1.dp, palette.border, BatteryPillShape)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        ReaderBatteryOnlyIndicator(compact = true)
    }
}

/**
 * Centralized Dynamic Island event banner with buttery GPU-composited motion:
 * - Pure top-center Zoom-In / Zoom-Out with smooth cubic-bezier easing.
 * - Seamless in-place [AnimatedContent] morphing between consecutive queued events.
 * - Tactile swipe-up-to-dismiss gesture with spring snap-back (zero recompositions during drag).
 * - Subtle bottom progress hairline indicating remaining visibility time.
 * - Renders ONLY [DynamicIslandEvent.CriticalState] and [DynamicIslandEvent.AllowedAppAlert].
 */
@Composable
fun DynamicIslandEventBanner(
    event: DynamicIslandEvent?,
    queuedCount: Int = 0,
    onTapEvent: (DynamicIslandEvent) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = LocalLumaMotion.current
    val topOrigin = TransformOrigin(0.5f, 0f)
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { -28.dp.toPx() }
    val coroutineScope = rememberCoroutineScope()
    val dragOffsetY = remember { Animatable(0f) }

    // Keep last non-null reference briefly so exit zoom-out renders smoothly
    var lastEvent by remember { mutableStateOf<DynamicIslandEvent?>(null) }
    if (event != null) {
        lastEvent = event
    }

    LaunchedEffect(event?.id) {
        if (event != null && dragOffsetY.value != 0f) {
            dragOffsetY.snapTo(0f)
        }
    }

    AnimatedVisibility(
        visible = event != null,
        modifier = modifier,
        enter = scaleIn(
            initialScale = 0.64f,
            transformOrigin = topOrigin,
            animationSpec = motion.modalSpring()
        ) + fadeIn(motion.emphasizedTween(185)),
        exit = scaleOut(
            targetScale = 0.64f,
            transformOrigin = topOrigin,
            animationSpec = motion.exitTween(185)
        ) + fadeOut(motion.exitTween(145))
    ) {
        val displayedEvent = event ?: lastEvent ?: return@AnimatedVisibility
        Box(
            modifier = Modifier
                .graphicsLayer {
                    val y = dragOffsetY.value
                    translationY = y
                    alpha = (1f + (y / (dismissThresholdPx.coerceAtMost(-1f) * -2.2f))).coerceIn(0.45f, 1f)
                }
                .pointerInput(displayedEvent.id) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragOffsetY.value <= dismissThresholdPx) {
                                onDismiss()
                            } else {
                                coroutineScope.launch {
                                    dragOffsetY.animateTo(0f, motion.tactileSpring())
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                dragOffsetY.animateTo(0f, motion.tactileSpring())
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val next = (dragOffsetY.value + dragAmount).coerceIn(dismissThresholdPx * 2.1f, 14f)
                            coroutineScope.launch { dragOffsetY.snapTo(next) }
                        }
                    )
                }
        ) {
            AnimatedContent(
                targetState = displayedEvent,
                contentKey = { it.id },
                transitionSpec = {
                    (fadeIn(motion.emphasizedTween(180)) + scaleIn(
                        initialScale = 0.94f,
                        transformOrigin = topOrigin,
                        animationSpec = motion.emphasizedTween(205)
                    )) togetherWith (fadeOut(motion.exitTween(130)) + scaleOut(
                        targetScale = 0.94f,
                        transformOrigin = topOrigin,
                        animationSpec = motion.exitTween(140)
                    )) using SizeTransform(clip = false) { _, _ ->
                        motion.modalSpring()
                    }
                },
                label = "dynamic_island_event_morph"
            ) { current ->
                DynamicIslandEventPillCard(
                    event = current,
                    queuedCount = queuedCount,
                    onTapEvent = onTapEvent,
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@Composable
private fun DynamicIslandEventPillCard(
    event: DynamicIslandEvent,
    queuedCount: Int,
    onTapEvent: (DynamicIslandEvent) -> Unit,
    onDismiss: () -> Unit
) {
    val islandBg = Color(0xFF0C111C)
    val islandFg = Color(0xFFF3F6FC)
    val pillShape = RoundedCornerShape(26.dp)

    val countdown = remember(event.id) { Animatable(1f) }
    LaunchedEffect(event.id) {
        countdown.snapTo(1f)
        countdown.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = event.durationMs.toInt().coerceAtLeast(800),
                easing = LinearEasing
            )
        )
    }

    when (event) {
        is DynamicIslandEvent.CriticalState -> {
            val accent = Color(event.accentColorArgb)
            val isInteractive = event.contentIntent != null || event.packageName.isNotBlank()

            Column(
                modifier = Modifier
                    .widthIn(min = 228.dp, max = 336.dp)
                    .shadow(18.dp, pillShape, ambientColor = accent.copy(alpha = 0.42f))
                    .clip(pillShape)
                    .background(islandBg.copy(alpha = 0.98f))
                    .border(1.2.dp, accent.copy(alpha = 0.58f), pillShape)
                    .clickable {
                        if (isInteractive) onTapEvent(event) else onDismiss()
                    }
                    .testTag("dynamic_island_critical_banner")
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 9.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.20f))
                            .border(1.dp, accent.copy(alpha = 0.52f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = criticalEventIcon(event.kind),
                            contentDescription = event.badgeLabel,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            PulsingCriticalDot(color = accent)
                            Text(
                                text = event.badgeLabel.uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.65.sp,
                                color = accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (queuedCount > 0) {
                                IslandQueueCountChip(count = queuedCount)
                            }
                        }
                        Text(
                            text = event.title,
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = islandFg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (event.subtitle.isNotBlank()) {
                            Text(
                                text = event.subtitle,
                                fontSize = 11.sp,
                                lineHeight = 13.5.sp,
                                color = islandFg.copy(alpha = 0.78f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IslandDismissCircleButton(
                        contentDescription = "Cerrar alerta crítica",
                        onClick = onDismiss
                    )
                }
                IslandCountdownHairline(
                    progressProvider = { countdown.value },
                    color = accent.copy(alpha = 0.68f)
                )
            }
        }

        is DynamicIslandEvent.AllowedAppAlert -> {
            val notif = event.notification
            Column(
                modifier = Modifier
                    .widthIn(min = 220.dp, max = 332.dp)
                    .shadow(16.dp, pillShape, ambientColor = Color.Black.copy(alpha = 0.45f))
                    .clip(pillShape)
                    .background(islandBg.copy(alpha = 0.97f))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), pillShape)
                    .clickable { onTapEvent(event) }
                    .testTag("dynamic_island_notification_banner")
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 9.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (notif.appIcon != null) {
                        Image(
                            bitmap = notif.appIcon.asImageBitmap(),
                            contentDescription = notif.appName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF3E6AE1)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = notif.appName,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF34D399))
                            )
                            Text(
                                text = notif.appName.uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.6.sp,
                                color = Color(0xFF7BA4FF),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (queuedCount > 0) {
                                IslandQueueCountChip(count = queuedCount)
                            }
                        }
                        Text(
                            text = notif.title,
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = islandFg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (notif.text.isNotBlank()) {
                            Text(
                                text = notif.text,
                                fontSize = 11.sp,
                                lineHeight = 13.5.sp,
                                color = islandFg.copy(alpha = 0.78f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IslandDismissCircleButton(
                        contentDescription = "Cerrar notificación",
                        onClick = onDismiss
                    )
                }
                IslandCountdownHairline(
                    progressProvider = { countdown.value },
                    color = Color(0xFF7BA4FF).copy(alpha = 0.52f)
                )
            }
        }
    }
}

/**
 * Isolated GPU-composited pulsing dot so the parent Dynamic Island card NEVER recomposes at 60-120Hz,
 * and pauses immediately when backgrounded or offscreen.
 */
@Composable
private fun PulsingCriticalDot(color: Color) {
    val motion = LocalLumaMotion.current
    val isResumed = rememberIsLifecycleResumed()
    val alphaAnim = remember { Animatable(0.35f) }
    LaunchedEffect(isResumed, motion.effectiveHz) {
        if (!isResumed) return@LaunchedEffect
        val dur = motion.ambientDuration(720)
        while (true) {
            alphaAnim.animateTo(1f, animationSpec = tween(dur, easing = motion.standardEasing))
            alphaAnim.animateTo(0.35f, animationSpec = tween(dur, easing = motion.standardEasing))
        }
    }
    Box(
        modifier = Modifier
            .size(6.dp)
            .graphicsLayer { alpha = alphaAnim.value }
            .clip(CircleShape)
            .background(color)
    )
}

/**
 * Zero-recomposition canvas hairline showing smooth live countdown inside the Dynamic Island pill.
 */
@Composable
private fun IslandCountdownHairline(
    progressProvider: () -> Float,
    color: Color
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(2.dp)
    ) {
        val fraction = progressProvider().coerceIn(0f, 1f)
        val w = size.width * fraction
        val startX = (size.width - w) / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(startX, 0f),
            size = Size(w, size.height),
            cornerRadius = CornerRadius(size.height, size.height)
        )
    }
}

@Composable
private fun IslandQueueCountChip(count: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+$count",
            fontSize = 8.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFE2E8F0)
        )
    }
}

@Composable
private fun IslandDismissCircleButton(
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.09f))
            .clickable(onClick = onClick)
            .testTag("dynamic_island_notification_dismiss"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = contentDescription,
            tint = Color(0xFFF3F6FC).copy(alpha = 0.78f),
            modifier = Modifier.size(13.dp)
        )
    }
}

private fun criticalEventIcon(kind: CriticalEventKind): ImageVector = when (kind) {
    CriticalEventKind.CRITICAL_BATTERY,
    CriticalEventKind.LOW_BATTERY -> Icons.Rounded.BatteryAlert
    CriticalEventKind.CHARGING_RESUMED -> Icons.Rounded.Bolt
    CriticalEventKind.OFFLINE -> Icons.Rounded.WifiOff
    CriticalEventKind.ONLINE_RESTORED -> Icons.Rounded.Wifi
    CriticalEventKind.INCOMING_CALL -> Icons.Rounded.PhoneInTalk
    CriticalEventKind.ALARM -> Icons.Rounded.Alarm
    CriticalEventKind.CRITICAL_ERROR -> Icons.Rounded.ErrorOutline
    CriticalEventKind.EXPORT_PROGRESS -> Icons.Rounded.IosShare
    CriticalEventKind.EXPORT_DONE -> Icons.Rounded.CheckCircle
    CriticalEventKind.AI_TASK -> Icons.Rounded.AutoFixHigh
}
