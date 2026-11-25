package com.example.notebook.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.notebook.model.NotebookEraserMode
import com.example.notebook.model.NotebookQuickStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.notebookStudioDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "luma_notebook_studio_prefs"
)

data class NotebookStudioPrefs(
    val savedColors: List<Long> = emptyList(),
    val recentColors: List<Long> = emptyList(),
    val stylusOnlyMode: Boolean = false,
    val stabilizerLevel: Float = 0.28f,
    val eraserMode: NotebookEraserMode = NotebookEraserMode.STROKE,
    val lockAspectRatio: Boolean = false,
    val favoriteFontIds: List<String> = emptyList(),
    val recentFontIds: List<String> = emptyList(),
    val customQuickStyles: List<NotebookQuickStyle> = emptyList(),
    val myStickerPaths: List<String> = emptyList()
)

/**
 * Strictly lazy DataStore preferences for Study Notebooks (instantiated ONLY when a Notebook is opened).
 * Persists saved/recent colors (max 12), stylus-only mode, stabilizer, eraser mode, aspect lock,
 * favorite/recent fonts, custom quick styles, and reusable user PNG stickers ("Mis stickers").
 */
class NotebookPreferencesStore(context: Context) {

    private val dataStore = context.applicationContext.notebookStudioDataStore

    val prefsFlow: Flow<NotebookStudioPrefs> = dataStore.data.map { prefs ->
        NotebookStudioPrefs(
            savedColors = parseColorList(prefs[KEY_SAVED_COLORS]),
            recentColors = parseColorList(prefs[KEY_RECENT_COLORS]),
            stylusOnlyMode = prefs[KEY_STYLUS_ONLY] ?: false,
            stabilizerLevel = (prefs[KEY_STABILIZER] ?: 0.28f).coerceIn(0f, 0.85f),
            eraserMode = NotebookEraserMode.fromName(prefs[KEY_ERASER_MODE]),
            lockAspectRatio = prefs[KEY_LOCK_ASPECT] ?: false,
            favoriteFontIds = parseStringList(prefs[KEY_FAVORITE_FONTS], MAX_FAVORITE_FONTS),
            recentFontIds = parseStringList(prefs[KEY_RECENT_FONTS], MAX_RECENT_FONTS),
            customQuickStyles = parseQuickStyles(prefs[KEY_QUICK_STYLES]),
            myStickerPaths = parseStringList(prefs[KEY_MY_STICKERS], MAX_USER_STICKERS)
        )
    }

    suspend fun recordRecentColor(colorArgb: Long) {
        dataStore.edit { prefs ->
            val current = parseColorList(prefs[KEY_RECENT_COLORS])
            val next = (listOf(colorArgb) + current.filter { it != colorArgb }).take(MAX_COLORS)
            prefs[KEY_RECENT_COLORS] = serializeColorList(next)
        }
    }

    suspend fun saveColor(colorArgb: Long) {
        dataStore.edit { prefs ->
            val current = parseColorList(prefs[KEY_SAVED_COLORS])
            val next = (listOf(colorArgb) + current.filter { it != colorArgb }).take(MAX_COLORS)
            prefs[KEY_SAVED_COLORS] = serializeColorList(next)
        }
    }

    suspend fun removeSavedColor(colorArgb: Long) {
        dataStore.edit { prefs ->
            val current = parseColorList(prefs[KEY_SAVED_COLORS])
            val next = current.filter { it != colorArgb }
            prefs[KEY_SAVED_COLORS] = serializeColorList(next)
        }
    }

    suspend fun setStylusOnlyMode(enabled: Boolean) {
        dataStore.edit { it[KEY_STYLUS_ONLY] = enabled }
    }

    suspend fun setStabilizerLevel(level: Float) {
        dataStore.edit { it[KEY_STABILIZER] = level.coerceIn(0f, 0.85f) }
    }

    suspend fun setEraserMode(mode: NotebookEraserMode) {
        dataStore.edit { it[KEY_ERASER_MODE] = mode.name }
    }

    suspend fun setLockAspectRatio(locked: Boolean) {
        dataStore.edit { it[KEY_LOCK_ASPECT] = locked }
    }

    suspend fun toggleFavoriteFont(fontId: String) {
        dataStore.edit { prefs ->
            val current = parseStringList(prefs[KEY_FAVORITE_FONTS], MAX_FAVORITE_FONTS)
            val next = if (fontId in current) {
                current.filterNot { it == fontId }
            } else {
                (listOf(fontId) + current).take(MAX_FAVORITE_FONTS)
            }
            prefs[KEY_FAVORITE_FONTS] = next.joinToString("|")
        }
    }

    suspend fun recordRecentFont(fontId: String) {
        dataStore.edit { prefs ->
            val current = parseStringList(prefs[KEY_RECENT_FONTS], MAX_RECENT_FONTS)
            val next = (listOf(fontId) + current.filterNot { it == fontId }).take(MAX_RECENT_FONTS)
            prefs[KEY_RECENT_FONTS] = next.joinToString("|")
        }
    }

