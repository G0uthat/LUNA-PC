package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Base64
import android.util.LruCache
import com.example.engine.BitmapDecodeUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PrivacySettings
import com.example.data.model.UserProfile
import com.example.engine.SocialRepository
import com.example.engine.SystemMediaController
import com.example.ui.theme.LocalFolioPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val AccountCardShape22 = RoundedCornerShape(22.dp)
private val AccountCardShape20 = RoundedCornerShape(20.dp)
private val AccountFieldShape14 = RoundedCornerShape(14.dp)
private val AccountSetupDialogShape = RoundedCornerShape(28.dp)

/**
 * Standalone Account Configuration Dialog (ONLY Profile, Avatar, Friend Code & Privacy).
 * Strictly separated from App Settings and Friends so each section stays focused and clean.
 */
@Composable
fun AccountProfileDialog(
    socialRepo: SocialRepository,
    currentOrRecentBookTitle: String,
    currentOrRecentBookProgressPct: Int,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val context = LocalContext.current

    val profile by socialRepo.profile.collectAsStateWithLifecycle()

    // Zero-permission Android Photo Picker for custom profile avatar
    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            socialRepo.updateAvatarFromUri(uri)
        }
    }

    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.TopCenter,
        transformOrigin = TransformOrigin(0.84f, 0.06f),
        maxWidth = 540.dp,
        maxHeightFraction = 0.85f,
        testTag = "account_profile_dialog"
    ) { dismissAnimated ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatarCircle(
                displayName = profile.username.ifBlank { "C" },
                avatarPath = profile.avatarPath,
                avatarBase64 = "",
                size = 42.dp,
                showOnlineDot = profile.privacy.showOnlineStatus
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Cuenta",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Perfil local y privacidad",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.inkMuted,
                    maxLines = 1
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(palette.surfaceAlt)
                    .border(1.dp, palette.border, CircleShape)
                    .clickable(onClick = dismissAnimated)
                    .testTag("account_profile_close"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Cerrar cuenta",
                    tint = palette.ink,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 1.dp,
            color = palette.border.copy(alpha = 0.6f)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(top = 6.dp)
        ) {
            ProfileAndPrivacyTab(
                profile = profile,
                currentBookTitle = currentOrRecentBookTitle,
                currentBookProgressPct = currentOrRecentBookProgressPct,
                onPickAvatar = {
                    avatarPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onSaveName = { socialRepo.updateProfileName(it) },
                onUpdatePrivacy = { socialRepo.updatePrivacy(it) },
                onRegenerateCode = { socialRepo.regeneratePeerCode() },
                onCopyCode = { code ->
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("Código Luma", code))
                },
                onShareCode = { code ->
                    val name = profile.username.ifBlank { "un amigo" }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Agrégame en Luma para ver qué leemos y escuchamos. Mi código de amigo es: $code ($name)"
                        )
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(intent, "Compartir código de amigo"))
                    }
                }
            )
        }
    }
}

/**
 * Standalone Friends & Simple P2P Chat Dialog (ONLY Friends & Chat).
 * Strictly separated from Account settings and App settings.
 */
@Composable
fun FriendsChatDialog(
    socialRepo: SocialRepository,
    currentOrRecentBookTitle: String,
    onDismiss: () -> Unit
) {
    val profile by socialRepo.profile.collectAsStateWithLifecycle()
    val friends by socialRepo.friends.collectAsStateWithLifecycle()
    val messagesByFriend by socialRepo.messagesByFriend.collectAsStateWithLifecycle()
    val isSyncing by socialRepo.isSyncing.collectAsStateWithLifecycle()

    var selectedChatFriendCode by remember { mutableStateOf<String?>(null) }

    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.TopCenter,
        transformOrigin = TransformOrigin(0.84f, 0.06f),
        maxWidth = 540.dp,
        maxHeightFraction = 0.85f,
        testTag = "friends_chat_dialog"
    ) { dismissAnimated ->
        LumaDropdownHeader(
            title = "Amigos",
            subtitle = "Lectura compartida y chat P2P",
            leadingIcon = Icons.Rounded.Group,
            onDismiss = dismissAnimated,
            closeTestTag = "friends_chat_close"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(top = 6.dp)
        ) {
            val activeFriend = friends.firstOrNull { it.peerCode == selectedChatFriendCode }
            if (activeFriend != null) {
                val chatMessages = messagesByFriend[activeFriend.peerCode].orEmpty()
                FriendSimpleChatPane(
                    friend = activeFriend,
                    messages = chatMessages,
                    currentBookTitle = currentOrRecentBookTitle,
                    allowChat = profile.privacy.allowFriendChat,
                    onBack = { selectedChatFriendCode = null },
                    onMarkRead = { socialRepo.markFriendChatRead(activeFriend.peerCode) },
                    onSendMessage = { text ->
                        socialRepo.sendChatMessage(activeFriend.peerCode, text)
                    }
                )
            } else {
                FriendsPeerListTab(
                    myPeerCode = profile.peerCode,
                    friends = friends,
                    isSyncing = isSyncing,
                    onAddFriend = { code, alias -> socialRepo.addFriendByCode(code, alias) },
                    onOpenChat = { code ->
                        socialRepo.markFriendChatRead(code)
                        selectedChatFriendCode = code
                    },
                    onRemoveFriend = { code -> socialRepo.removeFriend(code) },
                    onSyncNow = { socialRepo.syncNow() }
                )
            }
        }
    }
}

