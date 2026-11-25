package com.example

import android.content.ComponentCallbacks2
import android.os.Bundle
import android.os.StrictMode
import android.os.SystemClock
import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.FolioDatabase
import com.example.data.local.FolioRepository
import com.example.data.local.PreferencesRepository
import com.example.data.model.DocumentFormat
import com.example.data.model.ReaderPreferences
import com.example.data.model.TargetFpsOption
import com.example.engine.DisplayRefreshRateController
import com.example.engine.DocumentParser
import com.example.engine.DynamicIslandEventManager
import com.example.engine.GitHubUpdateManager
import com.example.engine.MemoryPressureManager
import com.example.engine.ParsedDocumentDiskCache
import com.example.engine.SocialRepository
import com.example.engine.SystemMediaController
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import com.example.notebook.data.NotebookRepository
import com.example.notebook.ui.NotebookScreen
import com.example.notebook.ui.NotebookViewModel
import com.example.ui.components.DynamicIslandEventBanner
import com.example.ui.components.ExternalBatchImportDialog
import com.example.ui.components.ExternalBatchProgressState
import com.example.ui.components.ExternalImportChooserDialog
import com.example.ui.components.GitHubUpdateDialog
import com.example.ui.components.LocalLumaOverlayController
import com.example.ui.components.LumaOverlayController
import com.example.ui.components.LumaOverlayHost
import com.example.ui.components.PendingExternalFile
import com.example.ui.library.CoverBitmapCache
import com.example.ui.library.LibraryScreen
import com.example.ui.library.LibraryViewModel
import com.example.ui.reader.ReaderScreen
import com.example.ui.reader.ReaderViewModel
import com.example.ui.theme.FolioTheme
import com.example.ui.theme.LocalFolioPalette
import com.example.ui.theme.paletteFor
import com.example.ui.theme.resolveEffectiveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import kotlin.math.roundToInt

private data class RootThemeSlice(
    val theme: com.example.data.model.ReadingTheme,
    val useSystemFont: Boolean,
    val nightModeEnabled: Boolean,
    val nightAutoSchedule: Boolean,
    val nightStartHour: Int,
    val nightEndHour: Int,
    val preferredDayTheme: com.example.data.model.ReadingTheme,
    val preferredNightTheme: com.example.data.model.ReadingTheme,
    val nightTextDimming: Float,
    val warmFilterStrength: Float,
    val targetFps: TargetFpsOption,
    val showFpsCounter: Boolean
)

private fun ReaderPreferences.toRootThemeSlice(): RootThemeSlice = RootThemeSlice(
    theme = theme,
    useSystemFont = useSystemFont,
    nightModeEnabled = nightModeEnabled,
    nightAutoSchedule = nightAutoSchedule,
    nightStartHour = nightStartHour,
    nightEndHour = nightEndHour,
    preferredDayTheme = preferredDayTheme,
    preferredNightTheme = preferredNightTheme,
    nightTextDimming = nightTextDimming,
    warmFilterStrength = warmFilterStrength,
    targetFps = targetFps,
    showFpsCounter = showFpsCounter
)

class MainActivity : ComponentActivity() {

    private var currentTargetFps: TargetFpsOption = TargetFpsOption.AUTO
    private var lastAppliedTargetFps: TargetFpsOption? = null
    private var deferredServicesStarted = false
    private val activeRefreshRateState = mutableFloatStateOf(60f)

    // Lazy singletons — zero synchronous Room/SQLite/DataStore disk blocking in onCreate
    private val preferencesRepository by lazy { PreferencesRepository.getInstance(applicationContext) }
    private val folioRepository by lazy {
        FolioRepository(FolioDatabase.getInstance(applicationContext).folioDao())
    }
    // Strictly lazy Study Notebook repository: NEVER instantiated at app launch until user opens Cuadernos!
    private val notebookRepository by lazy {
        NotebookRepository(applicationContext, FolioDatabase.getInstance(applicationContext).notebookDao())
    }
    private data class IncomingExternalIntent(
        val id: Long = System.nanoTime(),
        val action: String,
        val uris: List<Uri>
    )

    private val pendingExternalIntentState = mutableStateOf<IncomingExternalIntent?>(null)

