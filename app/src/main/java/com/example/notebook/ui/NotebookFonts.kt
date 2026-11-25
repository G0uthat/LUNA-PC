package com.example.notebook.ui

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.FontRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.res.ResourcesCompat
import com.example.R
import java.util.concurrent.ConcurrentHashMap

enum class NotebookFontCategory(val label: String) {
    TITLES("Títulos / Banners"),
    BODY("Cuerpo mecanografiado"),
    HANDWRITTEN("Manuscritas"),
    CALLIGRAPHIC("Caligráficas"),
    SERIF("Serif de lectura"),
    MONO("Monoespaciada")
}

@Immutable
data class NotebookFontSpec(
    val id: String,
    val displayName: String,
    val category: NotebookFontCategory,
    @FontRes val resId: Int,
    val supportsBold: Boolean,
    val supportsItalic: Boolean,
    val licenseName: String,
    val attribution: String,
    val sampleHint: String = "Áéíóú Ñ · H₂O · Ca²⁺"
)

/**
 * Strictly lazy, isolated Study Notebook font catalog.
 *
 * IMPORTANT:
 * - NEVER imported or referenced by `Theme.kt` or `MainActivity.kt` so cold startup incurs ZERO font load overhead.
 * - `TextObject.fontId` persists ONLY stable string IDs (e.g. `"poppins"`, `"caveat"`), NEVER integer `R.font.*` IDs.
 * - Includes automatic legacy alias migration (`"sans"` -> `"open_sans"`, `"serif"` -> `"literata"`, etc.)
 *   and safe fallback to `"open_sans"` for unknown future/past IDs.
 * - Single-weight handwritten/calligraphic/display fonts disable Bold/Italic toggles cleanly so no ugly fake bolding occurs.
 */
object NotebookFonts {

    const val DEFAULT_BANNER_ID = "poppins"
    const val DEFAULT_BODY_ID = "open_sans"
    const val DEFAULT_HAND_ID = "caveat"
    const val DEFAULT_DIAGRAM_ID = "nunito"

