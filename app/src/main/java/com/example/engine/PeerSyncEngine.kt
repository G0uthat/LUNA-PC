package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import com.example.data.model.FriendChatMessage
import com.example.data.model.FriendPeer
import com.example.data.model.MediaPlaybackInfo
import com.example.data.model.PrivacySettings
import com.example.data.model.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Serverless, database-free local profile & internet P2P friend sync engine.
 *
 * - Stores ONLY the user's chosen username, local avatar photo, privacy toggles, friends list,
 *   and local chat history inside app-private filesDir (`luma_social_local.json`).
 * - NO cloud database, NO accounts, NO passwords, NO registration.
 * - Completely idle when there are no friends or while the app is backgrounded.
 * - Shares [LumaHttpClient.shared] connection pool and cancels active OkHttp calls immediately
 *   when streams pause or friends are removed.
 */
class SocialRepository private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val streamClient: OkHttpClient by lazy {
        LumaHttpClient.shared.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // Long-lived streaming SSE/NDJSON
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val postClient: OkHttpClient by lazy {
        LumaHttpClient.shared.newBuilder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val textMediaType = "text/plain; charset=utf-8".toMediaType()
    private val storageFile by lazy { File(context.filesDir, "luma_social_local.json") }
    private val diskWriteMutex = Mutex()
    private var pendingSaveJob: Job? = null

    private val _profile = MutableStateFlow(UserProfile())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    private val _friends = MutableStateFlow<List<FriendPeer>>(emptyList())
    val friends: StateFlow<List<FriendPeer>> = _friends.asStateFlow()

    private val _messagesByFriend = MutableStateFlow<Map<String, List<FriendChatMessage>>>(emptyMap())
    val messagesByFriend: StateFlow<Map<String, List<FriendChatMessage>>> = _messagesByFriend.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    @Volatile
    private var isAppForeground: Boolean = true
    @Volatile
    private var currentReadingBook: String = ""
    @Volatile
    private var currentReadingProgress: Int = 0
    private var lastBroadcastFingerprint: String = ""
    private var lastBroadcastMs: Long = 0L

    // Cached Base64 avatar thumbnail keyed by "path|lastModified" to avoid repeated disk/JPEG work
    @Volatile
    private var cachedAvatarKey: String = ""
    @Volatile
    private var cachedAvatarB64: String = ""

    private val activeTopicJobs = ConcurrentHashMap<String, Job>()
    private val activeTopicCalls = ConcurrentHashMap<String, Call>()
    private val seenPacketIds = LinkedHashSet<String>()

    init {
        scope.launch {
            loadFromDisk()
            if (isAppForeground && _friends.value.isNotEmpty()) {
                startAllFriendStreams()
            }
        }
        observeMediaForPresence()
    }

    /**
     * Lifecycle hook called by [com.example.MainActivity]:
     * pauses all long-lived relay streams while backgrounded and resumes them on foreground
     * ONLY when at least one peer exists.
     */
    fun setAppForeground(foreground: Boolean) {
        if (isAppForeground == foreground) return
        isAppForeground = foreground
        if (foreground) {
            if (_friends.value.isNotEmpty()) {
                startAllFriendStreams()
            }
        } else {
            stopAllStreams()
        }
    }

    private fun observeMediaForPresence() {
        scope.launch {
            var lastTitle = ""
            SystemMediaController.mediaState.collect { media ->
                if (!isAppForeground || _friends.value.isEmpty()) return@collect
                val key = "${media.title}|${media.artist}|${media.isPlaying}"
                if (key != lastTitle) {
                    lastTitle = key
                    broadcastPresenceToAllFriends(force = false)
                }
            }
        }
    }

    fun updateProfileName(name: String) {
        val clean = name.trim().take(28)
        _profile.update {
            it.copy(
                username = clean.ifBlank { "Lector" },
                profileConfigured = true
            )
        }
        saveToDiskAsync()
        broadcastPresenceToAllFriends(force = true)
    }

    fun markProfilePromptDismissed() {
        _profile.update {
            it.copy(
                username = it.username.ifBlank { "Lector" },
                profileConfigured = true
            )
        }
        saveToDiskAsync()
    }

    fun updatePrivacy(transform: (PrivacySettings) -> PrivacySettings) {
        _profile.update { it.copy(privacy = transform(it.privacy)) }
        saveToDiskAsync()
        broadcastPresenceToAllFriends(force = true)
    }

    fun regeneratePeerCode() {
        val newCode = generatePeerCode()
        _profile.update { it.copy(peerCode = newCode) }
        saveToDiskAsync()
        restartAllStreams()
    }

    fun updateAvatarFromUri(uri: Uri, onDone: (Boolean) -> Unit = {}) {
        scope.launch {
            val savedPath = runCatching {
                val dir = File(context.filesDir, "profile").apply { mkdirs() }
                val dest = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
                val raw = BitmapDecodeUtils.decodeSampledUri(
                    contentResolver = context.contentResolver,
                    uri = uri,
                    reqWidth = 512,
                    reqHeight = 512
                ) ?: return@runCatching null
                val square = centerCropSquare(raw, 256)
                FileOutputStream(dest).use { out ->
                    square.compress(Bitmap.CompressFormat.JPEG, 86, out)
                }
                if (square !== raw) square.recycle()
                raw.recycle()
                dest.absolutePath
            }.getOrNull()

            if (savedPath != null) {
                val old = _profile.value.avatarPath
                _profile.update { it.copy(avatarPath = savedPath, profileConfigured = true) }
                if (old.isNotBlank() && old != savedPath) {
                    runCatching { File(old).delete() }
                }
                saveToDiskAsync()
                broadcastPresenceToAllFriends(force = true)
            }
            withContext(Dispatchers.Main) { onDone(savedPath != null) }
        }
    }

    fun updateReadingPresence(bookTitle: String, progressPct: Int) {
        currentReadingBook = bookTitle.trim()
        currentReadingProgress = progressPct.coerceIn(0, 100)
        broadcastPresenceToAllFriends(force = false)
    }

    fun clearReadingPresence() {
        currentReadingBook = ""
        currentReadingProgress = 0
        broadcastPresenceToAllFriends(force = true)
    }

    fun addFriendByCode(rawCode: String, optionalAlias: String = ""): Pair<Boolean, String> {
        val normalized = normalizeCode(rawCode)
        if (normalized.length < 6) {
            return false to "Código inválido. Pide a tu amigo su código LUMA."
        }
        if (normalized == _profile.value.peerCode) {
            return false to "Ese es tu propio código de amigo."
        }
        val existing = _friends.value.firstOrNull { it.peerCode == normalized }
        if (existing != null) {
            return false to "${existing.displayName} ya está en tu lista."
        }
        val alias = optionalAlias.trim().ifBlank { "Amigo ${normalized.takeLast(4)}" }
        val newFriend = FriendPeer(
            peerCode = normalized,
            displayName = alias
        )
        _friends.update { listOf(newFriend) + it }
        saveToDiskAsync()
        ensureStreamForFriend(normalized)
        // Immediately announce our presence on the shared channel
        scope.launch { sendPresencePacket(normalized) }
        return true to "Conectando con $alias…"
    }

    fun removeFriend(friendCode: String) {
        val topic = computePairTopic(_profile.value.peerCode, friendCode)
        activeTopicCalls.remove(topic)?.cancel()
        activeTopicJobs.remove(topic)?.cancel()
        _friends.update { list -> list.filterNot { it.peerCode == friendCode } }
        _messagesByFriend.update { map -> map - friendCode }
        saveToDiskAsync()
    }

    fun markFriendChatRead(friendCode: String) {
        _friends.update { list ->
            list.map { if (it.peerCode == friendCode) it.copy(unreadCount = 0) else it }
        }
        saveToDiskAsync()
    }

    fun sendChatMessage(friendCode: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val me = _profile.value
        val msgId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val localMsg = FriendChatMessage(
            id = msgId,
            friendCode = friendCode,
            senderCode = me.peerCode,
            senderName = me.username.ifBlank { "Lector" },
            text = clean,
            timestampMs = now,
            isFromMe = true
        )
        appendMessage(friendCode, localMsg, incrementUnread = false)
        rememberPacketId(msgId)

        scope.launch {
            val packet = JSONObject().apply {
                put("type", "chat")
                put("id", msgId)
                put("senderCode", me.peerCode)
                put("senderName", me.username.ifBlank { "Lector" })
                put("text", clean)
                put("ts", now)
            }
            publishToPair(friendCode, packet)
        }
    }

    fun syncNow() {
        scope.launch {
            _isSyncing.value = true
            startAllFriendStreams()
            broadcastPresenceToAllFriends(force = true)
            delay(500)
            _isSyncing.value = false
        }
    }

    private fun broadcastPresenceToAllFriends(force: Boolean) {
        val friendsList = _friends.value
        if (friendsList.isEmpty()) return
        val me = _profile.value
        val media = SystemMediaController.mediaState.value
        val fp = buildPresenceFingerprint(me, media)
        val now = System.currentTimeMillis()
        if (!force && fp == lastBroadcastFingerprint && (now - lastBroadcastMs) < 12_000L) {
            return
        }
        lastBroadcastFingerprint = fp
        lastBroadcastMs = now
        scope.launch {
            for (friend in friendsList) {
                sendPresencePacket(friend.peerCode)
            }
        }
    }

    private fun buildPresenceFingerprint(me: UserProfile, media: MediaPlaybackInfo): String {
        val bookPart = if (me.privacy.shareReadingBook) "$currentReadingBook:$currentReadingProgress" else ""
        val mediaPart = if (me.privacy.shareMediaActivity && media.hasActiveSession) {
            "${media.title}|${media.artist}|${media.appName}|${media.isPlaying}"
        } else ""
        return "${me.username}|${me.avatarPath}|${me.privacy.showOnlineStatus}|$bookPart|$mediaPart"
    }

    private fun sendPresencePacket(friendCode: String) {
        val me = _profile.value
        val media = SystemMediaController.mediaState.value
        val packetId = UUID.randomUUID().toString()
        rememberPacketId(packetId)

        val packet = JSONObject().apply {
            put("type", "presence")
            put("id", packetId)
            put("senderCode", me.peerCode)
            put("senderName", me.username.ifBlank { "Lector" })
            put("avatarB64", encodeSmallAvatarBase64(me.avatarPath))
            put("online", me.privacy.showOnlineStatus)
            if (me.privacy.shareReadingBook) {
                put("bookTitle", currentReadingBook)
                put("bookProgress", currentReadingProgress)
            } else {
                put("bookTitle", "")
                put("bookProgress", 0)
            }
            if (me.privacy.shareMediaActivity && media.hasActiveSession && media.title.isNotBlank()) {
                put("mediaTitle", media.title.take(80))
                put("mediaArtist", media.artist.take(60))
                put("mediaApp", media.appName.take(30))
            } else {
                put("mediaTitle", "")
                put("mediaArtist", "")
                put("mediaApp", "")
            }
            put("ts", System.currentTimeMillis())
        }
        publishToPair(friendCode, packet)
    }

    private fun publishToPair(friendCode: String, payload: JSONObject) {
        val myCode = _profile.value.peerCode
        if (myCode.isBlank()) return
        val topic = computePairTopic(myCode, friendCode)
        runCatching {
            val request = Request.Builder()
                .url("https://ntfy.sh/$topic")
                .header("Title", "LumaP2P")
                .post(payload.toString().toRequestBody(textMediaType))
                .build()
            postClient.newCall(request).execute().use { /* fire & forget */ }
        }
    }

    private fun stopAllStreams() {
        activeTopicCalls.values.forEach { runCatching { it.cancel() } }
        activeTopicCalls.clear()
        activeTopicJobs.values.forEach { it.cancel() }
        activeTopicJobs.clear()
    }

    private fun restartAllStreams() {
        stopAllStreams()
        if (isAppForeground && _friends.value.isNotEmpty()) {
            startAllFriendStreams()
        }
    }

    private fun startAllFriendStreams() {
        if (!isAppForeground) return
        val peers = _friends.value
        if (peers.isEmpty()) return
        for (friend in peers) {
            ensureStreamForFriend(friend.peerCode)
        }
    }

    private fun ensureStreamForFriend(friendCode: String) {
        if (!isAppForeground) return
        val myCode = _profile.value.peerCode
        if (myCode.isBlank()) return
        val topic = computePairTopic(myCode, friendCode)
        if (activeTopicJobs[topic]?.isActive == true) return

        val job = scope.launch {
            var backoffMs = 2500L
            while (isActive && isAppForeground && _friends.value.any { it.peerCode == friendCode }) {
                if (!DynamicIslandEventManager.isOnline.value) {
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2L).coerceAtMost(30_000L)
                    continue
                }
                var currentCall: Call? = null
                try {
                    val req = Request.Builder()
                        .url("https://ntfy.sh/$topic/json?since=30m")
                        .build()
                    val call = streamClient.newCall(req)
                    currentCall = call
                    activeTopicCalls[topic] = call
                    call.execute().use { response ->
                        if (response.isSuccessful) {
                            backoffMs = 2500L
                            val source = response.body?.source()
                            while (isActive && isAppForeground && source != null && !source.exhausted()) {
                                val line = runCatching { source.readUtf8LineStrict(MAX_RELAY_LINE_BYTES) }
                                    .getOrNull() ?: break
                                if (line.isNotBlank()) {
                                    handleIncomingRelayLine(friendCode, line)
                                }
                            }
                        }
                    }
                } catch (ce: CancellationException) {
                    currentCall?.cancel()
                    throw ce
                } catch (_: Exception) {
                    // Offline or transient network issue; retry politely with bounded backoff
                } finally {
                    if (currentCall != null) {
                        activeTopicCalls.remove(topic, currentCall)
                    }
                }
                if (!isActive || !isAppForeground) break
                delay(backoffMs)
                backoffMs = (backoffMs * 2L).coerceAtMost(30_000L)
            }
            activeTopicJobs.remove(topic)
        }
        job.invokeOnCompletion {
            activeTopicCalls.remove(topic)?.cancel()
        }
        activeTopicJobs[topic] = job
    }

