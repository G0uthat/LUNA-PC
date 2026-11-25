package com.example.notebook.model

import androidx.compose.runtime.Immutable
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Schema v2 unified object model for Study Notebooks (strictly isolated from ReaderScreen's DrawingStroke).
 * All coordinates are normalized to [0f..1f] relative to the page dimensions so notes scale identically
 * across phones, tablets, foldables, and HD PDF/PNG exports.
 */
@Immutable
data class NbPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f
)

enum class NotebookTool(val label: String) {
    PENCIL("Lápiz"),
    PEN("Bolígrafo"),
    HIGHLIGHTER("Resaltar"),
    ERASER("Borrador"),
    SELECT("Lazo"),
    SHAPE("Formas"),
    TEXT_BOX("Texto"),
    IMAGE("Imagen"),
    PAN_ZOOM("Mano"),
    // Extended study tools accessible cleanly from contextual menus & dock
    FINELINER("Fino"),
    TITLE_BANNER("Título"),
    CONNECTOR("Conector"),
    STICKER("Stickers"),
    TABLE("Tabla")
}

enum class NotebookEraserMode(val label: String) {
    STROKE("Por trazo"),
    PIXEL("Por píxel");

    companion object {
        fun fromName(raw: String?): NotebookEraserMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: STROKE
    }
}

enum class NotebookPageSizePreset(val label: String, val widthPx: Int, val heightPx: Int) {
    A4("A4", 1480, 2092),
    LETTER("Carta", 1530, 1980),
    WIDE_16_9("16:9", 1920, 1080),
    SQUARE("Cuadrado", 1600, 1600),
    CUSTOM("Personalizado", 1480, 2092)
}

enum class NotebookAlignAction(val label: String) {
    ALIGN_LEFT("Izq."),
    ALIGN_CENTER_H("Centro H"),
    ALIGN_RIGHT("Der."),
    ALIGN_TOP("Arriba"),
    ALIGN_CENTER_V("Centro V"),
    ALIGN_BOTTOM("Abajo"),
    DISTRIBUTE_H("Dist. H"),
    DISTRIBUTE_V("Dist. V")
}

enum class StrokeToolKind {
    PENCIL,
    PEN,
    FINELINER,
    HIGHLIGHTER,
    MARKER;

    companion object {
        fun fromName(raw: String?): StrokeToolKind =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PEN
    }
}

enum class StrokeBlendMode {
    NORMAL,
    MULTIPLY;

    companion object {
        fun fromName(raw: String?): StrokeBlendMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: NORMAL
    }
}

enum class NotebookShapeKind(val label: String, val isTapStamp: Boolean = false) {
    ARROW("Flecha"),
    DOUBLE_ARROW("Flecha doble"),
    CURVED_ARROW("Flecha curva"),
    POLY_ARROW("Flecha 2 tramos"),
    BRACE_LEFT("Llave {"),
    BRACE_RIGHT("Llave }"),
    BRACKET_LEFT("Corchete ["),
    BRACKET_RIGHT("Corchete ]"),
    CALLOUT("Globo con cola"),
    ROUNDED_RECT("Recuadro"),
    ELLIPSE("Elipse"),
    LINE("Línea"),
    BADGE_PLUS("Círculo +", isTapStamp = true),
    BADGE_MINUS("Círculo −", isTapStamp = true),
    CHECKMARK("Marca ✓", isTapStamp = true),
    CROSS_MARK("Marca ✗", isTapStamp = true),
    STAR("Estrella ★", isTapStamp = true),
    NUMBER_BADGE("Número ①", isTapStamp = true);

    companion object {
        fun fromName(raw: String?): NotebookShapeKind = when (raw?.uppercase()) {
            "RECTANGLE" -> ROUNDED_RECT
            "CIRCLE" -> ELLIPSE
            else -> entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ARROW
        }
    }
}

enum class TextAlignMode(val label: String) {
    START("Izquierda"),
    CENTER("Centro"),
    END("Derecha");

    companion object {
        fun fromName(raw: String?): TextAlignMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: START
    }
}

enum class TextListMode(val label: String) {
    NONE("Normal"),
    BULLETS("Viñetas •"),
    NUMBERED("Numerado 1.");

    companion object {
        fun fromName(raw: String?): TextListMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: NONE
    }
}

enum class NotebookFontId(val id: String, val label: String) {
    SANS("sans", "Moderna"),
    SERIF("serif", "Editorial"),
    MONO("mono", "Monoesp."),
    ROUNDED("rounded", "Suave");

    companion object {
        fun fromId(raw: String?): NotebookFontId =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: SANS
    }
}

