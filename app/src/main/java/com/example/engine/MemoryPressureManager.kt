package com.example.engine

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.util.LruCache
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Central RAM-awareness & memory-pressure coordinator for Luma.
 *
 * - Detects low-RAM devices (`ActivityManager.isLowRamDevice`, total RAM <= 4 GiB, or heap class <= 192 MB).
 * - Supplies adaptive byte budgets and decode dimensions for Cover, PDF, and Slide bitmap caches.
 * - Coordinates `onTrimMemory(level)` / `onLowMemory()` from MainActivity to immediately release
 *   rebuildable bitmap caches and avoid GC pauses or background kills on budget/low-RAM phones.
 */
object MemoryPressureManager {

    @Volatile
    var isLowRamDevice: Boolean = false
        private set

    @Volatile
    var memoryClassMb: Int = 256
        private set

    @Volatile
    private var initialized = false

    private val trimmers = CopyOnWriteArrayList<Pair<String, (Int) -> Unit>>()

    fun init(context: Context) {
        if (initialized) return
        val appCtx = context.applicationContext
        val am = appCtx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am != null) {
            val memInfo = ActivityManager.MemoryInfo()
            runCatching { am.getMemoryInfo(memInfo) }
            val totalRamBytes = memInfo.totalMem
            val memClass = am.memoryClass.coerceAtLeast(64)
            memoryClassMb = memClass
            isLowRamDevice = am.isLowRamDevice ||
                (totalRamBytes in 1L..(4L * 1024L * 1024L * 1024L)) ||
                memClass <= 192
        } else {
            val maxHeapMb = (Runtime.getRuntime().maxMemory() / (1024L * 1024L)).toInt()
            memoryClassMb = maxHeapMb.coerceAtLeast(64)
            isLowRamDevice = maxHeapMb <= 192
        }
        initialized = true
    }

    fun coverCacheMaxKb(): Int = if (isLowRamDevice) 8 * 1024 else 20 * 1024

    fun coverDecodeWidth(): Int = if (isLowRamDevice) 300 else 420

    fun coverDecodeHeight(): Int = if (isLowRamDevice) 440 else 620

    fun slideCacheMaxKb(): Int = if (isLowRamDevice) 8 * 1024 else 18 * 1024

    fun slideDecodeMaxDim(): Int = if (isLowRamDevice) 960 else 1280

    fun pdfCacheMaxKb(): Int {
        val heapKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt().coerceAtLeast(32 * 1024)
        return if (isLowRamDevice) {
            (heapKb / 8).coerceIn(8 * 1024, 16 * 1024)
        } else {
            (heapKb / 6).coerceIn(12 * 1024, 36 * 1024)
        }
    }

    fun registerTrimmer(tag: String, onTrim: (Int) -> Unit) {
        if (trimmers.none { it.first == tag }) {
            trimmers.add(tag to onTrim)
        }
    }

    /**
     * Trims or evicts an LruCache cleanly according to Android's ComponentCallbacks2 trim level.
     */
    fun <K : Any, V : Any> trimLruCache(cache: LruCache<K, V>, level: Int) {
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
                level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                level == ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                cache.evictAll()
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
                level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                cache.trimToSize((cache.maxSize() / 2).coerceAtLeast(1))
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                cache.trimToSize(((cache.maxSize() * 3) / 4).coerceAtLeast(1))
            }
        }
    }

    fun trimAll(level: Int) {
        for ((_, trimmer) in trimmers) {
            runCatching { trimmer(level) }
        }
    }
}