    private fun handleIncomingRelayLine(friendCode: String, rawLine: String) {
        runCatching {
            val envelope = JSONObject(rawLine)
            if (envelope.optString("event") != "message") return
            val bodyStr = envelope.optString("message", "").trim()
            if (!bodyStr.startsWith("{")) return
            val packet = JSONObject(bodyStr)
            val packetId = packet.optString("id", "")
            val senderCode = packet.optString("senderCode", "")
            if (senderCode.isBlank() || senderCode == _profile.value.peerCode) return
            if (packetId.isNotBlank() && !rememberPacketId(packetId)) return

            when (packet.optString("type")) {
                "presence" -> {
                    val name = packet.optString("senderName", "").trim()
                    val avatar = packet.optString("avatarB64", "")
                    val online = packet.optBoolean("online", true)
                    val book = packet.optString("bookTitle", "")
                    val progress = packet.optInt("bookProgress", 0)
                    val mediaTitle = packet.optString("mediaTitle", "")
                    val mediaArtist = packet.optString("mediaArtist", "")
                    val mediaApp = packet.optString("mediaApp", "")
                    val ts = packet.optLong("ts", System.currentTimeMillis())

                    _friends.update { list ->
                        list.map { peer ->
                            if (peer.peerCode == friendCode) {
                                peer.copy(
                                    displayName = name.ifBlank { peer.displayName },
                                    avatarBase64 = avatar.ifBlank { peer.avatarBase64 },
                                    readingBookTitle = book,
                                    readingProgressPct = progress,
                                    mediaTitle = mediaTitle,
                                    mediaArtist = mediaArtist,
                                    mediaApp = mediaApp,
                                    isOnline = online,
                                    lastSeenEpochMs = ts
                                )
                            } else peer
                        }
                    }
                    saveToDiskAsync()
                }

                "chat" -> {
                    if (!_profile.value.privacy.allowFriendChat) return
                    val senderName = packet.optString("senderName", "").ifBlank { "Amigo" }
                    val text = packet.optString("text", "").trim()
                    val ts = packet.optLong("ts", System.currentTimeMillis())
                    if (text.isBlank()) return

                    val msg = FriendChatMessage(
                        id = packetId.ifBlank { UUID.randomUUID().toString() },
                        friendCode = friendCode,
                        senderCode = senderCode,
                        senderName = senderName,
                        text = text,
                        timestampMs = ts,
                        isFromMe = false
                    )
                    appendMessage(friendCode, msg, incrementUnread = true)
                    _friends.update { list ->
                        list.map { peer ->
                            if (peer.peerCode == friendCode) {
                                peer.copy(
                                    displayName = senderName.ifBlank { peer.displayName },
                                    isOnline = true,
                                    lastSeenEpochMs = ts
                                )
                            } else peer
                        }
                    }
                    saveToDiskAsync()
                }
            }
        }
    }

