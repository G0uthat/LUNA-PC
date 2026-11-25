package com.example.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.roundToInt

/**
 * High-responsiveness, refresh-rate- and RAM-adaptive motion system for Luma.
 *
 * Calibrated for immediate frame-0 touch feedback (zero 100–150 ms perceived click lag)
 * across 60..240 Hz panels and low-RAM devices:
 * - Fast-out deceleration curves move immediately on the first frame after a tap.
 * - High-stiffness, critically-damped springs settle quickly without rubbery bounce.
 * - Low-RAM mode trims duration further and lets containers lower shadow/layer overhead.
 */
@Immutable
data class LumaMotionProfile(
    val effectiveHz: Float,
    val frameMs: Float,
    val durationScale: Float,
    val stiffnessScale: Float,
    val dampingFloor: Float,
    val dampingBoost: Float,
    val lottieSpeed: Float,
    val isLowRam: Boolean,
    val standardEasing: CubicBezierEasing,
    val emphasizedEasing: CubicBezierEasing,
    val exitEasing: CubicBezierEasing
) {
    /**
     * Scales a base duration and snaps it to an integer multiple of the panel's VSync frame period.
     */
    fun duration(baseMs: Int, minFrames: Int = 2): Int {
        if (baseMs <= 0) return 0
        val effectiveMinFrames = if (isLowRam) minFrames.coerceAtMost(2) else minFrames
        val scaled = baseMs * durationScale
        val frames = (scaled / frameMs).roundToInt().coerceAtLeast(effectiveMinFrames)
        return (frames * frameMs).roundToInt().coerceAtLeast(24)
    }

    /**
     * Scales a long breathing/ambient cycle smoothly.
     */
    fun ambientDuration(baseMs: Int): Int {
        val factor = when {
            effectiveHz <= 65f -> 1.00f
            effectiveHz <= 95f -> 0.96f
            effectiveHz <= 125f -> 0.92f
            else -> 0.88f
        }
        val frames = ((baseMs * factor) / frameMs).roundToInt().coerceAtLeast(10)
        return (frames * frameMs).roundToInt()
    }

    fun <T> tweenSpec(
        baseMs: Int,
        delayMs: Int = 0,
        easing: Easing = standardEasing
    ): TweenSpec<T> = tween(
        durationMillis = duration(baseMs),
        delayMillis = if (delayMs > 0) duration(delayMs, minFrames = 1) else 0,
        easing = easing
    )

    fun <T> emphasizedTween(
        baseMs: Int,
        delayMs: Int = 0
    ): TweenSpec<T> = tweenSpec(
        baseMs = baseMs,
        delayMs = delayMs,
        easing = emphasizedEasing
    )

    fun <T> exitTween(
        baseMs: Int,
        delayMs: Int = 0
    ): TweenSpec<T> = tweenSpec(
        baseMs = baseMs,
        delayMs = delayMs,
        easing = exitEasing
    )

    /**
     * Snappy, critically-damped spring with zero rubbery lag on 60..240 Hz panels.
     */
    fun <T> springSpec(
        baseDamping: Float = 0.88f,
        baseStiffness: Float = 680f,
        visibilityThreshold: T? = null
    ): SpringSpec<T> {
        val tunedDamping = (baseDamping + dampingBoost)
            .coerceAtLeast(dampingFloor)
            .coerceAtMost(0.98f)
        val lowRamBoost = if (isLowRam) 1.18f else 1.0f
        val tunedStiffness = (baseStiffness * stiffnessScale * lowRamBoost)
            .coerceIn(320f, 1400f)
        return spring(
            dampingRatio = tunedDamping,
            stiffness = tunedStiffness,
            visibilityThreshold = visibilityThreshold
        )
    }

    /** Instant tactile spring for button/chip/card presses (< 50 ms feel). */
    fun <T> tactileSpring(visibilityThreshold: T? = null): SpringSpec<T> =
        springSpec(
            baseDamping = 0.88f,
            baseStiffness = 980f,
            visibilityThreshold = visibilityThreshold
        )

    /** Snappy, zero-lag spring for menus, dropdowns, Dynamic Island, and popovers. */
    fun <T> modalSpring(visibilityThreshold: T? = null): SpringSpec<T> =
        springSpec(
            baseDamping = 0.90f,
            baseStiffness = 720f,
            visibilityThreshold = visibilityThreshold
        )

    /** Crisp critically-damped glide spring for spotlights, floating banners, and bounds morphing. */
    fun <T> glideSpring(visibilityThreshold: T? = null): SpringSpec<T> =
        springSpec(
            baseDamping = 0.92f,
            baseStiffness = 560f,
            visibilityThreshold = visibilityThreshold
        )

    /** Expressive yet rapid spring for bookmark ribbon drops & celebratory micro-feedback. */
    fun <T> expressiveSpring(visibilityThreshold: T? = null): SpringSpec<T> =
        springSpec(
            baseDamping = 0.82f,
            baseStiffness = 620f,
            visibilityThreshold = visibilityThreshold
        )
}