    private fun extractAndConsumeExternalIntent(intent: Intent?): IncomingExternalIntent? {
        if (intent == null) return null
        // Never re-import when Android relaunches the activity from Recents history
        if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return null

        val action = intent.action ?: return null
        val collected = LinkedHashSet<Uri>()

        when (action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { collected.add(it) }
            }
            Intent.ACTION_SEND -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let {
                    collected.add(it)
                }
                intent.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) {
                        clip.getItemAt(i)?.uri?.let { collected.add(it) }
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.forEach { collected.add(it) }
                intent.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) {
                        clip.getItemAt(i)?.uri?.let { collected.add(it) }
                    }
                }
            }
            else -> return null
        }

        if (collected.isEmpty()) return null

        // Consume the intent immediately so rotation or returning from background NEVER re-imports!
        val consumed = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        setIntent(consumed)

        return IncomingExternalIntent(action = action, uris = collected.toList())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractAndConsumeExternalIntent(intent)?.let { req ->
            pendingExternalIntentState.value = req
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .detectLeakedRegistrationObjects()
                    .penaltyLog()
                    .build()
            )
        }
        val launchStartMs = SystemClock.elapsedRealtime()
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Detect low-RAM devices immediately so bitmap caches and motion budgets adapt from frame 0
        MemoryPressureManager.init(applicationContext)

        // Kick off non-blocking DataStore preload on Dispatchers.IO immediately
        val prefsRepo = preferencesRepository
        // Hold splash only while initial preferences load (hard-capped at 220 ms for instant TTID)
        splashScreen.setKeepOnScreenCondition {
            !prefsRepo.isLoaded.value && (SystemClock.elapsedRealtime() - launchStartMs) < 220L
        }

        enableEdgeToEdge()

        // Capture & consume any file(s) launched via "Abrir con Luma" or "Compartir"
        // (strictly skipped on config-change recreation where savedInstanceState != null)
        if (savedInstanceState == null) {
            extractAndConsumeExternalIntent(intent)?.let { req ->
                pendingExternalIntentState.value = req
            }
        }

        // Defer all non-first-frame subsystems until AFTER the first frame is drawn
        window.decorView.post {
            startDeferredBackgroundServices()
        }

        setContent {
            // Observe ONLY root-relevant preference slices so reader slider edits / dock drags
            // never recompose the entire MainActivity root!
            val rootSliceFlow = remember(prefsRepo) {
                prefsRepo.preferencesFlow
                    .map { it.toRootThemeSlice() }
                    .distinctUntilChanged()
            }
            val rootSlice by rootSliceFlow.collectAsStateWithLifecycle(
                initialValue = prefsRepo.preferencesFlow.value.toRootThemeSlice()
            )

            // Sync Dynamic Island policy without recomposing UI tree
            LaunchedEffect(prefsRepo) {
                prefsRepo.preferencesFlow
                    .map { prefs ->
                        listOf(
                            prefs.islandFilterMode,
                            prefs.islandAllowedPackages,
                            prefs.islandShowCriticalBattery,
                            prefs.islandShowCriticalConnectivity,
                            prefs.islandAllowIncomingCalls
                        )
                    }
                    .distinctUntilChanged()
                    .collect {
                        DynamicIslandEventManager.updatePolicy(prefsRepo.preferencesFlow.value)
                    }
            }

            var currentHour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
            LaunchedEffect(rootSlice.nightAutoSchedule) {
                if (!rootSlice.nightAutoSchedule) return@LaunchedEffect
                while (true) {
                    currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    delay(60_000L)
                }
            }

            val effectiveTheme = remember(
                rootSlice.theme,
                rootSlice.nightModeEnabled,
                rootSlice.nightAutoSchedule,
                rootSlice.nightStartHour,
                rootSlice.nightEndHour,
                rootSlice.preferredDayTheme,
                rootSlice.preferredNightTheme,
                currentHour
            ) {
                resolveEffectiveTheme(
                    prefs = ReaderPreferences(
                        theme = rootSlice.theme,
                        nightModeEnabled = rootSlice.nightModeEnabled,
                        nightAutoSchedule = rootSlice.nightAutoSchedule,
                        nightStartHour = rootSlice.nightStartHour,
                        nightEndHour = rootSlice.nightEndHour,
                        preferredDayTheme = rootSlice.preferredDayTheme,
                        preferredNightTheme = rootSlice.preferredNightTheme
                    ),
                    currentHour = currentHour
                )
            }
            val palette = remember(effectiveTheme) { paletteFor(effectiveTheme) }

            LaunchedEffect(rootSlice.targetFps) {
                currentTargetFps = rootSlice.targetFps
                if (lastAppliedTargetFps != rootSlice.targetFps) {
                    lastAppliedTargetFps = rootSlice.targetFps
                    val applied = DisplayRefreshRateController.applyRefreshRate(
                        this@MainActivity,
                        rootSlice.targetFps
                    )
                    activeRefreshRateState.floatValue = applied.configuredRefreshRateHz
                }
            }

            LaunchedEffect(palette.isDark) {
                if (palette.isDark) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    )
                } else {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        ),
                        navigationBarStyle = SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    )
                }
            }

            val activeNightDimming = if (effectiveTheme.isDark) rootSlice.nightTextDimming else 0f
            val effectiveRefreshRateHz by activeRefreshRateState

            FolioTheme(
                readingTheme = effectiveTheme,
                useSystemFont = rootSlice.useSystemFont,
                softNightDimming = activeNightDimming,
                effectiveRefreshRateHz = effectiveRefreshRateHz
            ) {
                var activeDocumentId by rememberSaveable { mutableStateOf<Long?>(null) }
                var activeNotebookId by rememberSaveable { mutableStateOf<Long?>(null) }
                var pendingExternalFile by remember { mutableStateOf<PendingExternalFile?>(null) }
                var isHandlingExternalFile by remember { mutableStateOf(false) }
                var batchImportProgress by remember { mutableStateOf<ExternalBatchProgressState?>(null) }

                // Active session flush hooks: saves current Notebook or Reader state before navigating away
                var activeNotebookFlush by remember { mutableStateOf<(() -> Unit)?>(null) }
                var activeReaderFlush by remember { mutableStateOf<(() -> Unit)?>(null) }
                val flushActiveSessionBeforeNavigate: () -> Unit = {
                    activeNotebookFlush?.invoke()
                    activeReaderFlush?.invoke()
                }

                // Only allow media progress ticker when the reader is actually open
                LaunchedEffect(activeDocumentId) {
                    SystemMediaController.setReaderActive(activeDocumentId != null)
                }

                val libraryViewModel: LibraryViewModel = viewModel(
                    factory = remember {
                        LibraryViewModel.Factory(application, folioRepository, prefsRepo)
                    }
                )

                // Resolve incoming "Abrir con Luma" / "Compartir" / "Enviar a un cuaderno" URI(s) off main thread
                val incomingReq by pendingExternalIntentState
                LaunchedEffect(incomingReq?.id) {
                    val req = incomingReq ?: return@LaunchedEffect
                    pendingExternalIntentState.value = null

                    if (req.uris.size > 1) {
                        // Multiple shared files (ACTION_SEND_MULTIPLE): inspect off main thread
                        val inspections = kotlinx.coroutines.withContext(Dispatchers.IO) {
                            req.uris.map { libraryViewModel.resolveExternalFileInfo(it) }
                        }
                        val validImages = inspections.filter { it.isImage && it.errorReason == null }
                        val validDocs = inspections.filter { !it.isImage && it.isSupported }

                        if (validImages.isNotEmpty() && validDocs.isEmpty()) {
                            // Multiple images shared -> create multi-page Study Notebook with progress!
                            batchImportProgress = ExternalBatchProgressState(1, validImages.size, validImages.first().displayName)
                            val title = validImages.first().displayName.substringBeforeLast('.').ifBlank { "Cuaderno de imágenes" }
                            val newNbId = notebookRepository.createNotebookFromImageUris(
                                title = title,
                                imageUris = validImages.map { it.uri },
                                onProgress = { done, total ->
                                    batchImportProgress = ExternalBatchProgressState(
                                        currentIndex = done,
                                        totalCount = total,
                                        currentFileName = validImages.getOrNull(done - 1)?.displayName ?: "Imagen $done"
                                    )
                                }
                            )
                            batchImportProgress = null
                            flushActiveSessionBeforeNavigate()
                            activeDocumentId = null
                            activeNotebookId = newNbId
                        } else {
                            // Batch document import with progress; opens the last imported document automatically
                            batchImportProgress = ExternalBatchProgressState(1, req.uris.size, inspections.firstOrNull()?.displayName ?: "Archivo 1")
                            libraryViewModel.importMultipleDocuments(
                                uris = req.uris,
                                onProgress = { idx, total, name ->
                                    batchImportProgress = ExternalBatchProgressState(idx, total, name)
                                },
                                onComplete = { summary ->
                                    batchImportProgress = null
                                    summary.lastDocumentId?.let { lastId ->
                                        flushActiveSessionBeforeNavigate()
                                        activeNotebookId = null
                                        activeDocumentId = lastId
                                    }
                                }
                            )
                        }
                    } else {
                        val singleUri = req.uris.first()
                        val resolved = kotlinx.coroutines.withContext(Dispatchers.IO) {
                            libraryViewModel.resolveExternalFileInfo(singleUri)
                        }
                        if (req.action == Intent.ACTION_SEND && resolved.isImage && resolved.isSupported) {
                            // "Enviar a un cuaderno" direct 1-tap image flow
                            batchImportProgress = ExternalBatchProgressState(1, 1, resolved.displayName)
                            val cleanTitle = resolved.displayName.substringBeforeLast('.').ifBlank { "Apunte con imagen" }
                            val newNbId = notebookRepository.createNotebookFromImageUri(cleanTitle, resolved.uri)
                            batchImportProgress = null
                            flushActiveSessionBeforeNavigate()
                            activeDocumentId = null
                            activeNotebookId = newNbId
                        } else {
                            pendingExternalFile = resolved
                        }
                    }
                }

                val overlayController = remember { LumaOverlayController() }

                CompositionLocalProvider(LocalLumaOverlayController provides overlayController) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val docId = activeDocumentId
                        val nbId = activeNotebookId
                        when {
                            nbId != null -> {
                                // Strictly lazy Notebook Studio instantiation: only created when a Notebook is opened
                                val notebookViewModel: NotebookViewModel = viewModel(
                                    key = "notebook_$nbId",
                                    factory = remember(nbId) {
                                        NotebookViewModel.Factory(application, nbId, notebookRepository)
                                    }
                                )
                                DisposableEffect(notebookViewModel) {
                                    activeNotebookFlush = { notebookViewModel.flushPendingSaveAsync() }
                                    onDispose { activeNotebookFlush = null }
                                }
                                NotebookScreen(
                                    viewModel = notebookViewModel,
                                    onBackToLibrary = { activeNotebookId = null },
                                    onImportPdfToLibrary = { pdfFile, title ->
                                        libraryViewModel.importLocalPdfFileToLibrary(pdfFile, title)
                                    }
                                )
                                GlobalLibraryIslandHost(
                                    onTapEvent = { event ->
                                        DynamicIslandEventManager.openActiveEvent(this@MainActivity, event)
                                    },
                                    onDismiss = { DynamicIslandEventManager.dismissActiveEvent() }
                                )
                            }

                            docId != null -> {
                                val readerViewModel: ReaderViewModel = viewModel(
                                    key = "reader_$docId",
                                    factory = remember(docId) {
                                        ReaderViewModel.Factory(application, docId, folioRepository, prefsRepo)
                                    }
                                )
                                DisposableEffect(readerViewModel) {
                                    activeReaderFlush = { readerViewModel.flushReadingStateAsync() }
                                    onDispose { activeReaderFlush = null }
                                }
                                ReaderScreen(
                                    viewModel = readerViewModel,
                                    onBackToLibrary = { activeDocumentId = null }
                                )
                            }

                            else -> {
                                LibraryScreen(
                                    viewModel = libraryViewModel,
                                    notebookRepositoryProvider = { notebookRepository },
                                    onOpenDocument = { id ->
                                        activeNotebookId = null
                                        activeDocumentId = id
                                    },
                                    onOpenNotebook = { id ->
                                        activeDocumentId = null
                                        activeNotebookId = id
                                    }
                                )
                                // Isolated state scope: Island events only recompose this overlay, not LibraryScreen
                                GlobalLibraryIslandHost(
                                    onTapEvent = { event ->
                                        DynamicIslandEventManager.openActiveEvent(this@MainActivity, event)
                                    },
                                    onDismiss = { DynamicIslandEventManager.dismissActiveEvent() }
                                )
                            }
                        }

                        // "Abrir con Luma" / "Compartir" Chooser Dialog
                        pendingExternalFile?.let { ext ->
                            ExternalImportChooserDialog(
                                pending = ext,
                                isBusy = isHandlingExternalFile,
                                onOpenInReader = {
                                    isHandlingExternalFile = true
                                    libraryViewModel.importDocumentWithResult(
                                        uri = ext.uri,
                                        onError = {
                                            isHandlingExternalFile = false
                                            pendingExternalFile = null
                                        },
                                        onImported = { imported ->
                                            isHandlingExternalFile = false
                                            pendingExternalFile = null
                                            flushActiveSessionBeforeNavigate()
                                            activeNotebookId = null
                                            activeDocumentId = imported.id
                                        }
                                    )
                                },
                                onCreateNotebook = {
                                    isHandlingExternalFile = true
                                    val cleanTitle = ext.displayName.substringBeforeLast('.').ifBlank { "Cuaderno de estudio" }
                                    if (ext.isImage) {
                                        lifecycleScope.launch {
                                            val newNbId = notebookRepository.createNotebookFromImageUri(cleanTitle, ext.uri)
                                            isHandlingExternalFile = false
                                            pendingExternalFile = null
                                            flushActiveSessionBeforeNavigate()
                                            activeDocumentId = null
                                            activeNotebookId = newNbId
                                        }
                                    } else if (ext.format != null) {
                                        libraryViewModel.importDocumentWithResult(
                                            uri = ext.uri,
                                            onError = {
                                                isHandlingExternalFile = false
                                                pendingExternalFile = null
                                            },
                                            onImported = { imported ->
                                                lifecycleScope.launch {
                                                    val newNbId = if (imported.format == DocumentFormat.PDF) {
                                                        notebookRepository.createNotebookFromPdf(imported.title, File(imported.filePath))
                                                    } else {
                                                        notebookRepository.createNotebook(title = "Apuntes · ${imported.title}")
                                                    }
                                                    isHandlingExternalFile = false
                                                    pendingExternalFile = null
                                                    flushActiveSessionBeforeNavigate()
                                                    activeDocumentId = null
                                                    activeNotebookId = newNbId
                                                }
                                            }
                                        )
                                    } else {
                                        lifecycleScope.launch {
                                            val newNbId = notebookRepository.createNotebook(title = cleanTitle)
                                            isHandlingExternalFile = false
                                            pendingExternalFile = null
                                            flushActiveSessionBeforeNavigate()
                                            activeDocumentId = null
                                            activeNotebookId = newNbId
                                        }
                                    }
                                },
                                onDismiss = {
                                    pendingExternalFile = null
                                }
                            )
                        }

                        // Multi-file import progress dialog (ACTION_SEND_MULTIPLE)
                        batchImportProgress?.let { progress ->
                            ExternalBatchImportDialog(progress = progress)
                        }

                        // Choreographer FPS monitor: ONLY runs when explicitly enabled by user in settings AND resumed
                        if (rootSlice.showFpsCounter) {
                            LiveFpsBadge(
                                targetFps = rootSlice.targetFps,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .statusBarsPadding()
                                    .padding(top = 6.dp, end = 10.dp)
                            )
                        }

                        // Isolated state scope: GitHub update dialog attaches to LumaOverlayHost
                        GlobalGitHubUpdateHost(
                            onDownloadApk = { updateInfo ->
                                GitHubUpdateManager.openApkDownload(this@MainActivity, updateInfo)
                            },
                            onRemindLater = { GitHubUpdateManager.dismissOptionalForSession() },
                            onSkipVersion = { GitHubUpdateManager.skipOptionalVersion(this@MainActivity) },
                            onRetryCheck = {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    GitHubUpdateManager.checkForUpdates(applicationContext, manualCheck = true)
                                }
                            }
                        )

                        // Zero-IPC in-tree overlay host for all Luma dropdown menus & sheets (frame-0 open!)
                        LumaOverlayHost(controller = overlayController)

                        // Draw-phase-only optical eye-comfort overlay (zero extra composables/recompositions on alpha change)
                        OpticalEyeComfortOverlay(
                            warmFilterStrengthProvider = { rootSlice.warmFilterStrength },
                            nightDimmingProvider = { activeNightDimming }
                        )
                    }
                }
            }
        }
    }

    private fun startDeferredBackgroundServices() {
        if (deferredServicesStarted) return
        deferredServicesStarted = true
        val appCtx = applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            val prefsSnapshot = preferencesRepository.awaitInitialPreferences()
            com.example.engine.LumaDiskCacheManager.setLimitMb(prefsSnapshot.diskCacheLimitMb)
            com.example.engine.LumaDiskCacheManager.enforceGlobalBudget(appCtx)
            GitHubUpdateManager.enforceCachedMandatoryLockIfNeeded(appCtx)
            SystemMediaController.start(appCtx)
            DynamicIslandEventManager.start(appCtx)
            SocialRepository.getInstance(appCtx).setAppForeground(true)

            // Proactive speed prewarmer: warms top covers into RAM + hot-caches the most recent book
            // so scrolling the library and tapping "Continue Reading" feel instantaneous!
            runCatching {
                val recentDocs = folioRepository.documents.first()
                val coverWarmCount = if (MemoryPressureManager.isLowRamDevice) 6 else 14
                for (doc in recentDocs.take(coverWarmCount)) {
                    CoverBitmapCache.preload(doc.coverImagePath)
                }
                val topDoc = recentDocs.firstOrNull()
                if (topDoc != null) {
                    val file = File(topDoc.filePath)
                    val format = DocumentFormat.fromExtension(topDoc.format)
                    if (file.exists() && format != null) {
                        val hit = ParsedDocumentDiskCache.read(appCtx, file, format)
                        if (hit == null && file.length() <= 16L * 1024L * 1024L) {
                            delay(500L)
                            runCatching { DocumentParser.parse(appCtx, file, format) }
                        }
                    }
                }
            }

            // Defer silent update check after startup settles; respects 6-hour ETag throttle (zero cold-start downloads)
            delay(2_500L)
            GitHubUpdateManager.checkForUpdates(
                context = appCtx,
                manualCheck = false,
                bypassThrottle = false
            )
        }
    }

    override fun onStart() {
        super.onStart()
        // Immediately establish the foreground session boundary so historical notifications never appear
        DynamicIslandEventManager.markForegroundSessionStart()
        if (deferredServicesStarted) {
            val appCtx = applicationContext
            lifecycleScope.launch(Dispatchers.IO) {
                DynamicIslandEventManager.onAppForeground(appCtx)
                SocialRepository.getInstance(appCtx).setAppForeground(true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (lastAppliedTargetFps != currentTargetFps) {
            lastAppliedTargetFps = currentTargetFps
            val applied = DisplayRefreshRateController.applyRefreshRate(this, currentTargetFps)
            activeRefreshRateState.floatValue = applied.configuredRefreshRateHz
        }
        if (deferredServicesStarted) {
            SystemMediaController.setForegroundActive(true)
            lifecycleScope.launch(Dispatchers.IO) {
                SystemMediaController.refreshPermissionStatus(applicationContext)
                GitHubUpdateManager.checkForUpdates(applicationContext, manualCheck = false)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        SystemMediaController.setForegroundActive(false)
        // Flush any debounced slider writes so user adjustments persist before backgrounding
        preferencesRepository.flushPendingWritesAsync()
    }

    override fun onStop() {
        super.onStop()
        DynamicIslandEventManager.onAppBackground()
        if (deferredServicesStarted) {
            SocialRepository.getInstance(applicationContext).setAppForeground(false)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        MemoryPressureManager.trimAll(level)
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        super.onLowMemory()
        MemoryPressureManager.trimAll(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    }
}

@Composable
private fun BoxScope.GlobalLibraryIslandHost(
    onTapEvent: (com.example.engine.DynamicIslandEvent) -> Unit,
    onDismiss: () -> Unit
) {
    val activeIslandEvent by DynamicIslandEventManager.activeEvent.collectAsStateWithLifecycle()
    val queuedIslandCount by DynamicIslandEventManager.queuedCount.collectAsStateWithLifecycle()
    val topModalHostId by DynamicIslandEventManager.topModalHostId.collectAsStateWithLifecycle()

    DynamicIslandEventBanner(
        event = if (topModalHostId == null) activeIslandEvent else null,
        queuedCount = queuedIslandCount,
        onTapEvent = onTapEvent,
        onDismiss = onDismiss,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .displayCutoutPadding()
            .statusBarsPadding()
            .padding(top = 6.dp, start = 12.dp, end = 12.dp)
    )
}

@Composable
private fun GlobalGitHubUpdateHost(
    onDownloadApk: (com.example.engine.AppUpdateInfo) -> Unit,
    onRemindLater: () -> Unit,
    onSkipVersion: () -> Unit,
    onRetryCheck: () -> Unit
) {
    val activeGitHubUpdate by GitHubUpdateManager.activeUpdate.collectAsStateWithLifecycle()
    activeGitHubUpdate?.let { updateInfo ->
        GitHubUpdateDialog(
            updateInfo = updateInfo,
            onDownloadApk = { onDownloadApk(updateInfo) },
            onRemindLater = onRemindLater,
            onSkipVersion = onSkipVersion,
            onRetryCheck = onRetryCheck
        )
    }
}

@Composable
private fun OpticalEyeComfortOverlay(
    warmFilterStrengthProvider: () -> Float,
    nightDimmingProvider: () -> Float
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val warm = warmFilterStrengthProvider()
                if (warm > 0.01f) {
                    drawRect(
                        color = Color(0xFFFF9E3D),
                        alpha = (warm * 0.42f).coerceIn(0f, 0.22f)
                    )
                }
                val dim = nightDimmingProvider()
                if (dim > 0.01f) {
                    drawRect(
                        color = Color.Black,
                        alpha = (dim * 0.45f).coerceIn(0f, 0.22f)
                    )
                }
            }
    )
}

@Composable
private fun LiveFpsBadge(
    targetFps: TargetFpsOption,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var measuredFps by remember { mutableIntStateOf(60) }

    // Lifecycle-gated Choreographer callback: stops immediately when backgrounded/paused
    DisposableEffect(lifecycleOwner) {
        val choreographer = Choreographer.getInstance()
        var frameCount = 0
        var windowStartNs = 0L
        var running = false

        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!running) return
                if (windowStartNs == 0L) {
                    windowStartNs = frameTimeNanos
                }
                frameCount++
                val elapsedNs = frameTimeNanos - windowStartNs
                if (elapsedNs >= 500_000_000L) {
                    val fps = (frameCount * 1_000_000_000.0 / elapsedNs).roundToInt()
                    measuredFps = fps.coerceIn(1, 240)
                    frameCount = 0
                    windowStartNs = frameTimeNanos
                }
                choreographer.postFrameCallback(this)
            }
        }

        fun startMonitoring() {
            if (!running) {
                running = true
                frameCount = 0
                windowStartNs = 0L
                choreographer.postFrameCallback(callback)
            }
        }

        fun stopMonitoring() {
            if (running) {
                running = false
                choreographer.removeFrameCallback(callback)
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> startMonitoring()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> stopMonitoring()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            startMonitoring()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopMonitoring()
        }
    }

    val targetLabel = if (targetFps == TargetFpsOption.AUTO) "MÁX" else targetFps.label
    val shape = remember { RoundedCornerShape(10.dp) }
    Box(
        modifier = modifier
            .clip(shape)
            .background(palette.surface.copy(alpha = 0.88f))
            .border(1.dp, palette.accent.copy(alpha = 0.45f), shape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .testTag("live_fps_badge")
    ) {
        Text(
            text = "$measuredFps FPS · $targetLabel",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = palette.accent
        )
    }
}
