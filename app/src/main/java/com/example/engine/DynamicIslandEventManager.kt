package com.example.engine

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.util.LruCache
import androidx.compose.runtime.Immutable
import com.example.data.model.DEFAULT_ISLAND_ALLOWED_PACKAGES
import com.example.data.model.IslandNotificationFilterMode
import com.example.data.model.ReaderPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

@Immutable
data class BatteryTelemetry(
    val levelPct: Int = 100,
    val isCharging: Boolean = false
) {
    val isCritical: Boolean get() = !isCharging && levelPct <= 5
    val isLow: Boolean get() = !isCharging && levelPct <= 15
}

enum class CriticalEventKind {
    CRITICAL_BATTERY,
    LOW_BATTERY,
    CHARGING_RESUMED,
    OFFLINE,
    ONLINE_RESTORED,
    INCOMING_CALL,
    ALARM,
    CRITICAL_ERROR,
    EXPORT_PROGRESS,
    EXPORT_DONE,
    AI_TASK
}

@Immutable
sealed interface DynamicIslandEvent {
    val id: Long
    val priority: Int
    val durationMs: Long
    val dedupeKey: String

    @Immutable
    data class CriticalState(
        override val id: Long = System.nanoTime(),
        val kind: CriticalEventKind,
        val badgeLabel: String,
        val title: String,
        val subtitle: String,
        val accentColorArgb: Long,
        val contentIntent: PendingIntent? = null,
        val packageName: String = "",
        override val durationMs: Long = 5800L,
        override val dedupeKey: String = "critical_${kind.name}"
    ) : DynamicIslandEvent {
        override val priority: Int = 2
    }

    @Immutable
    data class AllowedAppAlert(
        val notification: IncomingAppNotification,
        override val durationMs: Long = 4800L
    ) : DynamicIslandEvent {
        override val id: Long get() = notification.id
        override val priority: Int = 1
        override val dedupeKey: String get() = notification.key
    }
}

@Immutable
data class CuratedIslandApp(
    val packageName: String,
    val displayName: String,
    val category: String
)

@Immutable
data class DiscoveredIslandApp(
    val packageName: String,
    val appName: String,
    val lastSeenMs: Long,
    val wasBlocked: Boolean
)

/**
 * Centralized event manager that replaces cluttered system notifications with curated
 * Dynamic Island alerts — displaying ONLY:
 * 1. Critical device / app states (critical battery, connectivity loss, urgent calls/alarms, critical errors)
 * 2. Notifications from explicitly permitted external apps (enforcing allowlist / critical-only policy)
 */
object DynamicIslandEventManager {

