package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AiProviderType
import com.example.data.model.BookmarkDockSide
import com.example.data.model.DEFAULT_ISLAND_ALLOWED_PACKAGES
import com.example.data.model.IslandNotificationFilterMode
import com.example.data.model.PageAnimationMode
import com.example.data.model.ReaderFont
import com.example.data.model.ReaderMenuStyle
import com.example.data.model.ReaderPreferences
import com.example.data.model.ReadingTheme
import com.example.data.model.StandardPageFormat
import com.example.data.model.TargetFpsOption
import com.example.data.model.TextAlignOption
import com.example.data.model.TranslationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.readerDataStore: DataStore<Preferences> by preferencesDataStore(name = "luma_preferences")

/**
 * Optimized DataStore repository:
 * - Single shared in-memory state (`preferencesFlow: StateFlow<ReaderPreferences>`) loaded once off-main-thread
 *   so multiple screens/ViewModels never re-parse all 41 preference keys redundantly.
 * - Differential writes: `DataStore.edit` mutates ONLY the exact keys that changed instead of rewriting
 *   all 41 entries on every update.
 * - Automatic slider debouncing (220 ms): continuous slider adjustments (font size, line height, margins,
 *   warm filter, night dimming, dock scale/position, brightness) update UI state optimistically at 0 ms
 *   and coalesce flash disk writes into a single differential write once the user pauses/releases.
 */