@Composable
private fun ProfileAndPrivacyTab(
    profile: UserProfile,
    currentBookTitle: String,
    currentBookProgressPct: Int,
    onPickAvatar: () -> Unit,
    onSaveName: (String) -> Unit,
    onUpdatePrivacy: ((PrivacySettings) -> PrivacySettings) -> Unit,
    onRegenerateCode: () -> Unit,
    onCopyCode: (String) -> Unit,
    onShareCode: (String) -> Unit
) {
    val palette = LocalFolioPalette.current
    var nameInput by remember(profile.username) { mutableStateOf(profile.username) }
    var copiedNotice by remember { mutableStateOf(false) }
    var savedNotice by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Customizable Photo + Username Card (Local only, no account registration!)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AccountCardShape22)
                .background(palette.surfaceAlt.copy(alpha = 0.55f))
                .border(1.dp, palette.border, AccountCardShape22)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clickable(onClick = onPickAvatar)
                    .testTag("profile_pick_avatar_button"),
                contentAlignment = Alignment.Center
            ) {
                UserAvatarCircle(
                    displayName = nameInput.ifBlank { "Lector" },
                    avatarPath = profile.avatarPath,
                    avatarBase64 = "",
                    size = 84.dp,
                    showOnlineDot = false
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(palette.accent)
                        .border(2.dp, palette.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = "Cambiar foto de perfil",
                        tint = palette.onAccent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = {
                        nameInput = it.take(28)
                        savedNotice = false
                    },
                    label = { Text("Nombre de usuario") },
                    placeholder = { Text("Ej. Sofía, Alex…") },
                    singleLine = true,
                    shape = AccountFieldShape14,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.border,
                        focusedContainerColor = palette.surface,
                        unfocusedContainerColor = palette.surface
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("profile_username_input")
                )
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(AccountFieldShape14)
                        .background(palette.accent)
                        .clickable {
                            onSaveName(nameInput)
                            savedNotice = true
                        }
                        .testTag("profile_save_name_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Guardar nombre",
                        tint = palette.onAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            if (savedNotice) {
                Text(
                    text = "Perfil actualizado en este dispositivo",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.accent
                )
            }
        }

        // 2. Live Activity Preview (what friends see)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AccountCardShape20)
                .background(palette.surfaceAlt.copy(alpha = 0.45f))
                .border(1.dp, palette.border, AccountCardShape20)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Tu actividad compartida",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = palette.ink
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoStories,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = when {
                        !profile.privacy.shareReadingBook -> "Lectura oculta por privacidad"
                        currentBookTitle.isNotBlank() -> "Leyendo: $currentBookTitle ($currentBookProgressPct%)"
                        else -> "Abre un libro para compartir tu lectura"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Narrowly scoped media row so 1Hz music progress ticks NEVER recompose the entire profile form
            ProfileMediaActivityRow(shareMediaActivity = profile.privacy.shareMediaActivity)
        }

        // 3. Peer Friend Code (Direct internet P2P, zero cloud database!)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AccountCardShape20)
                .background(palette.accentSoft.copy(alpha = 0.65f))
                .border(1.dp, palette.accent.copy(alpha = 0.35f), AccountCardShape20)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tu código de amigo (P2P)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.ink
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onRegenerateCode),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Generar nuevo código",
                        tint = palette.inkMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = profile.peerCode,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
                color = palette.accent,
                modifier = Modifier.testTag("profile_peer_code_text")
            )
            Text(
                text = "Comparte este código para conectar directo con amigos sin crear cuentas ni servidores.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkMuted
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TactileButton(
                    onClick = {
                        onCopyCode(profile.peerCode)
                        copiedNotice = true
                    },
                    containerColor = palette.surface,
                    shadowColor = palette.borderShadow,
                    contentColor = palette.ink,
                    borderColor = palette.border,
                    cornerRadius = 14.dp,
                    lipDepth = 2.dp,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("profile_copy_code_button")
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (copiedNotice) "Copiado" else "Copiar", fontWeight = FontWeight.Bold)
                }
                TactileButton(
                    onClick = { onShareCode(profile.peerCode) },
                    cornerRadius = 14.dp,
                    lipDepth = 2.dp,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("profile_share_code_button")
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Compartir", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 4. Privacy Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AccountCardShape20)
                .background(palette.surfaceAlt.copy(alpha = 0.55f))
                .border(1.dp, palette.border, AccountCardShape20)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "Privacidad",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.ink
                )
            }
            PrivacyToggleRow(
                title = "Mostrar estado en línea",
                subtitle = "Tus amigos verán cuándo estás activo",
                checked = profile.privacy.showOnlineStatus,
                onCheckedChange = { v -> onUpdatePrivacy { it.copy(showOnlineStatus = v) } },
                testTag = "privacy_online_switch"
            )
            PrivacyToggleRow(
                title = "Compartir libro actual",
                subtitle = "Muestra el título y avance que estás leyendo",
                checked = profile.privacy.shareReadingBook,
                onCheckedChange = { v -> onUpdatePrivacy { it.copy(shareReadingBook = v) } },
                testTag = "privacy_book_switch"
            )
            PrivacyToggleRow(
                title = "Compartir música y video",
                subtitle = "Muestra qué escuchas en Spotify, YouTube u otras apps",
                checked = profile.privacy.shareMediaActivity,
                onCheckedChange = { v -> onUpdatePrivacy { it.copy(shareMediaActivity = v) } },
                testTag = "privacy_media_switch"
            )
            PrivacyToggleRow(
                title = "Permitir chat entre amigos",
                subtitle = "Recibe mensajes simples en tiempo real",
                checked = profile.privacy.allowFriendChat,
                onCheckedChange = { v -> onUpdatePrivacy { it.copy(allowFriendChat = v) } },
                testTag = "privacy_chat_switch"
            )
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ProfileMediaActivityRow(shareMediaActivity: Boolean) {
    val palette = LocalFolioPalette.current
    val mediaInfo by SystemMediaController.mediaState.collectAsStateWithLifecycle()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = Color(0xFF1DB954),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = when {
                !shareMediaActivity -> "Música / video oculto por privacidad"
                mediaInfo.hasActiveSession && mediaInfo.title.isNotBlank() ->
                    "${mediaInfo.appName.ifBlank { "Multimedia" }}: ${mediaInfo.title}"
                else -> "Sin música o video activo ahora"
            },
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PrivacyToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val palette = LocalFolioPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = palette.ink
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkMuted
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
            modifier = Modifier.testTag(testTag)
        )
    }
}

