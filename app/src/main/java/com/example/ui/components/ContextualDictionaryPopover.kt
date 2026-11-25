package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.local.DictionarySourceEntity
import com.example.data.model.SelectedWordContext
import com.example.data.model.WikipediaSummary
import com.example.engine.TranslationService
import com.example.ui.theme.HighlightAmber
import com.example.ui.theme.HighlightCobalt
import com.example.ui.theme.HighlightCoral
import com.example.ui.theme.HighlightMint
import com.example.ui.theme.LocalFolioPalette
import com.example.ui.theme.LocalLumaMotion
import kotlin.math.roundToInt

val HighlightSwatches = listOf(
    HighlightAmber to 0xFFF6C445L,
    HighlightCobalt to 0xFF5A9BFFL,
    HighlightMint to 0xFF28C17CL,
    HighlightCoral to 0xFFFF6B78L
)

/**
 * Dynamic floating contextual popover anchored near the selected word without obscuring it,
 * clamping safely to screen edges. Includes:
 * - Instant EN/auto -> ES translation banner
 * - Quick Highlight (4 colors), Add Note, and Copy controls
 * - Wikipedia ES native API tab + customizable {palabra} WebView tabs (RAE, WordReference, DeepL, etc.)
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ContextualDictionaryPopover(
    selection: SelectedWordContext?,
    sources: List<DictionarySourceEntity>,
    instantTranslation: String?,
    isTranslatingWord: Boolean,
    translationStatusText: String,
    wikiSummary: WikipediaSummary?,
    wikiError: String?,
    isWikiLoading: Boolean,
    onHighlight: (Long) -> Unit,
    onAddNote: () -> Unit,
    onUpdateSelection: (SelectedWordContext) -> Unit,
    onOpenDictionarySettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val motion = LocalLumaMotion.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val enabledSources = remember(sources) { sources.filter { it.isEnabled } }

    // Retain the last active selection briefly during exit so the close animation plays smoothly!
    var cachedSelection by remember { mutableStateOf(selection) }
    if (selection != null) {
        cachedSelection = selection
    }
    val activeSelection = cachedSelection ?: return
    val isVisible = selection != null

    var selectedTabIndex by remember(
        activeSelection.locationKey,
        activeSelection.blockIndex,
        activeSelection.wordStartChar
    ) {
        mutableIntStateOf(0)
    }
    val initialOcrWord = remember(
        activeSelection.locationKey,
        activeSelection.blockIndex,
        activeSelection.anchorXFraction,
        activeSelection.anchorYFraction
    ) {
        activeSelection.word
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxW = constraints.maxWidth
        val maxH = constraints.maxHeight
        val density = androidx.compose.ui.platform.LocalDensity.current

        val maxPopWidthDp = if (maxWidth > 620.dp) 440.dp else 360.dp
        val popW = with(density) { minOf(maxPopWidthDp.toPx(), maxW - 24.dp.toPx()).roundToInt() }
        val popH = with(density) { minOf(404.dp.toPx(), maxH * 0.60f).roundToInt() }
        val marginPx = with(density) { 12.dp.toPx().roundToInt() }
        val clearancePx = with(density) { 36.dp.toPx().roundToInt() }

        val anchorX = (activeSelection.anchorXFraction * maxW).roundToInt()
        val anchorY = (activeSelection.anchorYFraction * maxH).roundToInt()

        val targetX = (anchorX - popW / 2).coerceIn(marginPx, (maxW - popW - marginPx).coerceAtLeast(marginPx))

        val placeBelow = activeSelection.anchorYFraction < 0.46f
        val rawY = if (placeBelow) {
            anchorY + clearancePx
        } else {
            anchorY - popH - clearancePx
        }
        val targetY = rawY.coerceIn(marginPx + 40, (maxH - popH - marginPx - 24).coerceAtLeast(marginPx))

        val animatedOffset by animateIntOffsetAsState(
            targetValue = IntOffset(targetX, targetY),
            animationSpec = motion.modalSpring(),
            label = "dictionary_popover_offset"
        )

        val pivotX = ((anchorX - targetX).toFloat() / popW.coerceAtLeast(1)).coerceIn(0.12f, 0.88f)
        val pivotY = if (placeBelow) 0.04f else 0.96f
        val glidePx = with(density) { 14.dp.toPx().roundToInt() }

        // Soft fading backdrop scrim — only intercepts taps while visible
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = motion.tweenSpec(215)),
            exit = fadeOut(animationSpec = motion.exitTween(170))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.10f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = motion.emphasizedTween(210)) +
                scaleIn(
                    initialScale = 0.86f,
                    transformOrigin = TransformOrigin(pivotX, pivotY),
                    animationSpec = motion.modalSpring()
                ) +
                slideInVertically(
                    initialOffsetY = { if (placeBelow) -glidePx else glidePx },
                    animationSpec = motion.modalSpring()
                ),
            exit = fadeOut(animationSpec = motion.exitTween(165)) +
                scaleOut(
                    targetScale = 0.90f,
                    transformOrigin = TransformOrigin(pivotX, pivotY),
                    animationSpec = motion.exitTween(175)
                ) +
                slideOutVertically(
                    targetOffsetY = { if (placeBelow) -glidePx / 2 else glidePx / 2 },
                    animationSpec = motion.exitTween(175)
                ),
            modifier = Modifier
                .offset { animatedOffset }
                .size(
                    width = with(density) { popW.toDp() },
                    height = with(density) { popH.toDp() }
                )
        ) {
            val shape = RoundedCornerShape(26.dp)
            val selection = activeSelection
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(22.dp, shape, ambientColor = Color.Black.copy(alpha = 0.26f))
                    .clip(shape)
                    .background(palette.surface.copy(alpha = 0.99f))
                    .border(1.2.dp, palette.border, shape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .animateContentSize(
                        animationSpec = motion.modalSpring()
                    )
                    .padding(12.dp)
                    .testTag("contextual_dictionary_popover")
            ) {
                // Header: Selected word/phrase + Highlight Swatches + Note + Copy + Discreet Settings + Close
                val isMultiWord = selection.word.trim().contains(' ')
                val headerLabel = if (isMultiWord) {
                    selection.word.trim()
                } else {
                    TranslationService.cleanWord(selection.word).ifEmpty { selection.word.trim() }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LumaSparkRevealLottie(
                        replayKey = "${selection.locationKey}_${selection.blockIndex}_${selection.wordStartChar}_${selection.word}",
                        tint = palette.accent,
                        modifier = Modifier
                            .size(22.dp)
                            .testTag("popover_lottie_reveal_spark")
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = headerLabel,
                        style = if (isMultiWord) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for ((color, argb) in HighlightSwatches) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(1.5.dp, palette.surface, CircleShape)
                                    .clickable { onHighlight(argb) }
                                    .testTag("popover_highlight_$argb")
                            )
                        }
                    }

                    Spacer(Modifier.width(5.dp))

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surfaceAlt)
                            .clickable(onClick = onAddNote)
                            .testTag("popover_add_note"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EditNote,
                            contentDescription = "Añadir nota",
                            tint = palette.ink,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surfaceAlt)
                            .clickable { clipboard.setText(AnnotatedString(selection.word)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copiar palabra",
                            tint = palette.ink,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    // Small discreet recuadro for Dictionary / API config right inside the popover!
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surfaceAlt.copy(alpha = 0.7f))
                            .clickable {
                                onDismiss()
                                onOpenDictionarySettings()
                            }
                            .testTag("popover_dict_config_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Ajustar diccionario o API",
                            tint = palette.inkMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Spacer(Modifier.width(2.dp))

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Cerrar popover",
                            tint = palette.inkMuted,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                // Selection Scope Switcher: Palabra | Oración | Párrafo (1-tap sentence/paragraph highlighting!)
                if (selection.fullBlockText.isNotBlank()) {
                    val fullText = selection.fullBlockText
                    val wordRelStart = (selection.wordStartChar - selection.blockCharOffset).coerceIn(0, fullText.length)
                    val wordRelEnd = (selection.wordEndChar - selection.blockCharOffset).coerceIn(wordRelStart, fullText.length)
                    val (sentStart, sentEnd) = remember(fullText, wordRelStart) {
                        extractSentenceRange(fullText, wordRelStart)
                    }
                    val paraStart = remember(fullText) { fullText.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0) }
                    val paraEnd = remember(fullText) {
                        val last = fullText.indexOfLast { !it.isWhitespace() }
                        if (last >= 0) last + 1 else fullText.length
                    }
                    val curRelStart = (selection.startChar - selection.blockCharOffset).coerceIn(0, fullText.length)
                    val curRelEnd = (selection.endChar - selection.blockCharOffset).coerceIn(curRelStart, fullText.length)

                    val isWordMode = curRelStart == wordRelStart && curRelEnd == wordRelEnd
                    val isSentenceMode = !isWordMode && curRelStart == sentStart && curRelEnd == sentEnd
                    val isParagraphMode = !isWordMode && !isSentenceMode && curRelStart == paraStart && curRelEnd == paraEnd
                    val selectedWordCount = remember(selection.word) {
                        selection.word.trim().split(Regex("\\s+")).count { it.isNotBlank() }
                    }

                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ScopePill(
                            label = "Palabra",
                            selected = isWordMode,
                            onClick = {
                                val text = fullText.substring(wordRelStart, wordRelEnd).trim()
                                if (text.isNotBlank()) {
                                    onUpdateSelection(
                                        selection.copy(
                                            word = text,
                                            startChar = wordRelStart + selection.blockCharOffset,
                                            endChar = wordRelEnd + selection.blockCharOffset
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.testTag("popover_scope_word")
                        )
                        ScopePill(
                            label = "Oración",
                            selected = isSentenceMode,
                            onClick = {
                                val text = fullText.substring(sentStart, sentEnd).trim()
                                if (text.isNotBlank()) {
                                    onUpdateSelection(
                                        selection.copy(
                                            word = text,
                                            startChar = sentStart + selection.blockCharOffset,
                                            endChar = sentEnd + selection.blockCharOffset
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.testTag("popover_scope_sentence")
                        )
                        ScopePill(
                            label = "Párrafo",
                            selected = isParagraphMode,
                            onClick = {
                                val text = fullText.substring(paraStart, paraEnd).trim()
                                if (text.isNotBlank()) {
                                    onUpdateSelection(
                                        selection.copy(
                                            word = text,
                                            startChar = paraStart + selection.blockCharOffset,
                                            endChar = paraEnd + selection.blockCharOffset
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.testTag("popover_scope_paragraph")
                        )
                        Spacer(Modifier.weight(1f))
                        if (selectedWordCount > 1) {
                            Text(
                                text = "$selectedWordCount pal.",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.inkMuted
                            )
                        }
                    }
                } else if (selection.snippet.isNotBlank() && selection.snippet.trim() != initialOcrWord.trim()) {
                    val isSentenceMode = selection.word.trim() == selection.snippet.trim()
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ScopePill(
                            label = "Palabra",
                            selected = !isSentenceMode,
                            onClick = { onUpdateSelection(selection.copy(word = initialOcrWord)) },
                            modifier = Modifier.testTag("popover_scope_word")
                        )
                        ScopePill(
                            label = "Oración",
                            selected = isSentenceMode,
                            onClick = { onUpdateSelection(selection.copy(word = selection.snippet.trim())) },
                            modifier = Modifier.testTag("popover_scope_sentence")
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Instant EN / Auto -> ES Translation Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.accentSoft)
                        .border(1.dp, palette.accent.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("popover_translation_banner"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Translate,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    if (isTranslatingWord) {
                        LumaDictionaryPulseLottie(
                            tint = palette.accent,
                            modifier = Modifier
                                .size(width = 30.dp, height = 16.dp)
                                .testTag("popover_lottie_translating")
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = translationStatusText.ifBlank { "Traduciendo al español…" },
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = instantTranslation ?: "Sin traducción directa disponible",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.ink,
                            maxLines = if (isMultiWord) 4 else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Source Tabs Row (Wikipedia + RAE + WordReference + Custom {palabra})
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    enabledSources.forEachIndexed { idx, source ->
                        val active = idx == selectedTabIndex
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) palette.accent else palette.surfaceAlt)
                                .clickable { selectedTabIndex = idx }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("dict_tab_${source.name}")
                        ) {
                            Text(
                                text = source.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (active) palette.onAccent else palette.ink
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Tab Content Body
                val activeSource = enabledSources.getOrNull(selectedTabIndex)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.background)
                        .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                ) {
                    if (activeSource == null) {
                        Text(
                            text = "Activa una fuente en Diccionario e IA.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkMuted,
                            modifier = Modifier.align(Alignment.Center).padding(12.dp)
                        )
                    } else if (activeSource.isWikipediaApi) {
                        WikipediaNativeSummaryBody(
                            summary = wikiSummary,
                            error = wikiError,
                            isLoading = isWikiLoading,
                            onOpenExternal = { url ->
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            }
                        )
                    } else {
                        val url = remember(activeSource.urlTemplate, selection.word) {
                            TranslationService.buildTemplatedUrl(activeSource.urlTemplate, selection.word)
                        }
                        EmbeddedDictionaryWebView(
                            url = url,
                            onOpenExternal = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WikipediaNativeSummaryBody(
    summary: WikipediaSummary?,
    error: String?,
    isLoading: Boolean,
    onOpenExternal: (String) -> Unit
) {
    val palette = LocalFolioPalette.current
    when {
        isLoading -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LumaDictionaryPulseLottie(
                    tint = palette.accent,
                    modifier = Modifier
                        .size(width = 64.dp, height = 34.dp)
                        .testTag("popover_lottie_wiki_loading")
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Consultando diccionario…",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.inkMuted
                )
            }
        }
        summary != null -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = summary.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.ink
                        )
                        if (summary.description.isNotBlank()) {
                            Text(
                                text = summary.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.accent
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surfaceAlt)
                            .clickable { onOpenExternal(summary.pageUrl) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = "Abrir en Wikipedia",
                            tint = palette.ink,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = summary.extract,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.ink
                )
            }
        }
        else -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = error ?: "No se encontró resumen en Wikipedia ES. Prueba las pestañas RAE o WordReference.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbeddedDictionaryWebView(
    url: String,
    onOpenExternal: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val motion = LocalLumaMotion.current
    var progress by remember(url) { mutableIntStateOf(15) }
    var isContentVisible by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    var retryNonce by remember(url) { mutableIntStateOf(0) }

    // Hard failsafe so third-party trackers (e.g., WordReference ad scripts) never trap the loading veil
    androidx.compose.runtime.LaunchedEffect(url, retryNonce) {
        isContentVisible = false
        kotlinx.coroutines.delay(2400L)
        isContentVisible = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (failed) {
            Column(
                modifier = Modifier.fillMaxSize().padding(14.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No se pudo cargar la vista embebida en este momento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TactileChip(
                        label = "Reintentar",
                        selected = false,
                        onClick = {
                            failed = false
                            isContentVisible = false
                            progress = 15
                            retryNonce++
                        }
                    )
                    TactileChip(
                        label = "Abrir en navegador",
                        selected = true,
                        icon = Icons.Rounded.OpenInNew,
                        onClick = onOpenExternal
                    )
                }
            }
        } else {
            androidx.compose.runtime.key(retryNonce) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(android.graphics.Color.WHITE)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true)
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean = false

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): android.webkit.WebResourceResponse? {
                                    val host = request?.url?.host?.lowercase().orEmpty()
                                    val isBlockingTracker = host.contains("googlesyndication") ||
                                        host.contains("doubleclick") ||
                                        host.contains("amazon-adsystem") ||
                                        host.contains("adnxs") ||
                                        host.contains("criteo") ||
                                        host.contains("taboola") ||
                                        host.contains("outbrain") ||
                                        host.contains("pubmatic") ||
                                        host.contains("rubiconproject") ||
                                        host.contains("scorecardresearch") ||
                                        host.contains("googletagmanager") ||
                                        host.contains("google-analytics")
                                    return if (isBlockingTracker) {
                                        android.webkit.WebResourceResponse(
                                            "text/plain",
                                            "utf-8",
                                            java.io.ByteArrayInputStream(ByteArray(0))
                                        )
                                    } else {
                                        super.shouldInterceptRequest(view, request)
                                    }
                                }

                                override fun onPageCommitVisible(view: WebView?, pageUrl: String?) {
                                    super.onPageCommitVisible(view, pageUrl)
                                    isContentVisible = true
                                    if (progress < 90) progress = 90
                                }

                                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                    super.onPageFinished(view, pageUrl)
                                    isContentVisible = true
                                    progress = 100
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        failed = true
                                    }
                                }

                                override fun onRenderProcessGone(
                                    view: WebView?,
                                    detail: RenderProcessGoneDetail?
                                ): Boolean {
                                    // Cleanly detach and destroy the crashed renderer without killing the app
                                    runCatching {
                                        (view?.parent as? ViewGroup)?.removeView(view)
                                        view?.destroy()
                                    }
                                    failed = true
                                    return true
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                    if (newProgress >= 65) {
                                        isContentVisible = true
                                    }
                                }
                            }
                            tag = url
                            loadUrl(url)
                        }
                    },
                    update = { webView ->
                        // Compare against the requested URL stored in tag (not webView.url, which
                        // changes on redirects and would otherwise trigger an infinite reload loop)
                        if (webView.tag != url) {
                            failed = false
                            isContentVisible = false
                            progress = 15
                            webView.tag = url
                            webView.loadUrl(url)
                        }
                    },
                    onRelease = { webView ->
                        runCatching {
                            webView.stopLoading()
                            webView.webChromeClient = null
                            webView.webViewClient = WebViewClient()
                            webView.loadUrl("about:blank")
                            webView.clearHistory()
                            webView.removeAllViews()
                            webView.destroy()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (progress < 95 && !isContentVisible) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.TopCenter),
                    color = palette.accent,
                    trackColor = Color.Transparent
                )
            }
            AnimatedVisibility(
                visible = !isContentVisible && progress < 65,
                enter = fadeIn(tween(motion.duration(180), easing = LumaEaseInOutCurve)),
                exit = fadeOut(tween(motion.duration(220), easing = LumaEaseInOutCurve)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(palette.surface.copy(alpha = 0.94f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LumaDictionaryPulseLottie(
                            tint = palette.accent,
                            modifier = Modifier
                                .size(width = 76.dp, height = 34.dp)
                                .testTag("dict_webview_lottie_loader")
                        )
                        Text(
                            text = "Cargando definición…",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.inkMuted
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(palette.surface.copy(alpha = 0.92f))
                    .border(1.dp, palette.border, CircleShape)
                    .clickable(onClick = onOpenExternal),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.OpenInNew,
                    contentDescription = "Abrir enlace externo",
                    tint = palette.accent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ScopePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) palette.accentSoft else palette.surfaceAlt)
            .border(
                width = 1.dp,
                color = if (selected) palette.accent.copy(alpha = 0.65f) else palette.border.copy(alpha = 0.6f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) palette.accent else palette.inkMuted
        )
    }
}

/**
 * Extracts the complete sentence bounds [start, end) surrounding [anchorIndex] inside [text].
 * Respects Spanish/English punctuation (., !, ?, …, \n) and preserves opening/closing quotes.
 */
