package com.omp.chat.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `headings paragraphs and lists are split into blocks`() {
        val blocks = MarkdownParser.parse(
            """
            # Overskrift

            En tekst med æ, ø og å.

            - første
            - andre

            1. en
            2. to
            """.trimIndent()
        )

        assertEquals(4, blocks.size)
        assertEquals(MdBlock.Heading(1, "Overskrift"), blocks[0])
        assertTrue(blocks[1] is MdBlock.Paragraph)
        assertEquals(
            MdBlock.Bullets(listOf("første", "andre"), ordered = false),
            blocks[2]
        )
        assertEquals(
            MdBlock.Bullets(listOf("en", "to"), ordered = true),
            blocks[3]
        )
    }

    @Test
    fun `fenced code keeps language and content untouched`() {
        val blocks = MarkdownParser.parse(
            "```kotlin\nfun hilsen() = \"hei på deg\"\n\nprintln(hilsen())\n```"
        )

        assertEquals(1, blocks.size)
        val code = blocks[0] as MdBlock.Code
        assertEquals("kotlin", code.lang)
        assertEquals("fun hilsen() = \"hei på deg\"\n\nprintln(hilsen())", code.code)
    }

    @Test
    fun `tables quotes and dividers parse`() {
        val blocks = MarkdownParser.parse(
            """
            | Navn | Verdi |
            | --- | --- |
            | alfa | 1 |
            | beta | 2 |

            > et sitat
            > med to linjer

            ---
            """.trimIndent()
        )

        assertEquals(3, blocks.size)
        val table = blocks[0] as MdBlock.Table
        assertEquals(listOf("Navn", "Verdi"), table.header)
        assertEquals(listOf(listOf("alfa", "1"), listOf("beta", "2")), table.rows)
        assertEquals(MdBlock.Quote("et sitat\nmed to linjer"), blocks[1])
        assertEquals(MdBlock.Divider, blocks[2])
    }

    @Test
    fun `unclosed fence does not lose trailing text`() {
        val blocks = MarkdownParser.parse("```\nuendelig kode\nfortsatt kode")

        assertEquals(1, blocks.size)
        val code = blocks[0] as MdBlock.Code
        assertTrue(code.code.contains("uendelig kode"))
        assertTrue(code.code.contains("fortsatt kode"))
    }
}
