package com.kongjjj.obscontroller

data class ChatMessage(
    val id: String,
    val username: String,
    val login: String? = null,
    val message: String,
    val color: String? = null,                  // hex like "#FF4500" or null
    val emotesTag: String? = null,              // raw IRC emotes tag e.g. "25:0-4,6-10/1902:12-17"
    val badgeTags: List<String> = emptyList(),  // e.g. ["broadcaster/1", "subscriber/0", "premium/1"] or full URLs
    val youtubeEmotes: Map<String, String> = emptyMap(), // shortcut -> url
    val timestamp: Long? = null,
    val platform: String = "twitch",
    val type: MessageType = MessageType.NORMAL,
    val announcementColor: String? = null,
    val systemMsg: String? = null,
    val bits: Int = 0,
    val isHighlighted: Boolean = false,
    val twitchMsgId: String? = null,
    val msgParams: Map<String, String> = emptyMap(),
)

enum class MessageType {
    NORMAL,
    ANNOUNCEMENT,
    USER_NOTICE
}

fun ChatMessage.getLocalizedSystemMessage(): String {
    val raw = (systemMsg ?: "")
        .replace(" (tmi.twitch.tv)", "")
        .replace("(tmi.twitch.tv)", "")
        .trim()
    if ((platform != "twitch" || type != MessageType.USER_NOTICE)) return raw

    val tags = msgParams
    val cleanUser = username
        .replace(" (tmi.twitch.tv)", "")
        .replace("(tmi.twitch.tv)", "")
        .trim()
    val user = if (cleanUser == "AnAnonymousGifter" || cleanUser == "tmi.twitch.tv") "有匿名贈禮人" else cleanUser
    
    val months = tags["msg-param-cumulative-months"] ?: tags["msg-param-months"] ?: "1"
    
    val rawRecipient = tags["msg-param-recipient-display-name"] ?: tags["msg-param-recipient-user-name"] ?: "某人"
    val cleanRecipient = rawRecipient
        .replace(" (tmi.twitch.tv)", "")
        .replace("(tmi.twitch.tv)", "")
        .trim()
    val recipient = if (cleanRecipient == "AnAnonymousGifter" || cleanRecipient == "tmi.twitch.tv") "某人" else cleanRecipient

    val senderRaw = tags["msg-param-sender-display-name"] ?: tags["msg-param-sender-login"] ?: "某人"
    val cleanSender = senderRaw
        .replace(" (tmi.twitch.tv)", "")
        .replace("(tmi.twitch.tv)", "")
        .trim()
    val sender = if (cleanSender == "AnAnonymousGifter" || cleanSender == "tmi.twitch.tv") "有匿名贈禮人" else cleanSender
    val viewCount = tags["msg-param-viewerCount"] ?: "0"
    val ritualName = tags["msg-param-ritual-name"]
    val massGiftCount = tags["msg-param-mass-gift-count"] ?: "0"
    val milestoneCategory = tags["msg-param-category"]
    val milestoneValue = tags["msg-param-value"]

    val tierMap = mapOf("1000" to "層級 1", "2000" to "層級 2", "3000" to "層級 3", "Prime" to "Prime")
    val tier = tierMap[tags["msg-param-sub-plan"]] ?: "層級 1"

    return when (twitchMsgId) {
        "sub" -> "$user 使用 $tier 訂閱了頻道！"
        "resub" -> "$user 已使用 $tier 訂閱。這位使用者已經訂閱了 $months 個月！"
        "subgift" -> "$user 送了一份 $tier 訂閱給 $recipient！"
        "anonsubgift" -> "匿名贊助者 送了一份 $tier 訂閱給 $recipient！"
        "submysterygift" -> "$user 在頻道社群隨機贈送了 $massGiftCount 個 $tier 訂閱！"
        "giftpaidupgrade" -> "$user 延續了由 $sender 贈送的訂閱！"
        "primepaidupgrade" -> "$user 延續了由 Prime 贈送的訂閱！"
        "communitypayforward" -> "$user 正在傳遞由 $sender 贈送的禮物！"
        "standardpayforward" -> "$user 正在傳遞由 $sender 贈送的禮物給 $recipient！"
        "raid" -> "$user 正與 $viewCount 個人一起揪團中。"
        "unraid" -> "揪團已取消。"
        "ritual" -> if (ritualName == "new_chatter") "歡迎 $user 第一次在聊天室發言！" else "$user 觸發了新活動！"
        "viewermilestone" -> if (milestoneCategory == "watch-streak") "$user 達成連續觀賞紀錄！$user 目前已連續觀賞 $milestoneValue 場實況！" else raw
        "bitsbadgetier" -> "$user 獲得了新的小額贊助徽章：${tags["msg-param-threshold"]}！"
        else -> raw.ifEmpty { "Twitch 通知" }
    }
}
