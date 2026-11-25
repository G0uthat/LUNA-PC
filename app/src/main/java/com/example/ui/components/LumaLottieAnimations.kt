package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.RenderMode
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.example.R
import com.example.ui.theme.LocalLumaMotion
import kotlinx.coroutines.isActive

/**
 * Custom silky EaseInOut cubic-Bezier curve calibrated for Lottie progress playback
 * across 60–240 Hz displays (gentle acceleration, velvety deceleration, zero harsh snap).
 */
val LumaEaseInOutCurve = CubicBezierEasing(0.42f, 0.0f, 0.58f, 1.0f)

/**
 * Tracks whether the host Lifecycle is currently RESUMED so infinite UI animations and
 * Lottie loops pause immediately when the screen goes offscreen or into the background.
 */
@Composable
fun rememberIsLifecycleResumed(): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    return isResumed
}

/**
 * Smooth Lottie beacon animation used to draw attention to the active spotlighted element
 * during the initial interactive tour, driven with a custom EaseInOut interpolation curve,
 * paced to the active display refresh rate, and automatically paused when offscreen or paused.
 *
 * Reads `progressAnim.value` ONLY inside LottieAnimation's draw-phase lambda (0 recompositions/sec).
 */
@Composable
fun LumaTourBeaconLottie(
    tint: Color,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true
) {
    val motion = LocalLumaMotion.current
    val isResumed = rememberIsLifecycleResumed()
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.lottie_tour_beacon)
    )
    val progressAnim = remember { Animatable(0f) }
    val shouldRun = isPlaying && isResumed && composition != null

    LaunchedEffect(shouldRun, motion.effectiveHz) {
        if (!shouldRun) return@LaunchedEffect
        val durationMs = motion.ambientDuration(1950)
        while (isActive) {
            progressAnim.snapTo(0f)
            progressAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = durationMs,
                    easing = LumaEaseInOutCurve
                )
            )
        }
    }

    val colorFilter = remember(tint) { SimpleColorFilter(tint.toArgb()) }
    val dynamicProperties = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR_FILTER,
            value = colorFilter,
            keyPath = arrayOf("**")
        )
    )
    LottieAnimation(
        composition = composition,
        progress = { progressAnim.value },
        dynamicProperties = dynamicProperties,
        renderMode = RenderMode.AUTOMATIC,
        modifier = modifier
    )
}

/**
 * Crisp one-shot Lottie spark reveal animation driven by a custom EaseInOut curve,
 * replayed smoothly whenever a new word opens in the contextual dictionary or when
 * advancing to a new interactive tour step.
 */
@Composable
fun LumaSparkRevealLottie(
    replayKey: Any,
    tint: Color,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true
) {
    val motion = LocalLumaMotion.current
    val isResumed = rememberIsLifecycleResumed()
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.lottie_spark_reveal)
    )
    val colorFilter = remember(tint) { SimpleColorFilter(tint.toArgb()) }
    val dynamicProperties = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR_FILTER,
            value = colorFilter,
            keyPath = arrayOf("**")
        )
    )
    key(replayKey) {
        val progressAnim = remember { Animatable(0f) }
        LaunchedEffect(composition, motion.effectiveHz, isPlaying, isResumed) {
            if (composition != null && isPlaying && isResumed) {
                progressAnim.snapTo(0f)
                progressAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = motion.duration(860),
                        easing = LumaEaseInOutCurve
                    )
                )
            }
        }
        LottieAnimation(
            composition = composition,
            progress = { progressAnim.value },
            dynamicProperties = dynamicProperties,
            renderMode = RenderMode.AUTOMATIC,
            modifier = modifier
        )
    }
}

/**
 * Fluid 3-dot wave Lottie loading animation driven by custom EaseInOut interpolation
 * for live dictionary translation, Wikipedia summary lookup, and embedded dictionary tabs.
 * Automatically pauses when not visible or when the app is backgrounded.
 */
@Composable
fun LumaDictionaryPulseLottie(
    tint: Color,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true
) {
    val motion = LocalLumaMotion.current
    val isResumed = rememberIsLifecycleResumed()
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.lottie_dict_pulse)
    )
    val progressAnim = remember { Animatable(0f) }
    val shouldRun = isPlaying && isResumed && composition != null

    LaunchedEffect(shouldRun, motion.effectiveHz) {
        if (!shouldRun) return@LaunchedEffect
        val durationMs = motion.ambientDuration(1320)
        while (isActive) {
            progressAnim.snapTo(0f)
            progressAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = durationMs,
                    easing = LumaEaseInOutCurve
                )
            )
        }
    }

    val colorFilter = remember(tint) { SimpleColorFilter(tint.toArgb()) }
    val dynamicProperties = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR_FILTER,
            value = colorFilter,
            keyPath = arrayOf("**")
        )
    )
    LottieAnimation(
        composition = composition,
        progress = { progressAnim.value },
        dynamicProperties = dynamicProperties,
        renderMode = RenderMode.AUTOMATIC,
        modifier = modifier
    )
}