class PreferencesRepository private constructor(private val appContext: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val USE_SYSTEM_FONT = booleanPreferencesKey("use_system_font")
        val FONT = stringPreferencesKey("font")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val MARGIN_DP = floatPreferencesKey("margin_dp")
        val TEXT_ALIGN = stringPreferencesKey("text_align")
        val PAGE_ANIMATION = stringPreferencesKey("page_animation")
        val MENU_STYLE = stringPreferencesKey("menu_style")
        val DOCK_SIDE = stringPreferencesKey("dock_side")
        val DOCK_Y_FRACTION = floatPreferencesKey("dock_y_fraction")
        val DOCK_SCALE = floatPreferencesKey("dock_scale")
        val READER_BRIGHTNESS = floatPreferencesKey("reader_brightness")
        val TARGET_FPS = stringPreferencesKey("target_fps")
        val SHOW_FPS_COUNTER = booleanPreferencesKey("show_fps_counter")
        val SHOW_FOOTER = booleanPreferencesKey("show_footer")
        val TAP_ZONES_ENABLED = booleanPreferencesKey("tap_zones_enabled")
        val PDF_NIGHT_FILTER = booleanPreferencesKey("pdf_night_filter")
        val AUTO_OCR_PDF = booleanPreferencesKey("auto_ocr_pdf")
        val TRANSLATION_ENGINE = stringPreferencesKey("translation_engine")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AI_API_KEY = stringPreferencesKey("ai_api_key")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val AI_BASE_URL = stringPreferencesKey("ai_base_url")
        val ISLAND_FILTER_MODE = stringPreferencesKey("island_filter_mode")
        val ISLAND_ALLOWED_PACKAGES = stringSetPreferencesKey("island_allowed_packages")
        val ISLAND_CRITICAL_BATTERY = booleanPreferencesKey("island_critical_battery")
        val ISLAND_CRITICAL_CONNECTIVITY = booleanPreferencesKey("island_critical_connectivity")
        val ISLAND_ALLOW_CALLS = booleanPreferencesKey("island_allow_calls")
        val USE_STANDARD_PAGE_VIEW = booleanPreferencesKey("use_standard_page_view")
        val STANDARD_PAGE_FORMAT = stringPreferencesKey("standard_page_format")
        val WARM_FILTER_STRENGTH = floatPreferencesKey("warm_filter_strength")
        val NIGHT_MODE_ENABLED = booleanPreferencesKey("night_mode_enabled")
        val NIGHT_AUTO_SCHEDULE = booleanPreferencesKey("night_auto_schedule")
        val NIGHT_START_HOUR = intPreferencesKey("night_start_hour")
        val NIGHT_END_HOUR = intPreferencesKey("night_end_hour")
        val PREFERRED_DAY_THEME = stringPreferencesKey("preferred_day_theme")
        val PREFERRED_NIGHT_THEME = stringPreferencesKey("preferred_night_theme")
        val NIGHT_TEXT_DIMMING = floatPreferencesKey("night_text_dimming")
        val AUTO_HIDE_CHROME_ENABLED = booleanPreferencesKey("auto_hide_chrome_enabled")
        val AUTO_HIDE_CHROME_DELAY_SEC = intPreferencesKey("auto_hide_chrome_delay_sec")
        val AUTO_HIDE_FADE_SPEED_MS = intPreferencesKey("auto_hide_fade_speed_ms")
        val DISK_CACHE_LIMIT_MB = intPreferencesKey("disk_cache_limit_mb")
        val HAS_SEEN_GUIDE = booleanPreferencesKey("has_seen_guide")
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val SEEDED_SAMPLE_VERSION = intPreferencesKey("seeded_sample_version")
    }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    private val _preferences = MutableStateFlow(ReaderPreferences())
    val preferencesFlow: StateFlow<ReaderPreferences> = _preferences.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    @Volatile
    private var lastPersisted: ReaderPreferences = ReaderPreferences()

    @Volatile
    private var hasPendingDebouncedWrite: Boolean = false

    private var debounceFlushJob: Job? = null

    init {
        ioScope.launch {
            appContext.readerDataStore.data.collect { prefs ->
                val mapped = mapFromDataStore(prefs)
                lastPersisted = mapped
                com.example.engine.LumaDiskCacheManager.setLimitMb(mapped.diskCacheLimitMb)
                // Do not clobber in-flight optimistic slider drags before the debounced flush completes
                if (!hasPendingDebouncedWrite) {
                    _preferences.value = mapped
                }
                if (!_isLoaded.value) {
                    _isLoaded.value = true
                }
            }
        }
    }

    /** Suspends until the initial DataStore snapshot has been read off the main thread. */
    suspend fun awaitInitialPreferences(): ReaderPreferences {
        if (!_isLoaded.value) {
            _isLoaded.first { it }
        }
        return _preferences.value
    }

    /**
     * Updates preferences with differential persistence:
     * - Continuous slider-only mutations update UI state immediately (0 ms) and debounce disk I/O (220 ms).
     * - Discrete toggles/selections persist immediately, mutating ONLY the keys that actually changed.
     */
    suspend fun updatePreferences(transform: (ReaderPreferences) -> ReaderPreferences) {
        if (!_isLoaded.value) {
            awaitInitialPreferences()
        }
        val previous = _preferences.value
        val updated = sanitize(transform(previous))
        if (updated == previous) return

        // Optimistic instant update for 60/120 fps UI responsiveness
        _preferences.value = updated

        if (isSliderOnlyMutation(previous, updated)) {
            scheduleDebouncedFlush()
        } else {
            debounceFlushJob?.cancel()
            hasPendingDebouncedWrite = false
            val limitChanged = updated.diskCacheLimitMb != previous.diskCacheLimitMb
            if (limitChanged) {
                com.example.engine.LumaDiskCacheManager.setLimitMb(updated.diskCacheLimitMb)
            }
            ioScope.launch {
                persistDiff(updated)
                if (limitChanged) {
                    com.example.engine.LumaDiskCacheManager.enforceGlobalBudget(appContext)
                }
            }
        }
    }

    /** Immediately flushes any pending debounced slider change to disk (call from Activity.onPause). */
    fun flushPendingWritesAsync() {
        if (!hasPendingDebouncedWrite) return
        debounceFlushJob?.cancel()
        val snapshot = _preferences.value
        ioScope.launch {
            hasPendingDebouncedWrite = false
            persistDiff(snapshot)
        }
    }

    private fun scheduleDebouncedFlush() {
        hasPendingDebouncedWrite = true
        debounceFlushJob?.cancel()
        debounceFlushJob = ioScope.launch {
            delay(SLIDER_DEBOUNCE_MS)
            val snapshot = _preferences.value
            hasPendingDebouncedWrite = false
            persistDiff(snapshot)
        }
    }

    private suspend fun persistDiff(target: ReaderPreferences) {
        writeMutex.withLock {
            val base = lastPersisted
            if (target == base) return
            appContext.readerDataStore.edit { prefs ->
                writeChangedKeys(prefs, base, target)
            }
            lastPersisted = target
        }
    }

    /**
     * Returns true when ONLY continuous slider / drag numeric fields changed between [prev] and [next].
     */
    private fun isSliderOnlyMutation(prev: ReaderPreferences, next: ReaderPreferences): Boolean {
        val normalizedNext = next.copy(
            fontSizeSp = prev.fontSizeSp,
            lineHeightMultiplier = prev.lineHeightMultiplier,
            horizontalMarginDp = prev.horizontalMarginDp,
            dockYFraction = prev.dockYFraction,
            dockScale = prev.dockScale,
            readerBrightness = prev.readerBrightness,
            warmFilterStrength = prev.warmFilterStrength,
            nightTextDimming = prev.nightTextDimming
        )
        return normalizedNext == prev
    }

    private fun sanitize(raw: ReaderPreferences): ReaderPreferences = raw.copy(
        fontSizeSp = raw.fontSizeSp.coerceIn(14f, 28f),
        lineHeightMultiplier = raw.lineHeightMultiplier.coerceIn(1.2f, 2.2f),
        horizontalMarginDp = raw.horizontalMarginDp.coerceIn(12f, 44f),
        dockYFraction = raw.dockYFraction.coerceIn(0.14f, 0.86f),
        dockScale = raw.dockScale.coerceIn(0.85f, 1.35f),
        warmFilterStrength = raw.warmFilterStrength.coerceIn(0f, 0.45f),
        nightStartHour = raw.nightStartHour.coerceIn(0, 23),
        nightEndHour = raw.nightEndHour.coerceIn(0, 23),
        nightTextDimming = raw.nightTextDimming.coerceIn(0f, 0.38f),
        autoHideReaderChromeDelaySec = raw.autoHideReaderChromeDelaySec.coerceIn(3, 60),
        autoHideFadeSpeedMs = raw.autoHideFadeSpeedMs.coerceIn(80, 600),
        diskCacheLimitMb = raw.diskCacheLimitMb.coerceIn(
            com.example.engine.LumaDiskCacheManager.MIN_LIMIT_MB,
            com.example.engine.LumaDiskCacheManager.MAX_LIMIT_MB
        )
    )

    private fun mapFromDataStore(prefs: Preferences): ReaderPreferences {
        val provider = enumOrDefault(prefs[Keys.AI_PROVIDER], AiProviderType.GEMINI)
        return ReaderPreferences(
            theme = enumOrDefault(prefs[Keys.THEME], ReadingTheme.LIGHT),
            useSystemFont = prefs[Keys.USE_SYSTEM_FONT] ?: false,
            font = enumOrDefault(prefs[Keys.FONT], ReaderFont.LITERATA),
            fontSizeSp = (prefs[Keys.FONT_SIZE] ?: 18f).coerceIn(14f, 28f),
            lineHeightMultiplier = (prefs[Keys.LINE_HEIGHT] ?: 1.6f).coerceIn(1.2f, 2.2f),
            horizontalMarginDp = (prefs[Keys.MARGIN_DP] ?: 22f).coerceIn(12f, 44f),
            textAlign = enumOrDefault(prefs[Keys.TEXT_ALIGN], TextAlignOption.START),
            pageAnimation = enumOrDefault(prefs[Keys.PAGE_ANIMATION], PageAnimationMode.BOOK_CURL),
            menuStyle = enumOrDefault(prefs[Keys.MENU_STYLE], ReaderMenuStyle.BOOKMARK_DOCK),
            dockSide = enumOrDefault(prefs[Keys.DOCK_SIDE], BookmarkDockSide.RIGHT),
            dockYFraction = (prefs[Keys.DOCK_Y_FRACTION] ?: 0.48f).coerceIn(0.14f, 0.86f),
            dockScale = (prefs[Keys.DOCK_SCALE] ?: 1.0f).coerceIn(0.85f, 1.35f),
            readerBrightness = prefs[Keys.READER_BRIGHTNESS] ?: -1f,
            targetFps = enumOrDefault(prefs[Keys.TARGET_FPS], TargetFpsOption.AUTO),
            showFpsCounter = prefs[Keys.SHOW_FPS_COUNTER] ?: false,
            showMinimalFooter = prefs[Keys.SHOW_FOOTER] ?: true,
            tapZonesEnabled = prefs[Keys.TAP_ZONES_ENABLED] ?: true,
            pdfNightFilter = prefs[Keys.PDF_NIGHT_FILTER] ?: false,
            autoOcrPdf = prefs[Keys.AUTO_OCR_PDF] ?: true,
            translationEngine = enumOrDefault(prefs[Keys.TRANSLATION_ENGINE], TranslationEngine.GOOGLE_TRANSLATE),
            aiProvider = provider,
            aiApiKey = prefs[Keys.AI_API_KEY].orEmpty(),
            aiModel = prefs[Keys.AI_MODEL]?.takeIf { it.isNotBlank() } ?: provider.defaultModel,
            aiBaseUrl = prefs[Keys.AI_BASE_URL]?.takeIf { it.isNotBlank() } ?: provider.defaultBaseUrl,
            islandFilterMode = enumOrDefault(prefs[Keys.ISLAND_FILTER_MODE], IslandNotificationFilterMode.ALLOWED_ONLY),
            islandAllowedPackages = prefs[Keys.ISLAND_ALLOWED_PACKAGES] ?: DEFAULT_ISLAND_ALLOWED_PACKAGES,
            islandShowCriticalBattery = prefs[Keys.ISLAND_CRITICAL_BATTERY] ?: true,
            islandShowCriticalConnectivity = prefs[Keys.ISLAND_CRITICAL_CONNECTIVITY] ?: true,
            islandAllowIncomingCalls = prefs[Keys.ISLAND_ALLOW_CALLS] ?: true,
            useStandardPageView = prefs[Keys.USE_STANDARD_PAGE_VIEW] ?: false,
            standardPageFormat = enumOrDefault(prefs[Keys.STANDARD_PAGE_FORMAT], StandardPageFormat.AUTO),
            warmFilterStrength = (prefs[Keys.WARM_FILTER_STRENGTH] ?: 0f).coerceIn(0f, 0.45f),
            nightModeEnabled = prefs[Keys.NIGHT_MODE_ENABLED] ?: false,
            nightAutoSchedule = prefs[Keys.NIGHT_AUTO_SCHEDULE] ?: false,
            nightStartHour = (prefs[Keys.NIGHT_START_HOUR] ?: 20).coerceIn(0, 23),
            nightEndHour = (prefs[Keys.NIGHT_END_HOUR] ?: 7).coerceIn(0, 23),
            preferredDayTheme = enumOrDefault(prefs[Keys.PREFERRED_DAY_THEME], ReadingTheme.WARM_CREAM),
            preferredNightTheme = enumOrDefault(prefs[Keys.PREFERRED_NIGHT_THEME], ReadingTheme.WARM_NIGHT),
            nightTextDimming = (prefs[Keys.NIGHT_TEXT_DIMMING] ?: 0f).coerceIn(0f, 0.38f),
            autoHideReaderChromeEnabled = prefs[Keys.AUTO_HIDE_CHROME_ENABLED] ?: true,
            autoHideReaderChromeDelaySec = (prefs[Keys.AUTO_HIDE_CHROME_DELAY_SEC] ?: 8).coerceIn(3, 60),
            autoHideFadeSpeedMs = (prefs[Keys.AUTO_HIDE_FADE_SPEED_MS] ?: 220).coerceIn(80, 600),
            diskCacheLimitMb = (prefs[Keys.DISK_CACHE_LIMIT_MB] ?: com.example.engine.LumaDiskCacheManager.DEFAULT_LIMIT_MB)
                .coerceIn(
                    com.example.engine.LumaDiskCacheManager.MIN_LIMIT_MB,
                    com.example.engine.LumaDiskCacheManager.MAX_LIMIT_MB
                ),
            hasSeenGuide = prefs[Keys.HAS_SEEN_GUIDE] ?: false,
            hasCompletedOnboarding = prefs[Keys.HAS_COMPLETED_ONBOARDING] ?: false,
            seededSampleVersion = prefs[Keys.SEEDED_SAMPLE_VERSION] ?: 0
        )
    }

    /**
     * Mutates ONLY keys whose values differ from [prev], avoiding 41-key rewrites on small changes.
     */
    private fun writeChangedKeys(prefs: MutablePreferences, prev: ReaderPreferences, next: ReaderPreferences) {
        if (next.theme != prev.theme) prefs[Keys.THEME] = next.theme.name
        if (next.useSystemFont != prev.useSystemFont) prefs[Keys.USE_SYSTEM_FONT] = next.useSystemFont
        if (next.font != prev.font) prefs[Keys.FONT] = next.font.name
        if (next.fontSizeSp != prev.fontSizeSp) prefs[Keys.FONT_SIZE] = next.fontSizeSp
        if (next.lineHeightMultiplier != prev.lineHeightMultiplier) prefs[Keys.LINE_HEIGHT] = next.lineHeightMultiplier
        if (next.horizontalMarginDp != prev.horizontalMarginDp) prefs[Keys.MARGIN_DP] = next.horizontalMarginDp
        if (next.textAlign != prev.textAlign) prefs[Keys.TEXT_ALIGN] = next.textAlign.name
        if (next.pageAnimation != prev.pageAnimation) prefs[Keys.PAGE_ANIMATION] = next.pageAnimation.name
        if (next.menuStyle != prev.menuStyle) prefs[Keys.MENU_STYLE] = next.menuStyle.name
        if (next.dockSide != prev.dockSide) prefs[Keys.DOCK_SIDE] = next.dockSide.name
        if (next.dockYFraction != prev.dockYFraction) prefs[Keys.DOCK_Y_FRACTION] = next.dockYFraction
        if (next.dockScale != prev.dockScale) prefs[Keys.DOCK_SCALE] = next.dockScale
        if (next.readerBrightness != prev.readerBrightness) prefs[Keys.READER_BRIGHTNESS] = next.readerBrightness
        if (next.targetFps != prev.targetFps) prefs[Keys.TARGET_FPS] = next.targetFps.name
        if (next.showFpsCounter != prev.showFpsCounter) prefs[Keys.SHOW_FPS_COUNTER] = next.showFpsCounter
        if (next.showMinimalFooter != prev.showMinimalFooter) prefs[Keys.SHOW_FOOTER] = next.showMinimalFooter
        if (next.tapZonesEnabled != prev.tapZonesEnabled) prefs[Keys.TAP_ZONES_ENABLED] = next.tapZonesEnabled
        if (next.pdfNightFilter != prev.pdfNightFilter) prefs[Keys.PDF_NIGHT_FILTER] = next.pdfNightFilter
        if (next.autoOcrPdf != prev.autoOcrPdf) prefs[Keys.AUTO_OCR_PDF] = next.autoOcrPdf
        if (next.translationEngine != prev.translationEngine) prefs[Keys.TRANSLATION_ENGINE] = next.translationEngine.name
        if (next.aiProvider != prev.aiProvider) prefs[Keys.AI_PROVIDER] = next.aiProvider.name
        if (next.aiApiKey != prev.aiApiKey) prefs[Keys.AI_API_KEY] = next.aiApiKey
        if (next.aiModel != prev.aiModel) prefs[Keys.AI_MODEL] = next.aiModel
        if (next.aiBaseUrl != prev.aiBaseUrl) prefs[Keys.AI_BASE_URL] = next.aiBaseUrl
        if (next.islandFilterMode != prev.islandFilterMode) prefs[Keys.ISLAND_FILTER_MODE] = next.islandFilterMode.name
        if (next.islandAllowedPackages != prev.islandAllowedPackages) prefs[Keys.ISLAND_ALLOWED_PACKAGES] = next.islandAllowedPackages
        if (next.islandShowCriticalBattery != prev.islandShowCriticalBattery) prefs[Keys.ISLAND_CRITICAL_BATTERY] = next.islandShowCriticalBattery
        if (next.islandShowCriticalConnectivity != prev.islandShowCriticalConnectivity) prefs[Keys.ISLAND_CRITICAL_CONNECTIVITY] = next.islandShowCriticalConnectivity
        if (next.islandAllowIncomingCalls != prev.islandAllowIncomingCalls) prefs[Keys.ISLAND_ALLOW_CALLS] = next.islandAllowIncomingCalls
        if (next.useStandardPageView != prev.useStandardPageView) prefs[Keys.USE_STANDARD_PAGE_VIEW] = next.useStandardPageView
        if (next.standardPageFormat != prev.standardPageFormat) prefs[Keys.STANDARD_PAGE_FORMAT] = next.standardPageFormat.name
        if (next.warmFilterStrength != prev.warmFilterStrength) prefs[Keys.WARM_FILTER_STRENGTH] = next.warmFilterStrength
        if (next.nightModeEnabled != prev.nightModeEnabled) prefs[Keys.NIGHT_MODE_ENABLED] = next.nightModeEnabled
        if (next.nightAutoSchedule != prev.nightAutoSchedule) prefs[Keys.NIGHT_AUTO_SCHEDULE] = next.nightAutoSchedule
        if (next.nightStartHour != prev.nightStartHour) prefs[Keys.NIGHT_START_HOUR] = next.nightStartHour
        if (next.nightEndHour != prev.nightEndHour) prefs[Keys.NIGHT_END_HOUR] = next.nightEndHour
        if (next.preferredDayTheme != prev.preferredDayTheme) prefs[Keys.PREFERRED_DAY_THEME] = next.preferredDayTheme.name
        if (next.preferredNightTheme != prev.preferredNightTheme) prefs[Keys.PREFERRED_NIGHT_THEME] = next.preferredNightTheme.name
        if (next.nightTextDimming != prev.nightTextDimming) prefs[Keys.NIGHT_TEXT_DIMMING] = next.nightTextDimming
        if (next.autoHideReaderChromeEnabled != prev.autoHideReaderChromeEnabled) {
            prefs[Keys.AUTO_HIDE_CHROME_ENABLED] = next.autoHideReaderChromeEnabled
        }
        if (next.autoHideReaderChromeDelaySec != prev.autoHideReaderChromeDelaySec) {
            prefs[Keys.AUTO_HIDE_CHROME_DELAY_SEC] = next.autoHideReaderChromeDelaySec
        }
        if (next.autoHideFadeSpeedMs != prev.autoHideFadeSpeedMs) {
            prefs[Keys.AUTO_HIDE_FADE_SPEED_MS] = next.autoHideFadeSpeedMs
        }
        if (next.diskCacheLimitMb != prev.diskCacheLimitMb) {
            prefs[Keys.DISK_CACHE_LIMIT_MB] = next.diskCacheLimitMb
        }
        if (next.hasSeenGuide != prev.hasSeenGuide) prefs[Keys.HAS_SEEN_GUIDE] = next.hasSeenGuide
        if (next.hasCompletedOnboarding != prev.hasCompletedOnboarding) prefs[Keys.HAS_COMPLETED_ONBOARDING] = next.hasCompletedOnboarding
        if (next.seededSampleVersion != prev.seededSampleVersion) prefs[Keys.SEEDED_SAMPLE_VERSION] = next.seededSampleVersion
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T {
        if (name.isNullOrBlank()) return default
        return enumValues<T>().firstOrNull { it.name == name } ?: default
    }

    companion object {
        private const val SLIDER_DEBOUNCE_MS = 220L

        @Volatile
        private var INSTANCE: PreferencesRepository? = null

        fun getInstance(context: Context): PreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferencesRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
