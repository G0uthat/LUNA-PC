package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

// Lazy-initialized FontFamilies ensure reader & brand fonts are not allocated or loaded during
// cold startup unless the active UI or reader actually requests them.
// Each bundled .ttf is a static Regular weight; registering only FontWeight.Normal allows Compose
// to apply proper FontSynthesis for Bold/SemiBold headings instead of overriding Bold with Regular.

val PlusJakartaSansFamily: FontFamily by lazy(LazyThreadSafetyMode.NONE) {
    FontFamily(Font(R.font.plus_jakarta_sans, FontWeight.Normal))
}

val LiterataFamily: FontFamily by lazy(LazyThreadSafetyMode.NONE) {
    FontFamily(Font(R.font.literata, FontWeight.Normal))
}

val LexendFamily: FontFamily by lazy(LazyThreadSafetyMode.NONE) {
    FontFamily(Font(R.font.lexend, FontWeight.Normal))
}

val JetBrainsMonoFamily: FontFamily by lazy(LazyThreadSafetyMode.NONE) {
    FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))
}

val LumaBrandFontFamily: FontFamily by lazy(LazyThreadSafetyMode.NONE) {
    FontFamily(Font(R.font.outfit, FontWeight.Normal))
}

fun buildFolioTypography(useSystemFont: Boolean): Typography {
    val uiFamily = if (useSystemFont) FontFamily.Default else PlusJakartaSansFamily
    return Typography(
        displaySmall = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            letterSpacing = (-0.5).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            letterSpacing = (-0.3).sp
        ),
        headlineSmall = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            letterSpacing = (-0.2).sp
        ),
        titleLarge = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            lineHeight = 24.sp
        ),
        titleMedium = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp
        ),
        titleSmall = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp
        ),
        bodySmall = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp
        ),
        labelLarge = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.2.sp
        ),
        labelMedium = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.3.sp
        ),
        labelSmall = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.4.sp
        )
    )
}