    val curatedApps: List<CuratedIslandApp> = listOf(
        CuratedIslandApp("com.whatsapp", "WhatsApp", "Mensajería"),
        CuratedIslandApp("com.whatsapp.w4b", "WhatsApp Business", "Mensajería"),
        CuratedIslandApp("org.telegram.messenger", "Telegram", "Mensajería"),
        CuratedIslandApp("com.google.android.apps.messaging", "Mensajes (Google)", "SMS"),
        CuratedIslandApp("com.samsung.android.messaging", "Mensajes (Samsung)", "SMS"),
        CuratedIslandApp("com.google.android.dialer", "Teléfono (Google)", "Llamadas"),
        CuratedIslandApp("com.samsung.android.dialer", "Teléfono (Samsung)", "Llamadas"),
        CuratedIslandApp("org.thoughtcrime.securesms", "Signal", "Mensajería"),
        CuratedIslandApp("com.google.android.gm", "Gmail", "Correo"),
        CuratedIslandApp("com.microsoft.office.outlook", "Outlook", "Correo"),
        CuratedIslandApp("com.instagram.android", "Instagram", "Social"),
        CuratedIslandApp("com.facebook.orca", "Messenger", "Mensajería"),
        CuratedIslandApp("com.discord", "Discord", "Comunidad"),
        CuratedIslandApp("com.twitter.android", "X / Twitter", "Social"),
        CuratedIslandApp("com.Slack", "Slack", "Trabajo")
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingQueue = ArrayDeque<DynamicIslandEvent>()
    private var clearRunnable: Runnable? = null

    private val appIconCache = object : LruCache<String, Bitmap>(
        if (MemoryPressureManager.isLowRamDevice) 12 else 24
    ) {}

    init {
        MemoryPressureManager.registerTrimmer("island_app_icon_cache") { level ->
            MemoryPressureManager.trimLruCache(appIconCache, level)
        }
    }
    private val modalHostIdGen = AtomicLong(1L)
    private val activeModalHosts = mutableListOf<Long>()

    private val _topModalHostId = MutableStateFlow<Long?>(null)
    val topModalHostId: StateFlow<Long?> = _topModalHostId.asStateFlow()

    private val _activeEvent = MutableStateFlow<DynamicIslandEvent?>(null)
    val activeEvent: StateFlow<DynamicIslandEvent?> = _activeEvent.asStateFlow()

    private val _queuedCount = MutableStateFlow(0)
    val queuedCount: StateFlow<Int> = _queuedCount.asStateFlow()

    private val _suppressedCount = MutableStateFlow(0)
    val suppressedCount: StateFlow<Int> = _suppressedCount.asStateFlow()

    private val _batteryState = MutableStateFlow(BatteryTelemetry())
    val batteryState: StateFlow<BatteryTelemetry> = _batteryState.asStateFlow()

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _discoveredApps = MutableStateFlow<List<DiscoveredIslandApp>>(emptyList())
    val discoveredApps: StateFlow<List<DiscoveredIslandApp>> = _discoveredApps.asStateFlow()

    fun registerModalHost(): Long {
        val id = modalHostIdGen.getAndIncrement()
        synchronized(activeModalHosts) {
            activeModalHosts.add(id)
            val next = activeModalHosts.lastOrNull()
            if (_topModalHostId.value != next) _topModalHostId.value = next
        }
        return id
    }

    fun unregisterModalHost(hostId: Long) {
        synchronized(activeModalHosts) {
            activeModalHosts.remove(hostId)
            val next = activeModalHosts.lastOrNull()
            if (_topModalHostId.value != next) _topModalHostId.value = next
        }
    }

    @Volatile
    private var filterMode: IslandNotificationFilterMode = IslandNotificationFilterMode.ALLOWED_ONLY

    @Volatile
    private var allowedPackages: Set<String> = DEFAULT_ISLAND_ALLOWED_PACKAGES

    @Volatile
    private var showCriticalBattery: Boolean = true

    @Volatile
    private var showCriticalConnectivity: Boolean = true

    @Volatile
    private var allowIncomingCalls: Boolean = true

    @Volatile
    private var isAppInForeground = false
    @Volatile
    private var foregroundSessionStartMs: Long = Long.MAX_VALUE
    private val preSessionSignatures = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var appContext: Context? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var notifiedLowBattery = false
    private var notifiedCriticalBattery = false
    private var hadOfflineDrop = false
    private var initialConnectivityObserved = false

    fun start(context: Context) {
        onAppForeground(context)
    }

    /**
     * Marks the exact timestamp when the user enters the app so any notification posted prior to
     * this moment (or already sitting in the system shade) is never displayed in the Dynamic Island.
     */
    fun markForegroundSessionStart(activeNotifications: Array<StatusBarNotification>? = null) {
        foregroundSessionStartMs = System.currentTimeMillis()
        isAppInForeground = true
        if (activeNotifications != null) {
            seedPreExistingNotifications(activeNotifications)
        }
    }

    /**
     * Records notifications that were already present in the status bar before the user entered Luma
     * so Android's NotificationListenerService replay/refresh never floods the Dynamic Island.
     */
    internal fun seedPreExistingNotifications(notifications: Array<StatusBarNotification>?) {
        if (notifications == null) return
        preSessionSignatures.clear()
        for (sbn in notifications) {
            val key = sbn.key ?: continue
            val sig = buildNotificationSignature(sbn)
            preSessionSignatures[key] = sig
        }
    }

    private fun buildNotificationSignature(sbn: StatusBarNotification): String {
        val extras = sbn.notification?.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val shortText = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val body = shortText.ifBlank { bigText }
        return "$title|$body|${sbn.postTime}"
    }

    /**
     * Registers battery & connectivity monitors while the app is in the foreground and establishes
     * a clean foreground session boundary (clearing any stale pre-session queue).
     */
    fun onAppForeground(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        if (!isAppInForeground) {
            foregroundSessionStartMs = System.currentTimeMillis()
            isAppInForeground = true
        } else if (foregroundSessionStartMs == Long.MAX_VALUE) {
            foregroundSessionStartMs = System.currentTimeMillis()
        }
        // Snapshot whatever is already sitting in the status bar right now so it won't replay
        seedPreExistingNotifications(LumaMediaListenerService.snapshotActiveNotifications())
        registerBatteryMonitor(appCtx)
        registerConnectivityMonitor(appCtx)
        mainHandler.post {
            // Never replay queued alerts from a previous session when entering the app
            val active = _activeEvent.value
            if (active != null && clearRunnable == null) {
                scheduleAutoClear(active)
            }
        }
    }

    /**
     * Unregisters system broadcast receivers & network callbacks, cancels pending Handler timers,
     * and clears active/queued banners while the app is backgrounded so returning later starts clean.
     */
    fun onAppBackground() {
        isAppInForeground = false
        foregroundSessionStartMs = Long.MAX_VALUE
        unregisterMonitors()
        mainHandler.post {
            clearRunnable?.let { mainHandler.removeCallbacks(it) }
            clearRunnable = null
            pendingQueue.clear()
            if (_queuedCount.value != 0) _queuedCount.value = 0
            if (_activeEvent.value != null) _activeEvent.value = null
        }
    }

    private fun unregisterMonitors() {
        val ctx = appContext ?: return
        synchronized(this) {
            batteryReceiver?.let { receiver ->
                runCatching { ctx.unregisterReceiver(receiver) }
                batteryReceiver = null
            }
            networkCallback?.let { cb ->
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                runCatching { cm?.unregisterNetworkCallback(cb) }
                networkCallback = null
            }
        }
    }

    fun updatePolicy(prefs: ReaderPreferences) {
        filterMode = prefs.islandFilterMode
        allowedPackages = prefs.islandAllowedPackages
        showCriticalBattery = prefs.islandShowCriticalBattery
        showCriticalConnectivity = prefs.islandShowCriticalConnectivity
        allowIncomingCalls = prefs.islandAllowIncomingCalls
    }

    fun postCriticalError(title: String, detail: String) {
        enqueueEvent(
            DynamicIslandEvent.CriticalState(
                kind = CriticalEventKind.CRITICAL_ERROR,
                badgeLabel = "ESTADO CRÍTICO",
                title = title.ifBlank { "Error de operación" },
                subtitle = detail.ifBlank { "Revisa el archivo e intenta de nuevo" },
                accentColorArgb = 0xFFEF4444L,
                durationMs = 5600L
            )
        )
    }

    fun postOperationStatus(
        kind: CriticalEventKind,
        badgeLabel: String,
        title: String,
        subtitle: String,
        accentColorArgb: Long = 0xFF1D5BFFL,
        durationMs: Long = 3800L,
        dedupeKey: String = "luma_operation_status"
    ) {
        enqueueEvent(
            DynamicIslandEvent.CriticalState(
                kind = kind,
                badgeLabel = badgeLabel,
                title = title,
                subtitle = subtitle,
                accentColorArgb = accentColorArgb,
                durationMs = durationMs,
                dedupeKey = dedupeKey
            )
        )
    }

    fun triggerPreviewCritical() {
        val battery = _batteryState.value
        enqueueEvent(
            DynamicIslandEvent.CriticalState(
                kind = CriticalEventKind.CRITICAL_BATTERY,
                badgeLabel = "ESTADO CRÍTICO · BATERÍA",
                title = "Batería crítica (${battery.levelPct}%)",
                subtitle = "Vista previa de alerta crítica integrada en la Isla Dinámica",
                accentColorArgb = 0xFFEF4444L,
                durationMs = 4600L,
                dedupeKey = "preview_critical_${System.nanoTime()}"
            )
        )
    }

    fun triggerPreviewAllowedApp(context: Context) {
        val ctx = context.applicationContext
        val iconBitmap = extractAppIconBitmap(ctx, ctx.packageName)
        val preview = IncomingAppNotification(
            key = "preview_app_${System.nanoTime()}",
            packageName = "",
            appName = "App permitida · Prueba",
            title = "Aviso en Isla Dinámica",
            text = "Solo las apps autorizadas y estados críticos aparecen aquí sin barra del sistema.",
            appIcon = iconBitmap,
            contentIntent = null,
            postedAtMs = System.currentTimeMillis(),
            isTestPreview = true
        )
        enqueueEvent(DynamicIslandEvent.AllowedAppAlert(preview, durationMs = 4600L))
    }

    fun dismissActiveEvent() {
        mainHandler.post {
            clearRunnable?.let { mainHandler.removeCallbacks(it) }
            clearRunnable = null
            _activeEvent.value = null
            drainNextIfIdle()
        }
    }

    fun openActiveEvent(context: Context, event: DynamicIslandEvent) {
        dismissActiveEvent()
        val intent: PendingIntent?
        val pkg: String
        when (event) {
            is DynamicIslandEvent.CriticalState -> {
                intent = event.contentIntent
                pkg = event.packageName
            }
            is DynamicIslandEvent.AllowedAppAlert -> {
                intent = event.notification.contentIntent
                pkg = event.notification.packageName
            }
        }
        val opened = runCatching {
            intent?.send()
            intent != null
        }.getOrDefault(false)

        if (!opened && pkg.isNotBlank()) {
            runCatching {
                val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                }
            }
        }
    }

