package com.kongjjj.obscontroller

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class YouTubeChatClient {
    private val tag = "YouTubeChatClient"
    private val http = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null
    private var viewerCountJob: Job? = null

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _connected = MutableStateFlow(value = false)
    val connected: StateFlow<Boolean> = _connected

    private val _viewerCount = MutableStateFlow<Int?>(null)
    val viewerCount: StateFlow<Int?> = _viewerCount

    private var currentChannelId: String? = null
    private var currentVideoId: String? = null
    private var apiKey: String? = null
    private var continuation: String? = null

    fun connect(channelId: String) {
        if ((channelId == currentChannelId) && _connected.value) return
        
        disconnect()
        currentChannelId = channelId
        _messages.value = emptyList()

        startViewerCountPolling(channelId)

        job = scope.launch {
            try {
                // If it's a channel ID (starts with UC), resolve it to a live video ID
                val videoId = if (channelId.startsWith("UC")) {
                    resolveLiveVideoId(channelId)
                } else {
                    channelId // Fallback for direct video ID
                }

                if (videoId != null && fetchInitialPage(videoId)) {
                    currentVideoId = videoId
                    _connected.value = true
                    pollChat()
                } else {
                    Log.e(tag, "Failed to resolve or fetch initial page for: $channelId")
                    _connected.value = false
                }
            } catch (e: Exception) {
                Log.e(tag, "Error connecting to YouTube chat", e)
                _connected.value = false
            }
        }
    }

    private fun startViewerCountPolling(channelId: String) {
        viewerCountJob?.cancel()
        viewerCountJob = scope.launch {
            while (isActive) {
                fetchLiveViewersScraping(channelId)
                delay(60.seconds)
            }
        }
    }

    private fun resolveLiveVideoId(channelId: String): String? {
        val url = "https://www.youtube.com/channel/$channelId/live"
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .build()

        return try {
            val response = http.newCall(request).execute()
            // YouTube redirects /live to the watch page. We want the final URL.
            val finalUrl = response.request.url.toString()
            
            // Extract v=VIDEO_ID from URL
            var videoId = Regex("[?&]v=([^&]+)").find(finalUrl)?.groupValues?.get(1)
            
            if (videoId == null) {
                // Try extracting from HTML if redirect didn't happen as expected
                val html = response.body?.string() ?: ""
                videoId = Regex("\"videoId\":\"([^\"]{11})\"").find(html)?.groupValues?.get(1)
            }
            
            videoId
        } catch (e: Exception) {
            Log.e(tag, "Error resolving live video ID", e)
            null
        }
    }

    fun disconnect() {
        job?.cancel()
        job = null
        viewerCountJob?.cancel()
        viewerCountJob = null
        _connected.value = false
        _viewerCount.value = null
        currentChannelId = null
        currentVideoId = null
        apiKey = null
        continuation = null
    }

    private fun fetchInitialPage(videoId: String): Boolean {
        val url = "https://www.youtube.com/live_chat?v=$videoId"
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .build()

        return try {
            val response = http.newCall(request).execute()
            if (!response.isSuccessful) return false
            val html = response.body?.string() ?: ""

            apiKey = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
            continuation = Regex("\"continuation\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)

            // Extract real-time concurrent viewers from ytInitialPlayerResponse
            val playerResponseMatch = Regex("var ytInitialPlayerResponse = (\\{.*?\\});").find(html)
            if (playerResponseMatch != null) {
                try {
                    val playerResponse = JSONObject(playerResponseMatch.groupValues[1])
                    val videoDetails = playerResponse.optJSONObject("videoDetails")
                    if (videoDetails?.optBoolean("isLive") == true) {
                        val concurrent = videoDetails.optString("concurrentViewers")
                        if (concurrent.isNotEmpty()) {
                            _viewerCount.value = concurrent.toIntOrNull()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error parsing ytInitialPlayerResponse", e)
                }
            }

            // Extract initial messages from ytInitialData
            val initialDataMatch = Regex("window\\[\"ytInitialData\"] = (\\{.*?\\});").find(html)
            if (initialDataMatch != null) {
                val initialDataStr = initialDataMatch.groupValues[1]
                try {
                    val initialData = JSONObject(initialDataStr)
                    val contents = initialData.optJSONObject("contents")
                    val liveChatRenderer = contents?.optJSONObject("liveChatRenderer")
                    val actions = liveChatRenderer?.optJSONArray("actions")
                    if (actions != null) {
                        val initialMessages = mutableListOf<ChatMessage>()
                        for (i in 0 until actions.length()) {
                            val action = actions.getJSONObject(i)
                            parseChatItemAction(action)?.let { initialMessages.add(it) }
                        }
                        if (initialMessages.isNotEmpty()) {
                            _messages.value = initialMessages.takeLast(MAX_CHAT_MESSAGES)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error parsing ytInitialData", e)
                }
            }

            apiKey != null && continuation != null
        } catch (e: Exception) {
            Log.e(tag, "Error fetching initial page", e)
            false
        }
    }

    private suspend fun pollChat() {
        while (job?.isActive == true && apiKey != null && continuation != null) {
            try {

                val url = "https://www.youtube.com/youtubei/v1/live_chat/get_live_chat?key=$apiKey"
                val json = JSONObject().apply {
                    put("context", JSONObject().apply {
                        put(
                            "client",
                            JSONObject().apply {
                                put("clientName", "WEB")
                                put("clientVersion", "2.20210622.10.00")
                            },
                        )
                    })
                    put("continuation", continuation)
                }

                val request = Request.Builder()
                    .url(url)
                    .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .build()

                val response = http.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val jsonObj = JSONObject(body)
                    
                    val continuationData = jsonObj.optJSONObject("continuationContents")?.optJSONObject("liveChatContinuation")
                    
                    // Extract exact concurrent viewer count from chat packet (handle both simpleText and runs)
                    val viewerCountObj = continuationData?.optJSONObject("viewerCount")
                        ?.optJSONObject("liveChatViewerCountRenderer")
                        ?.optJSONObject("viewerCount")
                    
                    val viewerCountText = viewerCountObj?.optString("simpleText")
                        ?: viewerCountObj?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")

                    viewerCountText?.let { text ->
                        // text is like "71" or "71 watching"
                        val count = text.replace(Regex("[^0-9]"), "").toIntOrNull()
                        if (count != null) _viewerCount.value = count
                    }

                    continuation = continuationData?.optJSONArray("continuations")?.optJSONObject(0)
                        ?.optJSONObject("invalidationContinuationData")?.optString("continuation")
                        ?: continuationData?.optJSONArray("continuations")?.optJSONObject(0)
                        ?.optJSONObject("timedContinuationData")?.optString("continuation")

                    val actions = continuationData?.optJSONArray("actions")
                    if (actions != null) {
                        val newMessages = mutableListOf<ChatMessage>()
                        for (i in 0 until actions.length()) {
                            val action = actions.getJSONObject(i)
                            parseChatItemAction(action)?.let { newMessages.add(it) }
                        }
                        if (newMessages.isNotEmpty()) {
                            _messages.value = (_messages.value + newMessages).takeLast(MAX_CHAT_MESSAGES)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error polling chat", e)
            }
            delay(5.seconds) // Poll every 5 seconds to avoid rate limiting
        }
    }

    internal fun parseChatItemAction(action: JSONObject): ChatMessage? {
        val item = action.optJSONObject("addChatItemAction")?.optJSONObject("item") ?: return null

        val textItem = item.optJSONObject("liveChatTextMessageRenderer")
        val paidItem = item.optJSONObject("liveChatPaidMessageRenderer")
        val stickerItem = item.optJSONObject("liveChatPaidStickerRenderer")

        val rendererItem = textItem ?: paidItem ?: stickerItem ?: return null

        val isSuperChat = (paidItem != null || stickerItem != null)
        val purchaseAmount = rendererItem.optJSONObject("purchaseAmountText")?.optString("simpleText") ?: ""

        val authorName = rendererItem.optJSONObject("authorName")?.optString("simpleText") ?: "Unknown"

        // Parse badges
        val badgeTags = mutableListOf<String>()
        val authorBadges = rendererItem.optJSONArray("authorBadges")
        if (authorBadges != null) {
            for (j in 0 until authorBadges.length()) {
                val badge = authorBadges.getJSONObject(j).optJSONObject("liveChatAuthorBadgeRenderer")
                if (badge != null) {
                    val customThumbnail = badge.optJSONObject("customThumbnail")
                    if (customThumbnail != null) {
                        val thumbnails = customThumbnail.optJSONArray("thumbnails")
                        val badgeUrl = thumbnails?.optJSONObject(thumbnails.length() - 1)?.optString("url")
                            ?: thumbnails?.optJSONObject(0)?.optString("url")
                        if (badgeUrl != null) {
                            val absoluteUrl = if (badgeUrl.startsWith("//")) "https:$badgeUrl" else badgeUrl
                            badgeTags.add(absoluteUrl)
                        }
                    } else {
                        val icon = badge.optJSONObject("icon")
                        val iconType = icon?.optString("iconType")
                        if (iconType != null) {
                            badgeTags.add("yt-$iconType")
                        }
                    }
                }
            }
        }

        val messageParts = rendererItem.optJSONObject("message")?.optJSONArray("runs")
        val messageText = StringBuilder()
        val youtubeEmotes = mutableMapOf<String, String>()

        if (stickerItem != null) {
            messageText.append("[超級貼圖]")
        } else if (messageParts != null) {
            for (j in 0 until messageParts.length()) {
                val run = messageParts.getJSONObject(j)
                if (run.has("text")) {
                    messageText.append(run.optString("text"))
                } else if (run.has("emoji")) {
                    val emoji = run.optJSONObject("emoji")
                    val shortcut = emoji?.optJSONArray("shortcuts")?.optString(0) ?: ":emoji:"
                    messageText.append(shortcut)

                    val url = emoji?.optJSONObject("image")?.optJSONArray("thumbnails")?.optJSONObject(0)?.optString("url")
                    if (url != null) {
                        youtubeEmotes[shortcut] = url
                    }
                }
            }
        }

        val timestampUsec = rendererItem.optString("timestampUsec").toLongOrNull() ?: 0L

        val systemMsg = if (isSuperChat) {
            val label = if (stickerItem != null) "SuperSticker" else "SuperChat"
            if (purchaseAmount.isNotEmpty()) "【$label $purchaseAmount】" else "【$label】"
        } else null

        return ChatMessage(
            id = UUID.randomUUID().toString(),
            username = authorName,
            message = messageText.toString(),
            badgeTags = badgeTags,
            youtubeEmotes = youtubeEmotes,
            timestamp = timestampUsec / 1000,
            platform = "youtube",
            type = if (isSuperChat) MessageType.USER_NOTICE else MessageType.NORMAL,
            announcementColor = if (isSuperChat) "RED" else null,
            systemMsg = systemMsg
        )
    }

    internal fun extractViewerCountFromHtml(htmlText: String): Int? {
        if (htmlText.isEmpty()) return null

        // 1. Direct Regex for "concurrentViewers":"1234" or "concurrentViewers":"1,234"
        val match1 = Regex(""""concurrentViewers"\s*:\s*"([\d,]+)"""").find(htmlText)
        match1?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()?.let {
            if (it > 0) return it
        }

        // 2. Regex for "viewCount":{"runs":[{"text":"1,234"}
        val match2 = Regex(""""viewCount"\s*:\s*\{\s*"runs"\s*:\s*\[\s*\{\s*"text"\s*:\s*"([\d,]+)"""").find(htmlText)
        match2?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()?.let {
            if (it > 0) return it
        }

        // 3. Regex for "simpleText":"1,234 watching" or "1,234 位正在觀看"
        val match3 = Regex(""""(?:simpleText|text)"\s*:\s*"([\d,]+)\s*(?:位正在觀看|人正在觀看|watching|viewers)"""").find(htmlText)
        match3?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()?.let {
            if (it > 0) return it
        }

        // 4. Parse ytInitialPlayerResponse JSON
        val playerResponseMatch = Regex("""var\s+ytInitialPlayerResponse\s*=\s*(\{.*?\});""").find(htmlText)
            ?: Regex("""window\["ytInitialPlayerResponse"]\s*=\s*(\{.*?\});""").find(htmlText)
            ?: Regex("""ytInitialPlayerResponse\s*=\s*(\{.*?\});""").find(htmlText)
        if (playerResponseMatch != null) {
            try {
                val json = JSONObject(playerResponseMatch.groupValues[1])
                val videoDetails = json.optJSONObject("videoDetails")
                val concurrent = videoDetails?.optString("concurrentViewers") ?: ""
                val num = concurrent.replace(",", "").toIntOrNull()
                if (num != null && num > 0) return num
            } catch (e: Exception) {
                Log.e(tag, "Error parsing ytInitialPlayerResponse JSON", e)
            }
        }

        // 5. Parse ytInitialData JSON
        val initialDataMatch = Regex("""window\["ytInitialData"]\s*=\s*(\{.*?\});""").find(htmlText)
            ?: Regex("""var\s+ytInitialData\s*=\s*(\{.*?\});""").find(htmlText)
        if (initialDataMatch != null) {
            try {
                val jsonStr = initialDataMatch.groupValues[1]
                val matchInJson = Regex(""""concurrentViewers"\s*:\s*"([\d,]+)"""").find(jsonStr)
                val num = matchInJson?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
                if (num != null && num > 0) return num
            } catch (e: Exception) {
                Log.e(tag, "Error parsing ytInitialData JSON", e)
            }
        }

        return null
    }

    private suspend fun fetchLiveViewersScraping(channelId: String) = withContext(Dispatchers.IO) {
        val targetChannelId = channelId.trim()
        if (targetChannelId.isEmpty()) {
            _viewerCount.value = null
            return@withContext
        }

        val urlsToTry = mutableListOf<String>()
        if (targetChannelId.startsWith("UC")) {
            urlsToTry.add("https://www.youtube.com/channel/$targetChannelId/live")
        } else if (targetChannelId.startsWith("@")) {
            urlsToTry.add("https://www.youtube.com/$targetChannelId/live")
        } else {
            urlsToTry.add("https://www.youtube.com/channel/$targetChannelId/live")
        }

        currentVideoId?.let { videoId ->
            if (videoId.isNotEmpty()) {
                urlsToTry.add("https://www.youtube.com/watch?v=$videoId")
            }
        }

        var foundCount: Int? = null

        for (url in urlsToTry) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .addHeader(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    )
                    .addHeader("Accept-Language", "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7")
                    .addHeader("Cookie", "SOCS=CAI; CONSENT=YES+; PREF=f6=40000&hl=zh-TW")
                    .build()

                http.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val htmlText = response.body?.string() ?: ""
                        foundCount = extractViewerCountFromHtml(htmlText)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error scraping live viewers from $url", e)
            }

            if (foundCount != null) break
        }

        val count = foundCount
        if (count != null && count <= 0) {
            foundCount = null
        }
        _viewerCount.value = foundCount
    }
}
