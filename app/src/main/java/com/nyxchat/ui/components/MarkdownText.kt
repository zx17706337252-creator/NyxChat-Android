package com.nyxchat.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nyxchat.ui.theme.CrimsonProFamily
import com.nyxchat.ui.theme.LocalNyxColors

// Renders a subset of Markdown inline with Compose without external libraries
// Supported: **bold**, *italic*, `code`, ~~strikethrough~~, # headers, ---, line breaks

@Composable
fun MarkdownText(
    text: String,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = 15.5.sp,
    lineHeight: TextUnit = 25.sp,
    fontStyle: FontStyle = FontStyle.Normal,
    modifier: Modifier = Modifier
) {
    val nyx = LocalNyxColors.current
    val resolvedColor = if (color == Color.Unspecified) nyx.TextPrimary else color
    val blocks = parseMarkdownBlocks(text)

    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Divider -> HorizontalDivider(
                    color = nyx.BorderSubtle,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
                is MarkdownBlock.Header -> Text(
                    text = buildInlineAnnotated(block.content, resolvedColor, nyx.Layer2, nyx.AccentSoft),
                    fontSize = when (block.level) { 1 -> 20.sp; 2 -> 17.sp; else -> 15.sp },
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = CrimsonProFamily,
                    color = resolvedColor,
                    lineHeight = lineHeight,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                )
                is MarkdownBlock.Paragraph -> Text(
                    text       = buildInlineAnnotated(block.content, resolvedColor, nyx.Layer2, nyx.AccentSoft),
                    fontSize   = fontSize,
                    fontFamily = CrimsonProFamily,
                    fontStyle  = fontStyle,
                    color      = resolvedColor,
                    lineHeight = lineHeight,
                    modifier   = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ─── Block-level parsing ──────────────────────────────────────────────────────

sealed class MarkdownBlock {
    data class Paragraph(val content: String) : MarkdownBlock()
    data class Header(val level: Int, val content: String) : MarkdownBlock()
    object Divider : MarkdownBlock()
}

private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val lines = text.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraphBuffer = StringBuilder()

    fun flushBuffer() {
        val s = paragraphBuffer.toString().trim()
        if (s.isNotEmpty()) blocks.add(MarkdownBlock.Paragraph(s))
        paragraphBuffer.clear()
    }

    for (line in lines) {
        when {
            line.matches(Regex("^#{1,3}\\s+.*")) -> {
                flushBuffer()
                val level = line.indexOfFirst { it != '#' }
                blocks.add(MarkdownBlock.Header(level, line.drop(level).trim()))
            }
            line.trim().matches(Regex("-{3,}|\\*{3,}|_{3,}")) -> {
                flushBuffer()
                blocks.add(MarkdownBlock.Divider)
            }
            else -> {
                if (paragraphBuffer.isNotEmpty()) paragraphBuffer.append("\n")
                paragraphBuffer.append(line)
            }
        }
    }
    flushBuffer()
    return blocks
}

// ─── Inline annotation parsing (internal — also used by StyledContent) ───────

internal fun buildInlineAnnotated(
    text: String,
    baseColor: Color,
    codeBackground: Color,
    codeColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // **bold**
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else { append(text[i]); i++ }
                }
                // *italic* (not **)
                text.startsWith("*", i) && !text.startsWith("**", i) -> {
                    val end = text.indexOf("*", i + 1)
                    if (end != -1 && !text.startsWith("**", end)) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else { append(text[i]); i++ }
                }
                // ~~strikethrough~~
                text.startsWith("~~", i) -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough,
                            color = baseColor.copy(alpha = 0.5f))) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else { append(text[i]); i++ }
                }
                // `inline code`
                text.startsWith("`", i) && !text.startsWith("```", i) -> {
                    val end = text.indexOf("`", i + 1)
                    if (end != -1) {
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackground,
                            color = codeColor,
                            fontSize = 13.sp
                        )) {
                            append(" ${text.substring(i + 1, end)} ")
                        }
                        i = end + 1
                    } else { append(text[i]); i++ }
                }
                else -> { append(text[i]); i++ }
            }
        }
    }
}
