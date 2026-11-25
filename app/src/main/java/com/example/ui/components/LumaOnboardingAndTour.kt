package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.engine.SystemBrightnessController
import com.example.engine.SystemMediaController
import com.example.ui.theme.LocalFolioPalette
import com.example.ui.theme.LocalLumaMotion
import kotlin.math.roundToInt

/**
 * Measured root-relative bounds of live LibraryScreen UI targets highlighted
 * during the XLNR-style interactive spotlight tour.
 */
data class TourTargetBounds(
    val headerRect: Rect? = null,
    val continueRect: Rect? = null,
    val tabsRect: Rect? = null,
    val searchFilterRect: Rect? = null,
    val profileSphereRect: Rect? = null,
    val guideButtonRect: Rect? = null
)

/**
 * First-launch permissions explanation popup:
 * - Clearly explains WHY each optional Android special-access permission improves Luma
 *   (True Absolute System Brightness & Dynamic Island notifications/media).
 * - Provides direct "Configurar" buttons that jump straight to Android's settings and
 *   automatically refresh their granted badges on ON_RESUME.
 * - Launches directly into the interactive spotlight tour when the user taps Continue.
 */
@Composable
fun LumaPermissionsOnboardingDialog(
    onStartTour: () -> Unit,
    onSkipAll: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var writeSettingsGranted by remember {
        mutableStateOf(SystemBrightnessController.canWriteSystemSettings(context))
    }
    var notificationAccessGranted by remember {
        mutableStateOf(SystemMediaController.isNotificationListenerEnabled(context))
    }

    // Automatically refresh permission status as soon as the user returns from Android Settings
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                writeSettingsGranted = SystemBrightnessController.canWriteSystemSettings(context)
                SystemMediaController.refreshPermissionStatus(context)
                notificationAccessGranted = SystemMediaController.isNotificationListenerEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LumaFloatingDropdownDialog(
        onDismiss = onSkipAll,
        alignment = Alignment.Center,
        transformOrigin = TransformOrigin(0.5f, 0.45f),
        maxWidth = 500.dp,
        maxHeightFraction = 0.88f,
        dismissOnScrimClick = false,
        testTag = "luma_permissions_onboarding_dialog"
    ) { _ ->
        LumaDropdownHeader(
            title = "Configura Luma",
            subtitle = "Permisos opcionales antes de comenzar",
            leadingIcon = Icons.Rounded.Security,
            onDismiss = onSkipAll,
            closeTestTag = "permissions_onboarding_close"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Welcoming brand banner with EaseInOut Lottie accents
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.surfaceAlt.copy(alpha = 0.62f))
                    .border(1.dp, palette.border, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(palette.accentSoft.copy(alpha = 0.72f))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LumaSparkRevealLottie(
                        replayKey = "onboarding_welcome",
                        tint = palette.accent,
                        modifier = Modifier
                            .size(18.dp)
                            .testTag("onboarding_lottie_spark")
                    )
                    Text(
                        text = "BIENVENIDO A LUMA",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.accent,
                        letterSpacing = 0.8.sp
                    )
                    LumaTourBeaconLottie(
                        tint = palette.accent,
                        modifier = Modifier
                            .size(18.dp)
                            .testTag("onboarding_lottie_beacon")
                    )
                }
                LumaCalligraphicLogo(
                    modifier = Modifier
                        .fillMaxWidth(0.68f)
                        .height(54.dp)
                )
                Text(
                    text = "Antes del recorrido rápido, activa estos dos permisos del sistema para desbloquear toda la experiencia sin interrupciones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }

            // Permission 1: Absolute System Brightness (WRITE_SETTINGS)
            PermissionExplainCard(
                icon = Icons.Rounded.Brightness6,
                title = "Brillo absoluto del sistema",
                reason = "Sincroniza el deslizador de brillo de Luma 1:1 con el brillo real de tu teléfono, sin niveles duplicados ni desincronizados.",
                granted = writeSettingsGranted,
                buttonLabel = if (writeSettingsGranted) "Listo" else "Configurar",
                onConfigure = { SystemBrightnessController.openWriteSettingsPermission(context) },
                testTag = "perm_brightness_configure_btn"
            )

            // Permission 2: Dynamic Island & Media Controls (Notification Listener)
            PermissionExplainCard(
                icon = Icons.Rounded.NotificationsActive,
                title = "Isla Dinámica y multimedia",
                reason = "Muestra únicamente avisos críticos y apps permitidas dentro de la Isla Dinámica, además de controles reales de música al leer.",
                granted = notificationAccessGranted,
                buttonLabel = if (notificationAccessGranted) "Listo" else "Configurar",
                onConfigure = { SystemMediaController.openNotificationAccessSettings(context) },
                testTag = "perm_island_configure_btn"
            )

            // Zero-permission privacy assurance (SAF & Photo Picker)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.accentSoft.copy(alpha = 0.55f))
                    .border(1.dp, palette.accent.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.VerifiedUser,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Tus libros y portadas usan el selector seguro de Android: no pedimos acceso amplio a tu galería ni archivos.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.inkMuted,
                    lineHeight = 15.sp
                )
            }

            // Primary CTA: Start Interactive Spotlight Tour
            TactileButton(
                onClick = onStartTour,
                cornerRadius = 18.dp,
                lipDepth = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("permissions_start_tour_btn")
            ) {
                Icon(Icons.Rounded.Explore, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Continuar al tour interactivo",
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // Subtle secondary skip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onSkipAll)
                    .padding(vertical = 8.dp)
                    .testTag("permissions_skip_all_btn"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Configurar más tarde",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.inkMuted
                )
            }
        }
    }
}

