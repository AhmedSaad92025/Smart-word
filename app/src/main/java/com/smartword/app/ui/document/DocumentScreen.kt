package com.smartword.app.ui.document

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartword.app.OpenDocument
import com.smartword.app.R
import com.smartword.app.data.FontKind
import com.smartword.app.data.FontMapping
import com.smartword.app.data.HexColor
import com.smartword.app.preview.PreviewBlock
import com.smartword.app.preview.PreviewParagraph
import com.smartword.app.preview.PreviewPlaceholder
import com.smartword.app.preview.PreviewSpan
import com.smartword.docx.model.Alignment as DocAlignment

private val DeskColor = Color(0xFFE3E6EC)
private val PaperColor = Color.White
private val InkColor = Color(0xFF111111)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(document: OpenDocument, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(document.name, maxLines = 1) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(
                            stringResource(R.string.action_back),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DeskColor),
        ) {
            Text(
                text = stringResource(R.string.document_read_only),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF444444),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            PageView(document.blocks, modifier = Modifier.weight(1f))
        }
    }
}

/** The document drawn like a sheet of paper: white page, black text, formatting as in the file. */
@Composable
fun PageView(blocks: List<PreviewBlock>, modifier: Modifier = Modifier) {
    val hasContent = blocks.any { it !is PreviewParagraph || !it.isEmpty }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        itemsIndexed(blocks) { index, block ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PaperColor)
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = if (index == 0) 24.dp else 0.dp,
                        bottom = if (index == blocks.lastIndex) 48.dp else 0.dp,
                    ),
            ) {
                when (block) {
                    is PreviewParagraph -> ParagraphView(block)
                    is PreviewPlaceholder -> PlaceholderView(block)
                }
            }
        }
        if (!hasContent) {
            item {
                Text(
                    text = stringResource(R.string.document_empty),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PaperColor)
                        .padding(20.dp),
                    color = Color(0xFF777777),
                )
            }
        }
    }
}

@Composable
private fun ParagraphView(p: PreviewParagraph) {
    if (p.isEmpty && p.listLabel == null) {
        Spacer(Modifier.height((p.spaceBeforePt + p.spaceAfterPt + 16f).dp))
        return
    }
    val text = remember(p) { buildText(p) }
    val direction = if (p.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    // Indents are "start" and "end" of the paragraph, so the paragraph's own direction decides the side.
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Text(
            text = text,
            style = TextStyle(
                color = InkColor,
                fontSize = 11.sp,
                textAlign = when (p.alignment) {
                    DocAlignment.LEFT -> TextAlign.Left
                    DocAlignment.RIGHT -> TextAlign.Right
                    DocAlignment.CENTER -> TextAlign.Center
                    DocAlignment.JUSTIFY -> TextAlign.Justify
                },
                textDirection = if (p.rtl) TextDirection.Rtl else TextDirection.Ltr,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = p.indentStartPt.coerceAtLeast(0f).dp,
                    end = p.indentEndPt.coerceAtLeast(0f).dp,
                    top = p.spaceBeforePt.coerceAtLeast(0f).dp,
                    bottom = p.spaceAfterPt.coerceAtLeast(0f).dp,
                ),
        )
    }
}

private fun buildText(p: PreviewParagraph): AnnotatedString = buildAnnotatedString {
    val label = p.listLabel
    if (label != null) {
        val first = p.spans.firstOrNull()
        withStyle(spanStyle(first)) { append("$label  ") }
    }
    for (span in p.spans) {
        withStyle(spanStyle(span)) { append(span.text) }
    }
}

private fun spanStyle(span: PreviewSpan?): SpanStyle {
    if (span == null) return SpanStyle()
    val argb = HexColor.toArgb(span.colorHex)
    return SpanStyle(
        fontWeight = if (span.bold) FontWeight.Bold else null,
        fontStyle = if (span.italic) FontStyle.Italic else null,
        textDecoration = if (span.underline) TextDecoration.Underline else null,
        fontSize = span.sizePt.sp,
        color = if (argb != null) Color(argb) else Color.Unspecified,
        fontFamily = when (FontMapping.kindOf(span.fontName)) {
            FontKind.SANS -> FontFamily.SansSerif
            FontKind.SERIF -> FontFamily.Serif
            FontKind.MONO -> FontFamily.Monospace
            FontKind.DEFAULT -> null
        },
    )
}

@Composable
private fun PlaceholderView(block: PreviewPlaceholder) {
    Text(
        text = stringResource(R.string.placeholder_content, block.text.ifBlank { "…" }),
        style = MaterialTheme.typography.bodySmall,
        fontStyle = FontStyle.Italic,
        color = Color(0xFF666666),
        modifier = Modifier.padding(vertical = 6.dp),
    )
}
