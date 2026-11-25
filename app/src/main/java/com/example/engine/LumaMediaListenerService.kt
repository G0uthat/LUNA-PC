package com.example.engine

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.KeyEvent
import androidx.compose.runtime.Immutable
import androidx.core.app.NotificationManagerCompat
import com.example.data.model.MediaPlaybackInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class IncomingAppNotification(
    val id: Long = System.nanoTime(),
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val appIcon: Bitmap? = null,
    val contentIntent: PendingIntent? = null,
    val postedAtMs: Long = System.currentTimeMillis(),
    val isTestPreview: Boolean = false
)

/**
 * System NotificationListenerService that allows Luma to:
 * 1. Read active MediaSessions (Spotify, YouTube, YouTube Music, VLC, etc.) for artwork,
 *    title, progress, and playback controls inside the Dynamic Island.
 * 2. Route incoming app notifications (WhatsApp, Instagram, Gmail, SMS, etc.) cleanly through
 *    the Dynamic Island while the reader hides the cluttered system status bar.
 */
class LumaMediaListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        connectedInstance = java.lang.ref.WeakReference(this)
        // Snapshot already-present notifications immediately so re-deliveries never flood the Dynamic Island
        val current = runCatching { activeNotifications }.getOrNull()
        DynamicIslandEventManager.seedPreExistingNotifications(current)
        SystemMediaController.bindActiveSessions(applicationContext)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connectedInstance = null
        SystemMediaController.onServiceDisconnected(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        DynamicIslandEventManager.onSystemNotificationPosted(applicationContext, sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return
        DynamicIslandEventManager.onSystemNotificationRemoved(sbn)
    }

    companion object {
        @Volatile
        private var connectedInstance: java.lang.ref.WeakReference<LumaMediaListenerService>? = null

        internal fun snapshotActiveNotifications(): Array<StatusBarNotification>? {
            val service = connectedInstance?.get() ?: return null
            return runCatching { service.activeNotifications }.getOrNull()
        }
    }
}

/**
 * Real-time Android MediaSession controller with graceful AudioManager media-key fallback.
 *
 * Lifecycle & power guarantees:
 * - Holds ONLY `applicationContext` (never an Activity).
 * - `progressTicker` runs ONLY while the app is foreground, the screen is interactive, the reader
 *   is open, AND an active media session is playing with remaining duration (or in fallback mode
 *   while the reader is open).
 * - Stops immediately when paused, stopped, screen off, backgrounded, or reader closed.
 * - Deduplicates all `_mediaState` updates so UI collectors never recompose without real changes.
 */
object SystemMediaController {

