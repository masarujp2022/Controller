package com.kongjjj.obscontroller

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson

class ProfileStore(context: Context) {

    private val prefs = context.getSharedPreferences("obs_profiles_v2", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getProfiles(): List<OBSProfile> {
        val json = prefs.getString("profiles", null) ?: return emptyList()
        return gson.fromJson(json, Array<OBSProfile>::class.java).toList()
    }

    fun saveProfile(profile: OBSProfile) {
        val list = getProfiles().toMutableList()
        val idx = list.indexOfFirst { it.id == profile.id }
        if (idx >= 0) list[idx] = profile else list.add(profile)
        prefs.edit { putString("profiles", gson.toJson(list)) }
    }

    fun deleteProfile(id: String) {
        val list = getProfiles().filter { it.id != id }
        prefs.edit { putString("profiles", gson.toJson(list)) }
        if (getLastUsedId() == id) clearLastUsedId()
    }

    fun getLastUsedId(): String? = prefs.getString("last_id", null)
    fun setLastUsedId(id: String) = prefs.edit { putString("last_id", id) }
    private fun clearLastUsedId() = prefs.edit { remove("last_id") }

    fun getAutoConnect(): Boolean = prefs.getBoolean("auto_connect", false)
    fun setAutoConnect(enabled: Boolean) = prefs.edit { putBoolean("auto_connect", enabled) }

    fun getYoutubeChannelId(): String = prefs.getString("youtube_channel_id", "") ?: ""
    fun setYoutubeChannelId(channelId: String) = prefs.edit { putString("youtube_channel_id", channelId) }

    fun getTwitchChannel(): String = prefs.getString("twitch_channel", "") ?: ""
    fun setTwitchChannel(channel: String) = prefs.edit { putString("twitch_channel", channel) }

    fun getKickChannel(): String = prefs.getString("kick_channel", "") ?: ""
    fun setKickChannel(channel: String) = prefs.edit { putString("kick_channel", channel) }

    fun getChatFontSize(): Float = prefs.getFloat("chat_font_size", DEFAULT_FONT_SIZE)
    fun setChatFontSize(sp: Float) = prefs.edit { putFloat("chat_font_size", sp) }

    fun getChatLineSpacing(): Float = prefs.getFloat("chat_line_spacing", DEFAULT_LINE_SPACING)
    fun setChatLineSpacing(dp: Float) = prefs.edit { putFloat("chat_line_spacing", dp) }

    fun getAnimatedEmotes(): Boolean = prefs.getBoolean("animated_emotes", true)
    fun setAnimatedEmotes(enabled: Boolean) = prefs.edit { putBoolean("animated_emotes", enabled) }

    fun getChatEmoteSize(): Float = prefs.getFloat("chat_emote_size", DEFAULT_EMOTE_SIZE)
    fun setChatEmoteSize(sp: Float) = prefs.edit { putFloat("chat_emote_size", sp) }

    fun getChatUsernameSize(): Float = prefs.getFloat("chat_username_size", DEFAULT_USERNAME_SIZE)
    fun setChatUsernameSize(sp: Float) = prefs.edit { putFloat("chat_username_size", sp) }

    fun getShowDebugBar(): Boolean = prefs.getBoolean("show_debug_bar", true)
    fun setShowDebugBar(enabled: Boolean) = prefs.edit { putBoolean("show_debug_bar", enabled) }

    fun getShowEmoteDebug(): Boolean = prefs.getBoolean("show_emote_debug", false)
    fun setShowEmoteDebug(enabled: Boolean) = prefs.edit { putBoolean("show_emote_debug", enabled) }

    fun getEnable7tv(): Boolean = prefs.getBoolean("enable_7tv", true)
    fun setEnable7tv(enabled: Boolean) = prefs.edit { putBoolean("enable_7tv", enabled) }

    fun getEnableBttv(): Boolean = prefs.getBoolean("enable_bttv", true)
    fun setEnableBttv(enabled: Boolean) = prefs.edit { putBoolean("enable_bttv", enabled) }

    fun getEnableFfz(): Boolean = prefs.getBoolean("enable_ffz", true)
    fun setEnableFfz(enabled: Boolean) = prefs.edit { putBoolean("enable_ffz", enabled) }

    fun getShowMiniMixer(): Boolean = prefs.getBoolean("show_mini_mixer", true)
    fun setShowMiniMixer(enabled: Boolean) = prefs.edit { putBoolean("show_mini_mixer", enabled) }

    fun getShowMessageTime(): Boolean = prefs.getBoolean("show_message_time", false)
    fun setShowMessageTime(enabled: Boolean) = prefs.edit { putBoolean("show_message_time", enabled) }

    fun getShowExpandButton(): Boolean = prefs.getBoolean("show_expand_button", true)
    fun setShowExpandButton(enabled: Boolean) = prefs.edit { putBoolean("show_expand_button", enabled) }

    fun getShowFullScreenButton(): Boolean = prefs.getBoolean("show_fullscreen_button", true)
    fun setShowFullScreenButton(enabled: Boolean) = prefs.edit { putBoolean("show_fullscreen_button", enabled) }

    fun getShowScreenLockButton(): Boolean = prefs.getBoolean("show_screen_lock_button", true)
    fun setShowScreenLockButton(enabled: Boolean) = prefs.edit { putBoolean("show_screen_lock_button", enabled) }

    fun getFullScreenActive(): Boolean = prefs.getBoolean("fullscreen_active", false)
    fun setFullScreenActive(active: Boolean) = prefs.edit { putBoolean("fullscreen_active", active) }

    fun getFilterMiniMixerByScene(): Boolean = prefs.getBoolean("filter_mini_mixer_by_scene", false)
    fun setFilterMiniMixerByScene(enabled: Boolean) = prefs.edit { putBoolean("filter_mini_mixer_by_scene", enabled) }

    fun getFilterMainMixerByScene(): Boolean = prefs.getBoolean("filter_main_mixer_by_scene", false)
    fun setFilterMainMixerByScene(enabled: Boolean) = prefs.edit { putBoolean("filter_main_mixer_by_scene", enabled) }

    fun getShowCollectionChip(): Boolean = prefs.getBoolean("show_collection_chip", true)
    fun setShowCollectionChip(enabled: Boolean) = prefs.edit { putBoolean("show_collection_chip", enabled) }

    fun getTtsEnabled(): Boolean = prefs.getBoolean("tts_enabled", false)
    fun setTtsEnabled(enabled: Boolean) = prefs.edit { putBoolean("tts_enabled", enabled) }

    fun getTtsSubBitsOnly(): Boolean = prefs.getBoolean("tts_sub_bits_only", false)
    fun setTtsSubBitsOnly(enabled: Boolean) = prefs.edit { putBoolean("tts_sub_bits_only", enabled) }

    fun getTtsIgnoreSender(): Boolean = prefs.getBoolean("tts_ignore_sender", false)
    fun setTtsIgnoreSender(ignore: Boolean) = prefs.edit { putBoolean("tts_ignore_sender", ignore) }

    fun getTtsIgnoreLinks(): Boolean = prefs.getBoolean("tts_ignore_links", true)
    fun setTtsIgnoreLinks(ignore: Boolean) = prefs.edit { putBoolean("tts_ignore_links", ignore) }

    fun getTtsIgnoreEmotes(): Boolean = prefs.getBoolean("tts_ignore_emotes", true)
    fun setTtsIgnoreEmotes(ignore: Boolean) = prefs.edit { putBoolean("tts_ignore_emotes", ignore) }

    fun getTtsLanguage(): String = prefs.getString("tts_language", "zh-TW") ?: "zh-TW"
    fun setTtsLanguage(lang: String) = prefs.edit { putString("tts_language", lang) }

    fun exportAllToJson(): String {
        return gson.toJson(prefs.all)
    }

    fun importFromJson(json: String): Boolean {
        return try {
            @Suppress("UNCHECKED_CAST")
            val map = gson.fromJson(json, Map::class.java) as Map<String, *>
            prefs.edit {
                clear()
                map.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Float -> putFloat(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Double -> putFloat(key, value.toFloat()) // GSON might parse numbers as Double
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** One-time migration from the old single-profile storage. */
    fun migrateFromLegacy(context: Context) {
        if (getProfiles().isNotEmpty()) return
        val old = context.getSharedPreferences("obs_profile", Context.MODE_PRIVATE)
        val host = old.getString("host", "") ?: ""
        if (host.isNotEmpty()) {
            saveProfile(
                OBSProfile(
                    name = "Default",
                    host = host,
                    port = old.getInt("port", DEFAULT_OBS_PORT),
                    password = old.getString("password", "") ?: "",
                )
            )
        }
    }
}
