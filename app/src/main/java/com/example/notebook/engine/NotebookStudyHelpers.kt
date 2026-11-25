package com.example.notebook.engine

import com.example.notebook.model.ConnectorObject
import com.example.notebook.model.NbPoint
import com.example.notebook.model.NotebookLayerIds
import com.example.notebook.model.NotebookObject
import com.example.notebook.model.NotebookShapeKind
import com.example.notebook.model.NotebookStarterTemplate
import com.example.notebook.model.NotebookTextSpan
import com.example.notebook.model.ShapeObject
import com.example.notebook.model.TableObject
import com.example.notebook.model.TextAlignMode
import com.example.notebook.model.TextListMode
import com.example.notebook.model.TextObject
import com.example.notebook.ui.NotebookFonts
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Study Notebook Smart Helpers (Phase 3):
 * 1. Hold-to-Snap geometric recognizer (straight line, perfect ellipse/circle, or rounded rectangle
 *    when the user holds still for ~450 ms at the end of a freehand stroke).
 * 2. Editable Starter Templates (Cornell, Concept Map with anchored Connectors, Title Summary, Comparative Table).
 */
object NotebookStudyHelpers {

    /**
     * Analyzes a freehand stroke held still at the end and returns a clean geometric `ShapeObject`
     * (`LINE`, `ELLIPSE`, or `ROUNDED_RECT`) if it matches within natural drawing tolerances.
     */
    fun recognizePerfectShape(
        points: List<NbPoint>,
        layerId: String,
        colorArgb: Long,
        fillEnabled: Boolean,
        isDashed: Boolean,
        hasShadow: Boolean,
        opacity: Float,
        strokeWidthNorm: Float
    ): ShapeObject? {
        if (points.size < 6) return null
        val first = points.first()
        val last = points.last()

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var pathLen = 0f
        for (i in points.indices) {
            val p = points[i]
            minX = min(minX, p.x)
            minY = min(minY, p.y)
            maxX = max(maxX, p.x)
            maxY = max(maxY, p.y)
            if (i > 0) {
                pathLen += hypot((p.x - points[i - 1].x).toDouble(), (p.y - points[i - 1].y).toDouble()).toFloat()
            }
        }
        val w = (maxX - minX).coerceAtLeast(0.01f)
        val h = (maxY - minY).coerceAtLeast(0.01f)
        val diag = hypot(w.toDouble(), h.toDouble()).toFloat()
        if (diag < 0.045f || pathLen < 0.05f) return null

        val endToEnd = hypot((last.x - first.x).toDouble(), (last.y - first.y).toDouble()).toFloat()
        val softFill = if (fillEnabled) {
            (0x36000000L or (colorArgb and 0x00FFFFFFL))
        } else null

        // 1. Straight line test: chord is long relative to total path and perpendicular deviation is tiny
        if (endToEnd > diag * 0.72f && pathLen <= endToEnd * 1.18f) {
            var maxDev = 0f
            val dx = last.x - first.x
            val dy = last.y - first.y
            val denom = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(0.0001f)
            for (p in points) {
                val dist = abs(dy * p.x - dx * p.y + last.x * first.y - last.y * first.x) / denom
                if (dist > maxDev) maxDev = dist
            }
            if (maxDev <= max(0.022f, endToEnd * 0.085f)) {
                // Snap nearly horizontal or vertical lines cleanly
                val snapEndX = if (abs(dx) < endToEnd * 0.08f) first.x else last.x
                val snapEndY = if (abs(dy) < endToEnd * 0.08f) first.y else last.y
                val lx = min(first.x, snapEndX)
                val ly = min(first.y, snapEndY)
                return ShapeObject(
                    layerId = layerId,
                    x = lx,
                    y = ly,
                    width = abs(snapEndX - first.x).coerceAtLeast(0.02f),
                    height = abs(snapEndY - first.y).coerceAtLeast(0.02f),
                    kind = NotebookShapeKind.LINE,
                    startX = first.x,
                    startY = first.y,
                    endX = snapEndX,
                    endY = snapEndY,
                    strokeColorArgb = colorArgb,
                    fillColorArgb = null,
                    strokeWidthNorm = strokeWidthNorm.coerceAtLeast(0.0034f),
                    isDashed = isDashed,
                    opacity = opacity,
                    hasShadow = hasShadow
                )
            }
        }

        // 2. Closed loop test -> decide between ELLIPSE (circle/oval) vs ROUNDED_RECT (box)
        val isClosed = endToEnd <= max(0.075f, diag * 0.38f) && w >= 0.035f && h >= 0.035f
        if (isClosed) {
            val cx = (minX + maxX) * 0.5f
            val cy = (minY + maxY) * 0.5f
            val rx = (w * 0.5f).coerceAtLeast(0.01f)
            val ry = (h * 0.5f).coerceAtLeast(0.01f)

            var ellipseErrSum = 0f
            var rectErrSum = 0f
            for (p in points) {
                val nx = (p.x - cx) / rx
                val ny = (p.y - cy) / ry
                val rad = hypot(nx.toDouble(), ny.toDouble()).toFloat()
                ellipseErrSum += abs(rad - 1f)

                val dEdge = min(abs(abs(nx) - 1f), abs(abs(ny) - 1f))
                rectErrSum += dEdge
            }
            val ellipseErr = ellipseErrSum / points.size
            val rectErr = rectErrSum / points.size

            val kind = if (rectErr + 0.03f < ellipseErr) {
                NotebookShapeKind.ROUNDED_RECT
            } else {
                NotebookShapeKind.ELLIPSE
            }
            return ShapeObject(
                layerId = layerId,
                x = minX,
                y = minY,
                width = w,
                height = h,
                kind = kind,
                startX = minX,
                startY = minY,
                endX = maxX,
                endY = maxY,
                strokeColorArgb = colorArgb,
                fillColorArgb = softFill,
                strokeWidthNorm = strokeWidthNorm.coerceAtLeast(0.0036f),
                isDashed = isDashed,
                cornerRadiusDp = if (kind == NotebookShapeKind.ROUNDED_RECT) 12f else 0f,
                opacity = opacity,
                hasShadow = hasShadow
            )
        }

        return null
    }