/**
 * First-launch lightweight profile dialog: asks ONLY for a display name and optional photo.
 * Zero accounts, zero passwords, zero cloud database.
 */
@Composable
fun QuickProfileSetupDialog(
    profile: UserProfile,
    onPickAvatar: () -> Unit,
    onSave: (String) -> Unit,
    onSkip: () -> Unit
) {
    val palette = LocalFolioPalette.current
    var name by remember(profile.username) { mutableStateOf(profile.username) }

    Dialog(onDismissRequest = onSkip) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(20.dp, AccountSetupDialogShape)
                .clip(AccountSetupDialogShape)
                .background(palette.surface)
                .border(1.dp, palette.border, AccountSetupDialogShape)
                .padding(22.dp)
                .testTag("quick_profile_setup_dialog"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Personaliza tu perfil",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = palette.ink
            )
            Text(
                text = "Solo necesitamos un nombre y una foto opcional. Todo se guarda en tu dispositivo, sin cuentas ni registro.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkMuted
            )

            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clickable(onClick = onPickAvatar)
                    .testTag("setup_pick_avatar_button"),
                contentAlignment = Alignment.Center
            ) {
                UserAvatarCircle(
                    displayName = name.ifBlank { "L" },
                    avatarPath = profile.avatarPath,
                    avatarBase64 = "",
                    size = 82.dp,
                    showOnlineDot = false
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(palette.accent)
                        .border(2.dp, palette.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = "Elegir foto",
                        tint = palette.onAccent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(28) },
                label = { Text("Tu nombre") },
                placeholder = { Text("Ej. Mateo, Camila…") },
                singleLine = true,
                shape = AccountFieldShape14,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_username_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Ahora no", color = palette.inkMuted)
                }
                TactileButton(
                    onClick = { onSave(name.ifBlank { "Lector" }) },
                    cornerRadius = 16.dp,
                    lipDepth = 2.dp,
                    modifier = Modifier
                        .weight(1.4f)
                        .testTag("setup_save_profile_button")
                ) {
                    Text("Guardar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private object AvatarBitmapCache {
    private val cache = object : LruCache<String, Bitmap>(6 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    fun get(key: String): Bitmap? = cache.get(key)?.takeIf { !it.isRecycled }
    fun put(key: String, bmp: Bitmap) { cache.put(key, bmp) }
}

/**
 * Reusable circular avatar supporting local file path, peer Base64 thumbnail, or crisp initial fallback.
 * Uses [AvatarBitmapCache] so avatars appear instantly with zero flicker or repeated disk decoding.
 */
@Composable
fun UserAvatarCircle(
    displayName: String,
    avatarPath: String,
    avatarBase64: String,
    size: Dp,
    showOnlineDot: Boolean = false,
    unreadBadge: Int = 0,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val cacheKey = remember(avatarPath, avatarBase64) {
        when {
            avatarPath.isNotBlank() -> "path:$avatarPath"
            avatarBase64.isNotBlank() -> "b64:${avatarBase64.hashCode()}:${avatarBase64.length}"
            else -> ""
        }
    }
    val cachedBitmap = remember(cacheKey) { if (cacheKey.isNotEmpty()) AvatarBitmapCache.get(cacheKey) else null }
    val bitmap by produceState<Bitmap?>(initialValue = cachedBitmap, key1 = cacheKey) {
        if (cachedBitmap != null) {
            value = cachedBitmap
            return@produceState
        }
        value = if (cacheKey.isEmpty()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                val decoded = when {
                    avatarPath.isNotBlank() -> runCatching {
                        BitmapDecodeUtils.decodeSampledFile(avatarPath, reqWidth = 256, reqHeight = 256)
                    }.getOrNull()
                    avatarBase64.isNotBlank() -> runCatching {
                        val bytes = Base64.decode(avatarBase64, Base64.DEFAULT)
                        BitmapDecodeUtils.decodeSampledBytes(bytes, reqWidth = 256, reqHeight = 256)
                    }.getOrNull()
                    else -> null
                }
                if (decoded != null) {
                    AvatarBitmapCache.put(cacheKey, decoded)
                }
                decoded
            }
        }
    }
    val avatarImage = remember(bitmap) { bitmap?.asImageBitmap() }
    val avatarGradient = remember(palette.accent, palette.accentShadow) {
        Brush.linearGradient(colors = listOf(palette.accent, palette.accentShadow))
    }

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(avatarGradient)
                .border(1.5.dp, palette.border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (avatarImage != null) {
                Image(
                    bitmap = avatarImage,
                    contentDescription = "Foto de perfil de $displayName",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val initial = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "L"
                Text(
                    text = initial,
                    fontSize = (size.value * 0.42f).sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }

        if (showOnlineDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size((size * 0.28f).coerceIn(9.dp, 13.dp))
                    .clip(CircleShape)
                    .background(Color(0xFF28B97B))
                    .border(2.dp, palette.surface, CircleShape)
            )
        }

        if (unreadBadge > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .heightIn(min = 17.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF26370))
                    .border(1.5.dp, palette.surface, CircleShape)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = unreadBadge.coerceAtMost(9).let { if (unreadBadge > 9) "9+" else "$it" },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }
    }
}
