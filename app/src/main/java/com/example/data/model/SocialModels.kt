package com.example.data.model

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable

/**
 * Live playback snapshot from Spotify, YouTube, YouTube Music, or any active Android MediaSession.
 */
@Immutable
data class MediaPlaybackInfo(
    val isNotificationAccessGranted: Boolean = false,
    val hasActiveSession: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val appName: String = "",
    val packageName: String = "",
    val albumArt: Bitmap? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastUpdatedRealtimeMs: Long = 0L
)

/**
 * Local-only user profile (no cloud database, no login, no stored accounts — only name & photo).
 */
@Immutable
data class UserProfile(
    val username: String = "",
    val avatarPath: String = "",
    val peerCode: String = "",
    val profileConfigured: Boolean = false,
    val privacy: PrivacySettings = PrivacySettings()
)

@Immutable
data class PrivacySettings(
    val showOnlineStatus: Boolean = true,
    val shareReadingBook: Boolean = true,
    val shareMediaActivity: Boolean = true,
    val allowFriendChat: Boolean = true
)

/**
 * A peer friend added via Friend Code; presence & reading/media info synced directly over internet P2P relay.
 */
@Immutable
data class FriendPeer(
    val peerCode: String,
    val displayName: String,
    val avatarBase64: String = "",
    val readingBookTitle: String = "",
    val readingProgressPct: Int = 0,
    val mediaTitle: String = "",
    val mediaArtist: String = "",
    val mediaApp: String = "",
    val isOnline: Boolean = false,
    val lastSeenEpochMs: Long = 0L,
    val unreadCount: Int = 0
)

@Immutable
data class FriendChatMessage(
    val id: String,
    val friendCode: String,
    val senderCode: String,
    val senderName: String,
    val text: String,
    val timestampMs: Long,
    val isFromMe: Boolean
)
