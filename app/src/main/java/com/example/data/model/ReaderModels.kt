package com.example.data.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontFamily
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LexendFamily
import com.example.ui.theme.LiterataFamily
import com.example.ui.theme.PlusJakartaSansFamily

enum class DocumentFormat(val label: String, val extension: String, val mimeType: String) {
    EPUB("EPUB", "epub", "application/epub+zip"),
    PDF("PDF", "pdf", "application/pdf"),
    DOCX("DOCX", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    PPTX("PPTX", "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
    TXT("TXT", "txt", "text/plain"),
    FB2("FB2", "fb2", "application/x-fictionbook+xml");

    companion object {
        fun fromExtension(ext: String): DocumentFormat? =
            entries.firstOrNull { it.extension.equals(ext.trim().lowercase(), ignoreCase = true) }

        fun fromFileName(name: String): DocumentFormat? {
            val ext = name.substringAfterLast('.', "")
            return fromExtension(ext)
        }
    }
}

enum class ReadingTheme(
    val displayName: String,
    val isDark: Boolean = false,
    val isWarmSoft: Boolean = false,
    val comfortTag: String = ""
) {
    // Warm & Soft Daytime Palettes (low eye strain)
    WARM_CREAM("Crema cálida", isDark = false, isWarmSoft = true, comfortTag = "Papel orgánico"),
    SEPIA("Sepia", isDark = false, isWarmSoft = true, comfortTag = "Clásico cálido"),
    SAND("Arena suave", isDark = false, isWarmSoft = true, comfortTag = "Bajo reflejo"),
    AMBER_SOFT("Ámbar", isDark = false, isWarmSoft = true, comfortTag = "Filtro azul natural"),
    PEACH("Durazno", isDark = false, isWarmSoft = true, comfortTag = "Pastel tibio"),
    SAGE("Salvia", isDark = false, isWarmSoft = true, comfortTag = "Descanso visual"),
    LEMON("Mantequilla", isDark = false, isWarmSoft = true, comfortTag = "Suave solar"),
    LIGHT("Papel", isDark = false, isWarmSoft = false, comfortTag = "Neutro limpio"),
    LAVENDER("Lavanda", isDark = false, isWarmSoft = false, comfortTag = "Pastel sereno"),
    SKY("Cielo", isDark = false, isWarmSoft = false, comfortTag = "Fresco suave"),
    ROSE("Rosa", isDark = false, isWarmSoft = false, comfortTag = "Rubor suave"),

    // Night Reading Modes (low-glare nocturnal palettes)
    WARM_NIGHT("Noche cálida", isDark = true, isWarmSoft = true, comfortTag = "Ámbar sin fatiga"),
    FOREST_NIGHT("Bosque noche", isDark = true, isWarmSoft = true, comfortTag = "Verde sereno"),
    NAVY_MID("Azul marino", isDark = true, isWarmSoft = false, comfortTag = "Suave nocturno"),
    MIDNIGHT("Medianoche", isDark = true, isWarmSoft = false, comfortTag = "Bajo brillo"),
    DARK("Grafito", isDark = true, isWarmSoft = false, comfortTag = "Oscuro neutro"),
    AMOLED("AMOLED", isDark = true, isWarmSoft = false, comfortTag = "Negro puro")
}

enum class ReaderFont(val displayName: String, val subtitle: String) {
    LITERATA("Literata", "Serif editorial"),
    PLUS_JAKARTA("Plus Jakarta", "Sans suave"),
    LEXEND("Lexend", "Dislexia / Fluidez"),
    JETBRAINS_MONO("JetBrains Mono", "Monoespaciada"),
    SYSTEM("Dispositivo", "Fuente del sistema");

    fun toFontFamily(): FontFamily = when (this) {
        LITERATA -> LiterataFamily
        PLUS_JAKARTA -> PlusJakartaSansFamily
        LEXEND -> LexendFamily
        JETBRAINS_MONO -> JetBrainsMonoFamily
        SYSTEM -> FontFamily.Default
    }
}

enum class PageAnimationMode(val displayName: String, val subtitle: String) {
    BOOK_CURL("Libro", "Hoja 3D"),
    E_INK("Tinta electrónica", "Destello Kindle"),
    SLIDE_DECK("Diapositiva", "Deslizamiento"),
    DEPTH_CARD("Tarjeta", "Superposición"),
    FADE("Desvanecer", "Fundido suave"),
    VERTICAL_SCROLL("Scroll continuo", "Webtoon / vertical")
}

enum class ReaderMenuStyle(val displayName: String, val description: String) {
    BOOKMARK_DOCK("Marcapáginas", "Pestaña arrastrable en cualquier lateral"),
    DYNAMIC_ISLAND("Isla dinámica", "Cápsula flotante superior desplegable"),
    CLASSIC_BAR("Clásico", "Barra superior e inferior al tocar el centro")
}

enum class BookmarkDockSide(val displayName: String) {
    RIGHT("Derecha"),
    LEFT("Izquierda")
}

enum class TextAlignOption(val displayName: String) {
    START("Izquierda"),
    JUSTIFY("Justificado")
}

enum class TranslationEngine(val displayName: String) {
    GOOGLE_TRANSLATE("Google Traductor"),
    AI_CUSTOM_API("IA con tu API key")
}

enum class AiProviderType(val displayName: String, val defaultModel: String, val defaultBaseUrl: String) {
    GEMINI("Google Gemini API", "gemini-2.5-flash", "https://generativelanguage.googleapis.com"),
    OPENAI_COMPATIBLE("OpenAI / Compatible", "gpt-4o-mini", "https://api.openai.com")
}

enum class TargetFpsOption(val label: String, val fps: Float) {
    AUTO("Máx", 0f),
    FPS_60("60", 60f),
    FPS_90("90", 90f),
    FPS_120("120", 120f),
    FPS_144("144", 144f),
    FPS_240("240", 240f)
}

enum class StandardPageFormat(
    val displayName: String,
    val shortLabel: String,
    val dimensionsLabel: String,
    val widthPt: Float,
    val heightPt: Float
) {
    AUTO("Auto (Archivo)", "Auto", "Tamaño original", 595f, 842f),
    A4("A4", "A4", "210 × 297 mm", 595f, 842f),
    LETTER("Carta", "Carta", "216 × 279 mm", 612f, 792f),
    LEGAL("Oficio", "Oficio", "216 × 356 mm", 612f, 1008f),
    A3("A3", "A3", "297 × 420 mm", 842f, 1191f),
    A5("A5", "A5", "148 × 210 mm", 420f, 595f),
    SLIDE_16_9("Slide 16:9", "16:9", "Panorámica", 960f, 540f),
    SLIDE_4_3("Slide 4:3", "4:3", "Clásica", 800f, 600f);

    fun resolveSizePt(nativeWidthPt: Float, nativeHeightPt: Float): Pair<Float, Float> =
        if (this == AUTO) {
            nativeWidthPt.coerceIn(240f, 2400f) to nativeHeightPt.coerceIn(240f, 2400f)
        } else {
            widthPt to heightPt
        }
}

enum class IslandNotificationFilterMode(val displayName: String, val subtitle: String) {
    ALLOWED_ONLY("Solo permitidas", "Estados críticos y únicamente apps que autorices"),
    CRITICAL_ONLY("Solo críticos", "Silencia apps externas; solo batería, red y llamadas"),
    ALL_APPS("Todas las apps", "Muestra avisos de cualquier app externa activa")
}

val DEFAULT_ISLAND_ALLOWED_PACKAGES: Set<String> = setOf(
    "com.whatsapp",
    "com.whatsapp.w4b",
    "org.telegram.messenger",
    "org.thoughtcrime.securesms",
    "com.google.android.apps.messaging",
    "com.samsung.android.messaging",
    "com.google.android.dialer",
    "com.android.dialer",
    "com.samsung.android.dialer",
    "com.google.android.gm"
)

@Immutable
data class ReaderPreferences(
    val theme: ReadingTheme = ReadingTheme.LIGHT,
    val useSystemFont: Boolean = false,
    val font: ReaderFont = ReaderFont.LITERATA,
    val fontSizeSp: Float = 18f,
    val lineHeightMultiplier: Float = 1.6f,
    val horizontalMarginDp: Float = 22f,
    val textAlign: TextAlignOption = TextAlignOption.START,
    val pageAnimation: PageAnimationMode = PageAnimationMode.BOOK_CURL,
    val menuStyle: ReaderMenuStyle = ReaderMenuStyle.BOOKMARK_DOCK,
    val dockSide: BookmarkDockSide = BookmarkDockSide.RIGHT,
    val dockYFraction: Float = 0.48f,
    val dockScale: Float = 1.0f, // 0.85f compact .. 1.35f large tablet
    val readerBrightness: Float = -1f, // -1f = system default, 0.05f..1.0f = manual
    val targetFps: TargetFpsOption = TargetFpsOption.AUTO,
    val showFpsCounter: Boolean = false,
    val showMinimalFooter: Boolean = true,
    val tapZonesEnabled: Boolean = true,
    val pdfNightFilter: Boolean = false,
    val autoOcrPdf: Boolean = true,
    val translationEngine: TranslationEngine = TranslationEngine.GOOGLE_TRANSLATE,
    val aiProvider: AiProviderType = AiProviderType.GEMINI,
    val aiApiKey: String = "",
    val aiModel: String = AiProviderType.GEMINI.defaultModel,
    val aiBaseUrl: String = AiProviderType.GEMINI.defaultBaseUrl,
    val islandFilterMode: IslandNotificationFilterMode = IslandNotificationFilterMode.ALLOWED_ONLY,
    val islandAllowedPackages: Set<String> = DEFAULT_ISLAND_ALLOWED_PACKAGES,
    val islandShowCriticalBattery: Boolean = true,
    val islandShowCriticalConnectivity: Boolean = true,
    val islandAllowIncomingCalls: Boolean = true,
    val useStandardPageView: Boolean = false,
    val standardPageFormat: StandardPageFormat = StandardPageFormat.AUTO,
    // Soft Visual Comfort & Configurable Night Reading Modes
    val warmFilterStrength: Float = 0f,
    val nightModeEnabled: Boolean = false,
    val nightAutoSchedule: Boolean = false,
    val nightStartHour: Int = 20,
    val nightEndHour: Int = 7,
    val preferredDayTheme: ReadingTheme = ReadingTheme.WARM_CREAM,
    val preferredNightTheme: ReadingTheme = ReadingTheme.WARM_NIGHT,
    val nightTextDimming: Float = 0f,
    // Auto-hide immersive reading chrome (fast fade-out after reading inactivity)
    val autoHideReaderChromeEnabled: Boolean = true,
    val autoHideReaderChromeDelaySec: Int = 8,
    val autoHideFadeSpeedMs: Int = 220,
    val diskCacheLimitMb: Int = 500,
    val hasSeenGuide: Boolean = false,
    val hasCompletedOnboarding: Boolean = false,
    val seededSampleVersion: Int = 0
)

enum class BlockType {
    HEADING_1,
    HEADING_2,
    PARAGRAPH,
    QUOTE,
    LIST_ITEM
}

@Immutable
data class ParsedBlock(
    val index: Int,
    val type: BlockType,
    val text: String
)

@Immutable
data class ParsedChapter(
    val index: Int,
    val title: String,
    val cfiPrefix: String,
    val href: String,
    val blocks: List<ParsedBlock>,
    val wordCount: Int
)

@Immutable
data class TocEntry(
    val title: String,
    val targetIndex: Int,
    val level: Int = 0
)

enum class SlideTextAlign { LEFT, CENTER, RIGHT, JUSTIFY }
enum class SlideVerticalAlign { TOP, MIDDLE, BOTTOM }
enum class SlideShapeGeometry { RECT, ROUND_RECT, ELLIPSE, LINE }

data class SlideTextRun(
    val text: String,
    val fontSizePt: Float = 16f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val colorArgb: Long? = null
)

data class SlideParagraph(
    val runs: List<SlideTextRun>,
    val align: SlideTextAlign = SlideTextAlign.LEFT,
    val bulletPrefix: String = "",
    val level: Int = 0
)

data class SlideTableCell(
    val text: String,
    val fillColorArgb: Long? = null,
    val textColorArgb: Long? = null,
    val isBold: Boolean = false,
    val fontSizePt: Float = 11f,
    val align: SlideTextAlign = SlideTextAlign.LEFT
)

@Immutable
sealed interface SlideElement {
    val left: Float
    val top: Float
    val width: Float
    val height: Float

    @Immutable
    data class ShapeBox(
        override val left: Float,
        override val top: Float,
        override val width: Float,
        override val height: Float,
        val rotationDeg: Float = 0f,
        val geometry: SlideShapeGeometry = SlideShapeGeometry.RECT,
        val fillColorArgb: Long? = null,
        val gradientEndColorArgb: Long? = null,
        val borderColorArgb: Long? = null,
        val borderWidthPt: Float = 0f,
        val verticalAlign: SlideVerticalAlign = SlideVerticalAlign.TOP,
        val paragraphs: List<SlideParagraph> = emptyList()
    ) : SlideElement

    @Immutable
    data class Picture(
        override val left: Float,
        override val top: Float,
        override val width: Float,
        override val height: Float,
        val rotationDeg: Float = 0f,
        val imagePath: String
    ) : SlideElement

    @Immutable
    data class Table(
        override val left: Float,
        override val top: Float,
        override val width: Float,
        override val height: Float,
        val colWeights: List<Float>,
        val rows: List<List<SlideTableCell>>
    ) : SlideElement
}

@Immutable
data class ParsedSlide(
    val index: Int,
    val title: String,
    val subtitle: String = "",
    val bodyBlocks: List<ParsedBlock> = emptyList(),
    val notes: String = "",
    val imagePaths: List<String> = emptyList(),
    val backgroundColorArgb: Long = 0xFFFFFFFFL,
    val backgroundGradientEndArgb: Long? = null,
    val backgroundImagePath: String? = null,
    val elements: List<SlideElement> = emptyList()
)

@Immutable
data class ParsedDocument(
    val title: String,
    val author: String,
    val format: DocumentFormat,
    val chapters: List<ParsedChapter>,
    val toc: List<TocEntry>,
    val totalWords: Int,
    val pdfPageCount: Int = 0,
    val pdfPageTexts: List<String> = emptyList(),
    val slides: List<ParsedSlide> = emptyList(),
    val nativePageWidthPt: Float = 595f,
    val nativePageHeightPt: Float = 842f,
    val detectedFormatLabel: String = "A4"
)

enum class DrawingTool(val displayName: String) {
    PEN("Lápiz"),
    HIGHLIGHTER("Resaltador"),
    LINE("Línea"),
    ARROW("Flecha"),
    RECTANGLE("Cuadrado"),
    CIRCLE("Círculo"),
    ERASER("Borrador")
}

@Immutable
data class StrokePoint(
    val x: Float,
    val y: Float
)

@Immutable
data class DrawingStroke(
    val id: String,
    val tool: DrawingTool,
    val colorArgb: Long,
    val widthNorm: Float,
    val alpha: Float,
    val points: List<StrokePoint>,
    val isFilled: Boolean = false
)

@Immutable
data class OcrWordBox(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

@Immutable
data class OcrBlockBox(
    val id: Int,
    val originalText: String,
    val translatedText: String? = null,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val words: List<OcrWordBox>,
    val bgColorArgb: Long = 0xFFFFFFFFL,
    val inkColorArgb: Long = 0xFF101828L
)

@Immutable
data class OcrPageResult(
    val pageIndex: Int,
    val blocks: List<OcrBlockBox>,
    val isTranslated: Boolean = false
)

@Immutable
data class SearchMatch(
    val targetIndex: Int,
    val chapterTitle: String,
    val snippet: String,
    val blockIndex: Int = 0
)

@Immutable
data class SelectedWordContext(
    val word: String,
    val snippet: String,
    val locationKey: String,
    val blockIndex: Int,
    val startChar: Int,
    val endChar: Int,
    val anchorXFraction: Float,
    val anchorYFraction: Float,
    val fullBlockText: String = "",
    val blockCharOffset: Int = 0,
    val wordStartChar: Int = startChar,
    val wordEndChar: Int = endChar
)

@Immutable
data class WikipediaSummary(
    val title: String,
    val description: String,
    val extract: String,
    val pageUrl: String
)

enum class ExportFormatOption(val label: String, val extension: String, val mimeType: String) {
    NATIVE_MERGED("Formato original fusionado", "", "*/*"),
    UNIVERSAL_PDF("PDF con texto seleccionable + dibujos", "pdf", "application/pdf")
}