    val CATALOG: List<NotebookFontSpec> = listOf(
        // ---- TÍTULOS / BANNERS ----
        NotebookFontSpec(
            id = "poppins",
            displayName = "Poppins",
            category = NotebookFontCategory.TITLES,
            resId = R.font.poppins,
            supportsBold = true,
            supportsItalic = false,
            licenseName = "SIL OFL 1.1",
            attribution = "Indian Type Foundry, Jonny Pinhorn",
            sampleHint = "TÍTULO DE APUNTES"
        ),
        NotebookFontSpec(
            id = "montserrat",
            displayName = "Montserrat",
            category = NotebookFontCategory.TITLES,
            resId = R.font.montserrat,
            supportsBold = true,
            supportsItalic = false,
            licenseName = "SIL OFL 1.1",
            attribution = "Julieta Ulanovsky, Sol Matas, Juan Pablo del Peral"
        ),
        NotebookFontSpec(
            id = "baloo_2",
            displayName = "Baloo 2",
            category = NotebookFontCategory.TITLES,
            resId = R.font.baloo_2,
            supportsBold = true,
            supportsItalic = false,
            licenseName = "SIL OFL 1.1",
            attribution = "Ek Type"
        ),
        NotebookFontSpec(
            id = "fredoka",
            displayName = "Fredoka",
            category = NotebookFontCategory.TITLES,
            resId = R.font.fredoka,
            supportsBold = true,
            supportsItalic = false,
            licenseName = "SIL OFL 1.1",
            attribution = "Milena Brandao, Hafontia"
        ),
        NotebookFontSpec(
            id = "lilita_one",
            displayName = "Lilita One",
            category = NotebookFontCategory.TITLES,
            resId = R.font.lilita_one,
            supportsBold = false,
            supportsItalic = false,
            licenseName = "SIL OFL 1.1",
            attribution = "Juan Montoreano"
        ),
        NotebookFontSpec(
            id = "bebas_neue",
            displayName = "Bebas Neue",
            category = NotebookFontCategory.TITLES,
            resId = R.font.bebas_neue,
            supportsBold = false,
            supportsItalic = false,
            licenseName = "SIL OFL 1.1",
            attribution = "Ryoichi Tsunekawa (Dharma Type)",
            sampleHint = "RESUMEN CLÍNICO"
        ),

        // ---- CUERPO MECANOGRAFIADO ----
        NotebookFontSpec(
            id = "open_sans",
            displayName = "Open Sans",
            category = NotebookFontCategory.BODY,
            resId = R.font.open_sans,
            supportsBold = true,
            supportsItalic = true,
            licenseName = "SIL OFL 1.1",
            attribution = "Steve Matteson"
        ),
        NotebookFontSpec(
            id = "nunito",
            displayName = "Nunito",
            category = NotebookFontCategory.BODY,
            resId = R.font.nunito,
            supportsBold = true,
            supportsItalic = true,
            licenseName = "SIL OFL 1.1",
            attribution = "Vernon Adams, Cyreal, Jacques Le Bailly"
        ),
        NotebookFontSpec(
            id = "lato",
            displayName = "Lato",
            category = NotebookFontCategory.BODY,
            resId = R.font.lato,
            supportsBold = true,
            supportsItalic = true,
            licenseName = "SIL OFL 1.1",
            attribution = "Łukasz Dziedzic"
        ),
        NotebookFontSpec(
            id = "inter",
            displayName = "Inter",
            category = NotebookFontCategory.BODY,
            resId = R.font.inter,
            supportsBold = true,
            supportsItalic = true,
            licenseName = "SIL OFL 1.1",
            attribution = "Rasmus Andersson"
        ),
        NotebookFontSpec(
            id = "roboto",
            displayName = "Roboto",
            category = NotebookFontCategory.BODY,
            resId = R.font.roboto,
            supportsBold = true,
            supportsItalic = true,
            licenseName = "Apache 2.0",
            attribution = "Christian Robertson / Google"
        ),
        NotebookFontSpec(
            id = "plus_jakarta_sans",
            displayName = "Plus Jakarta Sans",
            category = NotebookFontCategory.BODY,
            resId = R.font.plus_jakarta_sans,
            supportsBold = true,
            supportsItalic = true,
            licenseName = "SIL OFL 1.1",
            attribution = "Tokotype"
        ),
        NotebookFontSpec(
            id = "lexend",
            displayName = "Lexend",
            category = NotebookFontCategory.BODY,
            resId = R.font.lexend,
            supportsBold = true,
            supportsItalic = false,
            licenseName = "SIL OFL 1.1",
            attribution = "Bonnie Shaver-Troup, Thomas Jockin"
        ),
        NotebookFontSpec(
            id = "outfit",
            displayName = "Outfit",
            category = NotebookFontCategory.BODY,
            resId = R.font.outfit,
            supportsBold = true,
            supportsItalic = false,
            licenseName = "SIL OFL 1.1",
            attribution = "Rodrigo Fuenzalida, Outfit.io"
        ),

        // ---- MANUSCRITAS ----
        NotebookFontSpec(
            id = "caveat",
            displayName = "Caveat",
            category = NotebookFontCategory.HANDWRITTEN,
            resId = R.font.caveat,
            supportsBold = false,
            supportsItalic = false,
            licenseName = "SIL OFL 1.1",
            attribution = "Pablo Impallari, Igino Marini",
            sampleHint = "Nota a mano: repasar dosis"
        ),
        NotebookFontSpec(
            id = "kalam",
            displayName = "Kalam",
            category = NotebookFontCategory.HANDWRITTEN,
            resId = R.font.kalam,
            supportsBold = false,
            supportsItalic = false,
            licenseName = "SIL OFL 1.1",
            attribution = "Indian Type Foundry"
        ),
        NotebookFontSpec(
            id = "patrick_hand",
            displayName = "Patrick Hand",
            category = NotebookFontCategory.HANDWRITTEN,
            resId = R.font.patrick_hand,
            supportsBold = false,
            supportsItalic = false,
            licenseName = "SIL OFL 1.1",
            attribution = "Patrick Wagesreiter"
        ),
        NotebookFontSpec(
            id = "indie_flower",
            displayName = "Indie Flower",
            category = NotebookFontCategory.HANDWRITTEN,
            resId = R.font.indie_flower,
            supportsBold = false,
            supportsItalic = false,
            licenseName = "SIL OFL 1.1",
            attribution = "Kimberly Geswein"
        ),
        NotebookFontSpec(
            id = "shadows_into_light_two",
            displayName = "Shadows Into Light Two",
            category = NotebookFontCategory.HANDWRITTEN,
            resId = R.font.shadows_into_light_two,
            supportsBold = false,
            supportsItalic = false,
            licenseName = "SIL OFL 1.1",
            attribution = "Kimberly Geswein"
        ),
        NotebookFontSpec(
            id = "architects_daughter",
            displayName = "Architects Daughter",
            category = NotebookFontCategory.HANDWRITTEN,
            resId = R.font.architects_daughter,
            supportsBold = false,
            supportsItalic = false,
            licenseName = "SIL OFL 1.1",
            attribution = "Kimberly Geswein"
        ),

        // ---- CALIGRÁFICAS / DECORATIVAS ----
        NotebookFontSpec(
            id = "pacifico",
            displayName = "Pacifico",
            category = NotebookFontCategory.CALLIGRAPHIC,
            resId = R.font.pacifico,
            supportsBold = false,
            supportsItalic = false,
            licenseName = "SIL OFL 1.1",
            attribution = "Vernon Adams, Jacques Le Bailly"
        ),
        NotebookFontSpec(
            id = "kaushan_script",
            displayName = "Kaushan Script",
            category = NotebookFontCategory.CALLIGRAPHIC,
            resId = R.font.kaushan_script,
            supportsBold = false,
            supportsItalic = false,
            licenseName = "SIL OFL 1.1",
            attribution = "Impallari Type"
        ),
        NotebookFontSpec(
            id = "dancing_script",
            displayName = "Dancing Script",
            category = NotebookFontCategory.CALLIGRAPHIC,
            resId = R.font.dancing_script,
            supportsBold = false,
            supportsItalic = false,
            licenseName = "SIL OFL 1.1",
            attribution = "Impallari Type"
        ),
        NotebookFontSpec(
            id = "great_vibes",
            displayName = "Great Vibes",
            category = NotebookFontCategory.CALLIGRAPHIC,
            resId = R.font.great_vibes,
            supportsBold = false,
            supportsItalic = false,
            licenseName = "SIL OFL 1.1",
            attribution = "TypeSETit (Robert Leuschke)"
        ),

        // ---- SERIF DE LECTURA ----
        NotebookFontSpec(
            id = "literata",
            displayName = "Literata",
            category = NotebookFontCategory.SERIF,
            resId = R.font.literata,
            supportsBold = true,
            supportsItalic = true,
            licenseName = "SIL OFL 1.1",
            attribution = "TypeTogether"
        ),
        NotebookFontSpec(
            id = "lora",
            displayName = "Lora",
            category = NotebookFontCategory.SERIF,
            resId = R.font.lora,
            supportsBold = true,
            supportsItalic = true,
            licenseName = "SIL OFL 1.1",
            attribution = "Cyreal"
        ),
        NotebookFontSpec(
            id = "merriweather",
            displayName = "Merriweather",
            category = NotebookFontCategory.SERIF,
            resId = R.font.merriweather,
            supportsBold = true,
            supportsItalic = true,
            licenseName = "SIL OFL 1.1",
            attribution = "Sorkin Type"
        ),

        // ---- MONOESPACIADA ----
        NotebookFontSpec(
            id = "jetbrains_mono",
            displayName = "JetBrains Mono",
            category = NotebookFontCategory.MONO,
            resId = R.font.jetbrains_mono,
            supportsBold = true,
            supportsItalic = true,
            licenseName = "SIL OFL 1.1",
            attribution = "JetBrains s.r.o.",
            sampleHint = "pH = -log[H⁺] · f(x)"
        )
    )

