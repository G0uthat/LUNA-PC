package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalFolioPalette
import com.example.ui.theme.LocalLumaMotion

private val ChipShape = RoundedCornerShape(18.dp)
private val IconButtonShape = RoundedCornerShape(15.dp)

/**
 * Soft tactile button with gentle spring press depth and rounded geometry.
 * Reads press offset inside Modifier.offset { } (layout phase only — zero recompositions per press frame).
 */
@Composable
fun TactileButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = LocalFolioPalette.current.accent,
    shadowColor: Color = LocalFolioPalette.current.accentShadow,
    contentColor: Color = LocalFolioPalette.current.onAccent,
    borderColor: Color? = null,
    enabled: Boolean = true,
    cornerRadius: Dp = 20.dp,
    lipDepth: Dp = 2.5.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val motion = LocalLumaMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressOffsetState = animateDpAsState(
        targetValue = if (isPressed && enabled) lipDepth else 0.dp,
        animationSpec = tween(durationMillis = if (motion.isLowRam) 24 else 42),
        label = "tactile_press_offset"
    )

    val actualBg = if (enabled) containerColor else containerColor.copy(alpha = 0.45f)
    val actualShadow = if (enabled) shadowColor.copy(alpha = 0.72f) else shadowColor.copy(alpha = 0.28f)
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .padding(bottom = lipDepth)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset { IntOffset(0, lipDepth.roundToPx()) }
                .clip(shape)
                .background(actualShadow)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .offset { IntOffset(0, pressOffsetState.value.roundToPx()) }
                .clip(shape)
                .background(actualBg)
                .then(
                    if (borderColor != null) Modifier.border(BorderStroke(1.dp, borderColor), shape)
                    else Modifier
                )
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = ripple(color = contentColor.copy(alpha = 0.18f)),
                    onClick = onClick
                )
                .padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun TactileChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val palette = LocalFolioPalette.current
    val motion = LocalLumaMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val lip = 2.dp
    val pressOffsetState = animateDpAsState(
        targetValue = if (isPressed) lip else 0.dp,
        animationSpec = tween(durationMillis = if (motion.isLowRam) 24 else 42),
        label = "chip_offset"
    )
    val bg = if (selected) palette.accent else palette.surface
    val shadow = if (selected) palette.accentShadow.copy(alpha = 0.7f) else palette.borderShadow.copy(alpha = 0.65f)
    val textColor = if (selected) palette.onAccent else palette.ink
    val border = if (selected) palette.accent else palette.border

    Box(modifier = modifier.padding(bottom = lip)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset { IntOffset(0, lip.roundToPx()) }
                .clip(ChipShape)
                .background(shadow)
        )
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 40.dp)
                .offset { IntOffset(0, pressOffsetState.value.roundToPx()) }
                .clip(ChipShape)
                .background(bg)
                .border(1.dp, border, ChipShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TactileIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    containerColor: Color? = null,
    contentColor: Color? = null,
    size: Dp = 44.dp
) {
    val palette = LocalFolioPalette.current
    val motion = LocalLumaMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val lip = 2.dp
    val pressOffsetState = animateDpAsState(
        targetValue = if (isPressed) lip else 0.dp,
        animationSpec = tween(durationMillis = if (motion.isLowRam) 24 else 42),
        label = "icon_btn_offset"
    )
    val bg = containerColor ?: if (selected) palette.accent else palette.surface
    val shadow = if (selected) palette.accentShadow.copy(alpha = 0.7f) else palette.borderShadow.copy(alpha = 0.6f)
    val fg = contentColor ?: if (selected) palette.onAccent else palette.ink

    Box(modifier = modifier.padding(bottom = lip)) {
        Box(
            modifier = Modifier
                .size(size)
                .offset { IntOffset(0, lip.roundToPx()) }
                .clip(IconButtonShape)
                .background(shadow)
        )
        Box(
            modifier = Modifier
                .size(size)
                .offset { IntOffset(0, pressOffsetState.value.roundToPx()) }
                .clip(IconButtonShape)
                .background(bg)
                .border(1.dp, if (selected) palette.accent else palette.border, IconButtonShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = fg,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun TactileCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = LocalFolioPalette.current.surface,
    borderColor: Color = LocalFolioPalette.current.border,
    shadowColor: Color = LocalFolioPalette.current.borderShadow.copy(alpha = 0.65f),
    cornerRadius: Dp = 22.dp,
    lipDepth: Dp = 2.5.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val motion = LocalLumaMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressOffsetState = animateDpAsState(
        targetValue = if (isPressed && onClick != null) lipDepth * 0.7f else 0.dp,
        animationSpec = tween(durationMillis = if (motion.isLowRam) 24 else 42),
        label = "card_offset"
    )
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }

    Box(modifier = modifier.padding(bottom = lipDepth)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset { IntOffset(0, lipDepth.roundToPx()) }
                .clip(shape)
                .background(shadowColor)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, pressOffsetState.value.roundToPx()) }
                .clip(shape)
                .background(containerColor)
                .border(1.dp, borderColor, shape)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = ripple(),
                            onClick = onClick
                        )
                    } else {
                        Modifier
                    }
                ),
            content = content
        )
    }
}

@Composable
fun TactileProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = LocalFolioPalette.current.surfaceAlt,
    fillColor: Color = LocalFolioPalette.current.accent,
    height: Dp = 8.dp
) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(trackColor)
    ) {
        if (clamped > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped.coerceAtLeast(0.04f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(fillColor)
            )
        }
    }
}
