package com.example.engine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Controls real, absolute Android system screen brightness (1:1 with the phone's Quick Settings
 * slider) instead of an isolated per-window brightness override whenever WRITE_SETTINGS is granted.
 * Falls back cleanly to the current window brightness if the user has not yet enabled WRITE_SETTINGS.
 */
object SystemBrightnessController {

    private const val PERCEPTUAL_GAMMA = 2.15

    fun canWriteSystemSettings(context: Context): Boolean =
        runCatching { Settings.System.canWrite(context) }.getOrDefault(false)

    fun openWriteSettingsPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    /**
     * Reads the device's true hardware maximum brightness scale (255 on AOSP, up to 1023/4095 on some OEMs).
     */
    private fun getMaxBrightnessInt(context: Context): Int {
        return runCatching {
            val resId = context.resources.getIdentifier(
                "config_screenBrightnessSettingMaximum",
                "integer",
                "android"
            )
            if (resId > 0) context.resources.getInteger(resId).coerceAtLeast(255) else 255
        }.getOrDefault(255)
    }

    private fun getMinBrightnessInt(context: Context): Int {
        return runCatching {
            val resId = context.resources.getIdentifier(
                "config_screenBrightnessSettingMinimum",
                "integer",
                "android"
            )
            if (resId > 0) context.resources.getInteger(resId).coerceIn(1, 30) else 1
        }.getOrDefault(1)
    }

    /**
     * Reads the phone's exact current system brightness mapped onto a smooth perceptual 0f..1f slider scale.
     */
    fun readSystemBrightnessFraction(context: Context): Float {
        val minVal = getMinBrightnessInt(context)
        val maxVal = getMaxBrightnessInt(context)
        val raw = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault((maxVal * 0.5f).roundToInt())

        // Some OEMs report 0..255 even when resource says higher; normalize safely
        val effectiveMax = if (raw > 255) maxVal.coerceAtLeast(raw) else 255.coerceAtMost(maxVal)
        val linear = ((raw - minVal).toFloat() / (effectiveMax - minVal).coerceAtLeast(1).toFloat())
            .coerceIn(0f, 1f)
        return linear.toDouble().pow(1.0 / PERCEPTUAL_GAMMA).toFloat().coerceIn(0.01f, 1f)
    }

    /**
     * Applies absolute phone brightness:
     * - When WRITE_SETTINGS is granted: writes directly to Settings.System.SCREEN_BRIGHTNESS and keeps
     *   the Activity window override at BRIGHTNESS_OVERRIDE_NONE (-1f) so the reader matches the
     *   system brightness bar 1:1 with zero "separate volume" mismatch.
     * - Otherwise: falls back smoothly to the Activity window's screenBrightness.
     */
    fun applyAbsoluteBrightness(context: Context, activity: Activity?, perceptualFraction: Float) {
        val clamped = perceptualFraction.coerceIn(0.01f, 1f)
        val linear = clamped.toDouble().pow(PERCEPTUAL_GAMMA).toFloat().coerceIn(0.004f, 1f)

        if (canWriteSystemSettings(context)) {
            runCatching {
                val resolver = context.contentResolver
                val currentMode = Settings.System.getInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                if (currentMode != Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) {
                    Settings.System.putInt(
                        resolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                    )
                }
                val minVal = getMinBrightnessInt(context)
                val maxVal = getMaxBrightnessInt(context)
                // Always stay within standard 0..255 unless the device already reports > 255
                val currentRaw = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
                val targetMax = if (currentRaw > 255) maxVal else 255
                val targetInt = (minVal + linear * (targetMax - minVal)).roundToInt().coerceIn(minVal, targetMax)
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, targetInt)
            }
            // Clear any per-window override so system brightness IS the single source of truth
            activity?.window?.let { win ->
                val lp = win.attributes
                if (lp.screenBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    win.attributes = lp
                }
            }
        } else {
            activity?.window?.let { win ->
                val lp = win.attributes
                lp.screenBrightness = linear.coerceIn(0.02f, 1f)
                win.attributes = lp
            }
        }
    }

    /**
     * Clears any temporary per-window brightness override when leaving the reader.
     */
    fun clearWindowOverride(activity: Activity?) {
        activity?.window?.let { win ->
            val lp = win.attributes
            if (lp.screenBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                win.attributes = lp
            }
        }
    }

    /**
     * Emits real-time updates whenever the phone's system brightness changes (including from Android's
     * notification shade / Quick Settings), keeping Luma's slider synchronized 1:1.
     */
    fun observeSystemBrightness(context: Context): Flow<Float> = callbackFlow {
        val resolver = context.contentResolver
        trySend(readSystemBrightnessFraction(context))

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(readSystemBrightnessFraction(context))
            }
        }
        val uri = Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS)
        runCatching { resolver.registerContentObserver(uri, false, observer) }

        awaitClose {
            runCatching { resolver.unregisterContentObserver(observer) }
        }
    }.distinctUntilChanged()
}
