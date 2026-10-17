package com.kongjjj.obscontroller

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class OBSViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ProfileStore(app).also { it.migrateFromLegacy(app) }
    private val client = OBSWebSocketClient()
    private val ttsManager = TtsManager(app)

    // ── OBS connection state ──────────────────────────────────────────────────

    val state = client.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionState.Disconnected)

    val scenes = client.scenes
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentScene = client.currentScene
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val inputs = client.inputs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val streamActive = client.streamActive
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val recordActive = client.recordActive
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val studioModeEnabled = client.studioModeEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val previewScene = client.previewScene
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val programScreenshot = client.programScreenshot
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val previewScreenshot = client.previewScreenshot
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val sceneItems = client.sceneItems
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val filters = client.filters
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val inputSettings = client.inputSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val groupItems = client.groupItems
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val sceneCollections = client.sceneCollections
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentSceneCollection = client.currentSceneCollection
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val volumeMeters = client.volumeMeters
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // Recording timer (seconds elapsed since record start)
    private val _recordingTimeSec = MutableStateFlow(0L)
    val recordingTimeSec: StateFlow<Long> = _recordingTimeSec
    private var recordTimerJob: Job? = null

    private var sceneItemsScene: String = ""

    // ── Profile management ────────────────────────────────────────────────────

    private val _profiles = MutableStateFlow(store.getProfiles())
    val profiles: StateFlow<List<OBSProfile>> = _profiles

    val lastUsedProfileId: String? get() = store.getLastUsedId()

    private val _autoConnect = MutableStateFlow(store.getAutoConnect())
    val autoConnect: StateFlow<Boolean> = _autoConnect

    // ── Screenshot polling ────────────────────────────────────────────────────

    private var screenshotJob: Job? = null
    private var screenshotsPaused = false

    // ── Auto-reconnect ────────────────────────────────────────────────────────

    private var lastProfile: OBSProfile? = null
    private var manualDisconnect = false
    private var reconnectJob: Job? = null

    private val _reconnecting = MutableStateFlow(false)
    val reconnecting: StateFlow<Boolean> = _reconnecting

    // ── Twitch chat ───────────────────────────────────────────────────────────

    private val _twitchChannel = MutableStateFlow(store.getTwitchChannel())
    val twitchChannel: StateFlow<String> = _twitchChannel

    private val _youtubeChannelId = MutableStateFlow(store.getYoutubeChannelId())
    val youtubeChannelId: StateFlow<String> = _youtubeChannelId

    private val _kickChannel = MutableStateFlow(store.getKickChannel())
    val kickChannel: StateFlow<String> = _kickChannel

    private val twitchChatClient = TwitchChatClient()
    private val youtubeChatClient = YouTubeChatClient()
    val kickChatClient = KickChatClient()
    val emoteRepository = EmoteRepository()

    val chatMessages = combine(
        twitchChatClient.messages,
        youtubeChatClient.messages,
        kickChatClient.messages
    ) { twitch, youtube, kick ->
        (twitch + youtube + kick).sortedBy { it.timestamp ?: 0L }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val chatConnected = combine(
        twitchChatClient.connected,
        youtubeChatClient.connected,
        kickChatClient.connected
    ) { t, y, k -> t || y || k }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    @Suppress("unused")
    val twitchConnected = twitchChatClient.connected
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    @Suppress("unused")
    val youtubeConnected = youtubeChatClient.connected
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    @Suppress("unused")
    val kickConnected = kickChatClient.connected
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val thirdPartyEmotes = emoteRepository.thirdPartyEmotes
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val twitchBadges = emoteRepository.twitchBadges
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val emoteLoadReport = emoteRepository.loadReport
        .stateIn(viewModelScope, SharingStarted.Eagerly, "loading…")

    private val _enable7tv  = MutableStateFlow(store.getEnable7tv())
    val enable7tv: StateFlow<Boolean> = _enable7tv

    private val _enableBttv = MutableStateFlow(store.getEnableBttv())
    val enableBttv: StateFlow<Boolean> = _enableBttv

    private val _enableFfz  = MutableStateFlow(store.getEnableFfz())
    val enableFfz: StateFlow<Boolean> = _enableFfz

    // ── Chat display settings ─────────────────────────────────────────────────

    private val _chatFontSize = MutableStateFlow(store.getChatFontSize())
    val chatFontSize: StateFlow<Float> = _chatFontSize

    private val _chatLineSpacing = MutableStateFlow(store.getChatLineSpacing())
    val chatLineSpacing: StateFlow<Float> = _chatLineSpacing

    private val _chatEmoteSize = MutableStateFlow(store.getChatEmoteSize())
    val chatEmoteSize: StateFlow<Float> = _chatEmoteSize

    private val _chatUsernameSize = MutableStateFlow(store.getChatUsernameSize())
    val chatUsernameSize: StateFlow<Float> = _chatUsernameSize

    private val _animatedEmotes = MutableStateFlow(store.getAnimatedEmotes())
    val animatedEmotes: StateFlow<Boolean> = _animatedEmotes

    private val _showMessageTime = MutableStateFlow(store.getShowMessageTime())
    val showMessageTime: StateFlow<Boolean> = _showMessageTime

    private val _showExpandButton = MutableStateFlow(store.getShowExpandButton())
    val showExpandButton: StateFlow<Boolean> = _showExpandButton

    private val _showFullScreenButton = MutableStateFlow(store.getShowFullScreenButton())
    val showFullScreenButton: StateFlow<Boolean> = _showFullScreenButton

    private val _showScreenLockButton = MutableStateFlow(store.getShowScreenLockButton())
    val showScreenLockButton: StateFlow<Boolean> = _showScreenLockButton

    private val _fullScreenActive = MutableStateFlow(store.getFullScreenActive())
    val fullScreenActive: StateFlow<Boolean> = _fullScreenActive

    private val _isChatLocked = MutableStateFlow(false)
    val isChatLocked: StateFlow<Boolean> = _isChatLocked

    private val _showDebugBar = MutableStateFlow(store.getShowDebugBar())
    val showDebugBar: StateFlow<Boolean> = _showDebugBar

    private val _showEmoteDebug = MutableStateFlow(store.getShowEmoteDebug())
    val showEmoteDebug: StateFlow<Boolean> = _showEmoteDebug

    private val _twitchViewerCount = MutableStateFlow<Int?>(null)
    val twitchViewerCount: StateFlow<Int?> = _twitchViewerCount

    val youtubeViewerCount = youtubeChatClient.viewerCount
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _kickViewerCount = MutableStateFlow<Int?>(null)
    val kickViewerCount: StateFlow<Int?> = _kickViewerCount
    private var kickStatusJob: Job? = null

    private fun startKickStatusPolling(channel: String) {
        kickStatusJob?.cancel()
        if (channel.isBlank()) {
            _kickViewerCount.value = null
            return
        }
        kickStatusJob = viewModelScope.launch {
            while (true) {
                try {
                    val info = kickChatClient.resolveChannelInfo(channel)
                    if (info != null && info.viewersCount > 0) {
                        _kickViewerCount.value = info.viewersCount
                    } else {
                        _kickViewerCount.value = null
                    }
                } catch (_: Exception) {
                    _kickViewerCount.value = null
                }
                delay(15.seconds)
            }
        }
    }

    @Suppress("unused")
    private val _streamStartTime = MutableStateFlow<Long?>(null)
    @Suppress("unused")
    val streamStartTime: StateFlow<Long?> = _streamStartTime

    private val _streamUptime = MutableStateFlow("00:00:00")
    val streamUptime: StateFlow<String> = _streamUptime

    private val _streamCategory = MutableStateFlow<String?>(null)
    val streamCategory: StateFlow<String?> = _streamCategory

    private val http = okhttp3.OkHttpClient()
    private var twitchStatusJob: Job? = null
    private var uptimeJob: Job? = null

    private fun startTwitchStatusPolling(channel: String) {
        twitchStatusJob?.cancel()
        twitchStatusJob = viewModelScope.launch {
            while (true) {
                fetchTwitchStreamStatus(channel)
                delay(30.seconds) // Poll every 30s
            }
        }
    }

    private suspend fun fetchTwitchStreamStatus(channel: String) = withContext(Dispatchers.IO) {
        val query = "query { user(login: \"$channel\") { stream { viewersCount createdAt game { displayName } } } }"
        val json = org.json.JSONObject().apply { put("query", query) }
        val request = okhttp3.Request.Builder()
            .url("https://gql.twitch.tv/gql")
            .header("Client-ID", "kimne78kx3ncx6brgo4mv6wki5h1ko")
            .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()
        try {
            val response = http.newCall(request).execute()
            if (response.isSuccessful) {
                val respBody = response.body?.string() ?: ""
                val respJson = org.json.JSONObject(respBody)
                val stream = respJson.optJSONObject("data")?.optJSONObject("user")?.optJSONObject("stream")
                if (stream != null) {
                    _twitchViewerCount.value = stream.optInt("viewersCount")
                    _streamCategory.value = stream.optJSONObject("game")?.optString("displayName")
                    val createdAt = stream.optString("createdAt")
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    _streamStartTime.value = sdf.parse(createdAt)?.time
                } else {
                    _twitchViewerCount.value = null
                    _streamStartTime.value = null
                    _streamCategory.value = null
                }
            }
        } catch (_: Exception) {}
    }

    private fun startUptimeTimer() {
        uptimeJob?.cancel()
        uptimeJob = viewModelScope.launch {
            while (true) {
                val start = _streamStartTime.value
                if (start != null) {
                    val diff = (System.currentTimeMillis() - start) / 1000
                    if (diff >= 0) {
                        val h = diff / 3600
                        val m = (diff % 3600) / 60
                        val s = diff % 60
                        _streamUptime.value = String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
                    } else { _streamUptime.value = "00:00:00" }
                } else { _streamUptime.value = "00:00:00" }
                delay(1.seconds)
            }
        }
    }

    private val _showMiniMixer = MutableStateFlow(store.getShowMiniMixer())
    val showMiniMixer: StateFlow<Boolean> = _showMiniMixer

    private val _filterMiniMixerByScene = MutableStateFlow(store.getFilterMiniMixerByScene())
    val filterMiniMixerByScene: StateFlow<Boolean> = _filterMiniMixerByScene

    private val _filterMainMixerByScene = MutableStateFlow(store.getFilterMainMixerByScene())
    val filterMainMixerByScene: StateFlow<Boolean> = _filterMainMixerByScene

    val audioMixerInputs = combine(
        inputs,
        client.sceneItems,
        currentScene,
        previewScene,
        studioModeEnabled,
        _filterMainMixerByScene
    ) { all ->
        @Suppress("UNCHECKED_CAST")
        val allInputs = all[0] as List<OBSInput>
        @Suppress("UNCHECKED_CAST")
        val allSceneItems = all[1] as Map<String, List<OBSSceneItem>>
        val current = all[2] as String
        val preview = all[3] as String
        val studio = all[4] as Boolean
        val filter = all[5] as Boolean

        if (!filter) return@combine allInputs.filter { it.isAudio && it.active }

        val activeScenes = if (studio) setOf(current, preview) else setOf(current)
        val activeSourceNames = activeScenes.flatMap { sceneName ->
            allSceneItems[sceneName]?.map { it.sourceName } ?: emptyList()
        }.toSet()

        allInputs.filter {
            it.isAudio && it.active && (it.name in activeSourceNames ||
                    it.kind == "wasapi_input_capture" || it.kind == "wasapi_output_capture" ||
                    it.kind == "pulse_input_capture" || it.kind == "pulse_output_capture" ||
                    it.kind == "alsa_input_capture" || it.kind == "coreaudio_input_capture")
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _showCollectionChip = MutableStateFlow(store.getShowCollectionChip())
    val showCollectionChip: StateFlow<Boolean> = _showCollectionChip

    private val _ttsEnabled = MutableStateFlow(store.getTtsEnabled())
    val ttsEnabled: StateFlow<Boolean> = _ttsEnabled

    private val _ttsSubBitsOnly = MutableStateFlow(store.getTtsSubBitsOnly())
    val ttsSubBitsOnly: StateFlow<Boolean> = _ttsSubBitsOnly

    private val _ttsIgnoreSender = MutableStateFlow(store.getTtsIgnoreSender())
    val ttsIgnoreSender: StateFlow<Boolean> = _ttsIgnoreSender

    private val _ttsIgnoreLinks = MutableStateFlow(store.getTtsIgnoreLinks())
    val ttsIgnoreLinks: StateFlow<Boolean> = _ttsIgnoreLinks

    private val _ttsIgnoreEmotes = MutableStateFlow(store.getTtsIgnoreEmotes())
    val ttsIgnoreEmotes: StateFlow<Boolean> = _ttsIgnoreEmotes

    private val _ttsLanguage = MutableStateFlow(store.getTtsLanguage())
    val ttsLanguage: StateFlow<String> = _ttsLanguage

    private var filterSourceName: String = ""

    private var lastTtsMessageId: String? = null

    init {
        updateTtsLocale(_ttsLanguage.value)
        startUptimeTimer()
        if (_twitchChannel.value.isNotEmpty()) {
            startTwitchStatusPolling(_twitchChannel.value)
        }

        // YouTube chat init
        if (_youtubeChannelId.value.isNotEmpty()) {
            youtubeChatClient.connect(_youtubeChannelId.value)
        }

        // Kick chat init
        if (_kickChannel.value.isNotEmpty()) {
            kickChatClient.connect(_kickChannel.value)
            startKickStatusPolling(_kickChannel.value)
        }

        // Auto-connect on startup if enabled and a last-used profile exists
        if (store.getAutoConnect()) {
            val lastId = store.getLastUsedId()
            val profile = store.getProfiles().find { it.id == lastId }
            if (profile != null) connect(profile)
        }

        viewModelScope.launch {
            state.collect { s ->
                when (s) {
                    is ConnectionState.Connected -> {
                        reconnectJob?.cancel()
                        _reconnecting.value = false
                        startScreenshotPolling()
                    }
                    is ConnectionState.Disconnected, is ConnectionState.Error -> {
                        stopScreenshotPolling()
                        stopRecordTimer()
                        if (!manualDisconnect && lastProfile != null) {
                            scheduleReconnect()
                        }
                    }
                    else -> {}
                }
            }
        }

        viewModelScope.launch {
            recordActive.collect { active ->
                if (active) startRecordTimer() else stopRecordTimer()
            }
        }

        // Load global emotes at startup
        viewModelScope.launch {
            emoteRepository.loadAll(_enable7tv.value, _enableBttv.value, _enableFfz.value)
        }
        // Load channel emotes whenever the IRC room-id is received
        viewModelScope.launch {
            twitchChatClient.roomId.collect { roomId ->
                if (roomId.isNotEmpty()) {
                    emoteRepository.loadChannelEmotes(
                        roomId, _twitchChannel.value,
                        _enable7tv.value, _enableBttv.value, _enableFfz.value
                    )
                }
            }
        }

        // TTS processing for incoming messages
        viewModelScope.launch {
            chatMessages.collect { allMessages ->
                if (!_ttsEnabled.value || allMessages.isEmpty()) return@collect
                val lastMsg = allMessages.last()
                
                if (lastMsg.id == lastTtsMessageId) return@collect
                lastTtsMessageId = lastMsg.id
                
                // Avoid speaking history when first connecting or loading history
                // We only speak if the message is "new" (timestamp is very recent)
                // Relaxed to 10 seconds for YouTube timestamps which might be slightly delayed
                val now = System.currentTimeMillis()
                val msgTs = lastMsg.timestamp ?: now
                if (now - msgTs > 10_000) return@collect // Ignore if older than 10 seconds

                if (_ttsSubBitsOnly.value) {
                    val isTwitch = lastMsg.platform == "twitch"
                    val isBits = isTwitch && lastMsg.bits > 0
                    val isSub = isTwitch && (
                        lastMsg.twitchMsgId in SUB_TWITCH_MSG_IDS ||
                        (lastMsg.type == MessageType.USER_NOTICE && lastMsg.twitchMsgId?.contains("sub", ignoreCase = true) == true)
                    )
                    val isYouTubeSuper = lastMsg.platform == "youtube" && lastMsg.type == MessageType.USER_NOTICE
                    if (!isBits && !isSub && !isYouTubeSuper) return@collect
                }

                var speakText = lastMsg.message
                
                // 1. Ignore Emotes (Native & 3rd party)
                if (_ttsIgnoreEmotes.value) {
                    // Get segments to identify which parts are text
                    val segments = parseMessageSegments(lastMsg.message, lastMsg.emotesTag, thirdPartyEmotes.value)
                    speakText = segments.filterIsInstance<MessageSegment.TextPart>()
                        .joinToString(" ") { it.text }
                }

                // 2. Ignore Links
                if (_ttsIgnoreLinks.value) {
                    speakText = speakText.replace(Regex("https?://\\S+|www\\.\\S+", RegexOption.IGNORE_CASE), "")
                }

                if (speakText.isBlank()) return@collect

                val finalSpeech = if (_ttsIgnoreSender.value) {
                    speakText
                } else {
                    "${lastMsg.username} 說: $speakText"
                }

                ttsManager.speak(finalSpeech)
            }
        }
    }

    // ── Functions ─────────────────────────────────────────────────────────────

    fun loadSceneItems(sceneName: String) {
        sceneItemsScene = sceneName
        client.fetchSceneItems(sceneName)
    }

    fun toggleSceneItemVisibility(sceneItemId: Int, currentEnabled: Boolean) {
        if (sceneItemsScene.isNotEmpty())
            client.setSceneItemEnabled(sceneItemsScene, sceneItemId, !currentEnabled)
    }

    fun deleteSceneItem(sceneItemId: Int) {
        if (sceneItemsScene.isNotEmpty())
            client.removeSceneItem(sceneItemsScene, sceneItemId)
    }

    fun reorderSceneItem(sceneItemId: Int, newUiIndex: Int) {
        if (sceneItemsScene.isEmpty()) return
        val size = client.sceneItems.value.size
        val obsIndex = (size - 1 - newUiIndex).coerceIn(0, size - 1)
        client.setSceneItemIndex(sceneItemsScene, sceneItemId, obsIndex)
    }

    fun setAutoConnect(enabled: Boolean) {
        store.setAutoConnect(enabled)
        _autoConnect.value = enabled
    }

    fun saveProfile(profile: OBSProfile) {
        store.saveProfile(profile)
        _profiles.value = store.getProfiles()
    }

    fun deleteProfile(id: String) {
        store.deleteProfile(id)
        _profiles.value = store.getProfiles()
    }

    fun setScreenshotsPaused(paused: Boolean) {
        screenshotsPaused = paused
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            _reconnecting.value = true
            delay(RECONNECT_DELAY_MS.milliseconds)
            _reconnecting.value = false
            val profile = lastProfile ?: return@launch
            if (!manualDisconnect) {
                client.connect(profile.host, profile.port, profile.password)
            }
        }
    }

    private fun startRecordTimer() {
        recordTimerJob?.cancel()
        _recordingTimeSec.value = 0L
        recordTimerJob = viewModelScope.launch {
            while (true) {
                delay(1.seconds)
                _recordingTimeSec.value++
            }
        }
    }

    private fun stopRecordTimer() {
        recordTimerJob?.cancel()
        recordTimerJob = null
        _recordingTimeSec.value = 0L
    }

    private fun startScreenshotPolling() {
        if (screenshotJob?.isActive == true) return
        screenshotJob = viewModelScope.launch {
            while (true) {
                delay(SCREENSHOT_POLL_MS.milliseconds)
                if (!screenshotsPaused) {
                    val program = client.currentScene.value
                    if (program.isNotEmpty()) client.fetchScreenshot(program, isProgram = true)
                    if (client.studioModeEnabled.value) {
                        val preview = client.previewScene.value
                        if (preview.isNotEmpty() && preview != program)
                            client.fetchScreenshot(preview, isProgram = false)
                    }
                }
            }
        }
    }

    private fun stopScreenshotPolling() {
        screenshotJob?.cancel()
        screenshotJob = null
    }

    fun connect(profile: OBSProfile) {
        manualDisconnect = false
        lastProfile = profile
        store.setLastUsedId(profile.id)
        client.connect(profile.host, profile.port, profile.password)
    }

    fun disconnect() {
        manualDisconnect = true
        reconnectJob?.cancel()
        _reconnecting.value = false
        client.disconnect()
        twitchChatClient.clearMessages()
        kickChatClient.clearMessages()
    }

    fun createScene(name: String) = client.createScene(name)

    fun onSceneClick(name: String) {
        if (client.studioModeEnabled.value) client.setPreviewScene(name)
        else client.setCurrentScene(name)
    }

    fun triggerTransition() = client.triggerTransition()
    fun cutToScene() {
        val preview = client.previewScene.value
        if (preview.isNotEmpty()) client.setCurrentScene(preview)
    }

    fun toggleStudioMode() = client.setStudioModeEnabled(!client.studioModeEnabled.value)

    @Suppress("unused")
    fun reorderScene(sceneName: String, uiIndex: Int) {
        val obsIndex = (client.scenes.value.size - 1 - uiIndex).coerceAtLeast(0)
        client.setSceneIndex(sceneName, obsIndex)
    }

    fun setSceneCollection(name: String) = client.setCurrentSceneCollection(name)

    fun toggleStream() {
        if (client.streamActive.value) client.stopStream() else client.startStream()
    }

    fun toggleRecord() {
        if (client.recordActive.value) client.stopRecord() else client.startRecord()
    }

    fun loadFilters(sourceName: String) {
        filterSourceName = sourceName
        client.fetchFilters(sourceName)
    }

    fun addFilter(filterName: String, filterKind: String) {
        if (filterSourceName.isNotEmpty())
            client.createFilter(filterSourceName, filterName, filterKind)
    }

    fun removeFilter(filterName: String) {
        if (filterSourceName.isNotEmpty())
            client.removeFilter(filterSourceName, filterName)
    }

    fun toggleFilter(filterName: String, currentEnabled: Boolean) {
        if (filterSourceName.isNotEmpty())
            client.setFilterEnabled(filterSourceName, filterName, !currentEnabled)
    }

    fun setFilterSettings(filterName: String, partialSettings: Map<String, Any>) {
        if (filterSourceName.isNotEmpty())
            client.setFilterSettings(filterSourceName, filterName, partialSettings)
    }

    fun loadGroupItems(groupName: String) = client.fetchGroupItems(groupName)

    fun toggleGroupItemVisibility(groupName: String, sceneItemId: Int, currentEnabled: Boolean) {
        client.setGroupItemEnabled(groupName, sceneItemId, !currentEnabled)
    }

    fun deleteGroupItem(groupName: String, sceneItemId: Int) {
        client.removeGroupItem(groupName, sceneItemId)
    }

    fun loadInputSettings(sourceName: String) = client.fetchInputSettings(sourceName)

    fun setInputSettings(sourceName: String, partialSettings: Map<String, Any>) =
        client.setInputSettings(sourceName, partialSettings)

    fun setEnable7tv(enabled: Boolean) {
        store.setEnable7tv(enabled); _enable7tv.value = enabled; reloadEmotes()
    }
    fun setEnableBttv(enabled: Boolean) {
        store.setEnableBttv(enabled); _enableBttv.value = enabled; reloadEmotes()
    }
    fun setEnableFfz(enabled: Boolean) {
        store.setEnableFfz(enabled); _enableFfz.value = enabled; reloadEmotes()
    }

    private fun reloadEmotes() {
        viewModelScope.launch {
            emoteRepository.loadAll(_enable7tv.value, _enableBttv.value, _enableFfz.value, kickChatClient.userId)
            val roomId = twitchChatClient.roomId.value
            if (roomId.isNotEmpty()) {
                emoteRepository.loadChannelEmotes(
                    roomId, _twitchChannel.value,
                    _enable7tv.value, _enableBttv.value, _enableFfz.value
                )
            }
        }
    }

    fun saveTwitchChannel(channel: String) {
        store.setTwitchChannel(channel)
        _twitchChannel.value = channel
        twitchChatClient.connect(channel)
        startTwitchStatusPolling(channel)
    }

    fun saveYoutubeChannelId(channelId: String) {
        store.setYoutubeChannelId(channelId)
        _youtubeChannelId.value = channelId
        youtubeChatClient.connect(channelId)
    }

    fun saveKickChannel(channel: String) {
        store.setKickChannel(channel)
        _kickChannel.value = channel
        kickChatClient.connect(channel)
        startKickStatusPolling(channel)
        reloadEmotes()
    }

    fun connectTwitchChat() {
        val channel = _twitchChannel.value
        if (channel.isNotEmpty()) twitchChatClient.connect(channel)
        val channelId = _youtubeChannelId.value
        if (channelId.isNotEmpty()) youtubeChatClient.connect(channelId)
        val kick = _kickChannel.value
        if (kick.isNotEmpty()) kickChatClient.connect(kick)
    }

    fun setChatFontSize(sp: Float) { store.setChatFontSize(sp); _chatFontSize.value = sp }
    fun setChatLineSpacing(dp: Float) { store.setChatLineSpacing(dp); _chatLineSpacing.value = dp }
    fun setChatEmoteSize(sp: Float) { store.setChatEmoteSize(sp); _chatEmoteSize.value = sp }
    fun setChatUsernameSize(sp: Float) { store.setChatUsernameSize(sp); _chatUsernameSize.value = sp }
    fun setAnimatedEmotes(enabled: Boolean) { store.setAnimatedEmotes(enabled); _animatedEmotes.value = enabled }
    fun setShowMessageTime(enabled: Boolean) { store.setShowMessageTime(enabled); _showMessageTime.value = enabled }
    fun setShowExpandButton(enabled: Boolean) { store.setShowExpandButton(enabled); _showExpandButton.value = enabled }
    fun setShowFullScreenButton(enabled: Boolean) { store.setShowFullScreenButton(enabled); _showFullScreenButton.value = enabled }
    fun setShowScreenLockButton(enabled: Boolean) { store.setShowScreenLockButton(enabled); _showScreenLockButton.value = enabled }
    fun setFullScreenActive(active: Boolean) { store.setFullScreenActive(active); _fullScreenActive.value = active }
    fun setIsChatLocked(locked: Boolean) { _isChatLocked.value = locked }
    fun setShowDebugBar(enabled: Boolean) { store.setShowDebugBar(enabled); _showDebugBar.value = enabled }
    fun setShowEmoteDebug(enabled: Boolean) { store.setShowEmoteDebug(enabled); _showEmoteDebug.value = enabled }
    fun setShowMiniMixer(enabled: Boolean) { store.setShowMiniMixer(enabled); _showMiniMixer.value = enabled }
    fun setFilterMiniMixerByScene(enabled: Boolean) { store.setFilterMiniMixerByScene(enabled); _filterMiniMixerByScene.value = enabled }
    fun setFilterMainMixerByScene(enabled: Boolean) { store.setFilterMainMixerByScene(enabled); _filterMainMixerByScene.value = enabled }
    fun setShowCollectionChip(enabled: Boolean) { store.setShowCollectionChip(enabled); _showCollectionChip.value = enabled }

    fun setTtsEnabled(enabled: Boolean) { store.setTtsEnabled(enabled); _ttsEnabled.value = enabled }
    fun setTtsSubBitsOnly(enabled: Boolean) { store.setTtsSubBitsOnly(enabled); _ttsSubBitsOnly.value = enabled }
    fun setTtsIgnoreSender(ignore: Boolean) { store.setTtsIgnoreSender(ignore); _ttsIgnoreSender.value = ignore }
    fun setTtsIgnoreLinks(ignore: Boolean) { store.setTtsIgnoreLinks(ignore); _ttsIgnoreLinks.value = ignore }
    fun setTtsIgnoreEmotes(ignore: Boolean) { store.setTtsIgnoreEmotes(ignore); _ttsIgnoreEmotes.value = ignore }

    fun setTtsLanguage(lang: String) {
        store.setTtsLanguage(lang)
        _ttsLanguage.value = lang
        updateTtsLocale(lang)
    }

    private fun updateTtsLocale(lang: String) {
        val locale = when (lang) {
            "zh-HK" -> Locale("zh", "HK")
            "zh-CN" -> Locale.SIMPLIFIED_CHINESE
            else -> Locale.TRADITIONAL_CHINESE
        }
        ttsManager.setLanguage(locale)
    }

    fun exportSettings(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = store.exportAllToJson()
                context.contentResolver.openOutputStream(uri)?.use { 
                    it.write(json.toByteArray())
                }
            } catch (_: Exception) {}
        }
    }

    fun importSettings(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val json = input.bufferedReader().readText()
                    if (store.importFromJson(json)) {
                        _profiles.value = store.getProfiles()
                        _autoConnect.value = store.getAutoConnect()
                        _twitchChannel.value = store.getTwitchChannel()
                        _youtubeChannelId.value = store.getYoutubeChannelId()
                        _kickChannel.value = store.getKickChannel()
                        _chatFontSize.value = store.getChatFontSize()
                        _chatLineSpacing.value = store.getChatLineSpacing()
                        _chatEmoteSize.value = store.getChatEmoteSize()
                        _chatUsernameSize.value = store.getChatUsernameSize()
                        _animatedEmotes.value = store.getAnimatedEmotes()
                        _showMessageTime.value = store.getShowMessageTime()
                        _showExpandButton.value = store.getShowExpandButton()
                        _showFullScreenButton.value = store.getShowFullScreenButton()
                        _showScreenLockButton.value = store.getShowScreenLockButton()
                        _fullScreenActive.value = store.getFullScreenActive()
                        _showDebugBar.value = store.getShowDebugBar()
                        _enable7tv.value = store.getEnable7tv()
                        _enableBttv.value = store.getEnableBttv()
                        _enableFfz.value = store.getEnableFfz()
                        _showMiniMixer.value = store.getShowMiniMixer()
                        _filterMiniMixerByScene.value = store.getFilterMiniMixerByScene()
                        _filterMainMixerByScene.value = store.getFilterMainMixerByScene()
                        _showCollectionChip.value = store.getShowCollectionChip()
                        _showEmoteDebug.value = store.getShowEmoteDebug()
                        _ttsEnabled.value = store.getTtsEnabled()
                        _ttsSubBitsOnly.value = store.getTtsSubBitsOnly()
                        _ttsIgnoreSender.value = store.getTtsIgnoreSender()
                        _ttsIgnoreLinks.value = store.getTtsIgnoreLinks()
                        _ttsIgnoreEmotes.value = store.getTtsIgnoreEmotes()
                        _ttsLanguage.value = store.getTtsLanguage()
                        updateTtsLocale(_ttsLanguage.value)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun addSource(inputName: String, inputKind: String) {
        val scene = client.currentScene.value
        if (scene.isNotEmpty()) client.createInput(scene, inputName, inputKind)
    }

    fun toggleMute(inputName: String) = client.toggleMute(inputName)
    fun setVolume(inputName: String, db: Float) = client.setVolume(inputName, db)

    override fun onCleared() {
        super.onCleared()
        client.disconnect()
        twitchChatClient.disconnect()
        youtubeChatClient.disconnect()
        kickChatClient.disconnect()
        ttsManager.release()
    }
}