enum class BuiltInStickerKind(val label: String) {
    CHECK("Check ✓"),
    CROSS("Error ✕"),
    PLUS_CIRCLE("Positivo +"),
    MINUS_CIRCLE("Negativo −"),
    ALERT("Alerta !"),
    STAR("Clave ★"),
    HEART("Cardio ♥"),
    BRAIN("Neuro"),
    PILL("Fármaco"),
    LIGHTBULB("Idea"),
    PIN("Fijar"),
    ARROW_UP("Aumenta ↑"),
    ARROW_DOWN("Disminuye ↓"),
    ARROW_RIGHT("Flecha →"),
    SYRINGE("Jeringa"),
    MAGNIFIER("Lupa");

    companion object {
        fun fromName(raw: String?): BuiltInStickerKind =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: CHECK
    }
}

enum class NotebookStarterTemplate(val label: String, val subtitle: String) {
    CORNELL("Cornell", "Preguntas clave, apuntes y resumen"),
    CONCEPT_MAP("Mapa conceptual", "Idea central + 4 nodos con conectores"),
    TITLE_SUMMARY("Resumen con título", "Banner Poppins + bloques y nota a mano"),
    COMPARATIVE_TABLE("Tabla comparativa", "Banner + tabla editable 4×3 + conclusión")
}

@Immutable
data class NotebookQuickStyle(
    val id: String,
    val name: String,
    val fontId: String,
    val textColorArgb: Long,
    val fillColorArgb: Long? = null,
    val borderColorArgb: Long? = null,
    val highlightColorArgb: Long? = null,
    val fontSizeNorm: Float = 0.022f,
    val isBanner: Boolean = false,
    val isBold: Boolean = false,
    val isBuiltIn: Boolean = false
) {
    companion object {
        val BUILT_IN: List<NotebookQuickStyle> = listOf(
            NotebookQuickStyle(
                id = "preset_title",
                name = "Título",
                fontId = "poppins",
                textColorArgb = 0xFF1D5BFFL,
                fillColorArgb = 0xFFFFFFFFL,
                borderColorArgb = 0xFF1D5BFFL,
                fontSizeNorm = 0.036f,
                isBanner = true,
                isBold = true,
                isBuiltIn = true
            ),
            NotebookQuickStyle(
                id = "preset_subtitle",
                name = "Subtítulo",
                fontId = "montserrat",
                textColorArgb = 0xFF151C2BL,
                fillColorArgb = 0xFFEFF6FFL,
                borderColorArgb = 0xFF93C5FDL,
                fontSizeNorm = 0.026f,
                isBold = true,
                isBuiltIn = true
            ),
            NotebookQuickStyle(
                id = "preset_red_note",
                name = "Nota roja",
                fontId = "open_sans",
                textColorArgb = 0xFFE11D48L,
                fillColorArgb = 0xFFFFF1F2L,
                borderColorArgb = 0xFFE11D48L,
                fontSizeNorm = 0.021f,
                isBold = true,
                isBuiltIn = true
            ),
            NotebookQuickStyle(
                id = "preset_highlight",
                name = "Resaltado",
                fontId = "open_sans",
                textColorArgb = 0xFF151C2BL,
                fillColorArgb = 0xFFFFFBEBL,
                borderColorArgb = 0xFFF59E0BL,
                highlightColorArgb = 0xFFFDE047L,
                fontSizeNorm = 0.022f,
                isBold = false,
                isBuiltIn = true
            ),
            NotebookQuickStyle(
                id = "preset_hand",
                name = "A mano",
                fontId = "caveat",
                textColorArgb = 0xFF7C3AEDL,
                fillColorArgb = 0xFFFAF5FFL,
                borderColorArgb = 0xFFC4B5FDL,
                fontSizeNorm = 0.025f,
                isBold = false,
                isBuiltIn = true
            )
        )
    }
}

object NotebookLayerIds {
    const val UNDERLAY = "underlay"
    const val CONTENT = "content"
    const val INK = "ink"

    val ORDERED = listOf(UNDERLAY, CONTENT, INK)
}

/**
 * Per-layer metadata: supports adding, hiding, locking, reordering, and per-layer opacity.
 * Bottom-to-top render order matches list order in `NotebookPageContent.layers`.
 */