    internal fun onSystemNotificationRemoved(sbn: StatusBarNotification) {
        val removedKey = sbn.key ?: return
        preSessionSignatures.remove(removedKey)
        mainHandler.post {
            pendingQueue.removeAll { it.dedupeKey == removedKey }
            updateQueuedCountIfChanged()
            if (_activeEvent.value?.dedupeKey == removedKey) {
                clearRunnable?.let { mainHandler.removeCallbacks(it) }
                clearRunnable = null
                _activeEvent.value = null
                drainNextIfIdle()
            }
        }
    }

    internal fun onSystemNotificationPosted(context: Context, sbn: StatusBarNotification) {
        // Hard gate 1: Only surface notifications arriving WHILE the user is actively inside Luma
        if (!isAppInForeground) return

        // Hard gate 2: Reject any historical notification posted BEFORE entering this foreground session
        val sessionStart = foregroundSessionStartMs
        if (sbn.postTime < sessionStart) return

        val pkg = sbn.packageName.orEmpty()
        if (pkg.isBlank() || pkg == context.packageName) return

        val notif = sbn.notification ?: return
        // Hard gate 3: Reject notifications already sitting in the status bar before entering Luma
        // (even if OEM/Android replays them or re-delivers them on listener bind without new content)
        val key = sbn.key
        if (key != null) {
            val preSig = preSessionSignatures[key]
            if (preSig != null) {
                val newSig = buildNotificationSignature(sbn)
                val prevContent = preSig.substringBeforeLast('|')
                val newContent = newSig.substringBeforeLast('|')
                if (prevContent == newContent) {
                    return
                }
                // Genuine new message/update arrived on an existing thread during the session -> clear baseline
                preSessionSignatures.remove(key)
            }
        }
        val category = notif.category
        val isCallOrAlarm = category == Notification.CATEGORY_CALL || category == Notification.CATEGORY_ALARM

        // Skip persistent background/ongoing/media/progress notifications & group shells (except active calls/alarms)
        val flags = notif.flags
        if (!isCallOrAlarm) {
            if ((flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
                (flags and Notification.FLAG_FOREGROUND_SERVICE) != 0 ||
                (flags and Notification.FLAG_GROUP_SUMMARY) != 0
            ) {
                return
            }
            if (category == Notification.CATEGORY_TRANSPORT ||
                category == Notification.CATEGORY_SERVICE ||
                category == Notification.CATEGORY_PROGRESS ||
                category == Notification.CATEGORY_SYSTEM ||
                category == Notification.CATEGORY_RECOMMENDATION ||
                category == Notification.CATEGORY_PROMO
            ) {
                return
            }
        }

        val extras = notif.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val shortText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val body = shortText.ifBlank { bigText }
        if (title.isBlank() && body.isBlank()) return

        val appName = resolveAppLabel(context, pkg).ifBlank { "Aplicación" }

        // 1. Critical incoming calls / alarms bypass standard app filtering when enabled
        if (isCallOrAlarm && allowIncomingCalls) {
            recordDiscoveredApp(pkg, appName, wasBlocked = false)
            val isCall = category == Notification.CATEGORY_CALL
            enqueueEvent(
                DynamicIslandEvent.CriticalState(
                    kind = if (isCall) CriticalEventKind.INCOMING_CALL else CriticalEventKind.ALARM,
                    badgeLabel = if (isCall) "LLAMADA ENTRANTE" else "ALARMA CRÍTICA",
                    title = title.ifBlank { appName },
                    subtitle = body.ifBlank { appName },
                    accentColorArgb = if (isCall) 0xFF22C55EL else 0xFFF59E0BL,
                    contentIntent = notif.contentIntent,
                    packageName = pkg,
                    durationMs = 6800L,
                    dedupeKey = sbn.key
                )
            )
            return
        }

        // 2. Enforce centralized filter policy (ONLY permitted external apps pass through!)
        val permitted = when (filterMode) {
            IslandNotificationFilterMode.CRITICAL_ONLY -> false
            IslandNotificationFilterMode.ALLOWED_ONLY -> pkg in allowedPackages
            IslandNotificationFilterMode.ALL_APPS -> true
        }

        recordDiscoveredApp(pkg, appName, wasBlocked = !permitted)

        if (!permitted) {
            mainHandler.post {
                _suppressedCount.value = _suppressedCount.value + 1
            }
            return
        }

        val iconBitmap = extractAppIconBitmap(context, pkg)
        val item = IncomingAppNotification(
            key = sbn.key,
            packageName = pkg,
            appName = appName,
            title = title.ifBlank { appName },
            text = body,
            appIcon = iconBitmap,
            contentIntent = notif.contentIntent,
            postedAtMs = sbn.postTime
        )
        enqueueEvent(DynamicIslandEvent.AllowedAppAlert(item, durationMs = 4800L))
    }

    private fun recordDiscoveredApp(packageName: String, appName: String, wasBlocked: Boolean) {
        mainHandler.post {
            val currentList = _discoveredApps.value
            val now = System.currentTimeMillis()
            val head = currentList.firstOrNull()
            if (head != null &&
                head.packageName == packageName &&
                head.appName == appName &&
                head.wasBlocked == wasBlocked &&
                (now - head.lastSeenMs) < 30_000L
            ) {
                return@post
            }
            val existing = currentList.filterNot { it.packageName == packageName }
            val updated = listOf(
                DiscoveredIslandApp(
                    packageName = packageName,
                    appName = appName,
                    lastSeenMs = now,
                    wasBlocked = wasBlocked
                )
            ) + existing.take(24)
            _discoveredApps.value = updated
        }
    }

    private fun enqueueEvent(event: DynamicIslandEvent) {
        // Never enqueue alerts while the app is backgrounded — only show events during the user's stay
        if (!isAppInForeground) return
        mainHandler.post {
            val current = _activeEvent.value
            if (current == null) {
                presentNow(event)
                return@post
            }

            // Replace in-place if same notification key updates
            if (current.dedupeKey == event.dedupeKey) {
                presentNow(event)
                return@post
            }

            // Critical events preempt lower-priority allowed app notifications immediately
            if (event.priority > current.priority) {
                if (pendingQueue.size < 4) {
                    pendingQueue.addFirst(current)
                }
                presentNow(event)
                updateQueuedCountIfChanged()
                return@post
            }

            // Coalesce duplicate queued keys and cap queue size to prevent notification storms
            val idx = pendingQueue.indexOfFirst { it.dedupeKey == event.dedupeKey }
            if (idx >= 0) {
                pendingQueue[idx] = event
            } else {
                if (pendingQueue.size >= 4) {
                    pendingQueue.removeFirst()
                }
                pendingQueue.addLast(event)
            }
            updateQueuedCountIfChanged()
        }
    }

    private fun presentNow(event: DynamicIslandEvent) {
        _activeEvent.value = event
        updateQueuedCountIfChanged()
        scheduleAutoClear(event)
    }

    private fun scheduleAutoClear(event: DynamicIslandEvent) {
        clearRunnable?.let { mainHandler.removeCallbacks(it) }
        clearRunnable = null
        if (!isAppInForeground) return
        val task = Runnable {
            clearRunnable = null
            if (_activeEvent.value?.id == event.id) {
                _activeEvent.value = null
                drainNextIfIdle()
            }
        }
        clearRunnable = task
        mainHandler.postDelayed(task, event.durationMs)
    }

    private fun drainNextIfIdle() {
        if (!isAppInForeground || _activeEvent.value != null) return
        val next = pendingQueue.removeFirstOrNull()
        updateQueuedCountIfChanged()
        if (next != null) {
            presentNow(next)
        }
    }

    private fun updateQueuedCountIfChanged() {
        val nextSize = pendingQueue.size
        if (_queuedCount.value != nextSize) {
            _queuedCount.value = nextSize
        }
    }

    private fun registerBatteryMonitor(context: Context) {
        synchronized(this) {
            if (batteryReceiver != null) return
        }
        fun processBatteryIntent(intent: Intent?) {
            if (intent == null) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val pct = if (level >= 0) ((level * 100f) / scale.toFloat()).toInt().coerceIn(0, 100) else _batteryState.value.levelPct
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

            val prev = _batteryState.value
            val next = BatteryTelemetry(levelPct = pct, isCharging = charging)
            if (next == prev) return
            _batteryState.value = next

            if (!showCriticalBattery) return

            if (charging) {
                if (!prev.isCharging && (notifiedLowBattery || notifiedCriticalBattery || pct <= 20)) {
                    enqueueEvent(
                        DynamicIslandEvent.CriticalState(
                            kind = CriticalEventKind.CHARGING_RESUMED,
                            badgeLabel = "ENERGÍA CONECTADA",
                            title = "Cargando · $pct%",
                            subtitle = "Tu lectura continuará sin interrupciones",
                            accentColorArgb = 0xFF22C55EL,
                            durationMs = 3600L
                        )
                    )
                }
                if (pct >= 20) {
                    notifiedLowBattery = false
                    notifiedCriticalBattery = false
                }
                return
            }

            if (pct <= 5 && !notifiedCriticalBattery) {
                notifiedCriticalBattery = true
                notifiedLowBattery = true
                enqueueEvent(
                    DynamicIslandEvent.CriticalState(
                        kind = CriticalEventKind.CRITICAL_BATTERY,
                        badgeLabel = "BATERÍA CRÍTICA",
                        title = "Queda solo $pct% de batería",
                        subtitle = "Conecta el cargador para no perder tu sesión",
                        accentColorArgb = 0xFFEF4444L,
                        durationMs = 6400L
                    )
                )
            } else if (pct <= 15 && !notifiedLowBattery) {
                notifiedLowBattery = true
                enqueueEvent(
                    DynamicIslandEvent.CriticalState(
                        kind = CriticalEventKind.LOW_BATTERY,
                        badgeLabel = "BATERÍA BAJA",
                        title = "Batería al $pct%",
                        subtitle = "Considera conectar tu dispositivo pronto",
                        accentColorArgb = 0xFFF59E0BL,
                        durationMs = 5400L
                    )
                )
            } else if (pct > 20) {
                notifiedLowBattery = false
                notifiedCriticalBattery = false
            }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                processBatteryIntent(intent)
            }
        }
        synchronized(this) {
            if (batteryReceiver != null) return
            batteryReceiver = receiver
        }
        val sticky = runCatching {
            context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.onFailure {
            synchronized(this) { batteryReceiver = null }
        }.getOrNull()
        processBatteryIntent(sticky)
    }

    private fun registerConnectivityMonitor(context: Context) {
        synchronized(this) {
            if (networkCallback != null) return
        }
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val currentlyConnected = runCatching {
            val active = cm.activeNetwork
            val caps = active?.let { cm.getNetworkCapabilities(it) }
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }.getOrDefault(true)
        if (_isOnline.value != currentlyConnected) {
            _isOnline.value = currentlyConnected
        }
        initialConnectivityObserved = true

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val wasOffline = !_isOnline.value
                if (wasOffline) _isOnline.value = true
                if (wasOffline && hadOfflineDrop && showCriticalConnectivity) {
                    hadOfflineDrop = false
                    enqueueEvent(
                        DynamicIslandEvent.CriticalState(
                            kind = CriticalEventKind.ONLINE_RESTORED,
                            badgeLabel = "CONEXIÓN RESTAURADA",
                            title = "Vuelves a tener conexión",
                            subtitle = "Traductor y sincronización disponibles",
                            accentColorArgb = 0xFF22C55EL,
                            durationMs = 3400L
                        )
                    )
                }
            }

            override fun onLost(network: Network) {
                val stillHasOther = runCatching {
                    val active = cm.activeNetwork
                    val caps = active?.let { cm.getNetworkCapabilities(it) }
                    caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                }.getOrDefault(false)
                if (!stillHasOther) {
                    val wasOnline = _isOnline.value
                    if (wasOnline) _isOnline.value = false
                    if (initialConnectivityObserved && wasOnline && showCriticalConnectivity) {
                        hadOfflineDrop = true
                        enqueueEvent(
                            DynamicIslandEvent.CriticalState(
                                kind = CriticalEventKind.OFFLINE,
                                badgeLabel = "SIN CONEXIÓN",
                                title = "Se perdió la conexión a internet",
                                subtitle = "Tus libros locales siguen disponibles offline",
                                accentColorArgb = 0xFFF59E0BL,
                                durationMs = 5200L
                            )
                        )
                    }
                }
            }
        }
        synchronized(this) {
            if (networkCallback != null) return
            networkCallback = callback
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
            .onFailure { synchronized(this) { networkCallback = null } }
    }

    private fun extractAppIconBitmap(context: Context, packageName: String): Bitmap? {
        appIconCache.get(packageName)?.takeIf { !it.isRecycled }?.let { return it }
        return runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawableToSmallBitmap(drawable, 72).also { bmp ->
                appIconCache.put(packageName, bmp)
            }
        }.getOrNull()
    }

    private fun drawableToSmallBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return Bitmap.createScaledBitmap(drawable.bitmap, sizePx, sizePx, true)
        }
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bmp
    }

    private fun resolveAppLabel(context: Context, packageName: String): String {
        curatedApps.firstOrNull { it.packageName == packageName }?.let { return it.displayName }
        return runCatching {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            pm.getApplicationLabel(info).toString()
        }.getOrElse {
            runCatching {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getApplicationInfo(packageName, 0)
                context.packageManager.getApplicationLabel(info).toString()
            }.getOrDefault(packageName.substringAfterLast('.'))
        }
    }
}
