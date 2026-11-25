package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FriendChatMessage
import com.example.data.model.FriendPeer
import com.example.engine.SystemMediaController
import com.example.ui.theme.LocalFolioPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FriendCardShape = RoundedCornerShape(20.dp)
private val FriendFieldShape = RoundedCornerShape(14.dp)
private val FriendPillShape = RoundedCornerShape(50)
private val FriendSubPillShape = RoundedCornerShape(12.dp)
private val ChatHeaderShape = RoundedCornerShape(16.dp)
private val ChatInputShape = RoundedCornerShape(18.dp)
private val ChatBubbleMeShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
private val ChatBubbleOtherShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)

@Composable
internal fun FriendsPeerListTab(
    myPeerCode: String,
    friends: List<FriendPeer>,
    isSyncing: Boolean,
    onAddFriend: (code: String, alias: String) -> Pair<Boolean, String>,
    onOpenChat: (friendCode: String) -> Unit,
    onRemoveFriend: (friendCode: String) -> Unit,
    onSyncNow: () -> Unit
) {
    val palette = LocalFolioPalette.current
    var codeInput by remember { mutableStateOf("") }
    var aliasInput by remember { mutableStateOf("") }
    var feedbackMsg by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "add_friend_box", contentType = "add_friend_box") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(FriendCardShape)
                    .background(palette.surfaceAlt.copy(alpha = 0.55f))
                    .border(1.dp, palette.border, FriendCardShape)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.PersonAdd,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = "Conectar con un amigo",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.ink
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(FriendPillShape)
                            .background(palette.surface)
                            .border(1.dp, palette.border, FriendPillShape)
                            .clickable(enabled = !isSyncing, onClick = onSyncNow)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("friends_sync_now_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.8.dp,
                                    color = palette.accent
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Sincronizar amigos",
                                    tint = palette.accent,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Actualizar",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.ink
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it.uppercase().take(16) },
                    label = { Text("Código del amigo (ej. LUMA-XXXX-XXXX)") },
                    singleLine = true,
                    shape = FriendFieldShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.border,
                        focusedContainerColor = palette.surface,
                        unfocusedContainerColor = palette.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("friend_code_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = aliasInput,
                        onValueChange = { aliasInput = it.take(24) },
                        label = { Text("Apodo (opcional)") },
                        singleLine = true,
                        shape = FriendFieldShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.accent,
                            unfocusedBorderColor = palette.border,
                            focusedContainerColor = palette.surface,
                            unfocusedContainerColor = palette.surface
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    TactileButton(
                        onClick = {
                            val (ok, msg) = onAddFriend(codeInput, aliasInput)
                            feedbackMsg = msg
                            if (ok) {
                                codeInput = ""
                                aliasInput = ""
                            }
                        },
                        cornerRadius = 14.dp,
                        lipDepth = 2.dp,
                        modifier = Modifier.testTag("add_friend_submit_button")
                    ) {
                        Text("Agregar", fontWeight = FontWeight.Bold)
                    }
                }

                if (!feedbackMsg.isNullOrBlank()) {
                    Text(
                        text = feedbackMsg!!,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.accent
                    )
                }
            }
        }

        if (friends.isEmpty()) {
            item(key = "friends_empty", contentType = "friends_empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(FriendCardShape)
                        .background(palette.surfaceAlt.copy(alpha = 0.4f))
                        .border(1.dp, palette.border, FriendCardShape)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Group,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Aún no tienes amigos conectados",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.ink
                    )
                    Text(
                        text = "Intercambien sus códigos ($myPeerCode) para ver qué leen, qué escuchan en Spotify o YouTube, y chatear en vivo sin servidores ni cuentas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted
                    )
                }
            }
        } else {
            items(
                items = friends,
                key = { it.peerCode },
                contentType = { "friend_card" }
            ) { friend ->
                FriendStatusCard(
                    friend = friend,
                    onOpenChat = { onOpenChat(friend.peerCode) },
                    onRemove = { onRemoveFriend(friend.peerCode) }
                )
            }
        }

        item(key = "friends_bottom_spacer", contentType = "spacer") { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun FriendStatusCard(
    friend: FriendPeer,
    onOpenChat: () -> Unit,
    onRemove: () -> Unit
) {
    val palette = LocalFolioPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FriendCardShape)
            .background(palette.surfaceAlt.copy(alpha = 0.55f))
            .border(1.dp, palette.border, FriendCardShape)
            .clickable(onClick = onOpenChat)
            .padding(14.dp)
            .testTag("friend_card_${friend.peerCode}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatarCircle(
                displayName = friend.displayName,
                avatarPath = "",
                avatarBase64 = friend.avatarBase64,
                size = 46.dp,
                showOnlineDot = friend.isOnline
            )
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (friend.isOnline) "En línea · ${friend.peerCode}" else friend.peerCode,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (friend.isOnline) Color(0xFF28B97B) else palette.inkMuted,
                    maxLines = 1
                )
            }
            if (friend.unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(palette.accent)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${friend.unreadCount}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.onAccent
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(palette.surface)
                    .clickable(onClick = onOpenChat),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChatBubbleOutline,
                    contentDescription = "Chatear con ${friend.displayName}",
                    tint = palette.accent,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Eliminar amigo",
                    tint = palette.inkMuted,
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        // Live Book Reading Pill
        if (friend.readingBookTitle.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(FriendSubPillShape)
                    .background(palette.surface)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoStories,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Leyendo: ${friend.readingBookTitle}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${friend.readingProgressPct}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.accent
                )
            }
        }

        // Live Spotify / YouTube / Media Pill
        if (friend.mediaTitle.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(FriendSubPillShape)
                    .background(Color(0xFF1DB954).copy(alpha = 0.13f))
                    .border(1.dp, Color(0xFF1DB954).copy(alpha = 0.3f), FriendSubPillShape)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFF1DB954),
                    modifier = Modifier.size(15.dp)
                )
                val subtitle = remember(friend.mediaApp, friend.mediaTitle, friend.mediaArtist) {
                    buildString {
                        if (friend.mediaApp.isNotBlank()) append("${friend.mediaApp} · ")
                        append(friend.mediaTitle)
                        if (friend.mediaArtist.isNotBlank()) append(" — ${friend.mediaArtist}")
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun FriendSimpleChatPane(
    friend: FriendPeer,
    messages: List<FriendChatMessage>,
    currentBookTitle: String,
    allowChat: Boolean,
    onBack: () -> Unit,
    onMarkRead: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    val palette = LocalFolioPalette.current
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LaunchedEffect(messages.size) {
        onMarkRead()
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("friend_chat_pane")
    ) {
        // Chat Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ChatHeaderShape)
                .background(palette.surfaceAlt.copy(alpha = 0.65f))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Volver a amigos",
                    tint = palette.ink,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            UserAvatarCircle(
                displayName = friend.displayName,
                avatarPath = "",
                avatarBase64 = friend.avatarBase64,
                size = 36.dp,
                showOnlineDot = friend.isOnline
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.ink,
                    maxLines = 1
                )
                val liveSub = when {
                    friend.readingBookTitle.isNotBlank() -> "Leyendo ${friend.readingBookTitle}"
                    friend.mediaTitle.isNotBlank() -> "Escuchando ${friend.mediaTitle}"
                    friend.isOnline -> "En línea"
                    else -> "Chat P2P directo"
                }
                Text(
                    text = liveSub,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item(key = "chat_empty", contentType = "chat_empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Envía un mensaje a ${friend.displayName} para empezar a chatear.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkMuted
                        )
                    }
                }
            } else {
                items(
                    items = messages,
                    key = { it.id },
                    contentType = { if (it.isFromMe) "chat_msg_me" else "chat_msg_peer" }
                ) { msg ->
                    val align = if (msg.isFromMe) Alignment.End else Alignment.Start
                    val bubbleBg = if (msg.isFromMe) palette.accent else palette.surfaceAlt
                    val bubbleFg = if (msg.isFromMe) palette.onAccent else palette.ink
                    val bubbleShape = if (msg.isFromMe) ChatBubbleMeShape else ChatBubbleOtherShape
                    val formattedTime = remember(msg.timestampMs) {
                        timeFormat.format(Date(msg.timestampMs))
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = align
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 270.dp)
                                .clip(bubbleShape)
                                .background(bubbleBg)
                                .padding(horizontal = 13.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = bubbleFg
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = formattedTime,
                                fontSize = 10.sp,
                                color = bubbleFg.copy(alpha = 0.68f),
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }
        }

        // Quick Share Chips (isolates 1Hz mediaState so chat messages never recompose every second!)
        ChatQuickShareRow(
            currentBookTitle = currentBookTitle,
            onSendMessage = onSendMessage
        )

        // Message Composer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = {
                    Text(
                        text = if (allowChat) "Escribe un mensaje…" else "Chat desactivado en Privacidad",
                        color = palette.inkMuted
                    )
                },
                enabled = allowChat,
                singleLine = true,
                shape = ChatInputShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.accent,
                    unfocusedBorderColor = palette.border,
                    focusedContainerColor = palette.surfaceAlt.copy(alpha = 0.45f),
                    unfocusedContainerColor = palette.surfaceAlt.copy(alpha = 0.45f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("friend_chat_input")
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (draft.isNotBlank() && allowChat) palette.accent else palette.surfaceAlt)
                    .clickable(enabled = draft.isNotBlank() && allowChat) {
                        onSendMessage(draft)
                        draft = ""
                    }
                    .testTag("friend_chat_send_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Enviar mensaje",
                    tint = if (draft.isNotBlank() && allowChat) palette.onAccent else palette.inkMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatQuickShareRow(
    currentBookTitle: String,
    onSendMessage: (String) -> Unit
) {
    val palette = LocalFolioPalette.current
    val mediaInfo by SystemMediaController.mediaState.collectAsStateWithLifecycle()
    val hasMedia = mediaInfo.hasActiveSession && mediaInfo.title.isNotBlank()

    if (currentBookTitle.isNotBlank() || hasMedia) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (currentBookTitle.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(FriendPillShape)
                        .background(palette.accentSoft)
                        .clickable { onSendMessage("Estoy leyendo \"$currentBookTitle\"") }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "Compartir libro",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent
                    )
                }
            }
            if (hasMedia) {
                Box(
                    modifier = Modifier
                        .clip(FriendPillShape)
                        .background(Color(0xFF1DB954).copy(alpha = 0.16f))
                        .clickable {
                            val app = mediaInfo.appName.ifBlank { "Música" }
                            onSendMessage("Escuchando en $app: ${mediaInfo.title}")
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "Compartir música",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1DB954)
                    )
                }
            }
        }
    }
}
