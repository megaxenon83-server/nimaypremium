package com.omp.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omp.chat.ui.theme.MonoStyle
import com.omp.chat.ui.theme.NimayAccent
import com.omp.chat.ui.theme.NimayDivider
import com.omp.chat.ui.theme.NimayInput
import com.omp.chat.ui.theme.NimaySidebar
import com.omp.chat.ui.theme.NimayTextPrimary
import com.omp.chat.ui.theme.NimayTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Block model + parser
// ---------------------------------------------------------------------------

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullets(val items: List<String>, val ordered: Boolean) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val lang: String, val code: String) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
    data object Divider : MdBlock
}

object MarkdownParser {

    private val bullet = Regex("^\\s*[-*+]\\s+(.*)$")
    private val ordered = Regex("^\\s*(\\d+)[.)]\\s+(.*)$")
    private val heading = Regex("^(#{1,6})\\s+(.*)$")
    private val fence = Regex("^\\s*```\\s*([\\w+#.-]*)\\s*$")

    fun parse(source: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val lines = source.replace("\r\n", "\n").split("\n")
        var i = 0
        val paragraph = StringBuilder()

        fun flushParagraph() {
            if (paragraph.isNotBlank()) {
                blocks += MdBlock.Paragraph(paragraph.toString().trim())
                paragraph.setLength(0)
            }
        }

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            val fenceMatch = fence.find(trimmed)
            if (fenceMatch != null) {
                flushParagraph()
                val lang = fenceMatch.groupValues[1]
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    code.append(lines[i]).append('\n')
                    i++
                }
                i++
                blocks += MdBlock.Code(lang, code.toString().trimEnd('\n'))
                continue
            }

            if (trimmed.isEmpty()) {
                flushParagraph()
                i++
                continue
            }