internal fun extractSentenceRange(text: String, anchorIndex: Int): Pair<Int, Int> {
    if (text.isBlank()) return 0 to 0
    val safe = anchorIndex.coerceIn(0, text.lastIndex)

    fun isSentenceEndChar(c: Char): Boolean = c == '.' || c == '!' || c == '?' || c == '…' || c == '\n'
    fun isClosingQuoteOrParen(c: Char): Boolean = c == '»' || c == '"' || c == '”' || c == '\'' || c == '’' || c == ')' || c == ']'

    // Scan backwards to find the start of the current sentence
    var start = safe
    while (start > 0) {
        val prev = text[start - 1]
        if (prev == '\n') break
        if (isSentenceEndChar(prev)) {
            // Confirm it's a real sentence break (followed by whitespace, quote+whitespace, or end)
            var after = start
            while (after < text.length && isClosingQuoteOrParen(text[after])) after++
            if (after >= text.length || text[after].isWhitespace()) {
                start = after
                break
            }
        }
        start--
    }
    while (start < text.length && text[start].isWhitespace()) {
        start++
    }

    // Scan forwards to find the end of the current sentence
    var end = safe
    while (end < text.length) {
        val c = text[end]
        if (c == '\n') break
        if (isSentenceEndChar(c)) {
            end++
            while (end < text.length && (isSentenceEndChar(text[end]) || isClosingQuoteOrParen(text[end]))) {
                if (text[end] == '\n') break
                end++
            }
            break
        }
        end++
    }
    while (end > start && text[end - 1].isWhitespace()) {
        end--
    }
    return if (end > start) start to end else 0 to text.length
}
