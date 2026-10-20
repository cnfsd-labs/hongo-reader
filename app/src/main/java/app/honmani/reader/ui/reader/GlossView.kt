package app.honmani.reader.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.honmani.reader.lookup.GlossItem
import app.honmani.reader.lookup.GlossLine
import app.honmani.reader.lookup.GlossStyle

/**
 * A dictionary's definitions as laid out by [app.honmani.reader.lookup.GlossLayout]: meanings
 * with their labels, example sentences with furigana and the word marked, notes and references.
 * A tap on a referenced word looks it up ([onLookup]).
 */
@Composable
fun GlossLines(lines: List<GlossLine>, onLookup: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { line -> GlossLineRow(line.tagsLast(), onLookup) }
    }
}

@Composable
private fun GlossLineRow(line: GlossLine, onLookup: (String) -> Unit) {
    val style = line.style.textStyle()
    val color = line.style.color()
    val top = if (line.style == GlossStyle.EXAMPLE) 2.dp else 0.dp
    Row(modifier = Modifier.padding(start = INDENT * maxOf(line.depth - 1, 0), top = top)) {
        line.marker?.let {
            Text(text = it, style = style, color = color, modifier = Modifier.widthIn(min = MARKER_WIDTH))
        }
        // Examples and notes sit under the meaning they belong to.
        val inset = if (line.marker == null && line.depth > 0) MARKER_WIDTH else 0.dp
        if (line.hasRuby) {
            RubyFlow(line.items, style, color, onLookup, Modifier.padding(start = inset))
        } else {
            Text(
                text = annotated(line.items, onLookup),
                style = style,
                color = color,
                modifier = Modifier.padding(start = inset),
            )
        }
    }
}

/**
 * Text without furigana: one wrapping text, labels as grey chips (set apart from the meaning
 * and from the accent-coloured references), references as links.
 */
@Composable
private fun annotated(items: List<GlossItem>, onLookup: (String) -> Unit): AnnotatedString {
    val tag = SpanStyle(
        background = MaterialTheme.colorScheme.surfaceContainerHighest,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 0.8.em,
    )
    val keyword = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    val link = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary))
    return buildAnnotatedString {
        items.forEachIndexed { i, item ->
            when (item) {
                is GlossItem.Tag -> {
                    // A label after text is set off by a space.
                    if (i > 0 && items[i - 1] !is GlossItem.Tag) append(' ')
                    withStyle(tag) { append(" ${item.text} ") }
                    if (items.getOrNull(i + 1) != null) append(' ')
                }
                is GlossItem.Text -> {
                    val styled: AnnotatedString.Builder.() -> Unit = {
                        when {
                            item.keyword -> withStyle(keyword) { append(item.text) }
                            item.bold -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(item.text) }
                            else -> append(item.text)
                        }
                    }
                    val query = item.query
                    if (query != null) {
                        withLink(LinkAnnotation.Clickable(query, link) { onLookup(query) }) { styled() }
                    } else {
                        styled()
                    }
                }
                is GlossItem.Ruby -> append(item.base) // only in lines without furigana, so not reached
            }
        }
    }
}

/**
 * Text with furigana: each kanji group carries its reading above it, and the text between
 * wraps by character, as Japanese does.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RubyFlow(
    items: List<GlossItem>,
    style: TextStyle,
    color: Color,
    onLookup: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keywordColor = MaterialTheme.colorScheme.primary
    val rubyStyle = style.copy(fontSize = (style.fontSize.value * RUBY_SCALE).sp, lineHeight = (style.fontSize.value * RUBY_SCALE * 1.1f).sp)
    FlowRow(modifier = modifier, itemVerticalAlignment = Alignment.Bottom) {
        items.forEach { item ->
            val query = (item as? GlossItem.Text)?.query ?: (item as? GlossItem.Ruby)?.query
            val clickable = if (query != null) Modifier.clickable { onLookup(query) } else Modifier
            when (item) {
                is GlossItem.Ruby -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = clickable) {
                    Text(item.reading, style = rubyStyle, color = color, maxLines = 1, softWrap = false)
                    Text(
                        item.base,
                        style = style,
                        color = if (item.keyword || query != null) keywordColor else color,
                        fontWeight = if (item.keyword) FontWeight.Bold else null,
                    )
                }
                is GlossItem.Text -> tokens(item.text).forEach { token ->
                    Column(modifier = clickable) {
                        // An empty ruby line keeps every token on the same baseline.
                        Text("", style = rubyStyle)
                        Text(
                            token,
                            style = style,
                            color = if (item.keyword || query != null) keywordColor else color,
                            fontWeight = if (item.keyword) FontWeight.Bold else if (item.bold) FontWeight.SemiBold else null,
                        )
                    }
                }
                is GlossItem.Tag -> Column {
                    Text("", style = rubyStyle)
                    Text(
                        item.text,
                        style = style.copy(fontSize = (style.fontSize.value * 0.8f).sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Japanese wraps between any two characters; Latin text between words. */
private fun tokens(text: String): List<String> {
    val tokens = mutableListOf<String>()
    val word = StringBuilder()
    for (char in text) {
        if (char.code < 0x3000) {
            word.append(char)
            if (char == ' ') {
                tokens += word.toString()
                word.clear()
            }
        } else {
            if (word.isNotEmpty()) {
                tokens += word.toString()
                word.clear()
            }
            tokens += char.toString()
        }
    }
    if (word.isNotEmpty()) tokens += word.toString()
    return tokens
}

@Composable
private fun GlossStyle.textStyle(): TextStyle = when (this) {
    GlossStyle.GLOSS, GlossStyle.EXAMPLE -> MaterialTheme.typography.bodyMedium
    GlossStyle.TRANSLATION, GlossStyle.NOTE -> MaterialTheme.typography.bodySmall
}

@Composable
private fun GlossStyle.color(): Color = when (this) {
    GlossStyle.GLOSS, GlossStyle.EXAMPLE -> MaterialTheme.colorScheme.onSurface
    GlossStyle.TRANSLATION, GlossStyle.NOTE -> MaterialTheme.colorScheme.onSurfaceVariant
}

private val INDENT = 12.dp
private val MARKER_WIDTH = 20.dp
private const val RUBY_SCALE = 0.55f