@Composable
private fun PermissionExplainCard(
    icon: ImageVector,
    title: String,
    reason: String,
    granted: Boolean,
    buttonLabel: String,
    onConfigure: () -> Unit,
    testTag: String
) {
    val palette = LocalFolioPalette.current
    val okGreen = Color(0xFF22C55E)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (granted) okGreen.copy(alpha = 0.08f)
                else palette.surfaceAlt.copy(alpha = 0.65f)
            )
            .border(
                width = 1.dp,
                color = if (granted) okGreen.copy(alpha = 0.36f) else palette.border,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (granted) okGreen.copy(alpha = 0.16f) else palette.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (granted) Icons.Rounded.CheckCircle else icon,
                    contentDescription = null,
                    tint = if (granted) okGreen else palette.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.ink
                )
                Text(
                    text = if (granted) "Permiso concedido" else "Opcional · Recomendado",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (granted) okGreen else palette.accent
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (granted) okGreen.copy(alpha = 0.14f) else palette.accent)
                    .clickable(onClick = onConfigure)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag(testTag),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.Settings,
                    contentDescription = null,
                    tint = if (granted) okGreen else palette.onAccent,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = buttonLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (granted) okGreen else palette.onAccent
                )
            }
        }

        Text(
            text = reason,
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkMuted,
            lineHeight = 18.sp
        )
    }
}

private data class InteractiveTourStep(
    val id: String,
    val badge: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val targetRect: (TourTargetBounds) -> Rect?,
    val cornerRadiusDp: Float = 20f,
    val extraPadDp: Float = 8f
)

/**
 * Sleek XLNR-style interactive live-UI spotlight coach-mark tour:
 * - Cuts a real animated rounded spotlight window over the live LibraryScreen using
 *   CompositingStrategy.Offscreen + BlendMode.Clear.
 * - Glides smoothly between live UI elements with spring physics and a softly pulsing ring.
 * - Positions the concise floating step card above or below the highlighted element so the
 *   target stays 100% visible.
 */