    suspend fun saveQuickStyle(style: NotebookQuickStyle) {
        dataStore.edit { prefs ->
            val current = parseQuickStyles(prefs[KEY_QUICK_STYLES])
            val next = (listOf(style.copy(isBuiltIn = false)) + current.filterNot { it.id == style.id })
                .take(MAX_QUICK_STYLES)
            prefs[KEY_QUICK_STYLES] = serializeQuickStyles(next)
        }
    }

    suspend fun deleteQuickStyle(styleId: String) {
        dataStore.edit { prefs ->
            val current = parseQuickStyles(prefs[KEY_QUICK_STYLES])
            val next = current.filterNot { it.id == styleId }
            prefs[KEY_QUICK_STYLES] = serializeQuickStyles(next)
        }
    }

    suspend fun addMyStickerPath(absolutePath: String) {
        dataStore.edit { prefs ->
            val current = parseStringList(prefs[KEY_MY_STICKERS], MAX_USER_STICKERS)
            val next = (listOf(absolutePath) + current.filterNot { it == absolutePath }).take(MAX_USER_STICKERS)
            prefs[KEY_MY_STICKERS] = next.joinToString("|")
        }
    }

    suspend fun removeMyStickerPath(absolutePath: String) {
        dataStore.edit { prefs ->
            val current = parseStringList(prefs[KEY_MY_STICKERS], MAX_USER_STICKERS)
            val next = current.filterNot { it == absolutePath }
            prefs[KEY_MY_STICKERS] = next.joinToString("|")
        }
    }

    private fun parseColorList(raw: String?): List<Long> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(",")
            .mapNotNull { token -> token.trim().toLongOrNull(16) }
            .distinct()
            .take(MAX_COLORS)
    }

    private fun serializeColorList(colors: List<Long>): String =
        colors.take(MAX_COLORS).joinToString(",") { java.lang.Long.toHexString(it and 0xFFFFFFFFL) }

    private fun parseStringList(raw: String?, maxCount: Int): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split("|").map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(maxCount)
    }

    private fun parseQuickStyles(raw: String?): List<NotebookQuickStyle> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val out = ArrayList<NotebookQuickStyle>(arr.length())
            for (i in 0 until arr.length()) {
                val j = arr.optJSONObject(i) ?: continue
                out.add(
                    NotebookQuickStyle(
                        id = j.optString("id"),
                        name = j.optString("name", "Estilo"),
                        fontId = j.optString("font", "open_sans"),
                        textColorArgb = j.optLong("tc", 0xFF151C2BL),
                        fillColorArgb = if (j.has("fill")) j.optLong("fill") else null,
                        borderColorArgb = if (j.has("bdr")) j.optLong("bdr") else null,
                        highlightColorArgb = if (j.has("hl")) j.optLong("hl") else null,
                        fontSizeNorm = j.optDouble("fs", 0.022).toFloat(),
                        isBanner = j.optBoolean("ban", false),
                        isBold = j.optBoolean("b", false),
                        isBuiltIn = false
                    )
                )
            }
            out
        }.getOrDefault(emptyList())
    }

    private fun serializeQuickStyles(styles: List<NotebookQuickStyle>): String {
        val arr = JSONArray()
        for (st in styles) {
            val j = JSONObject()
            j.put("id", st.id)
            j.put("name", st.name)
            j.put("font", st.fontId)
            j.put("tc", st.textColorArgb)
            st.fillColorArgb?.let { j.put("fill", it) }
            st.borderColorArgb?.let { j.put("bdr", it) }
            st.highlightColorArgb?.let { j.put("hl", it) }
            j.put("fs", st.fontSizeNorm.toDouble())
            j.put("ban", st.isBanner)
            j.put("b", st.isBold)
            arr.put(j)
        }
        return arr.toString()
    }

    companion object {
        const val MAX_COLORS = 12
        const val MAX_FAVORITE_FONTS = 12
        const val MAX_RECENT_FONTS = 8
        const val MAX_QUICK_STYLES = 12
        const val MAX_USER_STICKERS = 24

        private val KEY_SAVED_COLORS = stringPreferencesKey("saved_colors_hex")
        private val KEY_RECENT_COLORS = stringPreferencesKey("recent_colors_hex")
        private val KEY_STYLUS_ONLY = booleanPreferencesKey("stylus_only_mode")
        private val KEY_STABILIZER = floatPreferencesKey("stabilizer_level")
        private val KEY_ERASER_MODE = stringPreferencesKey("eraser_mode")
        private val KEY_LOCK_ASPECT = booleanPreferencesKey("lock_aspect_ratio")
        private val KEY_FAVORITE_FONTS = stringPreferencesKey("favorite_font_ids")
        private val KEY_RECENT_FONTS = stringPreferencesKey("recent_font_ids")
        private val KEY_QUICK_STYLES = stringPreferencesKey("custom_quick_styles_json")
        private val KEY_MY_STICKERS = stringPreferencesKey("my_sticker_paths")
    }
}
