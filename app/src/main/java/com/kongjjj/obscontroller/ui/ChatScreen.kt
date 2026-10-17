package com.kongjjj.obscontroller.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.kongjjj.obscontroller.R
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import androidx.core.graphics.toColorInt
import com.kongjjj.obscontroller.MessageSegment
import com.kongjjj.obscontroller.ChatMessage
import com.kongjjj.obscontroller.MessageType
import com.kongjjj.obscontroller.getLocalizedSystemMessage
import com.kongjjj.obscontroller.parseMessageSegments
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
    twitchChannel: String,
    youtubeChannelId: String,
    kickChannel: String = "",
    chatMessages: List<ChatMessage>,
    chatConnected: Boolean,
    thirdPartyEmotes: Map<String, String>,
    twitchBadges: Map<String, String>,
    emoteLoadReport: String,
    chatFontSize: Float,
    chatLineSpacing: Float,
    chatEmoteSize: Float,
    chatUsernameSize: Float,
    animatedEmotes: Boolean,
    showMessageTime: Boolean,
    showExpandButton: Boolean,
    showFullScreenButton: Boolean,
    showScreenLockButton: Boolean,
    fullScreenActive: Boolean,
    isChatLocked: Boolean,
    showDebugBar: Boolean,
    showEmoteDebug: Boolean,
    viewerCount: Int?,
    youtubeViewerCount: Int?,
    kickViewerCount: Int? = null,
    streamUptime: String,
    streamCategory: String?,
    onConnect: () -> Unit,
    onToggleFullScreen: () -> Unit,
    onToggleChatLock: () -> Unit
) {
    val listState = rememberLazyListState()

    // Auto-connect when a channel is configured
    LaunchedEffect(twitchChannel, youtubeChannelId, kickChannel) {
        if (twitchChannel.isNotEmpty() || youtubeChannelId.isNotEmpty() || kickChannel.isNotEmpty()) onConnect()
    }

    // Keep track of whether we've already done the initial scroll for the current set of messages
    var hasPerformedInitialScroll by remember(twitchChannel) { mutableStateOf(false) }

    // Scroll to bottom when messages first appear or when switching back to this tab
    LaunchedEffect(chatMessages.isNotEmpty()) {
        if (chatMessages.isNotEmpty() && !hasPerformedInitialScroll) {
            listState.scrollToItem(chatMessages.size - 1)
            hasPerformedInitialScroll = true
        }
    }

    // Auto-scroll: consider "at bottom" if last visible item is within 1 of the end.
    // The ±1 buffer handles the race where totalItemsCount increases right as
    // a new message is added, before the LaunchedEffect can read the layout.
    val isAtBottom by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            if (total == 0) return@derivedStateOf true
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            // Use a threshold of 25 to allow auto-scroll when user is near the bottom
            lastVisible >= total - 25
        }
    }

    val context = LocalContext.current
    val imageLoader: ImageLoader = remember(animatedEmotes) {
        if (animatedEmotes) {
            ImageLoader.Builder(context).components { add(GifDecoder.Factory()) }.build()
        } else {
            ImageLoader.Builder(context).build()
        }
    }

    val systemInstruction = remember {
        ChatMessage(
            id = "system_instruction",
            username = "系統說明",
            login = null,
            message = "按 Home 鍵跳到桌面，聊天室會繼續運作；若按 Back 會結束程式並斷開 OBS 連線。",
            color = "#FFD700", // Gold
            platform = "system"
        )
    }

    val combinedMessages = remember(chatMessages) {
        if (chatMessages.isEmpty()) listOf(systemInstruction)
        else listOf(systemInstruction) + chatMessages
    }

    LaunchedEffect(chatMessages) {
        if (isAtBottom && chatMessages.isNotEmpty()) {
            listState.scrollToItem(combinedMessages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Status row ────────────────────────────────────────────────────────
        if (twitchChannel.isNotEmpty() || youtubeChannelId.isNotEmpty() || kickChannel.isNotEmpty()) {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (twitchChannel.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            color = if (chatConnected) Color(0xFF9146FF) else Color(0xFF9E9E9E),
                                            shape = CircleShape
                                        )
                                )
                                Text(
                                    text = "Twitch: #$twitchChannel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (showDebugBar && viewerCount != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Person,
                                            null,
                                            modifier = Modifier.size(12.dp),
                                            tint = Color(0xFF9146FF)
                                        )
                                        Text(
                                            text = String.format(Locale.US, "%,d", viewerCount),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        streamCategory?.let {
                                            Text(
                                                text = "($it)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                    if (streamUptime != "00:00:00") {
                                        Text(
                                            text = streamUptime,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        if (youtubeChannelId.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            color = if (chatConnected) Color(0xFFFF0000) else Color(0xFF9E9E9E),
                                            shape = CircleShape
                                        )
                                )
                                Text(
                                    text = "YouTube: $youtubeChannelId",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (youtubeViewerCount != null && youtubeViewerCount > 0) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Person,
                                            null,
                                            modifier = Modifier.size(12.dp),
                                            tint = Color(0xFFFF0000)
                                        )
                                        Text(
                                            text = String.format(Locale.US, "%,d", youtubeViewerCount),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        if (kickChannel.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            color = if (chatConnected) Color(0xFF53FC18) else Color(0xFF9E9E9E),
                                            shape = CircleShape
                                        )
                                )
                                Text(
                                    text = "Kick: #$kickChannel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (kickViewerCount != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = "Kick Viewers",
                                            modifier = Modifier.size(12.dp),
                                            tint = Color(0xFF53FC18)
                                        )
                                        Text(
                                            text = String.format(Locale.US, "%,d", kickViewerCount),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        if (showEmoteDebug && emoteLoadReport.isNotEmpty()) {
                            Text(
                                text = "Emotes: $emoteLoadReport",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontSize = 9.sp
                            )
                        }
                    }
                    // Reconnect button removed as per user request (handled by auto-reconnect)
                }
            }
            HorizontalDivider()
        }

        // ── Messages / empty states ───────────────────────────────────────────
        if (twitchChannel.isEmpty() && youtubeChannelId.isEmpty() && kickChannel.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tap the OBS Controller title above to open Settings and enter your Twitch channel, YouTube Channel ID, or Kick channel.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else if (chatMessages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (chatConnected) "Waiting for chat messages…" else "Not connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(combinedMessages, key = { it.id }) { msg ->
                        ChatMessageRow(
                            message = msg,
                            thirdPartyEmotes = thirdPartyEmotes,
                            twitchBadges = twitchBadges,
                            fontSize = chatFontSize,
                            lineSpacing = chatLineSpacing,
                            emoteSize = chatEmoteSize,
                            usernameSize = chatUsernameSize,
                            showMessageTime = showMessageTime,
                            imageLoader = imageLoader
                        )
                    }
                }

                // Interaction blocker when locked
                if (isChatLocked) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { /* consume */ }
                            }
                    )
                }

                if (showExpandButton || showFullScreenButton || showScreenLockButton) {
                    val scope = rememberCoroutineScope()
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (showFullScreenButton) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .pointerInput(Unit) {
                                        detectTapGestures(onTap = { onToggleFullScreen() })
                                    }
                            ) {
                                Icon(
                                    imageVector = if (fullScreenActive) Icons.Default.CloseFullscreen else Icons.Default.Fullscreen,
                                    contentDescription = "Toggle Fullscreen",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        if (showScreenLockButton) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                onToggleChatLock()
                                            },
                                            onTap = {
                                                Toast.makeText(context, "Double-click圖示以啟動及解除鎖定", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                            ) {
                                Icon(
                                    imageVector = if (isChatLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = "Toggle Lock",
                                    tint = if (isChatLocked) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        if (showExpandButton) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .pointerInput(Unit) {
                                        detectTapGestures(onTap = {
                                            scope.launch {
                                                listState.scrollToItem(combinedMessages.size - 1)
                                            }
                                        })
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ExpandMore,
                                    contentDescription = "Jump to latest",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageRow(
    message: ChatMessage,
    thirdPartyEmotes: Map<String, String>,
    twitchBadges: Map<String, String>,
    fontSize: Float,
    lineSpacing: Float,
    emoteSize: Float,
    usernameSize: Float,
    showMessageTime: Boolean,
    imageLoader: ImageLoader
) {
    val badgeSize = (fontSize * 1.1f).sp
    val emoteSizeSp = emoteSize.sp

    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeStr = remember(message.timestamp) {
        val ts = message.timestamp
        if (ts != null) timeFormatter.format(Date(ts)) else ""
    }
    val timeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    val defaultColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val nameColor: Color = remember(message.color, defaultColor, message.type, message.platform) {
        if (message.type == MessageType.USER_NOTICE && message.platform == "youtube") {
            Color(0xFF64B5F6)
        } else if (message.color != null) {
            try { Color(message.color.toColorInt()) }
            catch (_: Exception) { defaultColor }
        } else defaultColor
    }

    // Special message styling (Announcements & UserNotices & Bits & Highlights)
    val announcementBgColor = remember(message.type, message.announcementColor, message.bits, message.isHighlighted, message.platform) {
        when {
            message.isHighlighted -> Color(0xFFFFD700).copy(alpha = 0.15f) // Gold for Highlights
            message.type == MessageType.ANNOUNCEMENT -> {
                when (message.announcementColor?.uppercase()) {
                    "BLUE" -> Color(0xFF00ADFF).copy(alpha = 0.15f)
                    "GREEN" -> Color(0xFF00FF7F).copy(alpha = 0.15f)
                    "ORANGE" -> Color(0xFFFF8C00).copy(alpha = 0.15f)
                    "PURPLE" -> Color(0xFFA020F0).copy(alpha = 0.15f)
                    "PRIMARY" -> Color(0xFF9146FF).copy(alpha = 0.15f)
                    "RED" -> Color(0xFFFF0000).copy(alpha = 0.15f)
                    else -> Color(0xFF9146FF).copy(alpha = 0.1f) // Fallback Twitch Purple
                }
            }
            message.type == MessageType.USER_NOTICE || message.bits > 0 -> {
                if (message.announcementColor?.uppercase() == "RED" || message.platform == "youtube") {
                    Color(0xFFFF0000).copy(alpha = 0.15f)
                } else {
                    Color(0xFF9146FF).copy(alpha = 0.1f) // Default Purple for Subs/Raids/Bits
                }
            }
            else -> null
        }
    }
    val announcementBorderColor = remember(message.type, message.announcementColor, message.bits, message.isHighlighted, message.platform) {
        when {
            message.isHighlighted -> Color(0xFFFFD700)
            message.type == MessageType.ANNOUNCEMENT -> {
                when (message.announcementColor?.uppercase()) {
                    "BLUE" -> Color(0xFF00ADFF)
                    "GREEN" -> Color(0xFF00FF7F)
                    "ORANGE" -> Color(0xFFFF8C00)
                    "PURPLE" -> Color(0xFFFF8C00)
                    "PRIMARY" -> Color(0xFF9146FF)
                    "RED" -> Color(0xFFFF0000)
                    else -> Color(0xFF9146FF)
                }
            }
            message.type == MessageType.USER_NOTICE || message.bits > 0 -> {
                if (message.announcementColor?.uppercase() == "RED" || message.platform == "youtube") {
                    Color(0xFFFF0000)
                } else {
                    Color(0xFF9146FF).copy(alpha = 0.5f) // Softer border
                }
            }
            else -> null
        }
    }

    // Resolve badge URLs, falling back to version "0" for channel-specific sets
    val badgeUrls: List<String> = remember(message.id, twitchBadges.size) {
        message.badgeTags.map { tag ->
            when {
                tag.startsWith("http") -> tag
                tag == "yt-MODERATOR" -> "local:ic_youtubemod"
                tag == "yt-OWNER" -> "https://www.gstatic.com/youtube/img/live_chat/badges/owner_active.png"
                tag == "yt-VERIFIED" -> "https://www.gstatic.com/youtube/img/live_chat/badges/verified_active.png"
                tag == "kick-broadcaster" -> "https://kick.com/assets/images/badges/broadcaster.svg"
                tag == "kick-moderator" -> "https://kick.com/assets/images/badges/moderator.svg"
                tag == "kick-vip" -> "https://kick.com/assets/images/badges/vip.svg"
                tag == "kick-subscriber" -> "https://kick.com/assets/images/badges/subscriber.svg"
                tag == "kick-verified" -> "https://kick.com/assets/images/badges/verified.svg"
                tag == "kick-founder" -> "https://kick.com/assets/images/badges/founder.svg"
                tag == "kick-og" -> "https://kick.com/assets/images/badges/og.svg"
                else -> twitchBadges[tag] ?: twitchBadges["${tag.substringBefore('/')}/0"] ?: ""
            }
        }.filter { it.isNotEmpty() }
    }

    val segments: List<MessageSegment> = remember(message.id, thirdPartyEmotes.size) {
        parseMessageSegments(message.message, message.emotesTag, thirdPartyEmotes, message.youtubeEmotes, message.bits)
    }

    val inlineContent: Map<String, InlineTextContent> = remember(
        message.id, thirdPartyEmotes.size, twitchBadges.size, imageLoader, fontSize, emoteSize
    ) {
        buildMap {
            put("platform_icon", InlineTextContent(
                Placeholder(badgeSize, badgeSize, PlaceholderVerticalAlign.TextCenter)
            ) {
                val iconRes = when (message.platform) {
                    "youtube" -> R.drawable.ic_youtube
                    "kick" -> R.drawable.ic_kick
                    else -> R.drawable.ic_twitch
                }
                AsyncImage(model = iconRes, contentDescription = message.platform,
                    imageLoader = imageLoader, modifier = Modifier.fillMaxSize())
            })
            badgeUrls.forEach { url ->
                put(url, InlineTextContent(
                    Placeholder(badgeSize, badgeSize, PlaceholderVerticalAlign.TextCenter)
                ) {
                    val model: Any = if (url == "local:ic_youtubemod") R.drawable.ic_youtubemod else url
                    AsyncImage(model = model, contentDescription = null,
                        imageLoader = imageLoader, modifier = Modifier.fillMaxSize())
                })
            }
            segments.forEach { seg ->
                if (seg is MessageSegment.EmotePart) {
                    put(seg.url, InlineTextContent(
                        Placeholder(emoteSizeSp, emoteSizeSp, PlaceholderVerticalAlign.TextCenter)
                    ) {
                        AsyncImage(model = seg.url, contentDescription = seg.name,
                            imageLoader = imageLoader, modifier = Modifier.fillMaxSize())
                    })
                } else if (seg is MessageSegment.BitsPart) {
                    put(seg.url, InlineTextContent(
                        Placeholder(emoteSizeSp, emoteSizeSp, PlaceholderVerticalAlign.TextCenter)
                    ) {
                        AsyncImage(model = seg.url, contentDescription = seg.name,
                            imageLoader = imageLoader, modifier = Modifier.fillMaxSize())
                    })
                }
            }
        }
    }

    val annotatedText = remember(message.id, thirdPartyEmotes.size, twitchBadges.size, nameColor, usernameSize, message.systemMsg, secondaryColor, showMessageTime, timeStr, timeColor) {
        buildAnnotatedString {
            val localizedSystemMsg = message.getLocalizedSystemMessage()
            val isUserNotice = message.type == MessageType.USER_NOTICE
            val hasUserMessage = message.message.isNotEmpty()

            if (isUserNotice && localizedSystemMsg.isNotEmpty()) {
                val noticeHeaderColor = if (message.platform == "youtube" || message.announcementColor?.uppercase() == "RED") {
                    Color(0xFF64B5F6)
                } else {
                    secondaryColor
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = noticeHeaderColor)) {
                    append("✦ $localizedSystemMsg")
                }
                if (hasUserMessage) {
                    append("\n")
                }
            }

            if (showMessageTime && timeStr.isNotEmpty()) {
                withStyle(SpanStyle(
                    color = timeColor,
                    fontSize = (fontSize * 0.85f).sp
                )) {
                    append(timeStr)
                    append(" ")
                }
            }

            // Skip name and badges for UserNotice with no message (redundant info)
            if (isUserNotice && !hasUserMessage) return@buildAnnotatedString

            if (message.platform == "youtube" || message.platform == "twitch" || message.platform == "kick") {
                appendInlineContent("platform_icon", "[${message.platform}]")
                append(' ')
            }

            // Twitch & Kick: badges BEFORE name
            if (message.platform == "twitch" || message.platform == "kick") {
                badgeUrls.forEachIndexed { i, url ->
                    appendInlineContent(url, "[badge]")
                    if (i < badgeUrls.lastIndex) append('\u2009') else append(' ')
                }
            }

            withStyle(SpanStyle(color = nameColor, fontWeight = FontWeight.SemiBold, fontSize = usernameSize.sp)) {
                val displayUsername = message.username
                    .replace(" (tmi.twitch.tv)", "")
                    .replace("(tmi.twitch.tv)", "")
                    .trim()
                append(displayUsername)
                if (message.login != null &&
                    !message.login.equals(message.username, ignoreCase = true) &&
                    !message.login.equals("tmi.twitch.tv", ignoreCase = true)
                ) {
                    append(" (${message.login})")
                }
            }

            // YouTube: badges AFTER name
            if (message.platform == "youtube") {
                badgeUrls.forEachIndexed { i, url ->
                    if (i == 0) append(' ')
                    appendInlineContent(url, "[badge]")
                    if (i < badgeUrls.lastIndex) append('\u2009')
                }
            }

            if (segments.isNotEmpty()) {
                append(": ")
                val isYouTubeNotice = isUserNotice && message.platform == "youtube"
                val noticeTextColor = if (isYouTubeNotice || message.announcementColor?.uppercase() == "RED") Color(0xFF64B5F6) else null

                segments.forEach { seg ->
                    when (seg) {
                        is MessageSegment.TextPart  -> {
                            if (noticeTextColor != null) {
                                withStyle(SpanStyle(color = noticeTextColor)) {
                                    append(seg.text)
                                }
                            } else {
                                append(seg.text)
                            }
                        }
                        is MessageSegment.EmotePart -> appendInlineContent(seg.url, "[${seg.name}]")
                        is MessageSegment.BitsPart  -> {
                            appendInlineContent(seg.url, "[bits]")
                            withStyle(SpanStyle(color = Color(0xFF9146FF), fontWeight = FontWeight.Bold)) {
                                append(seg.amount)
                            }
                        }
                        is MessageSegment.LinkPart  -> {
                            pushStringAnnotation(tag = "URL", annotation = seg.url)
                            withStyle(SpanStyle(color = Color(0xFF64B5F6), textDecoration = TextDecoration.Underline)) {
                                append(seg.text)
                            }
                            pop()
                        }
                    }
                }
            }
        }
    }

    val uriHandler = LocalUriHandler.current
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Surface(
        color = announcementBgColor ?: Color.Transparent,
        border = announcementBorderColor?.let { BorderStroke(1.dp, it) },
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = annotatedText,
            inlineContent = inlineContent,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = fontSize.sp,
                lineHeight = (fontSize + lineSpacing).sp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        layoutResult?.let { result ->
                            val position = result.getOffsetForPosition(offset)
                            annotatedText.getStringAnnotations(tag = "URL", start = position, end = position)
                                .firstOrNull()?.let { annotation ->
                                    uriHandler.openUri(annotation.item)
                                }
                        }
                    }
                },
            onTextLayout = { layoutResult = it }
        )
    }
}