            if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                flushParagraph()
                blocks += MdBlock.Divider
                i++
                continue
            }

            val headingMatch = heading.find(trimmed)
            if (headingMatch != null) {
                flushParagraph()
                blocks += MdBlock.Heading(headingMatch.groupValues[1].length, headingMatch.groupValues[2].trim())
                i++
                continue
            }

            if (trimmed.startsWith(">")) {
                flushParagraph()
                val quote = StringBuilder()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quote.append(lines[i].trim().removePrefix(">").trim()).append('\n')
                    i++
                }
                blocks += MdBlock.Quote(quote.toString().trim())
                continue
            }

            if (trimmed.contains('|') && i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
                flushParagraph()
                val header = splitRow(trimmed)
                i += 2
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
                    rows += splitRow(lines[i].trim())
                    i++
                }
                blocks += MdBlock.Table(header, rows)
                continue
            }

            val isBullet = bullet.matches(line)
            val isOrdered = ordered.matches(line)
            if (isBullet || isOrdered) {
                flushParagraph()
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val current = lines[i]
                    val b = bullet.find(current)
                    val o = ordered.find(current)
                    when {
                        b != null -> items += b.groupValues[1].trim()
                        o != null -> items += "${o.groupValues[1]}. ${o.groupValues[2].trim()}"
                        current.isBlank() -> break
                        current.startsWith("  ") && items.isNotEmpty() ->
                            items[items.size - 1] = items.last() + " " + current.trim()
                        else -> break
                    }
                    i++
                }
                blocks += MdBlock.Bullets(items, isOrdered)
                continue
            }

            paragraph.append(line.trim()).append('\n')
            i++
        }
        flushParagraph()
        return blocks.map { block ->
            if (block is MdBlock.Bullets && block.ordered) {
                MdBlock.Bullets(block.items.map { it.replaceFirst(Regex("^\\d+\\.\\s*"), "") }, true)
            } else block
        }
    }

    private fun isTableSeparator(line: String): Boolean {
        val trimmed = line.trim()
        if (!trimmed.contains('-')) return false
        return trimmed.matches(Regex("^\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?$"))
    }

    private fun splitRow(line: String): List<String> =
        line.trim().trim('|').split('|').map { it.trim() }

    // -----------------------------------------------------------------------
    // Inline spans
    // -----------------------------------------------------------------------

    fun inline(text: String, codeBackground: Color, accent: Color): AnnotatedString = buildAnnotatedString {
        var i = 0
        val buffer = StringBuilder()

        fun flush() {
            if (buffer.isNotEmpty()) {
                append(buffer.toString())
                buffer.setLength(0)
            }
        }

        while (i < text.length) {
            when {
                text.startsWith("**", i) || text.startsWith("__", i) -> {
                    val marker = text.substring(i, i + 2)
                    val end = text.indexOf(marker, i + 2)
                    if (end > i) {
                        flush()
                        pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                        append(text.substring(i + 2, end))
                        pop()
                        i = end + 2
                    } else {
                        buffer.append(text[i]); i++
                    }
                }
                text.startsWith("~~", i) -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end > i) {
                        flush()
                        pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                        append(text.substring(i + 2, end))
                        pop()
                        i = end + 2
                    } else {
                        buffer.append(text[i]); i++
                    }
                }
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        flush()
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                background = codeBackground,
                                color = accent
                            )
                        )
                        append(" " + text.substring(i + 1, end) + " ")
                        pop()
                        i = end + 1
                    } else {
                        buffer.append(text[i]); i++
                    }
                }
                text.startsWith("*", i) || text.startsWith("_", i) -> {
                    val marker = text[i]
                    val end = text.indexOf(marker, i + 1)
                    if (end > i + 1) {
                        flush()
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(text.substring(i + 1, end))
                        pop()
                        i = end + 1
                    } else {
                        buffer.append(text[i]); i++
                    }
                }
                text.startsWith("[", i) -> {
                    val close = text.indexOf(']', i)
                    val open = if (close > 0) text.indexOf('(', close) else -1
                    val end = if (open == close + 1) text.indexOf(')', open) else -1
                    if (close > 0 && end > 0) {
                        flush()
                        pushStyle(
                            SpanStyle(
                                color = accent,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        append(text.substring(i + 1, close))
                        pop()
                        i = end + 1
                    } else {
                        buffer.append(text[i]); i++
                    }
                }
                else -> {
                    buffer.append(text[i]); i++
                }
            }
        }
        flush()
    }

    // -----------------------------------------------------------------------
    // Code highlighting
    // -----------------------------------------------------------------------

    private val keywords = setOf(
        "fun", "val", "var", "class", "object", "interface", "data", "enum", "sealed", "override",
        "private", "public", "internal", "protected", "if", "else", "when", "for", "while", "do",
        "return", "break", "continue", "import", "package", "typealias", "is", "as", "in", "out",
        "try", "catch", "finally", "throw", "this", "super", "null", "true", "false", "suspend",
        "async", "await", "yield", "const", "let", "function", "def", "lambda", "new", "delete",
        "export", "default", "from", "of", "typeof", "instanceof", "void", "int", "long", "float",
        "double", "char", "bool", "boolean", "struct", "union", "static", "final", "abstract",
        "extends", "implements", "throws", "switch", "case", "with", "match", "elif", "except",
        "raise", "pass", "lambda", "global", "nonlocal", "assert", "None", "True", "False",
        "and", "or", "not", "not", "println", "printf", "echo", "then", "fi", "esac", "done",
        "require", "module", "namespace", "using", "template", "typename", "auto", "foreach"
    )

    private val tokenRegex = Regex(
        "(\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|`[^`]*`|//[^\\n]*|#[^\\n]*|\\b\\d+(?:\\.\\d+)?\\b|\\b(?:" +
            keywords.joinToString("|") + ")\\b)"
    )

    fun highlight(
        code: String,
        lang: String,
        plain: Color,
        keyword: Color,
        string: Color,
        comment: Color,
        number: Color
    ): AnnotatedString = buildAnnotatedString {
        var cursor = 0
        for (match in tokenRegex.findAll(code)) {
            if (match.range.first > cursor) {
                withStyle(SpanStyle(color = plain)) {
                    append(code.substring(cursor, match.range.first))
                }
            }
            val token = match.value
            val color = when {
                token.startsWith("//") || token.startsWith("#") -> comment
                token.startsWith("\"") || token.startsWith("'") || token.startsWith("`") -> string
                token[0].isDigit() -> number
                else -> keyword
            }
            withStyle(SpanStyle(color = color, fontWeight = if (color == keyword) FontWeight.Medium else null)) {
                append(token)
            }
            cursor = match.range.last + 1
        }
        if (cursor < code.length) {
            withStyle(SpanStyle(color = plain)) { append(code.substring(cursor)) }
        }
    }
}