    private val _mediaState = MutableStateFlow(MediaPlaybackInfo())
    val mediaState: StateFlow<MediaPlaybackInfo> = _mediaState.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var appContext: Context? = null
    private var sessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private var sessionsListenerRegistered = false
    private var lastFallbackAudioActiveMs: Long = 0L

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            publishFromController(activeController)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            publishFromController(activeController)
        }

        override fun onSessionDestroyed() {
            appContext?.let { bindActiveSessions(it) }
        }
    }

    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        selectBestController(controllers.orEmpty())
    }

    private var tickerRunning = false
    @Volatile
    private var isForegroundActive = false
    @Volatile
    private var isReaderActive = false

    fun setForegroundActive(active: Boolean) {
        if (isForegroundActive == active) return
        isForegroundActive = active
        mainHandler.post { updateTickerState() }
    }

    fun setReaderActive(active: Boolean) {
        if (isReaderActive == active) return
        isReaderActive = active
        mainHandler.post { updateTickerState() }
    }

    private fun isScreenInteractive(context: Context?): Boolean {
        val pm = context?.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return runCatching { pm.isInteractive }.getOrDefault(true)
    }

    private fun shouldTickNow(): Boolean {
        val ctx = appContext ?: return false
        if (!isForegroundActive || !isReaderActive) return false
        if (!isScreenInteractive(ctx)) return false
        val current = _mediaState.value
        return if (current.isNotificationAccessGranted) {
            current.hasActiveSession &&
                current.isPlaying &&
                current.durationMs > 0L &&
                current.positionMs < current.durationMs
        } else {
            // Fallback AudioManager polling only while reader is actively visible on screen
            true
        }
    }

    private fun updateTickerState() {
        if (shouldTickNow()) {
            if (!tickerRunning) {
                tickerRunning = true
                val delayMs = if (_mediaState.value.isNotificationAccessGranted) 1000L else 1500L
                mainHandler.postDelayed(progressTicker, delayMs)
            }
        } else if (tickerRunning) {
            tickerRunning = false
            mainHandler.removeCallbacks(progressTicker)
        }
    }

    private fun stopTicker() {
        tickerRunning = false
        mainHandler.removeCallbacks(progressTicker)
    }

    private val progressTicker = object : Runnable {
        override fun run() {
            if (!shouldTickNow()) {
                tickerRunning = false
                return
            }
            val ctx = appContext
            val current = _mediaState.value
            if (current.isNotificationAccessGranted) {
                val now = SystemClock.elapsedRealtime()
                val elapsed = (now - current.lastUpdatedRealtimeMs).coerceAtLeast(0L)
                val projected = (current.positionMs + elapsed).coerceAtMost(current.durationMs)
                if (projected != current.positionMs) {
                    _mediaState.value = current.copy(
                        positionMs = projected,
                        lastUpdatedRealtimeMs = now
                    )
                }
                if (projected >= current.durationMs) {
                    tickerRunning = false
                    return
                }
            } else if (ctx != null) {
                pollAudioManagerFallback(ctx)
            }

            if (shouldTickNow()) {
                val nextIntervalMs = if (_mediaState.value.isNotificationAccessGranted) {
                    1000L
                } else if (_mediaState.value.isPlaying) {
                    1200L
                } else {
                    2500L
                }
                mainHandler.postDelayed(this, nextIntervalMs)
            } else {
                tickerRunning = false
            }
        }
    }

    private fun pollAudioManagerFallback(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val activeNow = runCatching { audioManager.isMusicActive }.getOrDefault(false)
        val now = SystemClock.elapsedRealtime()
        if (activeNow) {
            lastFallbackAudioActiveMs = now
        }
        val recentlyPaused = lastFallbackAudioActiveMs > 0L && (now - lastFallbackAudioActiveMs) <= 120_000L
        val prev = _mediaState.value
        if (activeNow || recentlyPaused) {
            val nextTitle = if (activeNow) "Reproduciendo audio" else "Audio en pausa"
            if (!prev.hasActiveSession ||
                prev.isNotificationAccessGranted ||
                prev.isPlaying != activeNow ||
                prev.title != nextTitle
            ) {
                _mediaState.value = MediaPlaybackInfo(
                    isNotificationAccessGranted = false,
                    hasActiveSession = true,
                    isPlaying = activeNow,
                    title = nextTitle,
                    artist = "Multimedia del dispositivo",
                    appName = "Reproductor",
                    lastUpdatedRealtimeMs = now
                )
            }
        } else if (prev.hasActiveSession || prev.isNotificationAccessGranted) {
            _mediaState.value = MediaPlaybackInfo(
                isNotificationAccessGranted = false,
                hasActiveSession = false
            )
        }
    }

    fun start(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        bindActiveSessions(ctx)
        mainHandler.post { updateTickerState() }
    }

    internal fun onServiceDisconnected(context: Context) {
        mainHandler.post {
            stopTicker()
            detachController()
            if (sessionsListenerRegistered) {
                runCatching { sessionManager?.removeOnActiveSessionsChangedListener(activeSessionsListener) }
                sessionsListenerRegistered = false
            }
        }
        refreshPermissionStatus(context.applicationContext)
    }

    fun refreshPermissionStatus(context: Context) {
        val ctx = context.applicationContext
        val granted = isNotificationListenerEnabled(ctx)
        if (granted) {
            bindActiveSessions(ctx)
        } else {
            mainHandler.post {
                detachController()
                if (sessionsListenerRegistered) {
                    runCatching { sessionManager?.removeOnActiveSessionsChangedListener(activeSessionsListener) }
                    sessionsListenerRegistered = false
                }
            }
            pollAudioManagerFallback(ctx)
            mainHandler.post { updateTickerState() }
        }
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        return runCatching {
            val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
            context.packageName in enabledPackages
        }.getOrDefault(false)
    }

    fun openNotificationAccessSettings(context: Context) {
        runCatching {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun bindActiveSessions(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        val granted = isNotificationListenerEnabled(ctx)
        if (!granted) {
            pollAudioManagerFallback(ctx)
            mainHandler.post { updateTickerState() }
            return
        }

        runCatching {
            val mgr = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
            sessionManager = mgr
            val component = ComponentName(ctx, LumaMediaListenerService::class.java)
            if (!sessionsListenerRegistered) {
                runCatching {
                    mgr.addOnActiveSessionsChangedListener(activeSessionsListener, component, mainHandler)
                    sessionsListenerRegistered = true
                }
            }
            val controllers = mgr.getActiveSessions(component)
            selectBestController(controllers)
        }.onFailure {
            val prev = _mediaState.value
            if (prev.isNotificationAccessGranted != granted) {
                _mediaState.value = prev.copy(isNotificationAccessGranted = granted)
            }
        }
    }

    private fun selectBestController(controllers: List<MediaController>) {
        // Prioritize actively playing session, then paused/buffering session with metadata
        val best = controllers.firstOrNull { ctrl ->
            val st = ctrl.playbackState?.state
            st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING
        } ?: controllers.firstOrNull { ctrl ->
            ctrl.metadata != null
        } ?: controllers.firstOrNull()

        if (best?.sessionToken != activeController?.sessionToken) {
            detachController()
            activeController = best
            best?.registerCallback(controllerCallback, mainHandler)
        }
        publishFromController(activeController)
    }

    private fun detachController() {
        runCatching { activeController?.unregisterCallback(controllerCallback) }
        activeController = null
    }

    private fun publishFromController(controller: MediaController?) {
        val ctx = appContext
        val granted = ctx != null && isNotificationListenerEnabled(ctx)
        val prev = _mediaState.value
        if (controller == null) {
            if (prev.hasActiveSession || prev.isNotificationAccessGranted != granted) {
                _mediaState.value = MediaPlaybackInfo(
                    isNotificationAccessGranted = granted,
                    hasActiveSession = false
                )
            }
            mainHandler.post { updateTickerState() }
            return
        }

        val metadata = controller.metadata
        val playback = controller.playbackState
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""
        val art: Bitmap? = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val duration = (metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L).coerceAtLeast(0L)
        val rawPos = (playback?.position ?: 0L).coerceAtLeast(0L)
        val isPlaying = playback?.state == PlaybackState.STATE_PLAYING ||
            playback?.state == PlaybackState.STATE_BUFFERING

        val pkg = controller.packageName.orEmpty()
        val friendlyApp = resolveAppLabel(ctx, pkg)

        val currentPos = if (isPlaying && playback != null && playback.lastPositionUpdateTime > 0L) {
            val delta = ((SystemClock.elapsedRealtime() - playback.lastPositionUpdateTime) * playback.playbackSpeed).toLong()
            (rawPos + delta).coerceIn(0L, if (duration > 0L) duration else Long.MAX_VALUE)
        } else {
            rawPos
        }

        val resolvedTitle = title.ifBlank { friendlyApp.ifBlank { "Reproduciendo multimedia" } }
        val resolvedHasSession = title.isNotBlank() || playback != null

        // Deduplicate unchanged emissions so UI collectors never recompose without semantic delta
        val unchanged = prev.isNotificationAccessGranted == granted &&
            prev.hasActiveSession == resolvedHasSession &&
            prev.isPlaying == isPlaying &&
            prev.title == resolvedTitle &&
            prev.artist == artist &&
            prev.appName == friendlyApp &&
            prev.packageName == pkg &&
            prev.durationMs == duration &&
            prev.albumArt === art &&
            kotlin.math.abs(prev.positionMs - currentPos) < 500L

        if (!unchanged) {
            _mediaState.value = MediaPlaybackInfo(
                isNotificationAccessGranted = granted,
                hasActiveSession = resolvedHasSession,
                isPlaying = isPlaying,
                title = resolvedTitle,
                artist = artist,
                appName = friendlyApp,
                packageName = pkg,
                albumArt = art,
                positionMs = currentPos,
                durationMs = duration,
                lastUpdatedRealtimeMs = SystemClock.elapsedRealtime()
            )
        }
        mainHandler.post { updateTickerState() }
    }

    fun togglePlayPause(context: Context) {
        val ctrl = activeController
        if (ctrl != null) {
            val playing = ctrl.playbackState?.state == PlaybackState.STATE_PLAYING
            runCatching {
                if (playing) ctrl.transportControls.pause() else ctrl.transportControls.play()
            }
            return
        }
        dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    fun skipToNext(context: Context) {
        val ctrl = activeController
        if (ctrl != null) {
            runCatching { ctrl.transportControls.skipToNext() }
            return
        }
        dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun skipToPrevious(context: Context) {
        val ctrl = activeController
        if (ctrl != null) {
            runCatching { ctrl.transportControls.skipToPrevious() }
            return
        }
        dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    fun seekTo(positionMs: Long) {
        val ctrl = activeController ?: return
        val clamped = positionMs.coerceAtLeast(0L)
        runCatching {
            ctrl.transportControls.seekTo(clamped)
            val prev = _mediaState.value
            if (prev.positionMs != clamped) {
                _mediaState.value = prev.copy(
                    positionMs = clamped,
                    lastUpdatedRealtimeMs = SystemClock.elapsedRealtime()
                )
            }
        }
    }

    private fun dispatchMediaKey(context: Context, keyCode: Int) {
        runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val now = SystemClock.uptimeMillis()
            audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        }
    }

    private fun resolveAppLabel(context: Context?, packageName: String): String {
        if (packageName.isBlank()) return ""
        val lower = packageName.lowercase()
        when {
            "spotify" in lower -> return "Spotify"
            "youtube.music" in lower -> return "YouTube Music"
            "youtube" in lower -> return "YouTube"
            "apple.android.music" in lower -> return "Apple Music"
            "soundcloud" in lower -> return "SoundCloud"
            "deezer" in lower -> return "Deezer"
            "tidal" in lower -> return "TIDAL"
            "vlc" in lower -> return "VLC"
        }
        if (context == null) return packageName.substringAfterLast('.')
        return runCatching {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            pm.getApplicationLabel(info).toString()
        }.getOrElse {
            runCatching {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getApplicationInfo(packageName, 0)
                context.packageManager.getApplicationLabel(info).toString()
            }.getOrDefault("Multimedia")
        }
    }
}
