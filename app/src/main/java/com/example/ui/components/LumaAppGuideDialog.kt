package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.LibraryDocumentItem
import com.example.data.model.DocumentFormat
import com.example.ui.theme.LocalFolioPalette
import kotlinx.coroutines.launch

private data class GuideStepPage(
    val badge: String,
    val title: String,
    val summary: String,
    @DrawableRes val imageRes: Int?,
    val imageDescription: String,
    val steps: List<String>,
    val isPracticeCatalog: Boolean = false
)

private data class PracticeFormatRow(
    val format: DocumentFormat,
    val featureTitle: String,
    val featureHint: String,
    val icon: ImageVector,
    val accent: Color
)

/**
 * Interactive, beginner-friendly illustrated guide presenting Luma and every core feature
 * in concise numbered steps with visual screenshots/illustrations of the app, plus direct
 * 1-tap links to try each built-in sample document format (EPUB, PDF, PPTX, DOCX, TXT, FB2).
 */
@Composable
fun LumaAppGuideDialog(
    documents: List<LibraryDocumentItem>,
    onOpenSampleDocument: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val scope = rememberCoroutineScope()

    val pages = remember {
        listOf(
            GuideStepPage(
                badge = "PASO 1 DE 6 · INICIO",
                title = "Bienvenido a Luma",
                summary = "Un lector limpio para libros, apuntes, PDFs y presentaciones en un solo lugar.",
                imageRes = R.drawable.img_guide_library,
                imageDescription = "Vista de la biblioteca de Luma con portadas de libros y formatos compatibles",
                steps = listOf(
                    "Toca cualquier portada de tu biblioteca para abrirla al instante.",
                    "Usa las pestañas Libros y Series para organizar sagas o materias.",
                    "Toca tu círculo de perfil arriba a la derecha y elige Importar para cargar tus propios archivos."
                )
            ),
            GuideStepPage(
                badge = "PASO 2 DE 6 · CONFORT VISUAL",
                title = "Temas suaves y modo nocturno",
                summary = "Paletas cálidas y luz regulable pensadas para leer horas sin cansar la vista.",
                imageRes = R.drawable.img_guide_themes,
                imageDescription = "Comparación entre tema cálido sepia y modo nocturno cálido con filtro de luz azul",
                steps = listOf(
                    "Abre Texto y Tema (o Ajustes) y elige Crema cálida, Sepia, Arena o Ámbar.",
                    "Desliza el Filtro de luz cálida para suavizar la pantalla en sesiones largas.",
                    "Activa el Modo nocturno o programa un horario automático (ej. 20:00 a 07:00) con Noche cálida."
                )
            ),
            GuideStepPage(
                badge = "PASO 3 DE 6 · TODOS LOS FORMATOS",
                title = "Formato original o Modo EPUB",
                summary = "Lee documentos tal como fueron creados o adáptalos como libro cómodo.",
                imageRes = R.drawable.img_guide_formats,
                imageDescription = "Interruptor que cambia entre una diapositiva PowerPoint original y texto EPUB adaptado",
                steps = listOf(
                    "Luma abre EPUB, PDF, PowerPoint (PPTX), Word (DOCX), TXT y FB2 sin apps extra.",
                    "En archivos que no son EPUB, toca el interruptor del menú para alternar entre su formato original y Modo EPUB.",
                    "En formato original puedes ver hojas A4, Carta, A3 o diapositivas 16:9 manteniendo su diseño."
                )
            ),
            GuideStepPage(
                badge = "PASO 4 DE 6 · TRABAJO Y ESTUDIO",
                title = "Dibuja, resalta y firma hojas",
                summary = "Anota con el dedo o lápiz y firma documentos listos para compartir.",
                imageRes = R.drawable.img_guide_sign_draw,
                imageDescription = "Hoja A4 con firma manuscrita azul, texto resaltado en amarillo y barra flotante de dibujo",
                steps = listOf(
                    "Pulsa Dibujar en el menú lector para mostrar lápiz, resaltador, borrador y el modo Firma.",
                    "Alterna a Mover si necesitas acercar la hoja con dos dedos para firmar con precisión.",
                    "Al terminar, pulsa Exportar para guardar tu archivo con tus firmas y trazos integrados."
                )
            ),
            GuideStepPage(
                badge = "PASO 5 DE 6 · HERRAMIENTAS",
                title = "Marcapáginas, Isla y Diccionario",
                summary = "Controles flotantes discretos que solo aparecen cuando los necesitas.",
                imageRes = R.drawable.img_guide_tools,
                imageDescription = "Vista de lectura con Isla Dinámica superior, marcapáginas lateral y diccionario flotante",
                steps = listOf(
                    "Arrastra la pestaña lateral (Marcapáginas) hacia arriba, abajo o al borde opuesto.",
                    "Mantén pulsada cualquier palabra para traducirla, consultar el diccionario o subrayarla.",
                    "La Isla Dinámica superior muestra solo batería o avisos de apps que tú autorices, sin distracciones."
                )
            ),
            GuideStepPage(
                badge = "PASO 6 DE 6 · PRÁCTICA RÁPIDA",
                title = "Prueba cada formato ahora",
                summary = "Incluimos un documento de ejemplo por cada formato para que pruebes todo sin complicarte.",
                imageRes = null,
                imageDescription = "",
                steps = emptyList(),
                isPracticeCatalog = true
            )
        )
    }

    val practiceFormats = remember {
        listOf(
            PracticeFormatRow(
                format = DocumentFormat.EPUB,
                featureTitle = "EPUB · Lectura, temas y diccionario",
                featureHint = "Prueba fuentes, temas cálidos y mantén pulsada una palabra.",
                icon = Icons.Rounded.AutoStories,
                accent = Color(0xFF2B64F5)
            ),
            PracticeFormatRow(
                format = DocumentFormat.PDF,
                featureTitle = "PDF · Estudio, zoom y Manhwa OCR",
                featureHint = "Prueba zoom táctil, traducción OCR en globos y Modo EPUB.",
                icon = Icons.Rounded.PictureAsPdf,
                accent = Color(0xFFE11D48)
            ),
            PracticeFormatRow(
                format = DocumentFormat.PPTX,
                featureTitle = "PPTX · Diapositivas PowerPoint reales",
                featureHint = "Ve el diseño 16:9 original o cámbialo a Modo EPUB en 1 toque.",
                icon = Icons.Rounded.Slideshow,
                accent = Color(0xFFD97706)
            ),
            PracticeFormatRow(
                format = DocumentFormat.DOCX,
                featureTitle = "DOCX · Hoja A4 para revisar y firmar",
                featureHint = "Activa Formato DOCX + Dibujar/Firma en el bloque de firma.",
                icon = Icons.Rounded.Description,
                accent = Color(0xFF0D9488)
            ),
            PracticeFormatRow(
                format = DocumentFormat.TXT,
                featureTitle = "TXT · Apuntes ligeros y búsqueda",
                featureHint = "Prueba lectura continua, buscador interno y modo hoja.",
                icon = Icons.Rounded.TextSnippet,
                accent = Color(0xFF6366F1)
            ),
            PracticeFormatRow(
                format = DocumentFormat.FB2,
                featureTitle = "FB2 · Novela corta FictionBook",
                featureHint = "Prueba capítulos, modo nocturno cálido y marcadores.",
                icon = Icons.Rounded.MenuBook,
                accent = Color(0xFF9333EA)
            )
        )
    }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pages.size })

    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.Center,
        transformOrigin = TransformOrigin(0.5f, 0.14f),
        maxWidth = 520.dp,
        maxHeightFraction = 0.88f,
        testTag = "luma_app_guide_dialog"
    ) { dismissAnimated ->
        LumaDropdownHeader(
            title = "Guía de Luma",
            subtitle = "Aprende a usar la app en 1 minuto",
            leadingIcon = Icons.Rounded.MenuBook,
            onDismiss = dismissAnimated,
            closeTestTag = "guide_close_button"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) { pageIndex ->
                val page = pages[pageIndex]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    // Step pill badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.accentSoft)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = page.badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.7.sp,
                            color = palette.accent
                        )
                    }

                    // Illustrated visual preview of the app feature
                    if (page.imageRes != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(palette.surfaceAlt)
                                .border(1.dp, palette.border, RoundedCornerShape(20.dp))
                        ) {
                            Image(
                                painter = painterResource(id = page.imageRes),
                                contentDescription = page.imageDescription,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Concise headline & 1-line description
                    Text(
                        text = page.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.ink
                    )
                    Text(
                        text = page.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.inkMuted
                    )

                    if (!page.isPracticeCatalog) {
                        // Simple numbered steps
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(palette.surfaceAlt.copy(alpha = 0.65f))
                                .border(1.dp, palette.border, RoundedCornerShape(18.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            page.steps.forEachIndexed { idx, stepText ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(palette.accent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${idx + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = palette.onAccent
                                        )
                                    }
                                    Text(
                                        text = stepText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.ink,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    } else {
                        // Interactive 6-format practice cards (1-tap launch straight into each sample!)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (row in practiceFormats) {
                                val matchingDoc = remember(documents, row.format) {
                                    documents.firstOrNull { it.format.equals(row.format.name, ignoreCase = true) }
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(palette.surfaceAlt.copy(alpha = 0.7f))
                                        .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                                        .clickable(enabled = matchingDoc != null) {
                                            matchingDoc?.let { doc ->
                                                dismissAnimated()
                                                onOpenSampleDocument(doc.id)
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .testTag("guide_try_${row.format.name}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(11.dp))
                                            .background(row.accent.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = row.icon,
                                            contentDescription = null,
                                            tint = row.accent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = row.featureTitle,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.ink,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = row.featureHint,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = palette.inkMuted,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (matchingDoc != null) {
                                        Spacer(Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(palette.accentSoft)
                                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Rounded.PlayArrow,
                                                    contentDescription = "Abrir ejemplo ${row.format.label}",
                                                    tint = palette.accent,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = "Abrir",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = palette.accent
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Progress dots + Back / Next controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Back button
                val canGoBack = pagerState.currentPage > 0
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (canGoBack) palette.surfaceAlt else Color.Transparent)
                        .clickable(enabled = canGoBack) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("guide_prev_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canGoBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Paso anterior",
                            tint = palette.ink,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Atrás",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = palette.ink
                        )
                    } else {
                        Spacer(Modifier.width(56.dp))
                    }
                }

                // Step dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(pages.size) { index ->
                        val active = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .height(7.dp)
                                .width(if (active) 20.dp else 7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) palette.accent else palette.inkMuted.copy(alpha = 0.28f)
                                )
                                .clickable {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                }
                        )
                    }
                }

                // Next or Finish button
                val isLast = pagerState.currentPage == pages.lastIndex
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.accent)
                        .clickable {
                            if (isLast) {
                                dismissAnimated()
                            } else {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag(if (isLast) "guide_finish_button" else "guide_next_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isLast) "Empezar" else "Siguiente",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = palette.onAccent
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isLast) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = palette.onAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
