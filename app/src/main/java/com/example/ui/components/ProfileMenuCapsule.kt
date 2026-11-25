package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.example.data.model.UserProfile
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import com.example.ui.theme.LocalFolioPalette
import com.example.ui.theme.LocalLumaMotion
import com.example.ui.theme.LumaBrandFontFamily

/**
 * Vectorized calligraphic "Luma" brush-lettering wordmark faithful to the custom signature:
 * - Flowing cursive capital 'L' with upper-left curl, upper-right loop, bold diagonal downstroke,
 *   and horizontal lower-left figure-8 loop sweeping seamlessly into 'u'.
 * - Connected brush-lettered 'u', 'm', 'a' with tapered thick downstrokes and hairline upstrokes.
 * - Long trailing swash exiting 'a' into a playful circular loop-de-loop and tilted 5-pointed star.
 */
@Composable
fun LumaCalligraphicLogo(
    modifier: Modifier = Modifier,
    tintOverride: Color? = null,
    accentOverride: Color? = null
) {
    val palette = LocalFolioPalette.current
    val primaryInk = tintOverride ?: if (palette.isDark) Color.White else palette.ink
    val accentInk = accentOverride ?: if (palette.isDark) {
        lerp(palette.accent, Color.White, 0.34f)
    } else {
        palette.accent
    }
    val midInk = remember(primaryInk, accentInk, palette.isDark) {
        if (palette.isDark) lerp(Color.White, palette.ink, 0.14f)
        else lerp(primaryInk, accentInk, 0.22f)
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 4f || h <= 4f) return@Canvas

        // Fit 1000 x 340 normalized calligraphic viewbox preserving exact aspect ratio
        val vbW = 1000f
        val vbH = 340f
        val scale = minOf(w / vbW, h / vbH)
        val dx = (w - vbW * scale) * 0.08f
        val dy = (h - vbH * scale) * 0.5f

        fun pt(x: Float, y: Float): Offset = Offset(dx + x * scale, dy + y * scale)

        val wordBrush = Brush.horizontalGradient(
            colors = listOf(primaryInk, primaryInk, midInk, accentInk),
            startX = dx,
            endX = dx + vbW * scale
        )
        val starBrush = Brush.linearGradient(
            colors = listOf(lerp(primaryInk, accentInk, 0.55f), accentInk),
            start = pt(860f, 220f),
            end = pt(985f, 100f)
        )

        val baseStroke = (scale * 9.8f).coerceAtLeast(1.6f)
        val boldDownstroke = (scale * 22.5f).coerceAtLeast(3.4f)
        val mediumDownstroke = (scale * 17.5f).coerceAtLeast(2.7f)
        val swashAccentStroke = (scale * 13.5f).coerceAtLeast(2.2f)

        // 1. Continuous flowing cursive skeleton: L -> u -> m -> a -> trailing swash -> mini-loop
        val cursivePath = Path().apply {
            // Capital L: tiny upper-left curl -> graceful wave -> upper-right oval loop
            moveTo(pt(72f, 152f).x, pt(72f, 152f).y)
            cubicTo(
                pt(56f, 146f).x, pt(56f, 146f).y,
                pt(64f, 114f).x, pt(64f, 114f).y,
                pt(104f, 118f).x, pt(104f, 118f).y
            )
            cubicTo(
                pt(154f, 124f).x, pt(154f, 124f).y,
                pt(204f, 174f).x, pt(204f, 174f).y,
                pt(254f, 166f).x, pt(254f, 166f).y
            )
            cubicTo(
                pt(292f, 158f).x, pt(292f, 158f).y,
                pt(292f, 116f).x, pt(292f, 116f).y,
                pt(262f, 116f).x, pt(262f, 116f).y
            )
            // Diagonal L stem sweeping down-left into bottom-left figure-8 loop
            cubicTo(
                pt(226f, 116f).x, pt(226f, 116f).y,
                pt(178f, 186f).x, pt(178f, 186f).y,
                pt(118f, 242f).x, pt(118f, 242f).y
            )
            cubicTo(
                pt(82f, 276f).x, pt(82f, 276f).y,
                pt(44f, 274f).x, pt(44f, 274f).y,
                pt(40f, 248f).x, pt(40f, 248f).y
            )
            cubicTo(
                pt(36f, 224f).x, pt(36f, 224f).y,
                pt(66f, 222f).x, pt(66f, 222f).y,
                pt(102f, 250f).x, pt(102f, 250f).y
            )
            // Baseline wave out of L flowing smoothly into 'u'
            cubicTo(
                pt(142f, 280f).x, pt(142f, 280f).y,
                pt(212f, 274f).x, pt(212f, 274f).y,
                pt(284f, 200f).x, pt(284f, 200f).y
            )
            // 'u' first downstroke & trough
            cubicTo(
                pt(268f, 222f).x, pt(268f, 222f).y,
                pt(250f, 260f).x, pt(250f, 260f).y,
                pt(276f, 270f).x, pt(276f, 270f).y
            )
            cubicTo(
                pt(304f, 280f).x, pt(304f, 280f).y,
                pt(334f, 246f).x, pt(334f, 246f).y,
                pt(352f, 208f).x, pt(352f, 208f).y
            )
            // 'u' second downstroke & exit connector to 'm'
            cubicTo(
                pt(340f, 238f).x, pt(340f, 238f).y,
                pt(338f, 270f).x, pt(338f, 270f).y,
                pt(368f, 272f).x, pt(368f, 272f).y
            )
            cubicTo(
                pt(404f, 274f).x, pt(404f, 274f).y,
                pt(436f, 236f).x, pt(436f, 236f).y,
                pt(456f, 204f).x, pt(456f, 204f).y
            )
            // 'm' first leg down
            cubicTo(
                pt(444f, 232f).x, pt(444f, 232f).y,
                pt(432f, 256f).x, pt(432f, 256f).y,
                pt(424f, 270f).x, pt(424f, 270f).y
            )
            // 'm' retrace up + first arch + second leg down
            cubicTo(
                pt(446f, 236f).x, pt(446f, 236f).y,
                pt(474f, 198f).x, pt(474f, 198f).y,
                pt(502f, 206f).x, pt(502f, 206f).y
            )
            cubicTo(
                pt(520f, 212f).x, pt(520f, 212f).y,
                pt(502f, 248f).x, pt(502f, 248f).y,
                pt(486f, 272f).x, pt(486f, 272f).y
            )
            // 'm' retrace up + second arch + third leg down
            cubicTo(
                pt(508f, 236f).x, pt(508f, 236f).y,
                pt(540f, 198f).x, pt(540f, 198f).y,
                pt(562f, 210f).x, pt(562f, 210f).y
            )
            cubicTo(
                pt(578f, 220f).x, pt(578f, 220f).y,
                pt(546f, 266f).x, pt(546f, 266f).y,
                pt(572f, 276f).x, pt(572f, 276f).y
            )
            // Connector into 'a' crest
            cubicTo(
                pt(602f, 284f).x, pt(602f, 284f).y,
                pt(636f, 212f).x, pt(636f, 212f).y,
                pt(688f, 204f).x, pt(688f, 204f).y
            )
            // 'a' round bowl (counter-clockwise)
            cubicTo(
                pt(648f, 198f).x, pt(648f, 198f).y,
                pt(614f, 234f).x, pt(614f, 234f).y,
                pt(626f, 264f).x, pt(626f, 264f).y
            )
            cubicTo(
                pt(638f, 286f).x, pt(638f, 286f).y,
                pt(676f, 272f).x, pt(676f, 272f).y,
                pt(692f, 234f).x, pt(692f, 234f).y
            )
            // 'a' right stem down into long graceful trailing swash
            cubicTo(
                pt(682f, 260f).x, pt(682f, 260f).y,
                pt(698f, 286f).x, pt(698f, 286f).y,
                pt(746f, 284f).x, pt(746f, 284f).y
            )
            // Sweeping rise into the playful little circular loop-de-loop
            cubicTo(
                pt(796f, 282f).x, pt(796f, 282f).y,
                pt(832f, 212f).x, pt(832f, 212f).y,
                pt(872f, 210f).x, pt(872f, 210f).y
            )
            cubicTo(
                pt(892f, 210f).x, pt(892f, 210f).y,
                pt(892f, 242f).x, pt(892f, 242f).y,
                pt(878f, 242f).x, pt(878f, 242f).y
            )
            cubicTo(
                pt(864f, 242f).x, pt(864f, 242f).y,
                pt(866f, 216f).x, pt(866f, 216f).y,
                pt(892f, 206f).x, pt(892f, 206f).y
            )
            // Final flick up-right right under the star
            cubicTo(
                pt(918f, 198f).x, pt(918f, 198f).y,
                pt(932f, 184f).x, pt(932f, 184f).y,
                pt(938f, 166f).x, pt(938f, 166f).y
            )
        }

        // 2. Crisp tilted 5-pointed calligraphic star (continuous hand-lettered star)
        val starPath = Path().apply {
            val sBottomLeft = pt(912f, 174f)
            val sTop = pt(942f, 108f)
            val sBottomRight = pt(964f, 164f)
            val sUpperLeft = pt(898f, 138f)
            val sUpperRight = pt(984f, 104f)
            moveTo(sBottomLeft.x, sBottomLeft.y)
            lineTo(sTop.x, sTop.y)
            lineTo(sBottomRight.x, sBottomRight.y)
            lineTo(sUpperLeft.x, sUpperLeft.y)
            lineTo(sUpperRight.x, sUpperRight.y)
            lineTo(sBottomLeft.x, sBottomLeft.y)
        }

        // Subtle luminous dark-mode halo behind the lettering
        if (palette.isDark) {
            drawPath(
                path = cursivePath,
                color = accentInk.copy(alpha = 0.20f),
                style = Stroke(
                    width = boldDownstroke * 1.32f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            drawPath(
                path = starPath,
                color = accentInk.copy(alpha = 0.24f),
                style = Stroke(
                    width = baseStroke * 2.1f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        // Base flowing hairline-to-medium skeleton
        drawPath(
            path = cursivePath,
            brush = wordBrush,
            style = Stroke(
                width = baseStroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // 3. Tapered thick brush-lettering downstrokes ("toques gruesos como lettering")
        // Capital L: sweeping diagonal main downstroke
        drawTaperedCubicStroke(
            p0 = pt(258f, 120f),
            p1 = pt(218f, 138f),
            p2 = pt(174f, 192f),
            p3 = pt(116f, 244f),
            brush = wordBrush,
            maxWidth = boldDownstroke
        )
        // Capital L: subtle calligraphic swell on upper crest
        drawTaperedCubicStroke(
            p0 = pt(80f, 120f),
            p1 = pt(124f, 116f),
            p2 = pt(172f, 146f),
            p3 = pt(220f, 162f),
            brush = wordBrush,
            maxWidth = swashAccentStroke
        )
        // Capital L: bold bottom-left flourish swell
        drawTaperedCubicStroke(
            p0 = pt(56f, 232f),
            p1 = pt(92f, 242f),
            p2 = pt(136f, 276f),
            p3 = pt(196f, 266f),
            brush = wordBrush,
            maxWidth = mediumDownstroke
        )

        // 'u' 1st downstroke
        drawTaperedCubicStroke(
            p0 = pt(282f, 202f),
            p1 = pt(268f, 224f),
            p2 = pt(256f, 256f),
            p3 = pt(276f, 270f),
            brush = wordBrush,
            maxWidth = mediumDownstroke
        )
        // 'u' 2nd downstroke
        drawTaperedCubicStroke(
            p0 = pt(350f, 210f),
            p1 = pt(342f, 236f),
            p2 = pt(342f, 264f),
            p3 = pt(364f, 272f),
            brush = wordBrush,
            maxWidth = mediumDownstroke
        )

        // 'm' 1st downstroke
        drawTaperedCubicStroke(
            p0 = pt(454f, 206f),
            p1 = pt(444f, 228f),
            p2 = pt(434f, 252f),
            p3 = pt(424f, 270f),
            brush = wordBrush,
            maxWidth = mediumDownstroke
        )
        // 'm' 2nd downstroke
        drawTaperedCubicStroke(
            p0 = pt(504f, 208f),
            p1 = pt(512f, 222f),
            p2 = pt(498f, 250f),
            p3 = pt(486f, 272f),
            brush = wordBrush,
            maxWidth = mediumDownstroke
        )
        // 'm' 3rd downstroke
        drawTaperedCubicStroke(
            p0 = pt(562f, 212f),
            p1 = pt(570f, 228f),
            p2 = pt(552f, 262f),
            p3 = pt(570f, 275f),
            brush = wordBrush,
            maxWidth = mediumDownstroke
        )

        // 'a' left curved belly downstroke
        drawTaperedCubicStroke(
            p0 = pt(676f, 204f),
            p1 = pt(644f, 204f),
            p2 = pt(616f, 236f),
            p3 = pt(632f, 268f),
            brush = wordBrush,
            maxWidth = mediumDownstroke
        )
        // 'a' right stem downstroke flowing into swash
        drawTaperedCubicStroke(
            p0 = pt(691f, 235f),
            p1 = pt(684f, 258f),
            p2 = pt(698f, 282f),
            p3 = pt(744f, 284f),
            brush = wordBrush,
            maxWidth = swashAccentStroke
        )

        // 4. Crisp 5-pointed calligraphic star with brush emphasis on primary diagonals
        drawPath(
            path = starPath,
            brush = starBrush,
            style = Stroke(
                width = baseStroke * 0.94f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        drawTaperedCubicStroke(
            p0 = pt(942f, 108f),
            p1 = pt(948f, 126f),
            p2 = pt(956f, 146f),
            p3 = pt(964f, 164f),
            brush = starBrush,
            maxWidth = swashAccentStroke * 0.92f
        )
    }
}

/**
 * Draws a pressure-sensitive tapered brush stroke along a cubic Bezier curve using
 * smooth sine-bell width modulation (hairline entry -> thick calligraphic belly -> hairline exit).
 */
private fun DrawScope.drawTaperedCubicStroke(
    p0: Offset,
    p1: Offset,
    p2: Offset,
    p3: Offset,
    brush: Brush,
    maxWidth: Float,
    steps: Int = 20
) {
    var prev = p0
    for (i in 1..steps) {
        val t = i.toFloat() / steps
        val u = 1f - t
        val x = u * u * u * p0.x + 3f * u * u * t * p1.x + 3f * u * t * t * p2.x + t * t * t * p3.x
        val y = u * u * u * p0.y + 3f * u * u * t * p1.y + 3f * u * t * t * p2.y + t * t * t * p3.y
        val curr = Offset(x, y)
        val midT = (i - 0.5f) / steps
        val pressure = sin(midT * PI).toFloat().coerceAtLeast(0f).pow(0.78f)
        val segWidth = (maxWidth * pressure).coerceAtLeast(1.2f)
        drawLine(
            brush = brush,
            start = prev,
            end = curr,
            strokeWidth = segWidth,
            cap = StrokeCap.Round
        )
        prev = curr
    }
}

/**
 * Wide calligraphic header wordmark for "Luma" paired with the live library counter subtitle.
 */
@Composable
fun LumaWideWordmark(
    bookCount: Int,
    seriesCount: Int,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        LumaCalligraphicLogo(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(end = 6.dp)
                .testTag("luma_brand_wordmark")
        )

        Text(
            text = "$bookCount libros · $seriesCount series",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            color = palette.inkMuted.copy(alpha = if (palette.isDark) 0.88f else 0.80f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

/**
 * Floating vertical profile capsule menu anchored seamlessly to the top-right profile sphere.
 * - Top sphere row IS "CUENTA" (no "Lector" text, no duplicate Cuenta circle): tapping it a second time
 *   immediately opens ONLY Account configuration.
 * - Below: cleanly separated AMIGOS, AJUSTES, and IMPORTAR entries.
 */
@Composable
fun ProfileDropdownCapsuleOverlay(
    visible: Boolean,
    anchorTopPx: Float,
    anchorRightPx: Float = 0f,
    userProfile: UserProfile,
    unreadMessages: Int,
    isImporting: Boolean,
    onDismiss: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit,
    onImportDocument: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val motion = LocalLumaMotion.current
    val density = LocalDensity.current
    val scrimInteraction = remember { MutableInteractionSource() }
    val innerPadPx = with(density) { 10.dp.toPx() }
    val edgeMarginPx = with(density) { 8.dp.toPx() }
    val defaultCapsuleWidthPx = with(density) { 226.dp.toPx() }
    val capsuleTopOffsetPx = (anchorTopPx - innerPadPx).coerceAtLeast(0f).roundToInt()
    var measuredCapsuleWidthPx by remember { mutableIntStateOf(defaultCapsuleWidthPx.roundToInt()) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val capsuleLeftOffsetPx = if (anchorRightPx > 1f) {
            (anchorRightPx + innerPadPx - measuredCapsuleWidthPx)
                .coerceIn(edgeMarginPx, (maxWidthPx - measuredCapsuleWidthPx - edgeMarginPx).coerceAtLeast(edgeMarginPx))
                .roundToInt()
        } else {
            (maxWidthPx - measuredCapsuleWidthPx - edgeMarginPx).coerceAtLeast(edgeMarginPx).roundToInt()
        }

        // Gentle 15% dim + subtle frosted veil
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = motion.tweenSpec(110)),
            exit = fadeOut(animationSpec = motion.exitTween(85))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f))
                    .background(palette.background.copy(alpha = 0.08f))
                    .clickable(
                        interactionSource = scrimInteraction,
                        indication = null,
                        onClick = onDismiss
                    )
                    .testTag("profile_menu_scrim")
            )
        }

        // Tall vertical pill/capsule aligned pixel-exact over the profile sphere (both phone top-right AND tablet left-pane!)
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(capsuleLeftOffsetPx, capsuleTopOffsetPx) },
            enter = scaleIn(
                initialScale = 0.93f,
                transformOrigin = TransformOrigin(0.88f, 0.06f),
                animationSpec = motion.modalSpring()
            ) + fadeIn(animationSpec = motion.emphasizedTween(110)),
            exit = scaleOut(
                targetScale = 0.95f,
                transformOrigin = TransformOrigin(0.88f, 0.06f),
                animationSpec = motion.exitTween(85)
            ) + fadeOut(animationSpec = motion.exitTween(80))
        ) {
            val capsuleShape = remember { RoundedCornerShape(32.dp) }
            val capsuleElevation = remember(palette.isDark, motion.isLowRam) {
                when {
                    motion.isLowRam -> 4.dp
                    palette.isDark -> 14.dp
                    else -> 12.dp
                }
            }
            val accountSubtitle = remember(userProfile.username) {
                val clean = userProfile.username.trim()
                if (clean.isNotBlank() && !clean.equals("Lector", ignoreCase = true)) {
                    clean
                } else {
                    "Perfil y privacidad"
                }
            }

            Column(
                modifier = Modifier
                    .widthIn(min = 206.dp, max = 238.dp)
                    .onSizeChanged { measuredCapsuleWidthPx = it.width }
                    .shadow(
                        elevation = capsuleElevation,
                        shape = capsuleShape,
                        ambientColor = Color.Black.copy(alpha = 0.24f),
                        spotColor = Color.Black.copy(alpha = 0.20f)
                    )
                    .clip(capsuleShape)
                    .background(palette.surface.copy(alpha = 0.98f))
                    .border(1.dp, palette.border, capsuleShape)
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .testTag("profile_dropdown_capsule"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top sphere row IS CUENTA: tapping the sphere once opens this menu; tapping right here again
                // ("doble click") opens ONLY Account configuration!
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .clickable {
                            onDismiss()
                            onOpenAccount()
                        }
                        .padding(start = 12.dp, end = 0.dp, top = 0.dp, bottom = 0.dp)
                        .testTag("profile_menu_account_item"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = "CUENTA",
                            fontFamily = LumaBrandFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            letterSpacing = 1.1.sp,
                            color = palette.ink,
                            maxLines = 1,
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = accountSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.5.sp,
                            color = palette.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        UserAvatarCircle(
                            displayName = userProfile.username.ifBlank { "C" },
                            avatarPath = userProfile.avatarPath,
                            avatarBase64 = "",
                            size = 44.dp,
                            showOnlineDot = userProfile.privacy.showOnlineStatus,
                            unreadBadge = 0
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    thickness = 1.dp,
                    color = palette.border.copy(alpha = 0.6f)
                )

                // 1. AMIGOS (Solo amigos P2P y chat)
                ProfileCapsuleActionRow(
                    title = "AMIGOS",
                    subtitle = if (unreadMessages > 0) {
                        "$unreadMessages sin leer"
                    } else {
                        "Chat y lectura"
                    },
                    subtitleHighlight = unreadMessages > 0,
                    icon = Icons.Rounded.Group,
                    iconContentDescription = "Abrir amigos y chat",
                    badgeCount = unreadMessages,
                    isAccentPrimary = false,
                    isLoading = false,
                    onClick = {
                        onDismiss()
                        onOpenFriends()
                    },
                    testTag = "profile_menu_friends_item"
                )

                // 2. AJUSTES (Solo temas, tipografía, lector y fuentes)
                ProfileCapsuleActionRow(
                    title = "AJUSTES",
                    subtitle = "Tema y lectura",
                    subtitleHighlight = false,
                    icon = Icons.Rounded.Settings,
                    iconContentDescription = "Abrir ajustes",
                    badgeCount = 0,
                    isAccentPrimary = false,
                    isLoading = false,
                    onClick = {
                        onDismiss()
                        onOpenSettings()
                    },
                    testTag = "profile_menu_settings_item"
                )

                // 2b. GUÍA (Guía ilustrada paso a paso)
                ProfileCapsuleActionRow(
                    title = "GUÍA",
                    subtitle = "Cómo usar Luma",
                    subtitleHighlight = false,
                    icon = Icons.Rounded.MenuBook,
                    iconContentDescription = "Abrir guía de uso de Luma",
                    badgeCount = 0,
                    isAccentPrimary = false,
                    isLoading = false,
                    onClick = {
                        onDismiss()
                        onOpenGuide()
                    },
                    testTag = "profile_menu_guide_item"
                )

                // 3. IMPORTAR (EPUB, PDF, DOCX, PPTX, TXT, FB2)
                ProfileCapsuleActionRow(
                    title = "IMPORTAR",
                    subtitle = if (isImporting) "Importando…" else "EPUB · PDF · DOCX · PPTX",
                    subtitleHighlight = true,
                    icon = Icons.Rounded.Add,
                    iconContentDescription = "Importar documento",
                    badgeCount = 0,
                    isAccentPrimary = true,
                    isLoading = isImporting,
                    enabled = !isImporting,
                    onClick = {
                        onDismiss()
                        onImportDocument()
                    },
                    testTag = "import_document_button"
                )
            }
        }
    }
}

@Composable
private fun ProfileCapsuleActionRow(
    title: String,
    subtitle: String,
    subtitleHighlight: Boolean,
    icon: ImageVector,
    iconContentDescription: String,
    badgeCount: Int,
    isAccentPrimary: Boolean,
    isLoading: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = LocalFolioPalette.current
    val rowShape = RoundedCornerShape(26.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(
                if (isAccentPrimary) palette.accentSoft.copy(alpha = 0.55f)
                else Color.Transparent
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 12.dp, end = 2.dp, top = 4.dp, bottom = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = title,
                fontFamily = LumaBrandFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                letterSpacing = 1.1.sp,
                color = if (isAccentPrimary) palette.accent else palette.ink,
                maxLines = 1,
                textAlign = TextAlign.End
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.5.sp,
                color = if (subtitleHighlight) palette.accent else palette.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
        }

        Spacer(Modifier.width(12.dp))

        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center
        ) {
            val circleModifier = if (isAccentPrimary) {
                Modifier
                    .size(44.dp)
                    .shadow(4.dp, CircleShape, ambientColor = palette.accent.copy(alpha = 0.35f))
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(palette.accent, palette.accentShadow)
                        )
                    )
            } else {
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(palette.surfaceAlt)
                    .border(1.dp, palette.border, CircleShape)
            }

            Box(
                modifier = circleModifier,
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = if (isAccentPrimary) palette.onAccent else palette.accent
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = iconContentDescription,
                        tint = if (isAccentPrimary) palette.onAccent else palette.ink,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(palette.accent)
                        .border(1.5.dp, palette.surface, CircleShape)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeCount.coerceAtMost(99).toString(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Floating vertical capsule filter menu matching ProfileDropdownCapsuleOverlay:
 * - Same 32.dp capsule, breathing margins, 15% dim + subtle frosted veil, circular icon badges,
 *   and crisp spring dropdown animation anchored cleanly below the filter button.
 */
@Composable
fun LibraryFilterDropdownOverlay(
    visible: Boolean,
    anchorBottomPx: Float,
    selectedFilter: String,
    onSelectFilter: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val motion = LocalLumaMotion.current
    val density = LocalDensity.current
    val scrimInteraction = remember { MutableInteractionSource() }
    val gapPx = with(density) { 10.dp.toPx() }
    val menuTopOffsetPx = (anchorBottomPx + gapPx).coerceAtLeast(with(density) { 80.dp.toPx() }).roundToInt()

    val options = remember {
        listOf(
            Triple("Todos", "Todos los formatos", Icons.Rounded.FolderCopy),
            Triple("EPUB", "Libros EPUB", Icons.Rounded.AutoStories),
            Triple("PDF", "PDF y Manhwa", Icons.Rounded.PictureAsPdf),
            Triple("DOCX", "Documentos DOCX", Icons.Rounded.Description),
            Triple("PPTX", "PowerPoint PPTX", Icons.Rounded.Slideshow),
            Triple("Otros", "TXT y FB2", Icons.Rounded.TextSnippet)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = motion.tweenSpec(215)),
            exit = fadeOut(animationSpec = motion.exitTween(170))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f))
                    .background(palette.background.copy(alpha = 0.08f))
                    .clickable(
                        interactionSource = scrimInteraction,
                        indication = null,
                        onClick = onDismiss
                    )
                    .testTag("filter_menu_scrim")
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, menuTopOffsetPx) }
                .padding(end = 16.dp, start = 16.dp),
            enter = scaleIn(
                initialScale = 0.89f,
                transformOrigin = TransformOrigin(0.90f, 0.05f),
                animationSpec = motion.modalSpring()
            ) + slideInVertically(
                initialOffsetY = { -10 },
                animationSpec = motion.modalSpring()
            ) + fadeIn(animationSpec = motion.emphasizedTween(200)),
            exit = scaleOut(
                targetScale = 0.92f,
                transformOrigin = TransformOrigin(0.90f, 0.05f),
                animationSpec = motion.exitTween(170)
            ) + slideOutVertically(
                targetOffsetY = { -8 },
                animationSpec = motion.exitTween(165)
            ) + fadeOut(animationSpec = motion.exitTween(155))
        ) {
            val capsuleShape = RoundedCornerShape(32.dp)
            Column(
                modifier = Modifier
                    .widthIn(min = 216.dp, max = 256.dp)
                    .shadow(
                        elevation = if (palette.isDark) 20.dp else 16.dp,
                        shape = capsuleShape,
                        ambientColor = Color.Black.copy(alpha = 0.28f),
                        spotColor = Color.Black.copy(alpha = 0.24f)
                    )
                    .clip(capsuleShape)
                    .background(palette.surface.copy(alpha = 0.98f))
                    .border(1.dp, palette.border, capsuleShape)
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .testTag("library_filter_capsule"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for ((key, subtitle, icon) in options) {
                    val isChosen = selectedFilter == key
                    LumaCapsuleOptionRow(
                        title = key.uppercase(),
                        subtitle = subtitle,
                        icon = icon,
                        selected = isChosen,
                        iconOnRight = true,
                        iconCircleSize = 40.dp,
                        onClick = {
                            onSelectFilter(key)
                            onDismiss()
                        },
                        testTag = "filter_option_$key"
                    )
                }
            }
        }
    }
}
