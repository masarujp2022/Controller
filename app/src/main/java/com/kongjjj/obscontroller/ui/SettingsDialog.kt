package com.kongjjj.obscontroller.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.kongjjj.obscontroller.BuildConfig
import com.kongjjj.obscontroller.DEFAULT_EMOTE_SIZE
import com.kongjjj.obscontroller.DEFAULT_FONT_SIZE
import com.kongjjj.obscontroller.DEFAULT_LINE_SPACING
import com.kongjjj.obscontroller.DEFAULT_USERNAME_SIZE
import kotlin.math.roundToInt

@Composable
fun SettingsDialog(
    twitchChannel: String,
    youtubeChannelId: String,
    kickChannel: String = "",
    chatFontSize: Float,
    chatLineSpacing: Float,
    chatEmoteSize: Float,
    chatUsernameSize: Float,
    animatedEmotes: Boolean,
    showMessageTime: Boolean,
    showExpandButton: Boolean,
    showFullScreenButton: Boolean,
    showScreenLockButton: Boolean,
    showDebugBar: Boolean,
    showEmoteDebug: Boolean,
    enable7tv: Boolean,
    enableBttv: Boolean,
    enableFfz: Boolean,
    showMiniMixer: Boolean,
    filterMiniMixerByScene: Boolean,
    filterMainMixerByScene: Boolean,
    showCollectionChip: Boolean,
    onSaveChannel: (String) -> Unit,
    onSaveYoutubeChannelId: (String) -> Unit,
    onSaveKickChannel: (String) -> Unit = {},
    onFontSizeChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onEmoteSizeChange: (Float) -> Unit,
    onUsernameSizeChange: (Float) -> Unit,
    onAnimatedEmotesChange: (Boolean) -> Unit,
    onShowMessageTimeChange: (Boolean) -> Unit,
    onShowExpandButtonChange: (Boolean) -> Unit,
    onShowFullScreenButtonChange: (Boolean) -> Unit,
    onShowScreenLockButtonChange: (Boolean) -> Unit,
    onShowDebugBarChange: (Boolean) -> Unit,
    onShowEmoteDebugChange: (Boolean) -> Unit,
    onEnable7tvChange: (Boolean) -> Unit,
    onEnableBttvChange: (Boolean) -> Unit,
    onEnableFfzChange: (Boolean) -> Unit,
    onShowMiniMixerChange: (Boolean) -> Unit,
    onFilterMiniMixerBySceneChange: (Boolean) -> Unit,
    onFilterMainMixerBySceneChange: (Boolean) -> Unit,
    onShowCollectionChipChange: (Boolean) -> Unit,
    ttsEnabled: Boolean,
    ttsSubBitsOnly: Boolean,
    ttsIgnoreSender: Boolean,
    ttsIgnoreLinks: Boolean,
    ttsIgnoreEmotes: Boolean,
    ttsLanguage: String,
    onTtsEnabledChange: (Boolean) -> Unit,
    onTtsSubBitsOnlyChange: (Boolean) -> Unit,
    onTtsIgnoreSenderChange: (Boolean) -> Unit,
    onTtsIgnoreLinksChange: (Boolean) -> Unit,
    onTtsIgnoreEmotesChange: (Boolean) -> Unit,
    onTtsLanguageChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current

    var channelInput by remember(twitchChannel) { mutableStateOf(twitchChannel) }
    var youtubeInput by remember(youtubeChannelId) { mutableStateOf(youtubeChannelId) }
    var kickInput by remember(kickChannel) { mutableStateOf(kickChannel) }

    fun hideKeyboard() {
        focusManager.clearFocus()
        keyboardController?.hide()
        // Fallback for some devices/scenarios
        val window = (context as? Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).hide(WindowInsetsCompat.Type.ime())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { hideKeyboard() },
        title = {
            Text(
                "設定",
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { hideKeyboard() }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { hideKeyboard() },
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                // ── Twitch channel ─────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Twitch 頻道", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = channelInput,
                            onValueChange = { channelInput = it },
                            placeholder = { Text("頻道名稱") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                hideKeyboard()
                                val trimmed = channelInput.trim().lowercase()
                                onSaveChannel(trimmed)
                                Toast.makeText(context, "已儲存", Toast.LENGTH_SHORT).show()
                            })
                        )
                        FilledTonalButton(
                            onClick = {
                                hideKeyboard()
                                val trimmed = channelInput.trim().lowercase()
                                onSaveChannel(trimmed)
                                Toast.makeText(context, "已儲存", Toast.LENGTH_SHORT).show()
                            }
                        ) { Text("儲存") }
                    }
                }

                // ── YouTube Channel ID ───────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("YouTube 頻道 ID (UC...)", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = youtubeInput,
                            onValueChange = { youtubeInput = it },
                            placeholder = { Text("Channel ID") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                hideKeyboard()
                                val trimmed = youtubeInput.trim()
                                onSaveYoutubeChannelId(trimmed)
                                Toast.makeText(context, "已儲存", Toast.LENGTH_SHORT).show()
                            })
                        )
                        FilledTonalButton(
                            onClick = {
                                hideKeyboard()
                                val trimmed = youtubeInput.trim()
                                onSaveYoutubeChannelId(trimmed)
                                Toast.makeText(context, "已儲存", Toast.LENGTH_SHORT).show()
                            }
                        ) { Text("儲存") }
                    }
                }

                // ── Kick Channel ──────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Kick 頻道", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = kickInput,
                            onValueChange = { kickInput = it },
                            placeholder = { Text("頻道名稱") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                hideKeyboard()
                                val trimmed = kickInput.trim().lowercase().removePrefix("/")
                                onSaveKickChannel(trimmed)
                                Toast.makeText(context, "已儲存", Toast.LENGTH_SHORT).show()
                            })
                        )
                        FilledTonalButton(
                            onClick = {
                                hideKeyboard()
                                val trimmed = kickInput.trim().lowercase().removePrefix("/")
                                onSaveKickChannel(trimmed)
                                Toast.makeText(context, "已儲存", Toast.LENGTH_SHORT).show()
                            }
                        ) { Text("儲存") }
                    }
                }

                HorizontalDivider()

                // ── Font Settings Section ──────────────────────────────────
                Text("字型設定", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)

                // ── Font size ──────────────────────────────────────────────
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("字型大小", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${chatFontSize.roundToInt()} sp",
                            style = MaterialTheme.typography.labelMedium)
                    }
                    Slider(
                        value = chatFontSize,
                        onValueChange = onFontSizeChange,
                        valueRange = 10f..20f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── Username size ──────────────────────────────────────────
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("使用者名稱大小", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${chatUsernameSize.roundToInt()} sp",
                            style = MaterialTheme.typography.labelMedium)
                    }
                    Slider(
                        value = chatUsernameSize,
                        onValueChange = onUsernameSizeChange,
                        valueRange = 10f..20f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── Line spacing ───────────────────────────────────────────
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("行距", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${chatLineSpacing.roundToInt()} dp",
                            style = MaterialTheme.typography.labelMedium)
                    }
                    Slider(
                        value = chatLineSpacing,
                        onValueChange = onLineSpacingChange,
                        valueRange = 0f..12f,
                        steps = 11,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── Emote size ─────────────────────────────────────────────
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("表情符號大小", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${chatEmoteSize.roundToInt()} sp",
                            style = MaterialTheme.typography.labelMedium)
                    }
                    Slider(
                        value = chatEmoteSize,
                        onValueChange = onEmoteSizeChange,
                        valueRange = 16f..48f,
                        steps = 15,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── Reset ──────────────────────────────────────────────────
                TextButton(
                    onClick = {
                        onFontSizeChange(DEFAULT_FONT_SIZE)
                        onUsernameSizeChange(DEFAULT_USERNAME_SIZE)
                        onLineSpacingChange(DEFAULT_LINE_SPACING)
                        onEmoteSizeChange(DEFAULT_EMOTE_SIZE)
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("重設為預設值",
                        style = MaterialTheme.typography.labelSmall)
                }

                HorizontalDivider()

                // ── Chatroom Settings Section ──────────────────────────────
                Text("聊天室設定", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)

                // ── Debug status bar ───────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("顯示觀看人數與直播時間", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("在聊天室標題顯示即時人數與開播時長",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = showDebugBar,
                        onCheckedChange = onShowDebugBarChange
                    )
                }

                // ── Show message time ──────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("顯示留言時間", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("在留言內容前顯示發送時間",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = showMessageTime,
                        onCheckedChange = onShowMessageTimeChange
                    )
                }

                // ── Animated emotes ────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("動態表情符號", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("將 GIF 顯示為動態圖片",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = animatedEmotes,
                        onCheckedChange = onAnimatedEmotesChange
                    )
                }

                HorizontalDivider()

                // ── Button Switches Section ────────────────────────────────
                Text("按鈕開關", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)

                // ── Show jump to latest button ─────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("顯示跳到最新按鈕", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("在聊天室右上角顯示跳到最新留言的按鈕",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = showExpandButton,
                        onCheckedChange = onShowExpandButtonChange
                    )
                }

                // ── Show full screen button ────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("顯示全螢幕按鈕", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("在聊天室右上角顯示隱藏導覽列的按鈕",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = showFullScreenButton,
                        onCheckedChange = onShowFullScreenButtonChange
                    )
                }

                // ── Show screen lock button ────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("顯示聊天鎖定按鈕", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("在聊天室右上角顯示鎖定聊天螢幕防止觸控的按鈕",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = showScreenLockButton,
                        onCheckedChange = onShowScreenLockButtonChange
                    )
                }

                HorizontalDivider()

                // ── Debug status bar ───────────────────────────────────────
                // Moved up to Chatroom Settings Section

                // ── Emote Debug Bar ────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("顯示表情符號載入狀態", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("在聊天室上方顯示 Emotes 載入資訊 (Debug)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = showEmoteDebug,
                        onCheckedChange = onShowEmoteDebugChange
                    )
                }

                HorizontalDivider()

                // ── Scenes tab UI ──────────────────────────────────────────
                Text("場景分頁設定", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("迷你混音器 (Mini Mixer)", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("在場景分頁顯示音效控制",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    Switch(checked = showMiniMixer, onCheckedChange = onShowMiniMixerChange)
                }

                if (showMiniMixer) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("按場景過濾", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("僅顯示當前場景的音軌",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                        Switch(checked = filterMiniMixerByScene, onCheckedChange = onFilterMiniMixerBySceneChange)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("場景清單切換器", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("顯示用於切換場景清單 (Collection) 的按鈕",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    Switch(checked = showCollectionChip, onCheckedChange = onShowCollectionChipChange)
                }

                HorizontalDivider()

                // ── Audio tab settings ───────────────────────────────────────
                Text("音效分頁設定", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("按場景過濾混音器", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("僅顯示當前節目與預覽場景的音軌",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    Switch(checked = filterMainMixerByScene, onCheckedChange = onFilterMainMixerBySceneChange)
                }

                HorizontalDivider()

                // ── Emote providers ────────────────────────────────────────
                Text("表情符號來源", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("7TV", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enable7tv, onCheckedChange = onEnable7tvChange)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("BetterTTV", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enableBttv, onCheckedChange = onEnableBttvChange)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("FrankerFaceZ", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enableFfz, onCheckedChange = onEnableFfzChange)
                }

                HorizontalDivider()

                // ── TTS ──────────────────────────────────────────────
                Text("語音輸出 (TTS)", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("聊天室語音", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = ttsEnabled, onCheckedChange = onTtsEnabledChange)
                }

                if (ttsEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("語音語言", style = MaterialTheme.typography.bodyMedium)
                        
                        var showLanguageMenu by remember { mutableStateOf(false) }
                        val languages = listOf(
                            "zh-TW" to "國語 (台灣)",
                            "zh-HK" to "廣東話 (香港)",
                            "zh-CN" to "國語 (簡體)"
                        )
                        val currentLangLabel = languages.find { it.first == ttsLanguage }?.second ?: "國語 (台灣)"

                        Box {
                            TextButton(onClick = { showLanguageMenu = true }) {
                                Text(currentLangLabel)
                            }
                            DropdownMenu(
                                expanded = showLanguageMenu,
                                onDismissRequest = { showLanguageMenu = false }
                            ) {
                                languages.forEach { (code, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            onTtsLanguageChange(code)
                                            showLanguageMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("僅朗讀Twitch訂閱與小奇點留言", style = MaterialTheme.typography.bodyMedium)
                            Text("開啟後只朗讀 Twitch 觀眾訂閱及贈送小奇點後的留言（不朗讀通知與普通留言）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                        Switch(checked = ttsSubBitsOnly, onCheckedChange = onTtsSubBitsOnlyChange)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("忽略發送者名稱", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = ttsIgnoreSender, onCheckedChange = onTtsIgnoreSenderChange)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("忽略網址連結", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = ttsIgnoreLinks, onCheckedChange = onTtsIgnoreLinksChange)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("忽略表情符號 (Emote)", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = ttsIgnoreEmotes, onCheckedChange = onTtsIgnoreEmotesChange)
                    }
                }

                HorizontalDivider()

                Text(
                    text = "@2026 kongjjj  Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("關閉") }
        }
    )
}
