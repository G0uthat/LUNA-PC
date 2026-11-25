package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DocumentFormat
import com.example.ui.theme.LocalFolioPalette
import com.example.ui.theme.LocalLumaMotion

private val ClassicCapsuleShape = RoundedCornerShape(28.dp)
private val ClassicTopOrigin = TransformOrigin(0.5f, 0f)
private val ClassicBottomOrigin = TransformOrigin(0.5f, 1f)

/**
 * Classic Kindle-style top & bottom overlay bars revealed by tapping the center of the page.
 */
@Composable
internal fun ClassicKindleOverlayBars(
    dockScale: Float,
    brightnessValue: Float,
    isExpanded: Boolean,
    isBookmarkedHere: Boolean,
    isPdfDocument: Boolean,
    documentFormat: DocumentFormat,
    showFormatModeToggle: Boolean,
    isOriginalFormatView: Boolean,
    isPageTranslated: Boolean,
    bookTitle: String,
    progressLabel: String,
    onDismiss: () -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    onToggleBookmarkHere: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenToc: () -> Unit,
    onToggleDrawing: () -> Unit,
    onSetOriginalFormatView: (Boolean) -> Unit,
    onTriggerOcrTranslate: () -> Unit,
    onOpenAnnotations: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExport: () -> Unit,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val motion = LocalLumaMotion.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isCompactHeight = this.maxHeight < 480.dp
        // Gentle 15% dim scrim matching ProfileDropdownCapsuleOverlay
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(motion.tweenSpec(105)),
            exit = fadeOut(motion.exitTween(80))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f))
                    .background(palette.background.copy(alpha = 0.08f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        // Floating Top Capsule with breathing space around edges + snappy spring Zoom-In/Zoom-Out
        AnimatedVisibility(
            visible = isExpanded,
            enter = scaleIn(
                initialScale = 0.94f,
                transformOrigin = ClassicTopOrigin,
                animationSpec = motion.modalSpring()
            ) + fadeIn(motion.emphasizedTween(105)),
            exit = scaleOut(
                targetScale = 0.95f,
                transformOrigin = ClassicTopOrigin,
                animationSpec = motion.exitTween(85)
            ) + fadeOut(motion.exitTween(80)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .displayCutoutPadding()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = if (isCompactHeight) 6.dp else 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .shadow(
                        elevation = if (motion.isLowRam) 4.dp else 12.dp,
                        shape = ClassicCapsuleShape,
                        ambientColor = Color.Black.copy(alpha = 0.22f)
                    )
                    .clip(ClassicCapsuleShape)
                    .background(palette.surface.copy(alpha = 0.98f))
                    .border(1.dp, palette.border, ClassicCapsuleShape)
                    .padding(horizontal = 12.dp, vertical = (10.dp * dockScale.coerceIn(0.9f, 1.25f))),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(palette.surfaceAlt)
                        .border(1.dp, palette.border, CircleShape)
                        .clickable { onDismiss(); onBackToLibrary() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Biblioteca", tint = palette.ink, modifier = Modifier.size(19.dp))
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(
                        text = bookTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = progressLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.inkMuted
                    )
                }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isBookmarkedHere) palette.accentSoft else palette.surfaceAlt)
                        .border(1.dp, if (isBookmarkedHere) palette.accent else palette.border, CircleShape)
                        .clickable { onToggleBookmarkHere() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isBookmarkedHere) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                        contentDescription = "Marcar página",
                        tint = if (isBookmarkedHere) palette.accent else palette.ink,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(palette.surfaceAlt)
                        .border(1.dp, palette.border, CircleShape)
                        .clickable { onDismiss(); onOpenSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = "Configuración",
                        tint = palette.inkMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Floating Bottom Capsule with breathing space around edges + brightness + tools
        AnimatedVisibility(
            visible = isExpanded,
            enter = scaleIn(
                initialScale = 0.94f,
                transformOrigin = ClassicBottomOrigin,
                animationSpec = motion.modalSpring()
            ) + fadeIn(motion.emphasizedTween(105)),
            exit = scaleOut(
                targetScale = 0.95f,
                transformOrigin = ClassicBottomOrigin,
                animationSpec = motion.exitTween(85)
            ) + fadeOut(motion.exitTween(80)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .displayCutoutPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = if (isCompactHeight) 8.dp else 14.dp)
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .shadow(16.dp, ClassicCapsuleShape, ambientColor = Color.Black.copy(alpha = 0.25f))
                    .clip(ClassicCapsuleShape)
                    .background(palette.surface.copy(alpha = 0.98f))
                    .border(1.dp, palette.border, ClassicCapsuleShape)
                    .padding(horizontal = 14.dp, vertical = if (isCompactHeight) 8.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 6.dp else 10.dp)
            ) {
                DirectBrightnessRow(
                    brightnessValue = brightnessValue,
                    onBrightnessChanged = onBrightnessChanged
                )
                // Dedicated View Mode Switcher ONLY for non-EPUB files (PDF, PPTX, DOCX, TXT, FB2)
                if (showFormatModeToggle) {
                    FormatViewModeSegmentedToggle(
                        documentFormat = documentFormat,
                        isOriginalFormatView = isOriginalFormatView,
                        onSelectOriginalFormat = { original ->
                            onSetOriginalFormatView(original)
                            onDismiss()
                        }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    ClassicBarAction(Icons.Rounded.FormatListBulleted, "Índice") { onDismiss(); onOpenToc() }
                    ClassicBarAction(Icons.Rounded.TextFields, "Texto") { onDismiss(); onOpenAppearance() }
                    ClassicBarAction(Icons.Rounded.Draw, "Firmar") { onDismiss(); onToggleDrawing() }
                    if (isPdfDocument) {
                        ClassicBarAction(Icons.Rounded.Translate, "Traducir") { onDismiss(); onTriggerOcrTranslate() }
                    }
                    ClassicBarAction(Icons.Rounded.Search, "Buscar") { onDismiss(); onOpenSearch() }
                    ClassicBarAction(Icons.Rounded.IosShare, "Exportar") { onDismiss(); onOpenExport() }
                }
            }
        }
    }
}