// ---------------------------------------------------------------------------
// Compose renderer
// ---------------------------------------------------------------------------

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val blocks = remember(text) { MarkdownParser.parse(text) }
    val scheme = MaterialTheme.colorScheme
    val bodyStyle = if (compact) MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp) else MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    text = MarkdownParser.inline(block.text, NimaySidebar, NimayAccent),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, lineHeight = 32.sp)
                        2 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, lineHeight = 28.sp)
                        else -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
                    },
                    color = NimayTextPrimary,
                    modifier = Modifier.padding(top = if (block.level == 1) 8.dp else 4.dp)
                )

                is MdBlock.Paragraph -> Text(
                    text = MarkdownParser.inline(block.text, NimaySidebar, NimayAccent),
                    style = bodyStyle.copy(lineHeight = if (compact) 22.sp else 26.sp),
                    color = NimayTextPrimary
                )

                is MdBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    block.items.forEachIndexed { index, item ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = if (block.ordered) "${index + 1}." else "â€¢",
                                style = bodyStyle,
                                color = NimayTextSecondary,
                                modifier = Modifier.width(if (block.ordered) 28.dp else 20.dp)
                            )
                            Text(
                                text = MarkdownParser.inline(item, NimaySidebar, NimayAccent),
                                style = bodyStyle,
                                color = NimayTextPrimary,
                                modifier = Modifier.weight(1f),
                                lineHeight = 26.sp
                            )
                        }
                    }
                }

                is MdBlock.Quote -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(NimayAccent)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = MarkdownParser.inline(block.text, NimaySidebar, NimayAccent),
                        style = bodyStyle,
                        color = NimayTextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }

                is MdBlock.Code -> CodeBlock(block.lang, block.code)

                is MdBlock.Table -> MarkdownTable(block)

                MdBlock.Divider -> HorizontalDivider(color = NimayDivider)
            }
        }
    }
}

@Composable
private fun CodeBlock(lang: String, code: String) {
    val scheme = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    Surface(
        color = NimaySidebar,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NimayDivider, RoundedCornerShape(10.dp))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NimayInput)
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = lang.ifBlank { "kode" },
                    style = MaterialTheme.typography.labelSmall,
                    color = NimayTextSecondary
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                        scope.launch {
                            delay(1600)
                            copied = false
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else OmpIcons.Copy,
                        contentDescription = "Kopier",
                        tint = if (copied) NimayAccent else NimayTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            SelectionContainer {
                Text(
                    text = MarkdownParser.highlight(
                        code = code,
                        lang = lang,
                        plain = NimayTextPrimary,
                        keyword = NimayAccent,
                        string = scheme.onSurfaceVariant,
                        comment = NimayTextSecondary,
                        number = scheme.onSurfaceVariant
                    ),
                    style = MonoStyle,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun MarkdownTable(table: MdBlock.Table) {
    val scrollState = rememberScrollState()

    Surface(
        color = NimaySidebar,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NimayDivider, RoundedCornerShape(10.dp))
    ) {
        Column(Modifier.horizontalScroll(scrollState).padding(vertical = 4.dp)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                table.header.forEachIndexed { index, cell ->
                    Text(
                        text = MarkdownParser.inline(cell, NimaySidebar, NimayAccent),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NimayTextPrimary,
                        modifier = Modifier.widthIn(min = 80.dp, max = 200.dp).padding(end = 12.dp)
                    )
                }
            }
            HorizontalDivider(color = NimayDivider)
            table.rows.forEachIndexed { rowIndex, row ->
                Row(
                    Modifier
                        .background(if (rowIndex % 2 == 0) NimaySidebar else NimayInput)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    row.forEachIndexed { cellIndex, cell ->
                        Text(
                            text = MarkdownParser.inline(cell, NimaySidebar, NimayAccent),
                            style = MaterialTheme.typography.bodySmall,
                            color = NimayTextPrimary,
                            modifier = Modifier.widthIn(min = 80.dp, max = 200.dp).padding(end = 12.dp)
                        )
                    }
                }
            }
        }
    }
}