@Immutable
data class NotebookLayerInfo(
    val id: String,
    val name: String,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val opacity: Float = 1f
) {
    companion object {
        val DEFAULT_LAYERS: List<NotebookLayerInfo> = listOf(
            NotebookLayerInfo(NotebookLayerIds.UNDERLAY, "Resaltador", visible = true, locked = false, opacity = 1f),
            NotebookLayerInfo(NotebookLayerIds.CONTENT, "Texto e Imágenes", visible = true, locked = false, opacity = 1f),
            NotebookLayerInfo(NotebookLayerIds.INK, "Tinta y Dibujo", visible = true, locked = false, opacity = 1f)
        )
    }
}

@Immutable
data class NotebookTextSpan(
    val text: String,
    val colorArgb: Long = 0xFF151C2BL,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikethrough: Boolean = false,
    val isSuperscript: Boolean = false,
    val isSubscript: Boolean = false,
    val highlightColorArgb: Long? = null
)

@Immutable
sealed class NotebookObject {
    abstract val id: String
    abstract val zIndex: Int
    abstract val layerId: String
    abstract val locked: Boolean
    abstract val visible: Boolean
    abstract val groupId: String?
    abstract val x: Float
    abstract val y: Float
    abstract val width: Float
    abstract val height: Float
    abstract val rotationDeg: Float
    abstract val objectOpacity: Float

    val centerX: Float get() = x + width * 0.5f
    val centerY: Float get() = y + height * 0.5f

    abstract fun movedBy(dx: Float, dy: Float): NotebookObject
    abstract fun resizedTo(newX: Float, newY: Float, newW: Float, newH: Float): NotebookObject
    abstract fun rotatedTo(deg: Float): NotebookObject
    abstract fun withZIndex(z: Int): NotebookObject
    abstract fun withLocked(isLocked: Boolean): NotebookObject
    abstract fun withVisible(isVisible: Boolean): NotebookObject
    abstract fun withLayer(layer: String): NotebookObject
    abstract fun withGroup(group: String?): NotebookObject
    abstract fun withOpacity(newOpacity: Float): NotebookObject
    abstract fun flippedHorizontal(): NotebookObject
    abstract fun flippedVertical(): NotebookObject
    abstract fun duplicateOffset(): NotebookObject
}

@Immutable
data class StrokeObject(
    override val id: String = UUID.randomUUID().toString(),
    override val zIndex: Int = 20,
    override val layerId: String = NotebookLayerIds.INK,
    override val locked: Boolean = false,
    override val visible: Boolean = true,
    override val groupId: String? = null,
    override val x: Float = 0f,
    override val y: Float = 0f,
    override val width: Float = 1f,
    override val height: Float = 1f,
    override val rotationDeg: Float = 0f,
    val tool: StrokeToolKind = StrokeToolKind.PEN,
    val colorArgb: Long = 0xFF151C2BL,
    val widthNorm: Float = 0.0042f,
    val opacity: Float = 1f,
    val points: List<NbPoint>,
    val blendMode: StrokeBlendMode = StrokeBlendMode.NORMAL
) : NotebookObject() {
    override val objectOpacity: Float get() = opacity

    val isHighlighterUnderlay: Boolean
        get() = tool == StrokeToolKind.HIGHLIGHTER || blendMode == StrokeBlendMode.MULTIPLY

    override fun movedBy(dx: Float, dy: Float): StrokeObject = copy(
        x = x + dx,
        y = y + dy,
        points = points.map { it.copy(x = it.x + dx, y = it.y + dy) }
    )

    override fun resizedTo(newX: Float, newY: Float, newW: Float, newH: Float): StrokeObject {
        val oldW = width.coerceAtLeast(0.001f)
        val oldH = height.coerceAtLeast(0.001f)
        return copy(
            x = newX,
            y = newY,
            width = newW.coerceAtLeast(0.01f),
            height = newH.coerceAtLeast(0.01f),
            points = points.map { pt ->
                pt.copy(
                    x = newX + ((pt.x - x) / oldW) * newW,
                    y = newY + ((pt.y - y) / oldH) * newH
                )
            }
        )
    }

    override fun rotatedTo(deg: Float): StrokeObject = copy(rotationDeg = deg)
    override fun withZIndex(z: Int): StrokeObject = copy(zIndex = z)
    override fun withLocked(isLocked: Boolean): StrokeObject = copy(locked = isLocked)
    override fun withVisible(isVisible: Boolean): StrokeObject = copy(visible = isVisible)
    override fun withLayer(layer: String): StrokeObject = copy(layerId = layer)
    override fun withGroup(group: String?): StrokeObject = copy(groupId = group)
    override fun withOpacity(newOpacity: Float): StrokeObject = copy(opacity = newOpacity.coerceIn(0.05f, 1f))
    override fun flippedHorizontal(): StrokeObject {
        val cx = centerX
        return copy(points = points.map { it.copy(x = cx * 2f - it.x) })
    }
    override fun flippedVertical(): StrokeObject {
        val cy = centerY
        return copy(points = points.map { it.copy(y = cy * 2f - it.y) })
    }
    override fun duplicateOffset(): StrokeObject = movedBy(0.03f, 0.03f).copy(
        id = UUID.randomUUID().toString(),
        zIndex = zIndex + 1
    )

    companion object {
        fun computeBounds(points: List<NbPoint>): FloatArray {
            if (points.isEmpty()) return floatArrayOf(0f, 0f, 0.01f, 0.01f)
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            for (p in points) {
                minX = min(minX, p.x)
                minY = min(minY, p.y)
                maxX = max(maxX, p.x)
                maxY = max(maxY, p.y)
            }
            return floatArrayOf(minX, minY, (maxX - minX).coerceAtLeast(0.01f), (maxY - minY).coerceAtLeast(0.01f))
        }
    }
}