@Composable
fun LumaInteractiveTourOverlay(
    visible: Boolean,
    targets: TourTargetBounds,
    onFinish: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val motion = LocalLumaMotion.current
    val density = LocalDensity.current

    val allSteps = remember {
        listOf(
            InteractiveTourStep(
                id = "brand",
                badge = "BIENVENIDA",
                title = "Tu biblioteca universal Luma",
                description = "Lee EPUB, PDF, Manhwa, DOCX, PowerPoint (PPTX), TXT y FB2 con temas suaves y modo nocturno configurable.",
                icon = Icons.Rounded.AutoStories,
                targetRect = { it.headerRect },
                cornerRadiusDp = 22f,
                extraPadDp = 8f
            ),
            InteractiveTourStep(
                id = "continue",
                badge = "RETOMAR",
                title = "Continúa exactamente donde dejaste",
                description = "Tu lectura más reciente aparece siempre arriba con su progreso exacto para entrar en un toque.",
                icon = Icons.Rounded.PlayArrow,
                targetRect = { it.continueRect },
                cornerRadiusDp = 24f,
                extraPadDp = 6f
            ),
            InteractiveTourStep(
                id = "tabs",
                badge = "ORGANIZACIÓN",
                title = "Libros y Series",
                description = "Alterna entre todos tus archivos o agrupa sagas, manhwas y presentaciones en colecciones tipo playlist.",
                icon = Icons.Rounded.ViewAgenda,
                targetRect = { it.tabsRect },
                cornerRadiusDp = 18f,
                extraPadDp = 6f
            ),
            InteractiveTourStep(
                id = "search_filter",
                badge = "EXPLORAR",
                title = "Búsqueda y filtro por formato",
                description = "Encuentra cualquier título al instante o filtra solo EPUB, PDF, DOCX, PPTX o TXT desde el botón derecho.",
                icon = Icons.Rounded.Search,
                targetRect = { it.searchFilterRect },
                cornerRadiusDp = 20f,
                extraPadDp = 6f
            ),
            InteractiveTourStep(
                id = "profile_sphere",
                badge = "CENTRO DE CONTROL",
                title = "Cuenta, Ajustes, Créditos e Importar",
                description = "Toca tu esfera de perfil para abrir el menú flotante: importa archivos, personaliza temas suaves, brillo e Isla Dinámica.",
                icon = Icons.Rounded.Person,
                targetRect = { it.profileSphereRect },
                cornerRadiusDp = 28f,
                extraPadDp = 8f
            ),
            InteractiveTourStep(
                id = "guide",
                badge = "AYUDA RÁPIDA",
                title = "Guía ilustrada siempre a mano",
                description = "Cuando quieras repasar el diccionario por pulsación larga, modo dibujo/firma o documentos de ejemplo, pulsa este botón.",
                icon = Icons.Rounded.HelpOutline,
                targetRect = { it.guideButtonRect },
                cornerRadiusDp = 26f,
                extraPadDp = 8f
            )
        )
    }

    // Only include steps whose target is currently present (e.g., skip "continue" if library is empty)
    val activeSteps = remember(targets) {
        val present = allSteps.filter { it.targetRect(targets) != null }
        present.ifEmpty { allSteps }
    }

    var currentIndex by remember(visible) { mutableIntStateOf(0) }
    val safeIndex = currentIndex.coerceIn(0, (activeSteps.size - 1).coerceAtLeast(0))
    val currentStep = activeSteps[safeIndex]

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(motion.duration(280), easing = LumaEaseInOutCurve)),
        exit = fadeOut(tween(motion.duration(220), easing = LumaEaseInOutCurve))
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* Intercept touches cleanly during guided tour */ }
                )
                .testTag("luma_interactive_tour_overlay")
        ) {
            val screenW = with(density) { maxWidth.toPx() }
            val screenH = with(density) { maxHeight.toPx() }
            val padPx = with(density) { currentStep.extraPadDp.dp.toPx() }
            val cornerPx = with(density) { currentStep.cornerRadiusDp.dp.toPx() }

            val rawTarget = currentStep.targetRect(targets)
                ?: Rect(
                    left = screenW * 0.14f,
                    top = screenH * 0.18f,
                    right = screenW * 0.86f,
                    bottom = screenH * 0.30f
                )

            val targetLeft = (rawTarget.left - padPx).coerceIn(8f, screenW - 40f)
            val targetTop = (rawTarget.top - padPx).coerceIn(8f, screenH - 40f)
            val targetRight = (rawTarget.right + padPx).coerceIn(targetLeft + 32f, screenW - 8f)
            val targetBottom = (rawTarget.bottom + padPx).coerceIn(targetTop + 32f, screenH - 8f)

            val spotlightSpec = remember(motion.effectiveHz) {
                tween<Float>(
                    durationMillis = motion.duration(380),
                    easing = LumaEaseInOutCurve
                )
            }
            val animLeftState = animateFloatAsState(targetLeft, spotlightSpec, label = "tour_left")
            val animTopState = animateFloatAsState(targetTop, spotlightSpec, label = "tour_top")
            val animRightState = animateFloatAsState(targetRight, spotlightSpec, label = "tour_right")
            val animBottomState = animateFloatAsState(targetBottom, spotlightSpec, label = "tour_bottom")
            val animCornerState = animateFloatAsState(cornerPx, spotlightSpec, label = "tour_corner")

            // Soft XLNR-style luminous breathing halo using custom EaseInOut interpolation;
            // strictly paused when offscreen/backgrounded and read ONLY in Canvas draw phase (0 recompositions/sec).
            val isResumed = rememberIsLifecycleResumed()
            val pulseAnim = remember { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(visible, isResumed, motion.effectiveHz) {
                if (!visible || !isResumed) return@LaunchedEffect
                val dur = motion.ambientDuration(1400)
                while (true) {
                    pulseAnim.animateTo(1f, animationSpec = tween(dur, easing = LumaEaseInOutCurve))
                    pulseAnim.animateTo(0f, animationSpec = tween(dur, easing = LumaEaseInOutCurve))
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            ) {
                val animLeft = animLeftState.value
                val animTop = animTopState.value
                val animRight = animRightState.value
                val animBottom = animBottomState.value
                val animCorner = animCornerState.value
                val pulseProgress = pulseAnim.value

                // Dimmed spotlight backdrop
                drawRect(color = Color.Black.copy(alpha = 0.64f))

                val holeTopLeft = Offset(animLeft, animTop)
                val holeSize = Size(
                    (animRight - animLeft).coerceAtLeast(16f),
                    (animBottom - animTop).coerceAtLeast(16f)
                )

                // Clear rounded spotlight hole exposing the live UI element underneath
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = holeTopLeft,
                    size = holeSize,
                    cornerRadius = CornerRadius(animCorner, animCorner),
                    blendMode = BlendMode.Clear
                )

                // Outer breathing glow ring
                val ringExpand = (4.dp.toPx()) * (0.4f + 0.6f * pulseProgress)
                drawRoundRect(
                    color = palette.accent.copy(alpha = 0.28f + 0.22f * (1f - pulseProgress)),
                    topLeft = Offset(animLeft - ringExpand, animTop - ringExpand),
                    size = Size(holeSize.width + ringExpand * 2f, holeSize.height + ringExpand * 2f),
                    cornerRadius = CornerRadius(animCorner + ringExpand, animCorner + ringExpand),
                    style = Stroke(width = 2.6.dp.toPx())
                )

                // Crisp inner accent ring framing the target
                drawRoundRect(
                    color = palette.accent.copy(alpha = 0.92f),
                    topLeft = holeTopLeft,
                    size = holeSize,
                    cornerRadius = CornerRadius(animCorner, animCorner),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Smooth 60-120fps Lottie spotlight beacon gliding in layout phase only
            val beaconSizePx = with(density) { 44.dp.toPx() }
            LumaTourBeaconLottie(
                tint = palette.accent,
                isPlaying = visible,
                modifier = Modifier
                    .offset {
                        val bx = (animRightState.value - beaconSizePx * 0.55f)
                            .coerceIn(6f, (screenW - beaconSizePx - 6f).coerceAtLeast(6f))
                        val by = (animTopState.value - beaconSizePx * 0.45f)
                            .coerceIn(6f, (screenH - beaconSizePx - 6f).coerceAtLeast(6f))
                        IntOffset(bx.roundToInt(), by.roundToInt())
                    }
                    .size(44.dp)
                    .testTag("tour_lottie_beacon")
            )

            // Decide whether the floating coach-mark card sits cleanly below or above the target
            val placeCardBelow = targetBottom < screenH * 0.56f
            val cardYTargetPx = if (placeCardBelow) {
                (targetBottom + with(density) { 18.dp.toPx() })
                    .coerceAtMost(screenH - with(density) { 270.dp.toPx() })
            } else {
                (targetTop - with(density) { 250.dp.toPx() })
                    .coerceAtLeast(with(density) { 64.dp.toPx() })
            }
            val animatedCardYState = animateFloatAsState(
                targetValue = cardYTargetPx,
                animationSpec = spotlightSpec,
                label = "tour_card_y"
            )

            // Floating XLNR-inspired coach-mark capsule card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp)
                    .offset { IntOffset(0, animatedCardYState.value.roundToInt()) },
                contentAlignment = Alignment.TopCenter
            ) {
                val cardShape = RoundedCornerShape(28.dp)
                Column(
                    modifier = Modifier
                        .widthIn(max = 440.dp)
                        .fillMaxWidth()
                        .shadow(
                            elevation = 22.dp,
                            shape = cardShape,
                            ambientColor = Color.Black.copy(alpha = 0.36f),
                            spotColor = Color.Black.copy(alpha = 0.28f)
                        )
                        .clip(cardShape)
                        .background(palette.surface.copy(alpha = 0.99f))
                        .border(1.2.dp, palette.accent.copy(alpha = 0.45f), cardShape)
                        .padding(18.dp)
                        .testTag("luma_tour_card"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header row: step counter pill + Skip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(palette.accentSoft)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            LumaSparkRevealLottie(
                                replayKey = currentStep.id,
                                tint = palette.accent,
                                modifier = Modifier
                                    .size(18.dp)
                                    .testTag("tour_lottie_step_spark")
                            )
                            Text(
                                text = "${safeIndex + 1} / ${activeSteps.size} · ${currentStep.badge}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = palette.accent,
                                letterSpacing = 0.6.sp
                            )
                        }

                        Text(
                            text = "Saltar",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = palette.inkMuted,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onFinish)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("tour_skip_button")
                        )
                    }

                    // Smoothly animated step title + concise copy with custom EaseInOut curve
                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = {
                            (fadeIn(tween(motion.duration(260), easing = LumaEaseInOutCurve)) +
                                slideInHorizontally(tween(motion.duration(280), easing = LumaEaseInOutCurve)) { it / 10 })
                                .togetherWith(
                                    fadeOut(tween(motion.duration(180), easing = LumaEaseInOutCurve)) +
                                        slideOutHorizontally(tween(motion.duration(200), easing = LumaEaseInOutCurve)) { -it / 12 }
                                )
                        },
                        label = "tour_step_copy"
                    ) { step ->
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                text = step.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = palette.ink
                            )
                            Text(
                                text = step.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkMuted,
                                lineHeight = 19.sp
                            )
                        }
                    }

                    // Progress dots + Back / Next navigation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            activeSteps.forEachIndexed { idx, _ ->
                                val isCurrent = idx == safeIndex
                                val dotWidth by animateDpAsState(
                                    targetValue = if (isCurrent) 20.dp else 6.dp,
                                    animationSpec = tween(
                                        durationMillis = motion.duration(280),
                                        easing = LumaEaseInOutCurve
                                    ),
                                    label = "tour_dot_w_$idx"
                                )
                                Box(
                                    modifier = Modifier
                                        .height(6.dp)
                                        .width(dotWidth)
                                        .clip(CircleShape)
                                        .background(
                                            if (isCurrent) palette.accent
                                            else palette.border
                                        )
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (safeIndex > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(palette.surfaceAlt)
                                        .border(1.dp, palette.border, CircleShape)
                                        .clickable { currentIndex = (safeIndex - 1).coerceAtLeast(0) }
                                        .testTag("tour_prev_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Paso anterior",
                                        tint = palette.ink,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            val isLast = safeIndex >= activeSteps.lastIndex
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(palette.accent)
                                    .clickable {
                                        if (isLast) onFinish() else currentIndex = safeIndex + 1
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                                    .testTag("tour_next_button"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isLast) "¡Empezar!" else "Siguiente",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = palette.onAccent
                                )
                                if (!isLast) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = null,
                                        tint = palette.onAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
