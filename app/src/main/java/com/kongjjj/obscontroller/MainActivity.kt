package com.kongjjj.obscontroller

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kongjjj.obscontroller.ui.AudioScreen
import com.kongjjj.obscontroller.ui.ChatScreen
import com.kongjjj.obscontroller.ui.ConnectScreen
import com.kongjjj.obscontroller.ui.ScenesScreen
import com.kongjjj.obscontroller.ui.SettingsDialog

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Keep the screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        enableEdgeToEdge()
        setContent {
            OBSControllerTheme {
                OBSControllerApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Stop foreground service when app is in foreground
        BridgeService.stop(this)
    }

    override fun onStop() {
        super.onStop()
        // Start foreground service only if we are NOT finishing (e.g. Home button)
        // If isFinishing is true, the user is exiting the app (e.g. Back button)
        if (!isFinishing) {
            BridgeService.start(this)
        }
    }

    // Removed onResume and onPause to use onStart/onStop for more reliable service control
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OBSControllerApp(vm: OBSViewModel = viewModel()) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    val scenes by vm.scenes.collectAsState()
    val currentScene by vm.currentScene.collectAsState()
    val inputs by vm.inputs.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val streamActive by vm.streamActive.collectAsState()
    val recordActive by vm.recordActive.collectAsState()
    val studioModeEnabled by vm.studioModeEnabled.collectAsState()
    val previewScene by vm.previewScene.collectAsState()
    val programScreenshot by vm.programScreenshot.collectAsState()
    val previewScreenshot by vm.previewScreenshot.collectAsState()
    val sceneItems by vm.sceneItems.collectAsState()
    val audioMixerInputs by vm.audioMixerInputs.collectAsState()
    val groupItems by vm.groupItems.collectAsState()
    val filters by vm.filters.collectAsState()
    val inputSettings by vm.inputSettings.collectAsState()
    val autoConnect by vm.autoConnect.collectAsState()
    val reconnecting by vm.reconnecting.collectAsState()
    val twitchChannel by vm.twitchChannel.collectAsState()
    val youtubeChannelId by vm.youtubeChannelId.collectAsState()
    val kickChannel by vm.kickChannel.collectAsState()
    val chatMessages by vm.chatMessages.collectAsState()
    val chatConnected by vm.chatConnected.collectAsState()
    val thirdPartyEmotes by vm.thirdPartyEmotes.collectAsState()
    val twitchBadges by vm.twitchBadges.collectAsState()
    val emoteLoadReport by vm.emoteLoadReport.collectAsState()
    val chatFontSize by vm.chatFontSize.collectAsState()
    val chatLineSpacing by vm.chatLineSpacing.collectAsState()
    val chatEmoteSize by vm.chatEmoteSize.collectAsState()
    val chatUsernameSize by vm.chatUsernameSize.collectAsState()
    val animatedEmotes by vm.animatedEmotes.collectAsState()
    val showDebugBar by vm.showDebugBar.collectAsState()
    val showEmoteDebug by vm.showEmoteDebug.collectAsState()
    val twitchViewerCount by vm.twitchViewerCount.collectAsState()
    val youtubeViewerCount by vm.youtubeViewerCount.collectAsState()
    val kickViewerCount by vm.kickViewerCount.collectAsState()
    val streamUptime by vm.streamUptime.collectAsState()
    val streamCategory by vm.streamCategory.collectAsState()
    val enable7tv by vm.enable7tv.collectAsState()
    val enableBttv by vm.enableBttv.collectAsState()
    val enableFfz by vm.enableFfz.collectAsState()
    val recordingTimeSec by vm.recordingTimeSec.collectAsState()
    val sceneCollections by vm.sceneCollections.collectAsState()
    val currentSceneCollection by vm.currentSceneCollection.collectAsState()
    val volumeMeters by vm.volumeMeters.collectAsState()
    val showMiniMixer by vm.showMiniMixer.collectAsState()
    val filterMiniMixerByScene by vm.filterMiniMixerByScene.collectAsState()
    val showCollectionChip by vm.showCollectionChip.collectAsState()
    val ttsEnabled by vm.ttsEnabled.collectAsState()
    val ttsSubBitsOnly by vm.ttsSubBitsOnly.collectAsState()
    val ttsIgnoreSender by vm.ttsIgnoreSender.collectAsState()
    val ttsIgnoreLinks by vm.ttsIgnoreLinks.collectAsState()
    val ttsIgnoreEmotes by vm.ttsIgnoreEmotes.collectAsState()
    val ttsLanguage by vm.ttsLanguage.collectAsState()
    val showMessageTime by vm.showMessageTime.collectAsState()
    val showExpandButton by vm.showExpandButton.collectAsState()
    val showFullScreenButton by vm.showFullScreenButton.collectAsState()
    val showScreenLockButton by vm.showScreenLockButton.collectAsState()
    val fullScreenActive by vm.fullScreenActive.collectAsState()
    val isChatLocked by vm.isChatLocked.collectAsState()

    val isConnected = state is ConnectionState.Connected
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showExitConfirmation by remember { mutableStateOf(false) }

    // Handle System Bars (Status & Navigation) visibility for Full Screen mode
    val activity = context as? Activity
    val window = activity?.window
    if (window != null) {
        val controller = remember(window) { WindowInsetsControllerCompat(window, window.decorView) }
        LaunchedEffect(fullScreenActive, selectedTab) {
            if (fullScreenActive && selectedTab == 2) {
                // Hide system bars
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                // Show system bars
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("結束程式") },
            text = { Text("確定要關閉程式嗎？按 Home 鍵可讓程式在背景繼續運作。") },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmation = false
                        (context as? Activity)?.finish()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("確定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) {
                    Text("取消")
                }
            }
        )
    }

    BackHandler(enabled = true) {
        showExitConfirmation = true
    }

    if (showSettings) {
        SettingsDialog(
            twitchChannel = twitchChannel,
            youtubeChannelId = youtubeChannelId,
            kickChannel = kickChannel,
            chatFontSize = chatFontSize,
            chatLineSpacing = chatLineSpacing,
            chatEmoteSize = chatEmoteSize,
            chatUsernameSize = chatUsernameSize,
            animatedEmotes = animatedEmotes,
            showMessageTime = showMessageTime,
            showExpandButton = showExpandButton,
            showFullScreenButton = showFullScreenButton,
            showScreenLockButton = showScreenLockButton,
            showDebugBar = showDebugBar,
            showEmoteDebug = showEmoteDebug,
            enable7tv = enable7tv,
            enableBttv = enableBttv,
            enableFfz = enableFfz,
            onSaveChannel = { vm.saveTwitchChannel(it) },
            onSaveYoutubeChannelId = { vm.saveYoutubeChannelId(it) },
            onSaveKickChannel = { vm.saveKickChannel(it) },
            onFontSizeChange = { vm.setChatFontSize(it) },
            onLineSpacingChange = { vm.setChatLineSpacing(it) },
            onEmoteSizeChange = { vm.setChatEmoteSize(it) },
            onUsernameSizeChange = { vm.setChatUsernameSize(it) },
            onAnimatedEmotesChange = { vm.setAnimatedEmotes(it) },
            onShowMessageTimeChange = { vm.setShowMessageTime(it) },
            onShowExpandButtonChange = { vm.setShowExpandButton(it) },
            onShowFullScreenButtonChange = { vm.setShowFullScreenButton(it) },
            onShowScreenLockButtonChange = { vm.setShowScreenLockButton(it) },
            onShowDebugBarChange = { vm.setShowDebugBar(it) },
            onShowEmoteDebugChange = { vm.setShowEmoteDebug(it) },
            onEnable7tvChange = { vm.setEnable7tv(it) },
            onEnableBttvChange = { vm.setEnableBttv(it) },
            onEnableFfzChange = { vm.setEnableFfz(it) },
            showMiniMixer = showMiniMixer,
            filterMiniMixerByScene = filterMiniMixerByScene,
            filterMainMixerByScene = vm.filterMainMixerByScene.collectAsState().value,
            showCollectionChip = showCollectionChip,
            onShowMiniMixerChange = { vm.setShowMiniMixer(it) },
            onFilterMiniMixerBySceneChange = { vm.setFilterMiniMixerByScene(it) },
            onFilterMainMixerBySceneChange = { vm.setFilterMainMixerByScene(it) },
            onShowCollectionChipChange = { vm.setShowCollectionChip(it) },
            ttsEnabled = ttsEnabled,
            ttsSubBitsOnly = ttsSubBitsOnly,
            ttsIgnoreSender = ttsIgnoreSender,
            ttsIgnoreLinks = ttsIgnoreLinks,
            ttsIgnoreEmotes = ttsIgnoreEmotes,
            ttsLanguage = ttsLanguage,
            onTtsEnabledChange = { vm.setTtsEnabled(it) },
            onTtsSubBitsOnlyChange = { vm.setTtsSubBitsOnly(it) },
            onTtsIgnoreSenderChange = { vm.setTtsIgnoreSender(it) },
            onTtsIgnoreLinksChange = { vm.setTtsIgnoreLinks(it) },
            onTtsIgnoreEmotesChange = { vm.setTtsIgnoreEmotes(it) },
            onTtsLanguageChange = { vm.setTtsLanguage(it) },
            onDismiss = { showSettings = false }
        )
    }


    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current

    fun hideKeyboard() {
        focusManager.clearFocus()
        keyboardController?.hide()
        val window = (context as? Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).hide(WindowInsetsCompat.Type.ime())
        }
    }

    Scaffold(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { hideKeyboard() },
        topBar = {
            if (!fullScreenActive || selectedTab != 2) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // "OBS Controller" button (Leftmost)
                            val obsButtonColor = if (selectedTab != 2) MaterialTheme.colorScheme.primary 
                                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            OutlinedButton(
                                onClick = { selectedTab = 0 },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = obsButtonColor
                                ),
                                border = BorderStroke(
                                    1.dp, 
                                    (if (selectedTab != 2) MaterialTheme.colorScheme.primary 
                                     else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.5f)
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_obs),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = obsButtonColor
                                )
                                Spacer(Modifier.width(3.dp))
                                Text("OBS控制器", style = MaterialTheme.typography.labelMedium)
                            }

                            // "Chat" button
                            OutlinedButton(
                                onClick = { selectedTab = 2 },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (selectedTab == 2) MaterialTheme.colorScheme.secondary 
                                                  else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                ),
                                border = BorderStroke(
                                    1.dp, 
                                    (if (selectedTab == 2) MaterialTheme.colorScheme.secondary 
                                     else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.5f)
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("聊天室", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    },
                    actions = {
                        // "Disconnect" button only if connected
                        if (isConnected) {
                            OutlinedButton(
                                onClick = { vm.disconnect() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.WifiOff, contentDescription = "Disconnect", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("斷開OBS", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        
                        // Settings icon (Rightmost)
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        },
        bottomBar = {
            if (isConnected && selectedTab != 2 && !fullScreenActive) {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Movie, contentDescription = "Scenes") },
                        label = { Text("場景") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Audio") },
                        label = { Text("音效") }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (!isConnected && selectedTab != 2) {
                ConnectScreen(
                    profiles = profiles,
                    lastUsedProfileId = vm.lastUsedProfileId,
                    connectionState = state,
                    autoConnect = autoConnect,
                    reconnecting = reconnecting,
                    onConnect = { vm.connect(it) },
                    onSaveProfile = { vm.saveProfile(it) },
                    onDeleteProfile = { vm.deleteProfile(it) },
                    onAutoConnectChange = { vm.setAutoConnect(it) },
                    onCancelReconnect = { vm.disconnect() },
                    onExport = { uri -> vm.exportSettings(context, uri) },
                    onImport = { uri -> vm.importSettings(context, uri) }
                )
            } else {
                when (selectedTab) {
                    0 -> ScenesScreen(
                        scenes = scenes,
                        currentScene = currentScene,
                        previewScene = previewScene,
                        studioModeEnabled = studioModeEnabled,
                        streamActive = streamActive,
                        recordActive = recordActive,
                        recordingTimeSec = recordingTimeSec,
                        programScreenshot = programScreenshot,
                        previewScreenshot = previewScreenshot,
                        onSceneClick = { vm.onSceneClick(it) },
                        onTransition = { vm.triggerTransition() },
                        onCut = { vm.cutToScene() },
                        onToggleStudioMode = { vm.toggleStudioMode() },
                        onToggleStream = { vm.toggleStream() },
                        onToggleRecord = { vm.toggleRecord() },
                        onCreateScene = { vm.createScene(it) },
                        onAddSource = { name, kind -> vm.addSource(name, kind) },
                        onPreviewVisibilityChange = { visible -> vm.setScreenshotsPaused(!visible) },
                        sceneItems = sceneItems,
                        groupItems = groupItems,
                        onLoadSceneItems = { vm.loadSceneItems(it) },
                        onToggleSceneItemVisibility = { id, enabled -> vm.toggleSceneItemVisibility(id, enabled) },
                        onDeleteSceneItem = { vm.deleteSceneItem(it) },
                        onLoadGroupItems = { vm.loadGroupItems(it) },
                        onToggleGroupItemVisibility = { grp, id, enabled -> vm.toggleGroupItemVisibility(grp, id, enabled) },
                        onDeleteGroupItem = { grp, id -> vm.deleteGroupItem(grp, id) },
                        filters = filters,
                        onLoadFilters = { vm.loadFilters(it) },
                        onAddFilter = { name, kind -> vm.addFilter(name, kind) },
                        onRemoveFilter = { vm.removeFilter(it) },
                        onToggleFilter = { name, enabled -> vm.toggleFilter(name, enabled) },
                        onSetFilterSettings = { name, settings -> vm.setFilterSettings(name, settings) },
                        inputSettings = inputSettings,
                        onLoadInputSettings = { vm.loadInputSettings(it) },
                        onSetInputSettings = { name, settings -> vm.setInputSettings(name, settings) },
                        onReorderSceneItem = { id, idx -> vm.reorderSceneItem(id, idx) },
                        sceneCollections = sceneCollections,
                        currentSceneCollection = currentSceneCollection,
                        onSetSceneCollection = { vm.setSceneCollection(it) },
                        inputs = inputs,
                        onToggleMute = { vm.toggleMute(it) },
                        onMiniVolumeChange = { name, db -> vm.setVolume(name, db) },
                        volumeMeters = volumeMeters,
                        showMiniMixer = showMiniMixer,
                        filterMiniMixerByScene = filterMiniMixerByScene,
                        showCollectionChip = showCollectionChip
                    )
                    1 -> AudioScreen(
                        inputs = audioMixerInputs,
                        filters = filters,
                        volumeMeters = volumeMeters,
                        onToggleMute = { vm.toggleMute(it) },
                        onVolumeChange = { name, db -> vm.setVolume(name, db) },
                        onLoadFilters = { vm.loadFilters(it) },
                        onAddFilter = { name, kind -> vm.addFilter(name, kind) },
                        onRemoveFilter = { vm.removeFilter(it) },
                        onToggleFilter = { name, enabled -> vm.toggleFilter(name, enabled) },
                        onSetFilterSettings = { name, settings -> vm.setFilterSettings(name, settings) }
                    )
                    2 -> ChatScreen(
                        twitchChannel = twitchChannel,
                        youtubeChannelId = youtubeChannelId,
                        kickChannel = kickChannel,
                        chatMessages = chatMessages,
                        chatConnected = chatConnected,
                        thirdPartyEmotes = thirdPartyEmotes,
                        twitchBadges = twitchBadges,
                        emoteLoadReport = emoteLoadReport,
                        chatFontSize = chatFontSize,
                        chatLineSpacing = chatLineSpacing,
                        chatEmoteSize = chatEmoteSize,
                        chatUsernameSize = chatUsernameSize,
                        animatedEmotes = animatedEmotes,
                        showMessageTime = showMessageTime,
                        showExpandButton = showExpandButton,
                        showFullScreenButton = showFullScreenButton,
                        showScreenLockButton = showScreenLockButton,
                        fullScreenActive = fullScreenActive,
                        isChatLocked = isChatLocked,
                        showDebugBar = showDebugBar,
                        showEmoteDebug = showEmoteDebug,
                        viewerCount = twitchViewerCount,
                        youtubeViewerCount = youtubeViewerCount,
                        kickViewerCount = kickViewerCount,
                        streamUptime = streamUptime,
                        streamCategory = streamCategory,
                        onConnect = { vm.connectTwitchChat() },
                        onToggleFullScreen = { vm.setFullScreenActive(!fullScreenActive) },
                        onToggleChatLock = { vm.setIsChatLocked(!isChatLocked) }
                    )
                }
            }
        }
    }
}

@Composable
fun OBSControllerTheme(content: @Composable () -> Unit) {
    val logoDarkBlue  = Color(0xFF1A237E) // 深藍
    val logoLightBlue = Color(0xFF03A9F4) // 淺藍
    val logoOrange    = Color(0xFFFB8C00) // 橙色
    val logoWhite     = Color(0xFFF5F5F5) // 近白色

    val customColorScheme = darkColorScheme(
        primary = logoLightBlue,
        onPrimary = Color.Black,
        primaryContainer = logoDarkBlue,
        onPrimaryContainer = Color.White,
        secondary = logoOrange,
        onSecondary = Color.Black,
        tertiary = logoLightBlue,
        background = Color(0xFF0D1117), // 極深藍背景
        surface = Color(0xFF161B22),    // 深色表面
        onBackground = logoWhite,
        onSurface = logoWhite,
        error = logoOrange,
        onError = Color.Black
    )

    MaterialTheme(
        colorScheme = customColorScheme,
        content = content
    )
}
