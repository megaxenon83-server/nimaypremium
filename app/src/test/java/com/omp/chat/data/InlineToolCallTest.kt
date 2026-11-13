package com.omp.chat.data

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Local models sometimes print a tool call as text. Those messages must become a
 * tool card so the chat never shows a wall of raw JSON.
 */
class InlineToolCallTest {

    private fun assistant(text: String): ChatMessage {
        val json = JsonParser.parseString(
            """{"id":"m1","role":"assistant","text":${escape(text)},"timestamp":1}"""
        ).asJsonObject
        return Parsers.message(json)
    }

    private fun escape(value: String): String {
        val out = StringBuilder("\"")
        for (ch in value) {
            when (ch) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> out.append(ch)
            }
        }
        return out.append('"').toString()
    }

    @Test
    fun `fenced json tool call becomes an unparsed tool card`() {
        val message = assistant(
            "```json\n{\"name\":\"write\",\"arguments\":{\"path\":\"deny.txt\",\"content\":\"nope\"}}\n```"
        )

        assertEquals("", message.text)
        assertEquals(1, message.toolCalls.size)
        val call = message.toolCalls.first()
        assertEquals("write", call.name)
        assertEquals("unparsed", call.state)
        assertTrue(call.args.contains("deny.txt"))
    }

    @Test
    fun `bare json tool call still converts`() {
        val message = assistant(
            """{"name":"bash","arguments":{"command":"echo hei"}}"""
        )

        assertEquals("", message.text)
        assertEquals("bash", message.toolCalls.first().name)
    }

    @Test
    fun `normal prose and code are left alone`() {
        val prose = assistant("Her er et eksempel:\n\n```kotlin\nval x = 1\n```")
        assertEquals(0, prose.toolCalls.size)
        assertTrue(prose.text.contains("val x = 1"))

        val fencedNotTool = assistant("```json\n{\"hello\":\"world\"}\n```")
        assertEquals(0, fencedNotTool.toolCalls.size)
        assertTrue(fencedNotTool.text.contains("hello"))
    }

    @Test
    fun `user messages are never rewritten`() {
        val json = JsonParser.parseString(
            """{"id":"u1","role":"user","text":"{\"name\":\"write\",\"arguments\":{}}","timestamp":1}"""
        ).asJsonObject
        val message = Parsers.message(json)
        assertEquals(0, message.toolCalls.size)
        assertTrue(message.text.contains("write"))
    }
}