@Immutable
data class ShapeObject(
    override val id: String = UUID.randomUUID().toString(),
    override val zIndex: Int = 15,
    override val layerId: String = NotebookLayerIds.CONTENT,
    override val locked: Boolean = false,
    override val visible: Boolean = true,
    override val groupId: String? = null,
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
    override val rotationDeg: Float = 0f,
    val kind: NotebookShapeKind,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val midX: Float? = null,
    val midY: Float? = null,
    val strokeColorArgb: Long = 0xFF1D5BFFL,
    val fillColorArgb: Long? = null,
    val strokeWidthNorm: Float = 0.0045f,
    val isDashed: Boolean = false,
    val cornerRadiusDp: Float = 14f,
    val opacity: Float = 1f,
    val hasShadow: Boolean = false,
    val labelText: String = "",
    val labelBgColorArgb: Long = 0xFFFFF8D6L
) : NotebookObject() {
    override val objectOpacity: Float get() = opacity

    override fun movedBy(dx: Float, dy: Float): ShapeObject = copy(
        x = (x + dx).coerceIn(-0.15f, 0.96f),
        y = (y + dy).coerceIn(-0.15f, 0.96f),
        startX = (startX + dx).coerceIn(-0.15f, 1.08f),
        startY = (startY + dy).coerceIn(-0.15f, 1.08f),
        endX = (endX + dx).coerceIn(-0.15f, 1.08f),
        endY = (endY + dy).coerceIn(-0.15f, 1.08f),
        midX = midX?.let { (it + dx).coerceIn(-0.15f, 1.08f) },
        midY = midY?.let { (it + dy).coerceIn(-0.15f, 1.08f) }
    )

    override fun resizedTo(newX: Float, newY: Float, newW: Float, newH: Float): ShapeObject {
        val oldW = width.coerceAtLeast(0.001f)
        val oldH = height.coerceAtLeast(0.001f)
        fun mapX(px: Float): Float = newX + ((px - x) / oldW) * newW
        fun mapY(py: Float): Float = newY + ((py - y) / oldH) * newH
        return copy(
            x = newX,
            y = newY,
            width = newW.coerceIn(0.03f, 0.98f),
            height = newH.coerceIn(0.03f, 0.98f),
            startX = mapX(startX),
            startY = mapY(startY),
            endX = mapX(endX),
            endY = mapY(endY),
            midX = midX?.let(::mapX),
            midY = midY?.let(::mapY)
        )
    }

    override fun rotatedTo(deg: Float): ShapeObject = copy(rotationDeg = deg)
    override fun withZIndex(z: Int): ShapeObject = copy(zIndex = z)
    override fun withLocked(isLocked: Boolean): ShapeObject = copy(locked = isLocked)
    override fun withVisible(isVisible: Boolean): ShapeObject = copy(visible = isVisible)
    override fun withLayer(layer: String): ShapeObject = copy(layerId = layer)
    override fun withGroup(group: String?): ShapeObject = copy(groupId = group)
    override fun withOpacity(newOpacity: Float): ShapeObject = copy(opacity = newOpacity.coerceIn(0.05f, 1f))
    override fun flippedHorizontal(): ShapeObject {
        val cx = centerX
        val nextKind = when (kind) {
            NotebookShapeKind.BRACE_LEFT -> NotebookShapeKind.BRACE_RIGHT
            NotebookShapeKind.BRACE_RIGHT -> NotebookShapeKind.BRACE_LEFT
            NotebookShapeKind.BRACKET_LEFT -> NotebookShapeKind.BRACKET_RIGHT
            NotebookShapeKind.BRACKET_RIGHT -> NotebookShapeKind.BRACKET_LEFT
            else -> kind
        }
        return copy(
            kind = nextKind,
            startX = cx * 2f - startX,
            endX = cx * 2f - endX,
            midX = midX?.let { cx * 2f - it }
        )
    }
    override fun flippedVertical(): ShapeObject {
        val cy = centerY
        return copy(
            startY = cy * 2f - startY,
            endY = cy * 2f - endY,
            midY = midY?.let { cy * 2f - it }
        )
    }
    override fun duplicateOffset(): ShapeObject = movedBy(0.03f, 0.03f).copy(
        id = UUID.randomUUID().toString(),
        zIndex = zIndex + 1
    )
}

