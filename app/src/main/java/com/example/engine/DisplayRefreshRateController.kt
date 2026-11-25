package com.example.engine

import android.app.Activity
import android.os.Build
import android.view.Display
import com.example.data.model.TargetFpsOption
import kotlin.math.abs
import kotlin.math.roundToInt

data class DisplayRefreshInfo(
    val activeRefreshRateHz: Float,
    val maxSupportedHz: Float,
    val supportedRatesHz: List<Int>,
    val configuredRefreshRateHz: Float = activeRefreshRateHz
)

/**
 * Unlocks and applies high display refresh rates (60, 90, 120, 144, 240 Hz)
 * across Android 7–16 using Display.Mode matching, WindowManager.LayoutParams,
 * and Android 14/15+ touch-boost / ARR / View.requestedFrameRate APIs.
 */
object DisplayRefreshRateController {

    fun getDisplayInfo(activity: Activity): DisplayRefreshInfo {
        val display = resolveDisplay(activity)
            ?: return DisplayRefreshInfo(
                activeRefreshRateHz = 60f,
                maxSupportedHz = 60f,
                supportedRatesHz = listOf(60),
                configuredRefreshRateHz = 60f
            )

        val currentMode = display.mode
        val allModes = display.supportedModes.orEmpty()
        val sameResModes = allModes.filter {
            it.physicalWidth == currentMode.physicalWidth &&
                it.physicalHeight == currentMode.physicalHeight
        }.ifEmpty { allModes.toList() }

        val maxHz = sameResModes.maxOfOrNull { it.refreshRate } ?: currentMode.refreshRate
        val rates = sameResModes
            .map { it.refreshRate.roundToInt() }
            .distinct()
            .sorted()

        return DisplayRefreshInfo(
            activeRefreshRateHz = currentMode.refreshRate,
            maxSupportedHz = maxHz,
            supportedRatesHz = rates.ifEmpty { listOf(currentMode.refreshRate.roundToInt()) },
            configuredRefreshRateHz = currentMode.refreshRate
        )
    }

    fun applyRefreshRate(activity: Activity, option: TargetFpsOption): DisplayRefreshInfo {
        val window = activity.window
        val display = resolveDisplay(activity)
        val info = getDisplayInfo(activity)

        val desiredHz = if (option == TargetFpsOption.AUTO) {
            info.maxSupportedHz.coerceAtLeast(60f)
        } else {
            option.fps
        }

        var selectedModeHz = desiredHz.coerceAtMost(info.maxSupportedHz.coerceAtLeast(60f))
        val attrs = window.attributes
        if (display != null) {
            val currentMode = display.mode
            val sameResModes = display.supportedModes
                .orEmpty()
                .filter {
                    it.physicalWidth == currentMode.physicalWidth &&
                        it.physicalHeight == currentMode.physicalHeight
                }
                .ifEmpty { display.supportedModes.orEmpty().toList() }

            if (sameResModes.isNotEmpty()) {
                val targetMode = selectBestDisplayMode(sameResModes, desiredHz)
                attrs.preferredDisplayModeId = targetMode.modeId
                selectedModeHz = targetMode.refreshRate
            }
        }

        // Request the desired refresh rate directly on the window
        attrs.preferredRefreshRate = desiredHz
        window.attributes = attrs

        // Android 14+ (API 34): keep touch boost enabled for ultra-responsive scrolling & drawing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching {
                window.frameRateBoostOnTouchEnabled = true
            }
        }

        // Android 15+ (API 35): disable ARR power-saving downclocking when an explicit FPS is selected,
        // and propagate the requested frame rate to the root decorView.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            runCatching {
                window.isFrameRatePowerSavingsBalanced = (option == TargetFpsOption.AUTO)
                window.decorView.requestedFrameRate = desiredHz
            }
        }

        val updated = getDisplayInfo(activity)
        return updated.copy(
            configuredRefreshRateHz = selectedModeHz.coerceIn(60f, 240f)
        )
    }

    private fun selectBestDisplayMode(modes: List<Display.Mode>, desiredHz: Float): Display.Mode {
        // If desiredHz is >= the display's max rate (e.g., requesting 144/240 on a 120Hz panel),
        // pick the highest supported mode so the display unlocks its full hardware capability.
        val maxMode = modes.maxBy { it.refreshRate }
        if (desiredHz >= maxMode.refreshRate - 0.5f) {
            return maxMode
        }
        // Otherwise pick the mode closest to desiredHz (preferring >= desiredHz on ties)
        return modes.minWith(
            compareBy<Display.Mode> { abs(it.refreshRate - desiredHz) }
                .thenByDescending { it.refreshRate }
        )
    }

    @Suppress("DEPRECATION")
    private fun resolveDisplay(activity: Activity): Display? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            activity.windowManager.defaultDisplay
        }
    }
}