    /**
     * Generates a ready-to-edit study layout for the current page using real `TextObject`,
     * `ConnectorObject`, and `TableObject` instances with curated Phase 3 fonts.
     */
    fun buildStarterTemplateObjects(
        template: NotebookStarterTemplate,
        startZ: Int
    ): List<NotebookObject> {
        var z = startZ
        return when (template) {
            NotebookStarterTemplate.CORNELL -> listOf(
                TextObject(
                    zIndex = z++,
                    layerId = NotebookLayerIds.CONTENT,
                    x = 0.05f,
                    y = 0.04f,
                    width = 0.90f,
                    height = 0.09f,
                    spans = listOf(
                        NotebookTextSpan("TEMA · MÉTODO CORNELL", colorArgb = 0xFF1D5BFFL, isBold = true)
                    ),
                    fontId = NotebookFonts.DEFAULT_BANNER_ID,
                    fontSizeNorm = 0.032f,
                    alignment = TextAlignMode.CENTER,
                    isBanner = true,
                    fillColorArgb = 0xFFEFF6FFL,
                    borderColorArgb = 0xFF1D5BFFL,
                    cornerRadiusDp = 16f,
                    hasShadow = true
                ),
                TextObject(
                    zIndex = z++,
                    layerId = NotebookLayerIds.CONTENT,
                    x = 0.05f,
                    y = 0.15f,
                    width = 0.27f,
                    height = 0.62f,
                    spans = listOf(
                        NotebookTextSpan("PREGUNTAS CLAVE\n", colorArgb = 0xFF7C3AEDL, isBold = true),
                        NotebookTextSpan("¿Concepto central?\n¿Cómo funciona?\nPalabras clave", colorArgb = 0xFF334155L)
                    ),
                    fontId = NotebookFonts.DEFAULT_HAND_ID,
                    fontSizeNorm = 0.022f,
                    fillColorArgb = 0xFFFAF5FFL,
                    borderColorArgb = 0xFF7C3AEDL,
                    cornerRadiusDp = 12f
                ),
                TextObject(
                    zIndex = z++,
                    layerId = NotebookLayerIds.CONTENT,
                    x = 0.34f,
                    y = 0.15f,
                    width = 0.61f,
                    height = 0.62f,
                    spans = listOf(
                        NotebookTextSpan("Apuntes de clase\n", colorArgb = 0xFF151C2BL, isBold = true),
                        NotebookTextSpan("Idea principal y desarrollo\nEjemplos, fórmulas (H₂O, Ca²⁺)\nDetalles de examen", colorArgb = 0xFF151C2BL)
                    ),
                    fontId = NotebookFonts.DEFAULT_BODY_ID,
                    fontSizeNorm = 0.021f,
                    listMode = TextListMode.BULLETS,
                    fillColorArgb = 0xFFFFFFFFL,
                    borderColorArgb = 0xFFCBD5E1L,
                    cornerRadiusDp = 12f
                ),
                TextObject(
                    zIndex = z++,
                    layerId = NotebookLayerIds.CONTENT,
                    x = 0.05f,
                    y = 0.79f,
                    width = 0.90f,
                    height = 0.15f,
                    spans = listOf(
                        NotebookTextSpan("RESUMEN: ", colorArgb = 0xFF16A34AL, isBold = true),
                        NotebookTextSpan("Sintetiza en 2–3 líneas lo más importante de esta hoja.", colorArgb = 0xFF151C2BL)
                    ),
                    fontId = NotebookFonts.DEFAULT_BODY_ID,
                    fontSizeNorm = 0.020f,
                    fillColorArgb = 0xFFECFDF5L,
                    borderColorArgb = 0xFF16A34AL,
                    cornerRadiusDp = 14f
                )
            )

            NotebookStarterTemplate.CONCEPT_MAP -> {
                val centerId = UUID.randomUUID().toString()
                val tlId = UUID.randomUUID().toString()
                val trId = UUID.randomUUID().toString()
                val blId = UUID.randomUUID().toString()
                val brId = UUID.randomUUID().toString()

                val center = TextObject(
                    id = centerId,
                    zIndex = z++,
                    layerId = NotebookLayerIds.CONTENT,
                    x = 0.30f,
                    y = 0.39f,
                    width = 0.40f,
                    height = 0.11f,
                    spans = listOf(NotebookTextSpan("IDEA CENTRAL", colorArgb = 0xFFFFFFFFL, isBold = true)),
                    fontId = NotebookFonts.DEFAULT_BANNER_ID,
                    fontSizeNorm = 0.028f,
                    alignment = TextAlignMode.CENTER,
                    isBanner = true,
                    fillColorArgb = 0xFF1D5BFFL,
                    borderColorArgb = 0xFF1E40AFL,
                    cornerRadiusDp = 18f,
                    hasShadow = true
                )
                val tl = TextObject(
                    id = tlId,
                    zIndex = z++,
                    x = 0.06f,
                    y = 0.12f,
                    width = 0.34f,
                    height = 0.14f,
                    spans = listOf(
                        NotebookTextSpan("1. Definición\n", colorArgb = 0xFF0891B2L, isBold = true),
                        NotebookTextSpan("Concepto base y origen", colorArgb = 0xFF151C2BL)
                    ),
                    fontId = NotebookFonts.DEFAULT_DIAGRAM_ID,
                    fontSizeNorm = 0.020f,
                    fillColorArgb = 0xFFECFEFFL,
                    borderColorArgb = 0xFF0891B2L,
                    cornerRadiusDp = 14f
                )
                val tr = TextObject(
                    id = trId,
                    zIndex = z++,
                    x = 0.60f,
                    y = 0.12f,
                    width = 0.34f,
                    height = 0.14f,
                    spans = listOf(
                        NotebookTextSpan("2. Mecanismo\n", colorArgb = 0xFF7C3AEDL, isBold = true),
                        NotebookTextSpan("Cómo actúa paso a paso", colorArgb = 0xFF151C2BL)
                    ),
                    fontId = NotebookFonts.DEFAULT_DIAGRAM_ID,
                    fontSizeNorm = 0.020f,
                    fillColorArgb = 0xFFF5F3FFL,
                    borderColorArgb = 0xFF7C3AEDL,
                    cornerRadiusDp = 14f
                )
                val bl = TextObject(
                    id = blId,
                    zIndex = z++,
                    x = 0.06f,
                    y = 0.66f,
                    width = 0.34f,
                    height = 0.14f,
                    spans = listOf(
                        NotebookTextSpan("3. Ejemplos\n", colorArgb = 0xFF16A34AL, isBold = true),
                        NotebookTextSpan("Casos y aplicaciones", colorArgb = 0xFF151C2BL)
                    ),
                    fontId = NotebookFonts.DEFAULT_DIAGRAM_ID,
                    fontSizeNorm = 0.020f,
                    fillColorArgb = 0xFFECFDF5L,
                    borderColorArgb = 0xFF16A34AL,
                    cornerRadiusDp = 14f
                )
                val br = TextObject(
                    id = brId,
                    zIndex = z++,
                    x = 0.60f,
                    y = 0.66f,
                    width = 0.34f,
                    height = 0.14f,
                    spans = listOf(
                        NotebookTextSpan("4. Alertas / Claves\n", colorArgb = 0xFFE11D48L, isBold = true),
                        NotebookTextSpan("Puntos críticos de examen", colorArgb = 0xFF151C2BL)
                    ),
                    fontId = NotebookFonts.DEFAULT_DIAGRAM_ID,
                    fontSizeNorm = 0.020f,
                    fillColorArgb = 0xFFFFF1F2L,
                    borderColorArgb = 0xFFE11D48L,
                    cornerRadiusDp = 14f
                )
                listOf(
                    ConnectorObject(zIndex = z++, fromObjectId = centerId, toObjectId = tlId, colorArgb = 0xFF0891B2L),
                    ConnectorObject(zIndex = z++, fromObjectId = centerId, toObjectId = trId, colorArgb = 0xFF7C3AEDL),
                    ConnectorObject(zIndex = z++, fromObjectId = centerId, toObjectId = blId, colorArgb = 0xFF16A34AL),
                    ConnectorObject(zIndex = z++, fromObjectId = centerId, toObjectId = brId, colorArgb = 0xFFE11D48L),
                    center, tl, tr, bl, br
                )
            }

            NotebookStarterTemplate.TITLE_SUMMARY -> listOf(
                TextObject(
                    zIndex = z++,
                    x = 0.06f,
                    y = 0.04f,
                    width = 0.88f,
                    height = 0.10f,
                    spans = listOf(
                        NotebookTextSpan("TÍTULO DEL RESUMEN", colorArgb = 0xFF1D5BFFL, isBold = true)
                    ),
                    fontId = NotebookFonts.DEFAULT_BANNER_ID,
                    fontSizeNorm = 0.036f,
                    alignment = TextAlignMode.CENTER,
                    isBanner = true,
                    fillColorArgb = 0xFFFFFFFFL,
                    borderColorArgb = 0xFF1D5BFFL,
                    cornerRadiusDp = 18f,
                    hasShadow = true
                ),
                TextObject(
                    zIndex = z++,
                    x = 0.06f,
                    y = 0.17f,
                    width = 0.88f,
                    height = 0.25f,
                    spans = listOf(
                        NotebookTextSpan("1. Puntos fundamentales\n", colorArgb = 0xFF151C2BL, isBold = true, highlightColorArgb = 0xFFFDE047L),
                        NotebookTextSpan("Definición precisa del tema\nFactores principales y causas\nRelación con temas previos", colorArgb = 0xFF151C2BL)
                    ),
                    fontId = NotebookFonts.DEFAULT_BODY_ID,
                    fontSizeNorm = 0.022f,
                    listMode = TextListMode.BULLETS,
                    fillColorArgb = 0xFFFFFFFFL,
                    borderColorArgb = 0xFF151C2BL,
                    cornerRadiusDp = 14f
                ),
                TextObject(
                    zIndex = z++,
                    x = 0.06f,
                    y = 0.45f,
                    width = 0.43f,
                    height = 0.26f,
                    spans = listOf(
                        NotebookTextSpan("¡No olvidar!\n", colorArgb = 0xFFE11D48L, isBold = true),
                        NotebookTextSpan("Anota excepciones, signos de alarma o valores límite (↑ / ↓).", colorArgb = 0xFF151C2BL)
                    ),
                    fontId = NotebookFonts.DEFAULT_BODY_ID,
                    fontSizeNorm = 0.020f,
                    fillColorArgb = 0xFFFFF1F2L,
                    borderColorArgb = 0xFFE11D48L,
                    cornerRadiusDp = 14f
                ),
                TextObject(
                    zIndex = z++,
                    x = 0.52f,
                    y = 0.45f,
                    width = 0.42f,
                    height = 0.26f,
                    spans = listOf(
                        NotebookTextSpan("Nota rápida a mano:\n", colorArgb = 0xFF7C3AEDL),
                        NotebookTextSpan("Truco mnemotécnico para recordar en el examen.", colorArgb = 0xFF334155L)
                    ),
                    fontId = NotebookFonts.DEFAULT_HAND_ID,
                    fontSizeNorm = 0.024f,
                    fillColorArgb = 0xFFFFFBEBL,
                    borderColorArgb = 0xFFF59E0BL,
                    cornerRadiusDp = 14f
                )
            )

            NotebookStarterTemplate.COMPARATIVE_TABLE -> listOf(
                TextObject(
                    zIndex = z++,
                    x = 0.06f,
                    y = 0.04f,
                    width = 0.88f,
                    height = 0.09f,
                    spans = listOf(
                        NotebookTextSpan("CUADRO COMPARATIVO", colorArgb = 0xFF1D5BFFL, isBold = true)
                    ),
                    fontId = NotebookFonts.DEFAULT_BANNER_ID,
                    fontSizeNorm = 0.033f,
                    alignment = TextAlignMode.CENTER,
                    isBanner = true,
                    fillColorArgb = 0xFFEFF6FFL,
                    borderColorArgb = 0xFF1D5BFFL,
                    cornerRadiusDp = 18f,
                    hasShadow = true
                ),
                TableObject(
                    zIndex = z++,
                    x = 0.06f,
                    y = 0.16f,
                    width = 0.88f,
                    height = 0.46f,
                    rows = 4,
                    cols = 3,
                    cells = listOf(
                        "Criterio", "Opción A", "Opción B",
                        "Mecanismo", "Detalle A", "Detalle B",
                        "Ventajas", "Punto fuerte A", "Punto fuerte B",
                        "Efectos / Notas", "Observación A", "Observación B"
                    ),
                    hasHeaderRow = true,
                    headerBgColorArgb = 0xFFEFF6FFL,
                    borderColorArgb = 0xFF334155L
                ),
                TextObject(
                    zIndex = z++,
                    x = 0.06f,
                    y = 0.66f,
                    width = 0.88f,
                    height = 0.16f,
                    spans = listOf(
                        NotebookTextSpan("Conclusión clave: ", colorArgb = 0xFF16A34AL, isBold = true),
                        NotebookTextSpan("Doble toque en la tabla o en esta caja para editar libremente.", colorArgb = 0xFF151C2BL)
                    ),
                    fontId = NotebookFonts.DEFAULT_BODY_ID,
                    fontSizeNorm = 0.020f,
                    fillColorArgb = 0xFFECFDF5L,
                    borderColorArgb = 0xFF16A34AL,
                    cornerRadiusDp = 14f
                )
            )
        }
    }
}