    private fun appendMessage(friendCode: String, msg: FriendChatMessage, incrementUnread: Boolean) {
        _messagesByFriend.update { current ->
            val list = current[friendCode].orEmpty()
            if (list.any { it.id == msg.id }) return@update current
            val updated = (list + msg).takeLast(120)
            current + (friendCode to updated)
        }
        if (incrementUnread) {
            _friends.update { list ->
                list.map { if (it.peerCode == friendCode) it.copy(unreadCount = it.unreadCount + 1) else it }
            }
        }
        saveToDiskAsync()
    }

    private fun rememberPacketId(id: String): Boolean {
        synchronized(seenPacketIds) {
            if (id in seenPacketIds) return false
            seenPacketIds.add(id)
            if (seenPacketIds.size > 500) {
                val it = seenPacketIds.iterator()
                if (it.hasNext()) {
                    it.next()
                    it.remove()
                }
            }
            return true
        }
    }

    private fun encodeSmallAvatarBase64(path: String): String {
        if (path.isBlank()) return ""
        val file = File(path)
        if (!file.exists()) return ""
        val cacheKey = "$path|${file.lastModified()}|${file.length()}"
        if (cachedAvatarKey == cacheKey) return cachedAvatarB64

        val encoded = runCatching {
            val bmp = BitmapDecodeUtils.decodeSampledFile(
                path = file.absolutePath,
                reqWidth = 128,
                reqHeight = 128
            ) ?: return ""
            val tiny = Bitmap.createScaledBitmap(bmp, 64, 64, true)
            val baos = ByteArrayOutputStream(4096)
            tiny.compress(Bitmap.CompressFormat.JPEG, 58, baos)
            if (tiny !== bmp) tiny.recycle()
            bmp.recycle()
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        }.getOrDefault("")
        cachedAvatarKey = cacheKey
        cachedAvatarB64 = encoded
        return encoded
    }

