package com.kongjjj.obscontroller

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeChatClientTest {

    private val client = YouTubeChatClient()

    @Test
    fun testParseNormalTextMessage() {
        val actionJson = JSONObject().apply {
            put("addChatItemAction", JSONObject().apply {
                put("item", JSONObject().apply {
                    put("liveChatTextMessageRenderer", JSONObject().apply {
                        put("authorName", JSONObject().apply { put("simpleText", "Alice") })
                        put("message", JSONObject().apply {
                            put("runs", JSONArray().apply {
                                put(JSONObject().apply { put("text", "Hello YouTube!") })
                            })
                        })
                        put("timestampUsec", "1700000000000000")
                    })
                })
            })
        }

        val msg = client.parseChatItemAction(actionJson)
        assertNotNull(msg)
        assertEquals("Alice", msg?.username)
        assertEquals("Hello YouTube!", msg?.message)
        assertEquals("youtube", msg?.platform)
        assertEquals(MessageType.NORMAL, msg?.type)
        assertNull(msg?.systemMsg)
    }

    @Test
    fun testParseSuperChat() {
        val actionJson = JSONObject().apply {
            put("addChatItemAction", JSONObject().apply {
                put("item", JSONObject().apply {
                    put("liveChatPaidMessageRenderer", JSONObject().apply {
                        put("authorName", JSONObject().apply { put("simpleText", "Bob") })
                        put("purchaseAmountText", JSONObject().apply { put("simpleText", "NT$150") })
                        put("message", JSONObject().apply {
                            put("runs", JSONArray().apply {
                                put(JSONObject().apply { put("text", "Keep up the great stream!") })
                            })
                        })
                        put("timestampUsec", "1700000000000000")
                    })
                })
            })
        }

        val msg = client.parseChatItemAction(actionJson)
        assertNotNull(msg)
        assertEquals("Bob", msg?.username)
        assertEquals("Keep up the great stream!", msg?.message)
        assertEquals("youtube", msg?.platform)
        assertEquals(MessageType.USER_NOTICE, msg?.type)
        assertEquals("RED", msg?.announcementColor)
        assertEquals("【SuperChat NT$150】", msg?.systemMsg)
    }

    @Test
    fun testParseSuperSticker() {
        val actionJson = JSONObject().apply {
            put("addChatItemAction", JSONObject().apply {
                put("item", JSONObject().apply {
                    put("liveChatPaidStickerRenderer", JSONObject().apply {
                        put("authorName", JSONObject().apply { put("simpleText", "Charlie") })
                        put("purchaseAmountText", JSONObject().apply { put("simpleText", "$5.00") })
                        put("timestampUsec", "1700000000000000")
                    })
                })
            })
        }

        val msg = client.parseChatItemAction(actionJson)
        assertNotNull(msg)
        assertEquals("Charlie", msg?.username)
        assertEquals("[超級貼圖]", msg?.message)
        assertEquals("youtube", msg?.platform)
        assertEquals(MessageType.USER_NOTICE, msg?.type)
        assertEquals("RED", msg?.announcementColor)
        assertEquals("【SuperSticker $5.00】", msg?.systemMsg)
    }

    @Test
    fun testExtractViewerCountFromHtml() {
        val html1 = """<div>"concurrentViewers":"1,500"</div>"""
        assertEquals(1500, client.extractViewerCountFromHtml(html1))

        val html2 = """<div>"viewCount":{"runs":[{"text":"2,345"}]}</div>"""
        assertEquals(2345, client.extractViewerCountFromHtml(html2))

        val html3 = """<div>"simpleText":"3,456 位正在觀看"</div>"""
        assertEquals(3456, client.extractViewerCountFromHtml(html3))

        val html4 = """<script>var ytInitialPlayerResponse = {"videoDetails":{"isLive":true,"concurrentViewers":"8,888"}};</script>"""
        assertEquals(8888, client.extractViewerCountFromHtml(html4))
    }
}