    private val byId: Map<String, NotebookFontSpec> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        CATALOG.associateBy { it.id }
    }

    private val legacyAliasMap = mapOf(
        "sans" to DEFAULT_BODY_ID,
        "serif" to "literata",
        "mono" to "jetbrains_mono",
        "rounded" to DEFAULT_DIAGRAM_ID
    )

    private val composeFamilyCache = ConcurrentHashMap<String, FontFamily>()
    private val androidTypefaceCache = ConcurrentHashMap<String, Typeface>()

    fun canonicalId(rawFontId: String?): String {
        val clean = rawFontId?.trim()?.lowercase().orEmpty()
        if (clean.isEmpty()) return DEFAULT_BODY_ID
        val mapped = legacyAliasMap[clean] ?: clean
        return if (byId.containsKey(mapped)) mapped else DEFAULT_BODY_ID
    }

    fun specFor(rawFontId: String?): NotebookFontSpec =
        byId[canonicalId(rawFontId)] ?: byId.getValue(DEFAULT_BODY_ID)

    fun getComposeFontFamily(rawFontId: String?): FontFamily {
        val spec = specFor(rawFontId)
        return composeFamilyCache.getOrPut(spec.id) {
            // Single real static weight so Compose uses true glyph metrics and avoids duplicate weight bindings
            FontFamily(Font(spec.resId, FontWeight.Normal))
        }
    }

    fun resolveAndroidTypeface(context: Context, rawFontId: String?): Typeface {
        val spec = specFor(rawFontId)
        return androidTypefaceCache.getOrPut(spec.id) {
            runCatching { ResourcesCompat.getFont(context, spec.resId) }.getOrNull()
                ?: runCatching { ResourcesCompat.getFont(context, R.font.open_sans) }.getOrNull()
                ?: Typeface.SANS_SERIF
        }
    }

    fun resolveFallbackSymbolTypeface(context: Context): Typeface {
        return androidTypefaceCache.getOrPut("__symbol_fallback__") {
            // System SANS_SERIF resolves Android's full Noto Sans + Noto Sans Symbols + Greek + Math fallback chain
            // while bundled noto_sans_symbols acts as auxiliary verification resource.
            runCatching { ResourcesCompat.getFont(context, R.font.noto_sans_symbols) }
            Typeface.SANS_SERIF
        }
    }
}