@Immutable
data class ConnectorObject(
    override val id: String = UUID.randomUUID().toString(),
    override val zIndex: Int = 18,
    override val layerId: String = NotebookLayerIds.CONTENT,
    override val locked: Boolean = false,
    override val visible: Boolean = true,
    override val groupId: String? = null,
    override val x: Float = 0f,
    override val y: Float = 0f,
    override val width: Float = 0.1f,
    override val height: Float = 0.1f,
    override val rotationDeg: Float = 0f,
    val fromObjectId: String,
    val toObjectId: String,
    val colorArgb: Long = 0xFF1D5BFFL,
    val strokeWidthNorm: Float = 0.0042f,
    val isDashed: Boolean = false,
    val isCurved: Boolean = true,
    val doubleHeaded: Boolean = false,
    val opacity: Float = 1f,
    val labelText: String = ""
) : NotebookObject() {
    override val objectOpacity: Float get() = opacity
    override fun movedBy(dx: Float, dy: Float): ConnectorObject = this
    override fun resizedTo(newX: Float, newY: Float, newW: Float, newH: Float): ConnectorObject = this
    override fun rotatedTo(deg: Float): ConnectorObject = this
    override fun withZIndex(z: Int): ConnectorObject = copy(zIndex = z)
    override fun withLocked(isLocked: Boolean): ConnectorObject = copy(locked = isLocked)
    override fun withVisible(isVisible: Boolean): ConnectorObject = copy(visible = isVisible)
    override fun withLayer(layer: String): ConnectorObject = copy(layerId = layer)
    override fun withGroup(group: String?): ConnectorObject = copy(groupId = group)
    override fun withOpacity(newOpacity: Float): ConnectorObject = copy(opacity = newOpacity.coerceIn(0.05f, 1f))
    override fun flippedHorizontal(): ConnectorObject = copy(fromObjectId = toObjectId, toObjectId = fromObjectId)
    override fun flippedVertical(): ConnectorObject = copy(fromObjectId = toObjectId, toObjectId = fromObjectId)
    override fun duplicateOffset(): ConnectorObject = copy(
        id = UUID.randomUUID().toString(),
        zIndex = zIndex + 1
    )
}

@Immutable
data class ImageObject(
    override val id: String = UUID.randomUUID().toString(),
    override val zIndex: Int = 5,
    override val layerId: String = NotebookLayerIds.CONTENT,
    override val locked: Boolean = false,
    override val visible: Boolean = true,
    override val groupId: String? = null,
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
    override val rotationDeg: Float = 0f,
    val relativeAssetPath: String,
    val opacity: Float = 1f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    val borderColorArgb: Long? = null,
    val borderWidthDp: Float = 2f,
    val cornerRadiusDp: Float = 10f,
    val hasShadow: Boolean = false,
    val isOvalCrop: Boolean = false
) : NotebookObject() {
    override val objectOpacity: Float get() = opacity
    override fun movedBy(dx: Float, dy: Float): ImageObject = copy(
        x = (x + dx).coerceIn(-0.25f, 0.95f),
        y = (y + dy).coerceIn(-0.25f, 0.95f)
    )
    override fun resizedTo(newX: Float, newY: Float, newW: Float, newH: Float): ImageObject = copy(
        x = newX.coerceIn(-0.25f, 0.95f),
        y = newY.coerceIn(-0.25f, 0.95f),
        width = newW.coerceIn(0.06f, 0.98f),
        height = newH.coerceIn(0.06f, 0.98f)
    )
    override fun rotatedTo(deg: Float): ImageObject = copy(rotationDeg = deg)
    override fun withZIndex(z: Int): ImageObject = copy(zIndex = z)
    override fun withLocked(isLocked: Boolean): ImageObject = copy(locked = isLocked)
    override fun withVisible(isVisible: Boolean): ImageObject = copy(visible = isVisible)
    override fun withLayer(layer: String): ImageObject = copy(layerId = layer)
    override fun withGroup(group: String?): ImageObject = copy(groupId = group)
    override fun withOpacity(newOpacity: Float): ImageObject = copy(opacity = newOpacity.coerceIn(0.05f, 1f))
    override fun flippedHorizontal(): ImageObject = copy(flipHorizontal = !flipHorizontal)
    override fun flippedVertical(): ImageObject = copy(flipVertical = !flipVertical)
    override fun duplicateOffset(): ImageObject = copy(
        id = UUID.randomUUID().toString(),
        x = (x + 0.03f).coerceAtMost(0.85f),
        y = (y + 0.03f).coerceAtMost(0.85f),
        zIndex = zIndex + 1
    )
}