    private fun centerCropSquare(src: Bitmap, size: Int): Bitmap {
        val dim = minOf(src.width, src.height).coerceAtLeast(1)
        val x = (src.width - dim) / 2
        val y = (src.height - dim) / 2
        val cropped = Bitmap.createBitmap(src, x, y, dim, dim)
        val scaled = Bitmap.createScaledBitmap(cropped, size, size, true)
        if (cropped !== src && cropped !== scaled) cropped.recycle()
        return scaled
    }

    private fun readStorageFileStreaming(file: File): String? {
        if (!file.exists() || file.length() <= 0L || file.length() > MAX_SOCIAL_STORAGE_BYTES) return null
        val sb = StringBuilder(file.length().toInt().coerceAtMost(256 * 1024))
        BufferedReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8), 8 * 1024).use { reader ->
            val buf = CharArray(4096)
            while (true) {
                val n = reader.read(buf)
                if (n <= 0) break
                sb.append(buf, 0, n)
            }
        }
        return sb.toString()
    }

    private fun loadFromDisk() {
        val loaded = runCatching {
            val rawJson = readStorageFileStreaming(storageFile) ?: return@runCatching false
            val root = JSONObject(rawJson)
            val pObj = root.optJSONObject("profile")
            val privObj = pObj?.optJSONObject("privacy")
            val code = pObj?.optString("peerCode").orEmpty().ifBlank { generatePeerCode() }
            _profile.value = UserProfile(
                username = pObj?.optString("username").orEmpty(),
                avatarPath = pObj?.optString("avatarPath").orEmpty(),
                peerCode = code,
                profileConfigured = pObj?.optBoolean("profileConfigured", false) ?: false,
                privacy = PrivacySettings(
                    showOnlineStatus = privObj?.optBoolean("showOnlineStatus", true) ?: true,
                    shareReadingBook = privObj?.optBoolean("shareReadingBook", true) ?: true,
                    shareMediaActivity = privObj?.optBoolean("shareMediaActivity", true) ?: true,
                    allowFriendChat = privObj?.optBoolean("allowFriendChat", true) ?: true
                )
            )

            val friendsArr = root.optJSONArray("friends") ?: JSONArray()
            val fList = mutableListOf<FriendPeer>()
            for (i in 0 until friendsArr.length()) {
                val o = friendsArr.optJSONObject(i) ?: continue
                fList.add(
                    FriendPeer(
                        peerCode = o.optString("peerCode"),
                        displayName = o.optString("displayName", "Amigo"),
                        avatarBase64 = o.optString("avatarBase64", ""),
                        readingBookTitle = o.optString("readingBookTitle", ""),
                        readingProgressPct = o.optInt("readingProgressPct", 0),
                        mediaTitle = o.optString("mediaTitle", ""),
                        mediaArtist = o.optString("mediaArtist", ""),
                        mediaApp = o.optString("mediaApp", ""),
                        isOnline = false,
                        lastSeenEpochMs = o.optLong("lastSeenEpochMs", 0L),
                        unreadCount = o.optInt("unreadCount", 0)
                    )
                )
            }
            _friends.value = fList

            val msgsObj = root.optJSONObject("messages") ?: JSONObject()
            val msgMap = mutableMapOf<String, List<FriendChatMessage>>()
            val keys = msgsObj.keys()
            while (keys.hasNext()) {
                val fCode = keys.next()
                val arr = msgsObj.optJSONArray(fCode) ?: continue
                val list = mutableListOf<FriendChatMessage>()
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    list.add(
                        FriendChatMessage(
                            id = m.optString("id"),
                            friendCode = fCode,
                            senderCode = m.optString("senderCode"),
                            senderName = m.optString("senderName"),
                            text = m.optString("text"),
                            timestampMs = m.optLong("timestampMs"),
                            isFromMe = m.optBoolean("isFromMe", false)
                        )
                    )
                }
                msgMap[fCode] = list
            }
            _messagesByFriend.value = msgMap
            true
        }.getOrDefault(false)

        if (!loaded) {
            _profile.value = UserProfile(
                username = "",
                avatarPath = "",
                peerCode = generatePeerCode(),
                profileConfigured = false
            )
            saveToDiskAsync()
        }
    }

    private fun saveToDiskAsync() {
        pendingSaveJob?.cancel()
        pendingSaveJob = scope.launch {
            delay(250L) // Coalesce bursts of presence/chat updates into a single atomic disk write
            diskWriteMutex.withLock {
                val prof = _profile.value
                val fList = _friends.value
                val msgMap = _messagesByFriend.value
                runCatching {
                    val root = JSONObject()
                    root.put(
                        "profile",
                        JSONObject().apply {
                            put("username", prof.username)
                            put("avatarPath", prof.avatarPath)
                            put("peerCode", prof.peerCode)
                            put("profileConfigured", prof.profileConfigured)
                            put(
                                "privacy",
                                JSONObject().apply {
                                    put("showOnlineStatus", prof.privacy.showOnlineStatus)
                                    put("shareReadingBook", prof.privacy.shareReadingBook)
                                    put("shareMediaActivity", prof.privacy.shareMediaActivity)
                                    put("allowFriendChat", prof.privacy.allowFriendChat)
                                }
                            )
                        }
                    )
                    val fArr = JSONArray()
                    for (f in fList) {
                        fArr.put(
                            JSONObject().apply {
                                put("peerCode", f.peerCode)
                                put("displayName", f.displayName)
                                put("avatarBase64", f.avatarBase64)
                                put("readingBookTitle", f.readingBookTitle)
                                put("readingProgressPct", f.readingProgressPct)
                                put("mediaTitle", f.mediaTitle)
                                put("mediaArtist", f.mediaArtist)
                                put("mediaApp", f.mediaApp)
                                put("lastSeenEpochMs", f.lastSeenEpochMs)
                                put("unreadCount", f.unreadCount)
                            }
                        )
                    }
                    root.put("friends", fArr)

                    val mObj = JSONObject()
                    for ((k, list) in msgMap) {
                        val arr = JSONArray()
                        for (m in list.takeLast(120)) {
                            arr.put(
                                JSONObject().apply {
                                    put("id", m.id)
                                    put("senderCode", m.senderCode)
                                    put("senderName", m.senderName)
                                    put("text", m.text)
                                    put("timestampMs", m.timestampMs)
                                    put("isFromMe", m.isFromMe)
                                }
                            )
                        }
                        mObj.put(k, arr)
                    }
                    root.put("messages", mObj)

                    val tmp = File(context.filesDir, "luma_social_local.tmp")
                    BufferedWriter(OutputStreamWriter(FileOutputStream(tmp), Charsets.UTF_8), 8 * 1024).use { writer ->
                        writer.write(root.toString())
                    }
                    tmp.renameTo(storageFile)
                }
            }
        }
    }

    companion object {
        private const val MAX_RELAY_LINE_BYTES = 64L * 1024L
        private const val MAX_SOCIAL_STORAGE_BYTES = 4L * 1024L * 1024L
        private val HEX_CHARS = "0123456789abcdef".toCharArray()

        @Volatile
        private var INSTANCE: SocialRepository? = null

        fun getInstance(context: Context): SocialRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SocialRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private val random = SecureRandom()

        fun generatePeerCode(): String {
            fun chunk(len: Int): String = (1..len).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
            return "LUMA-${chunk(4)}-${chunk(4)}"
        }

        fun normalizeCode(raw: String): String =
            raw.trim().uppercase().replace(" ", "")

        fun computePairTopic(codeA: String, codeB: String): String {
            val sorted = listOf(normalizeCode(codeA), normalizeCode(codeB)).sorted()
            val seed = "luma-p2p-channel::${sorted[0]}::${sorted[1]}"
            val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
            val sb = StringBuilder(24)
            // First 12 bytes = 24 lowercase hex chars (preserves protocol compatibility with existing peers)
            for (i in 0 until 12) {
                val v = digest[i].toInt() and 0xFF
                sb.append(HEX_CHARS[v ushr 4])
                sb.append(HEX_CHARS[v and 0x0F])
            }
            return "luma_p2p_$sb"
        }
    }
}
