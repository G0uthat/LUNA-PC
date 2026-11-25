package com.example.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates an ART Baseline Profile covering Luma's Critical User Journeys:
 * 1) Cold startup into LibraryScreen
 * 2) Scrolling library catalog
 * 3) Opening a document into ReaderScreen and turning pages
 *
 * Run on an API 33+ physical device or rooted API 28+ emulator to capture fresh rules:
 *   gradle :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.benchmark.BaselineProfileGenerator
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateLumaBaselineProfile() {
        baselineProfileRule.collect(
            packageName = LumaMacrobenchmarks.TARGET_PACKAGE,
            includeInStartupProfile = true
        ) {
            pressHome()
            startActivityAndWait()

            // Dismiss first-launch onboarding / tour if present
            device.wait(Until.findObject(By.res("permissions_skip_all_btn")), 1_500L)?.click()
            device.wait(Until.findObject(By.res("tour_skip_button")), 1_000L)?.click()
            device.waitForIdle()

            // CUJ 1: Scroll library
            val scrollable = device.findObject(By.scrollable(true))
            scrollable?.setGestureMargin(device.displayWidth / 8)
            scrollable?.fling(Direction.DOWN)
            device.waitForIdle()
            scrollable?.fling(Direction.UP)
            device.waitForIdle()

            // CUJ 2: Open document and turn pages inside ReaderScreen
            val docCard = device.wait(Until.findObject(By.res("continue_reading_card")), 2_000L)
                ?: device.wait(Until.findObject(By.res(Regex("book_card_.*").toPattern())), 2_000L)
            if (docCard != null) {
                docCard.click()
                device.wait(Until.hasObject(By.res("reader_screen_root")), 5_000L)
                val w = device.displayWidth
                val h = device.displayHeight
                repeat(3) {
                    device.swipe((w * 0.85f).toInt(), h / 2, (w * 0.15f).toInt(), h / 2, 12)
                    device.waitForIdle()
                }
            }
        }
    }
}