@Immutable
data class TextObject(
    override val id: String = UUID.randomUUID().toString(),
    override val zIndex: Int = 10,
    override val layerId: String = NotebookLayerIds.CONTENT,
    override val locked: Boolean = false,
    override val visible: Boolean = true,
    override val groupId: String? = null,
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
    override val rotationDeg: Float = 0f,
    val spans: List<NotebookTextSpan>,
    val fontId: String = "open_sans",
    val fontSizeNorm: Float = 0.022f,
    val lineSpacingMult: Float = 1.18f,
    val alignment: TextAlignMode = TextAlignMode.START,
    val listMode: TextListMode = TextListMode.NONE,
    val isBanner: Boolean = false,
    val fillColorArgb: Long? = null,
    val borderColorArgb: Long? = null,
    val borderWidthDp: Float = 2f,
    val cornerRadiusDp: Float = 14f,
    val hasShadow: Boolean = false,
    val opacity: Float = 1f
) : NotebookObject() {
    override val objectOpacity: Float get() = opacity
    override fun movedBy(dx: Float, dy: Float): TextObject = copy(
        x = (x + dx).coerceIn(-0.2f, 0.96f),
        y = (y + dy).coerceIn(-0.1f, 0.96f)
    )
    override fun resizedTo(newX: Float, newY: Float, newW: Float, newH: Float): TextObject = copy(
        x = newX.coerceIn(-0.2f, 0.95f),
        y = newY.coerceIn(-0.1f, 0.95f),
        width = newW.coerceIn(0.08f, 0.98f),
        height = newH.coerceIn(0.04f, 0.95f)
    )
    override fun rotatedTo(deg: Float): TextObject = copy(rotationDeg = deg)
    override fun withZIndex(z: Int): TextObject = copy(zIndex = z)
    override fun withLocked(isLocked: Boolean): TextObject = copy(locked = isLocked)
    override fun withVisible(isVisible: Boolean): TextObject = copy(visible = isVisible)
    override fun withLayer(layer: String): TextObject = copy(layerId = layer)
    override fun withGroup(group: String?): TextObject = copy(groupId = group)
    override fun withOpacity(newOpacity: Float): TextObject = copy(opacity = newOpacity.coerceIn(0.05f, 1f))
    override fun flippedHorizontal(): TextObject = copy(
        alignment = when (alignment) {
            TextAlignMode.START -> TextAlignMode.END
            TextAlignMode.END -> TextAlignMode.START
            TextAlignMode.CENTER -> TextAlignMode.CENTER
        }
    )
    override fun flippedVertical(): TextObject = this
    override fun duplicateOffset(): TextObject = copy(
        id = UUID.randomUUID().toString(),
        x = (x + 0.03f).coerceAtMost(0.85f),
        y = (y + 0.03f).coerceAtMost(0.85f),
        zIndex = zIndex + 1
    )
}