fun motionProfileFor(refreshRateHz: Float, isLowRam: Boolean = false): LumaMotionProfile {
    val hz = refreshRateHz.coerceIn(60f, 240f)
    val frameMs = 1000f / hz

    val baseDurationScale = interpolateAnchors(
        hz,
        60f to 0.76f,
        90f to 0.74f,
        120f to 0.70f,
        144f to 0.68f,
        240f to 0.65f
    )
    val durationScale = if (isLowRam) baseDurationScale * 0.78f else baseDurationScale

    val stiffnessScale = interpolateAnchors(
        hz,
        60f to 1.00f,
        90f to 1.05f,
        120f to 1.10f,
        144f to 1.14f,
        240f to 1.18f
    )
    val dampingBoost = interpolateAnchors(
        hz,
        60f to 0.02f,
        90f to 0.01f,
        120f to 0.02f,
        144f to 0.03f,
        240f to 0.04f
    )
    val dampingFloor = interpolateAnchors(
        hz,
        60f to 0.84f,
        90f to 0.82f,
        120f to 0.84f,
        144f to 0.85f,
        240f to 0.86f
    )
    val lottieSpeed = interpolateAnchors(
        hz,
        60f to 1.04f,
        90f to 1.06f,
        120f to 1.08f,
        144f to 1.10f,
        240f to 1.12f
    )

    // Fast-out deceleration curves: immediate visual motion on frame 1 after every tap
    val stdX1 = interpolateAnchors(hz, 60f to 0.08f, 90f to 0.07f, 120f to 0.06f, 240f to 0.05f)
    val stdX2 = interpolateAnchors(hz, 60f to 0.22f, 90f to 0.20f, 120f to 0.18f, 240f to 0.16f)

    val empX1 = interpolateAnchors(hz, 60f to 0.05f, 90f to 0.05f, 120f to 0.04f, 240f to 0.04f)
    val empX2 = interpolateAnchors(hz, 60f to 0.18f, 90f to 0.16f, 120f to 0.15f, 240f to 0.14f)

    return LumaMotionProfile(
        effectiveHz = hz,
        frameMs = frameMs,
        durationScale = durationScale,
        stiffnessScale = stiffnessScale,
        dampingFloor = dampingFloor,
        dampingBoost = dampingBoost,
        lottieSpeed = lottieSpeed,
        isLowRam = isLowRam,
        standardEasing = CubicBezierEasing(stdX1, 0.92f, stdX2, 1f),
        emphasizedEasing = CubicBezierEasing(empX1, 0.96f, empX2, 1f),
        exitEasing = CubicBezierEasing(0.30f, 0f, 0.80f, 0.20f)
    )
}

private fun interpolateAnchors(x: Float, vararg points: Pair<Float, Float>): Float {
    if (points.isEmpty()) return 1f
    if (x <= points.first().first) return points.first().second
    if (x >= points.last().first) return points.last().second
    for (i in 0 until points.lastIndex) {
        val (x0, y0) = points[i]
        val (x1, y1) = points[i + 1]
        if (x in x0..x1) {
            val t = ((x - x0) / (x1 - x0)).coerceIn(0f, 1f)
            return y0 + (y1 - y0) * t
        }
    }
    return points.last().second
}

val LocalLumaMotion = staticCompositionLocalOf { motionProfileFor(60f) }
