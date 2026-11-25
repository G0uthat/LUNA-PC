package com.example.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark suite for Luma (`com.lord67.luma`) measuring:
 * 1) Cold Startup (TTID / TTFD) — comparing CompilationMode.None() vs Baseline Profile (Partial).
 * 2) Library scrolling frame timings (60/120 fps jank tracking).
 * 3) Reader page-turning frame timings across swipe gestures.
 *
 * Execute on a connected physical device or API 29+ emulator via:
 *   gradle :benchmark:connectedBenchmarkAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class LumaMacrobenchmarks {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupColdCompilationNone() = measureColdStartup(CompilationMode.None())

    @Test
    fun startupColdBaselineProfile() = measureColdStartup(CompilationMode.Partial())

    private fun measureColdStartup(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = {
                pressHome()
            }
        ) {
            startActivityAndWait()
            dismissOnboardingIfPresent()
        }
    }

    @Test
    fun libraryScrollFrameTiming() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                dismissOnboardingIfPresent()
            }
        ) {
            val content = device.findObject(By.scrollable(true))
                ?: device.findObject(By.pkg(TARGET_PACKAGE))
            content?.setGestureMargin(device.displayWidth / 8)
            repeat(3) {
                content?.fling(Direction.DOWN)
                device.waitForIdle()
            }
            repeat(3) {
                content?.fling(Direction.UP)
                device.waitForIdle()
            }
        }
    }

    @Test
    fun readerPageTurnFrameTiming() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                dismissOnboardingIfPresent()
                // Open the first available book card or "Continue Reading" hero card
                val continueCard = device.wait(Until.findObject(By.res("continue_reading_card")), 3_000L)
                val firstBook = continueCard
                    ?: device.wait(Until.findObject(By.res(Regex("book_card_.*").toPattern())), 3_000L)
                firstBook?.click()
                device.wait(Until.hasObject(By.res("reader_screen_root")), 5_000L)
                device.waitForIdle()
            }
        ) {
            val w = device.displayWidth
            val h = device.displayHeight
            // Swipe forward 4 pages, then backward 4 pages to measure pager smoothness
            repeat(4) {
                device.swipe((w * 0.85f).toInt(), h / 2, (w * 0.15f).toInt(), h / 2, 12)
                device.waitForIdle()
            }
            repeat(4) {
                device.swipe((w * 0.15f).toInt(), h / 2, (w * 0.85f).toInt(), h / 2, 12)
                device.waitForIdle()
            }
        }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.dismissOnboardingIfPresent() {
        val skipBtn = device.wait(Until.findObject(By.res("permissions_skip_all_btn")), 1_500L)
        if (skipBtn != null) {
            skipBtn.click()
            device.waitForIdle()
        }
        val tourSkip = device.wait(Until.findObject(By.res("tour_skip_button")), 1_000L)
        if (tourSkip != null) {
            tourSkip.click()
            device.waitForIdle()
        }
    }

    companion object {
        const val TARGET_PACKAGE = "com.lord67.luma"
    }
}