@Immutable
data class StickerObject(
    override val id: String = UUID.randomUUID().toString(),
    override val zIndex: Int = 16,
    override val layerId: String = NotebookLayerIds.CONTENT,
    override val locked: Boolean = false,
    override val visible: Boolean = true,
    override val groupId: String? = null,
    override val x: Float,
    override val y: Float,
    override val width: Float = 0.085f,
    override val height: Float = 0.062f,
    override val rotationDeg: Float = 0f,
    val builtInKind: BuiltInStickerKind = BuiltInStickerKind.CHECK,
    val customAssetPath: String? = null,
    val colorArgb: Long = 0xFF16A34AL,
    val bgColorArgb: Long? = null,
    val opacity: Float = 1f
) : NotebookObject() {
    override val objectOpacity: Float get() = opacity
    override fun movedBy(dx: Float, dy: Float): StickerObject = copy(
        x = (x + dx).coerceIn(-0.1f, 0.95f),
        y = (y + dy).coerceIn(-0.1f, 0.95f)
    )
    override fun resizedTo(newX: Float, newY: Float, newW: Float, newH: Float): StickerObject = copy(
        x = newX.coerceIn(-0.1f, 0.95f),
        y = newY.coerceIn(-0.1f, 0.95f),
        width = newW.coerceIn(0.035f, 0.65f),
        height = newH.coerceIn(0.035f, 0.65f)
    )
    override fun rotatedTo(deg: Float): StickerObject = copy(rotationDeg = deg)
    override fun withZIndex(z: Int): StickerObject = copy(zIndex = z)
    override fun withLocked(isLocked: Boolean): StickerObject = copy(locked = isLocked)
    override fun withVisible(isVisible: Boolean): StickerObject = copy(visible = isVisible)
    override fun withLayer(layer: String): StickerObject = copy(layerId = layer)
    override fun withGroup(group: String?): StickerObject = copy(groupId = group)
    override fun withOpacity(newOpacity: Float): StickerObject = copy(opacity = newOpacity.coerceIn(0.05f, 1f))
    override fun flippedHorizontal(): StickerObject = copy(rotationDeg = (360f - rotationDeg) % 360f)
    override fun flippedVertical(): StickerObject = copy(rotationDeg = (180f - rotationDeg + 360f) % 360f)
    override fun duplicateOffset(): StickerObject = copy(
        id = UUID.randomUUID().toString(),
        x = (x + 0.03f).coerceAtMost(0.9f),
        y = (y + 0.03f).coerceAtMost(0.9f),
        zIndex = zIndex + 1
    )
}

@Immutable
data class TableObject(
    override val id: String = UUID.randomUUID().toString(),
    override val zIndex: Int = 12,
    override val layerId: String = NotebookLayerIds.CONTENT,
    override val locked: Boolean = false,
    override val visible: Boolean = true,
    override val groupId: String? = null,
    override val x: Float,
    override val y: Float,
    override val width: Float = 0.72f,
    override val height: Float = 0.24f,
    override val rotationDeg: Float = 0f,
    val rows: Int = 3,
    val cols: Int = 3,
    val cells: List<String> = List(9) { "" },
    val hasHeaderRow: Boolean = true,
    val borderColorArgb: Long = 0xFF334155L,
    val headerBgColorArgb: Long = 0xFFEFF6FFL,
    val cellBgColorArgb: Long = 0xFFFFFFFFL,
    val textColorArgb: Long = 0xFF151C2BL,
    val fontSizeNorm: Float = 0.018f,
    val opacity: Float = 1f
) : NotebookObject() {
    override val objectOpacity: Float get() = opacity
    override fun movedBy(dx: Float, dy: Float): TableObject = copy(
        x = (x + dx).coerceIn(-0.15f, 0.94f),
        y = (y + dy).coerceIn(-0.1f, 0.94f)
    )
    override fun resizedTo(newX: Float, newY: Float, newW: Float, newH: Float): TableObject = copy(
        x = newX.coerceIn(-0.15f, 0.94f),
        y = newY.coerceIn(-0.1f, 0.94f),
        width = newW.coerceIn(0.16f, 0.98f),
        height = newH.coerceIn(0.08f, 0.92f)
    )
    override fun rotatedTo(deg: Float): TableObject = copy(rotationDeg = deg)
    override fun withZIndex(z: Int): TableObject = copy(zIndex = z)
    override fun withLocked(isLocked: Boolean): TableObject = copy(locked = isLocked)
    override fun withVisible(isVisible: Boolean): TableObject = copy(visible = isVisible)
    override fun withLayer(layer: String): TableObject = copy(layerId = layer)
    override fun withGroup(group: String?): TableObject = copy(groupId = group)
    override fun withOpacity(newOpacity: Float): TableObject = copy(opacity = newOpacity.coerceIn(0.05f, 1f))
    override fun flippedHorizontal(): TableObject = this
    override fun flippedVertical(): TableObject = this
    override fun duplicateOffset(): TableObject = copy(
        id = UUID.randomUUID().toString(),
        x = (x + 0.03f).coerceAtMost(0.85f),
        y = (y + 0.03f).coerceAtMost(0.85f),
        zIndex = zIndex + 1
    )
}

