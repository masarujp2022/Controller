package com.kongjjj.obscontroller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KickChatClientTest {

    @Test
    fun testParseKickEmoteInMessage() {
        val message = "Hello [emote:37226:KEKW] world"
        val segments = parseMessageSegments(
            message = message,
            emotesTag = null,
            thirdPartyEmotes = emptyMap(),
            youtubeEmotes = emptyMap(),
            bits = 0
        )

        assertEquals(3, segments.size)
        assertTrue(segments[0] is MessageSegment.TextPart && (segments[0] as MessageSegment.TextPart).text == "Hello ")
        assertTrue(segments[1] is MessageSegment.EmotePart && (segments[1] as MessageSegment.EmotePart).name == "KEKW")
        assertEquals("https://files.kick.com/emotes/37226/fullsize", (segments[1] as MessageSegment.EmotePart).url)
        assertTrue(segments[2] is MessageSegment.TextPart && (segments[2] as MessageSegment.TextPart).text == " world")
    }

    @Test
    fun testParseKickBadgeTag() {
        val message = ChatMessage(
            id = "kick_123",
            username = "KickStreamer",
            message = "Welcome to Kick!",
            badgeTags = listOf("kick-broadcaster", "kick-subscriber"),
            platform = "kick"
        )

        assertEquals("kick", message.platform)
        assertEquals(2, message.badgeTags.size)
        assertEquals("kick-broadcaster", message.badgeTags[0])
        assertEquals("kick-subscriber", message.badgeTags[1])
    }
}
