package com.kongjjj.obscontroller

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID

class TwitchChatClient {
    private val tag = "TwitchChatClient"
    private val http = OkHttpClient()
    private var socket: WebSocket? = null

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _connected = MutableStateFlow(value = false)
    val connected: StateFlow<Boolean> = _connected

    private val _roomId = MutableStateFlow("")
    val roomId: StateFlow<String> = _roomId

    private var currentChannel: String? = null
    private var lastReceivedTimestamp: Long? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun connect(channel: String) {
        val normalizedChannel = channel.lowercase().trim()
        if ((normalizedChannel == currentChannel) && _connected.value) return

        if (normalizedChannel != currentChannel) {
            _messages.value = emptyList()
            lastReceivedTimestamp = null
        }
        
        disconnect()
        currentChannel = normalizedChannel
        _roomId.value = ""

        val nick = "justinfan${(10000..99999).random()}"
        val req = Request.Builder().url("wss://irc-ws.chat.twitch.tv:443").build()

        socket = http.newWebSocket(
            req,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("CAP REQ :twitch.tv/tags twitch.tv/commands")
                    webSocket.send("NICK $nick")
                    webSocket.send("JOIN #$normalizedChannel")
                    _connected.value = true

                    // Fetch recent messages after connected
                    scope.launch {
                        val recent = fetchRecentMessages(normalizedChannel)
                        if (recent.isNotEmpty()) {
                            // Merge with any real-time messages that arrived while fetching history
                            val current = _messages.value
                            val existingIds = current.asSequence().map { it.id }.toSet()
                            val filteredRecent = recent.filter { it.id !in existingIds }
                            _messages.value = (filteredRecent + current).takeLast(MAX_CHAT_MESSAGES)
                        }
                    }
                }

            override fun onMessage(webSocket: WebSocket, text: String) {
                text.lines().forEach { handleLine(it.trim()) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connected.value = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connected.value = false
            }
        },
    )
}

    private fun handleLine(line: String) {
        if (line.isBlank()) return

        // Keep-alive
        if (line.startsWith("PING")) {
            socket?.send("PONG :tmi.twitch.tv")
            return
        }

        // Extract the channel's Twitch user ID from ROOMSTATE
        if (line.contains("ROOMSTATE")) {
            if (line.startsWith("@")) {
                val spaceIdx = line.indexOf(' ')
                if (spaceIdx > 0) {
                    line.substring(1, spaceIdx).split(";").forEach { tag ->
                        val eqIdx = tag.indexOf('=')
                        if (eqIdx >= 0 && tag.substring(0, eqIdx) == "room-id") {
                            val id = tag.substring(eqIdx + 1)
                            if (id.toLongOrNull() != null) _roomId.value = id
                        }
                    }
                }
            }
            return
        }

        if (!line.contains("PRIVMSG") && !line.contains("USERNOTICE")) return

        val msg = parseTwitchIrcLine(line)
        if (msg != null) {
            _messages.value = (_messages.value + msg).takeLast(MAX_CHAT_MESSAGES)
            msg.timestamp?.let { ts ->
                val lastTs = lastReceivedTimestamp
                if (lastTs == null || ts > lastTs) {
                    lastReceivedTimestamp = ts
                }
            }
        }
    }

    internal fun parseTwitchIrcLine(line: String): ChatMessage? {
        try {
            var rest = line
            var color: String? = null
            var displayName: String? = null
            var emotesTag: String? = null
            var badgesStr: String? = null
            var msgId: String? = null
            var serverTimestamp: Long? = null
            var msgType: MessageType = MessageType.NORMAL
            var announcementColor: String? = null
            var systemMsg: String? = null
            var bits = 0
            var isHighlighted = false
            var twitchMsgId: String? = null
            val msgParams = mutableMapOf<String, String>()

            // Strip IRCv3 tags: @key=value;key=value ... <space> rest-of-line
            if (rest.startsWith("@")) {
                val spaceIdx = rest.indexOf(' ')
                if (spaceIdx < 0) return null
                val tagsStr = rest.substring(1, spaceIdx)
                rest = rest.substring(spaceIdx + 1).trimStart()

                tagsStr.split(";").forEach { tag ->
                    val eqIdx = tag.indexOf('=')
                    if (eqIdx < 0) return@forEach
                    val key = tag.substring(0, eqIdx)
                    val value = tag.substring(eqIdx + 1)
                    if (value.isNotEmpty()) msgParams[key] = value
                    when (key) {
                        "color"           -> if (value.isNotEmpty()) color = value
                        "display-name"    -> if (value.isNotEmpty()) displayName = value
                        "emotes"          -> if (value.isNotEmpty()) emotesTag = value
                        "badges"          -> if (value.isNotEmpty()) badgesStr = value
                        "id"              -> if (value.isNotEmpty()) msgId = value
                        "tmi-sent-ts"     -> serverTimestamp = value.toLongOrNull()
                        "bits"            -> bits = value.toIntOrNull() ?: 0
                        "msg-id"          -> {
                            twitchMsgId = value
                            if (value == "announcement") msgType = MessageType.ANNOUNCEMENT
                            else if (value == "highlighted-message") isHighlighted = true
                            else if (line.contains("USERNOTICE")) msgType = MessageType.USER_NOTICE
                        }
                        "msg-param-color" -> if (value.isNotEmpty()) announcementColor = value
                        "system-msg"      -> if (value.isNotEmpty()) {
                            systemMsg = value
                                .replace("\\s", " ")
                                .replace("\\:", ":")
                                .replace("\\r", "\r")
                                .replace("\\n", "\n")
                                .replace("\\\\", "\\")
                        }
                    }
                }
            }

            val isUserNotice = line.contains("USERNOTICE")
            if (isUserNotice && msgType == MessageType.NORMAL) {
                msgType = MessageType.USER_NOTICE
            }

            // bits > 0 handling removed as it was empty

            // Robust IRC Parsing
            var prefix: String? = null
            if (rest.startsWith(":")) {
                val spaceIdx = rest.indexOf(' ')
                if (spaceIdx > 0) {
                    prefix = rest.substring(1, spaceIdx)
                    rest = rest.substring(spaceIdx + 1).trimStart()
                }
            }

            val login = prefix?.substringBefore("!")?.takeIf { it != "tmi.twitch.tv" }
            val rawName = displayName ?: login ?: (if (line.contains("USERNOTICE")) "Twitch" else null) ?: return null
            val username = rawName
                .replace(" (tmi.twitch.tv)", "")
                .replace("(tmi.twitch.tv)", "")
                .trim()

            // Extract Command and Parameters
            val firstSpace = rest.indexOf(' ')
            if (firstSpace < 0) return null
            val command = rest.substring(0, firstSpace)
            if (command != "PRIVMSG" && command != "USERNOTICE") return null

            val paramsPart = rest.substring(firstSpace + 1).trimStart()
            // paramsPart looks like: #channel :message text OR #channel message

            // Extract Message (the trailing parameter)
            var message = when {
                paramsPart.contains(" :") -> {
                    paramsPart.substring(paramsPart.indexOf(" :") + 2)
                }
                paramsPart.startsWith(":") -> {
                    paramsPart.substring(1)
                }
                paramsPart.indexOf(' ') >= 0 -> {
                    paramsPart.substring(paramsPart.indexOf(' ') + 1)
                }
                else -> "" // User message is optional in USERNOTICE
            }

            // Handle Twitch ACTION (/me)
            if (message.startsWith("\u0001ACTION ") && message.endsWith("\u0001")) {
                message = message.substring(8, message.length - 1)
            }

            if (message.isEmpty() && systemMsg.isNullOrEmpty()) return null

            val badges = badgesStr?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()

            return ChatMessage(
                id                = msgId ?: UUID.randomUUID().toString(),
                username          = username,
                login             = login,
                message           = message,
                color             = color,
                emotesTag         = emotesTag,
                badgeTags         = badges,
                timestamp         = serverTimestamp,
                platform          = "twitch",
                type              = msgType,
                announcementColor = announcementColor,
                systemMsg         = systemMsg,
                bits              = bits,
                isHighlighted     = isHighlighted,
                twitchMsgId       = twitchMsgId,
                msgParams         = msgParams
            )
        } catch (_: Exception) {
            return null
        }
    }

    private suspend fun fetchRecentMessages(channel: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        var url = "https://recent-messages.robotty.de/api/v2/recent-messages/$channel?limit=200&data=json"
        lastReceivedTimestamp?.let { ts ->
            url += "&after=$ts"
        }
        val request = Request.Builder().url(url).build()
        try {
            val response = http.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(tag, "Recent messages API 請求失敗: ${response.code}")
                return@withContext emptyList()
            }
            val jsonStr = response.body?.string() ?: ""
            if (jsonStr.isBlank()) return@withContext emptyList()

            val json = JSONObject(jsonStr)
            val messagesArray = json.optJSONArray("messages") ?: return@withContext emptyList()

            val recentList = mutableListOf<ChatMessage>()
            var maxTimestamp: Long? = lastReceivedTimestamp
            for (i in 0 until messagesArray.length()) {
                val rawMessage = messagesArray.optString(i)
                if (rawMessage.isNullOrBlank()) continue

                val msg = parseTwitchIrcLine(rawMessage)
                if (msg != null) {
                    recentList.add(msg)
                    msg.timestamp?.let { ts ->
                        if (maxTimestamp == null || ts > maxTimestamp) {
                            maxTimestamp = ts
                        }
                    }
                }
            }
            if (maxTimestamp != null && maxTimestamp != lastReceivedTimestamp) {
                lastReceivedTimestamp = maxTimestamp
            }
            recentList
        } catch (e: Exception) {
            Log.e(tag, "獲取最近訊息失敗", e)
            emptyList()
        }
    }

    fun clearMessages() {
        _messages.value = emptyList()
        lastReceivedTimestamp = null
        currentChannel = null
    }

    fun disconnect() {
        socket?.close(1000, null)
        socket = null
        _connected.value = false
        _roomId.value = ""
    }
}