@Immutable
data class NotebookPageContent(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val objects: List<NotebookObject> = emptyList(),
    val layers: List<NotebookLayerInfo> = NotebookLayerInfo.DEFAULT_LAYERS,
    val revision: Long = 0L
) {
    fun nextZIndex(): Int = (objects.maxOfOrNull { it.zIndex } ?: 0) + 1

    fun findById(id: String): NotebookObject? = objects.firstOrNull { it.id == id }

    fun resolvedLayers(): List<NotebookLayerInfo> {
        val base = if (layers.isEmpty()) NotebookLayerInfo.DEFAULT_LAYERS else layers
        val existingIds = base.map { it.id }.toSet()
        val extras = objects.map { it.layerId }
            .distinct()
            .filterNot { it in existingIds }
            .map { NotebookLayerInfo(id = it, name = it.replaceFirstChar { c -> c.uppercase() }) }
        return if (extras.isEmpty()) base else base + extras
    }

    fun isLayerLocked(layerId: String): Boolean =
        resolvedLayers().firstOrNull { it.id == layerId }?.locked == true

    fun isLayerVisible(layerId: String): Boolean =
        resolvedLayers().firstOrNull { it.id == layerId }?.visible != false

    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
    }
}

/**
 * Command Pattern for UNLIMITED, tiny-memory per-page Undo / Redo (stores only delta commands!).
 */
sealed interface NotebookCommand {
    fun apply(content: NotebookPageContent): NotebookPageContent
    fun revert(content: NotebookPageContent): NotebookPageContent

    data class AddObject(val obj: NotebookObject) : NotebookCommand {
        override fun apply(content: NotebookPageContent): NotebookPageContent =
            content.copy(objects = content.objects + obj, revision = content.revision + 1)

        override fun revert(content: NotebookPageContent): NotebookPageContent =
            content.copy(objects = content.objects.filterNot { it.id == obj.id }, revision = content.revision + 1)
    }

    data class AddObjects(val added: List<NotebookObject>) : NotebookCommand {
        private val ids: Set<String> = added.map { it.id }.toSet()

        override fun apply(content: NotebookPageContent): NotebookPageContent =
            content.copy(objects = content.objects + added, revision = content.revision + 1)

        override fun revert(content: NotebookPageContent): NotebookPageContent =
            content.copy(objects = content.objects.filterNot { it.id in ids }, revision = content.revision + 1)
    }

    data class RemoveObjects(val removed: List<NotebookObject>) : NotebookCommand {
        private val ids: Set<String> = removed.map { it.id }.toSet()

        override fun apply(content: NotebookPageContent): NotebookPageContent =
            content.copy(objects = content.objects.filterNot { it.id in ids }, revision = content.revision + 1)

        override fun revert(content: NotebookPageContent): NotebookPageContent =
            content.copy(objects = content.objects + removed, revision = content.revision + 1)
    }

    data class ReplaceObject(val before: NotebookObject, val after: NotebookObject) : NotebookCommand {
        override fun apply(content: NotebookPageContent): NotebookPageContent =
            content.copy(
                objects = content.objects.map { if (it.id == after.id) after else it },
                revision = content.revision + 1
            )

        override fun revert(content: NotebookPageContent): NotebookPageContent =
            content.copy(
                objects = content.objects.map { if (it.id == before.id) before else it },
                revision = content.revision + 1
            )
    }

    data class ReplaceObjects(
        val beforeList: List<NotebookObject>,
        val afterList: List<NotebookObject>
    ) : NotebookCommand {
        private val beforeIds = beforeList.map { it.id }.toSet()
        private val afterIds = afterList.map { it.id }.toSet()

        override fun apply(content: NotebookPageContent): NotebookPageContent {
            val remaining = content.objects.filterNot { it.id in beforeIds }
            return content.copy(objects = remaining + afterList, revision = content.revision + 1)
        }

        override fun revert(content: NotebookPageContent): NotebookPageContent {
            val remaining = content.objects.filterNot { it.id in afterIds }
            return content.copy(objects = remaining + beforeList, revision = content.revision + 1)
        }
    }

    data class UpdateLayers(
        val before: List<NotebookLayerInfo>,
        val after: List<NotebookLayerInfo>
    ) : NotebookCommand {
        override fun apply(content: NotebookPageContent): NotebookPageContent =
            content.copy(layers = after, revision = content.revision + 1)

        override fun revert(content: NotebookPageContent): NotebookPageContent =
            content.copy(layers = before, revision = content.revision + 1)
    }

    data class ClearPage(val previousObjects: List<NotebookObject>) : NotebookCommand {
        override fun apply(content: NotebookPageContent): NotebookPageContent =
            content.copy(objects = emptyList(), revision = content.revision + 1)

        override fun revert(content: NotebookPageContent): NotebookPageContent =
            content.copy(objects = previousObjects, revision = content.revision + 1)
    }
}
