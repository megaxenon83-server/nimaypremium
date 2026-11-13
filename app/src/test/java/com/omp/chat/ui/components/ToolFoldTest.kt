package com.omp.chat.ui.components

import com.omp.chat.data.ChatMessage
import com.omp.chat.data.ToolCallInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A denied tool call used to render twice: once as the card from the assistant
 * entry, once as a result card that reported a different state.
 */
class ToolFoldTest {

    private fun assistantWithTool(id: String, name: String) = ChatMessage(
        id = "a1",
        role = "assistant",
        text = "",
        toolCalls = listOf(ToolCallInfo(id = id, name = name, state = "done"))
    )

    private fun toolResult(id: String, text: String, isError: Boolean) = ChatMessage(
        id = "t1",
        role = "tool",
        text = text,
        toolCallId = id,
        toolName = "write",
        isError = isError
    )

    @Test
    fun `denied tool result folds into the existing card and marks it failed`() {
        val messages = listOf(
            ChatMessage(id = "u1", role = "user", text = "lag fil"),
            assistantWithTool("call-1", "write"),
            toolResult("call-1", "Tool call denied by user: write", isError = true)
        )

        val folded = foldToolResults(messages)

        assertEquals(2, folded.size)
        assertFalse(folded.any { it.role == "tool" })
        val card = folded[1].toolCalls.single()
        assertEquals("error", card.state)
        assertTrue(card.isError)
        assertTrue(card.result.contains("denied"))
    }

    @Test
    fun `successful tool result keeps the card in done state`() {
        val folded = foldToolResults(
            listOf(
                assistantWithTool("call-2", "bash"),
                toolResult("call-2", "hei", isError = false)
            )
        )

        assertEquals(1, folded.size)
        val card = folded[0].toolCalls.single()
        assertEquals("done", card.state)
        assertEquals("hei", card.result)
    }

    @Test
    fun `orphan tool result is kept instead of being dropped`() {
        val folded = foldToolResults(listOf(toolResult("missing", "no anchor", isError = false)))

        assertEquals(1, folded.size)
        assertEquals("tool", folded[0].role)
    }

    @Test
    fun `messages without tool results are returned untouched`() {
        val messages = listOf(ChatMessage(id = "u1", role = "user", text = "hei"))
        assertEquals(messages, foldToolResults(messages))
    }
}
