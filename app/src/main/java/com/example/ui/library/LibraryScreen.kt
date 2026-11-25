package com.example.ui.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.ReportDrawnWhen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.LibraryDocumentItem
import com.example.data.local.PlaylistEntity
import com.example.engine.SocialRepository
import com.example.ui.components.AccountProfileDialog
import com.example.ui.components.AppSettingsSheet
import com.example.ui.components.FriendsChatDialog
import com.example.ui.components.LibraryFilterDropdownOverlay
import com.example.ui.components.LumaActionFeedbackOverlay
import com.example.ui.components.LumaAppGuideDialog
import com.example.ui.components.LumaInteractiveTourOverlay
import com.example.ui.components.LumaPermissionsOnboardingDialog
import com.example.ui.components.LumaWideWordmark
import com.example.ui.components.ProfileDropdownCapsuleOverlay
import com.example.ui.components.QuickProfileSetupDialog
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileCard
import com.example.ui.components.TourTargetBounds
import com.example.ui.components.UserAvatarCircle
import com.example.ui.theme.LocalFolioPalette
import com.example.ui.theme.LocalLumaMotion
import android.net.Uri
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.runtime.rememberCoroutineScope
import com.example.notebook.data.NotebookPageTemplate
import com.example.notebook.data.NotebookRepository
import com.example.notebook.data.NotebookSummaryItem
import com.example.notebook.ui.CreateNotebookDialog
import com.example.notebook.ui.EmptyNotebooksCard
import com.example.notebook.ui.NotebookGridCard
import com.example.notebook.ui.NotebookOptionsDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

private enum class LibraryViewTab(val label: String) {
    BOOKS("Libros"),
    SERIES("Series"),
    NOTEBOOKS("Cuadernos")
}

private val SwitcherOuterShape = RoundedCornerShape(14.dp)
private val SwitcherTabShape = RoundedCornerShape(10.dp)
private val SearchInputShape = RoundedCornerShape(16.dp)
private val SeriesHeaderShape = RoundedCornerShape(18.dp)

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    notebookRepositoryProvider: () -> NotebookRepository,
    onOpenDocument: (Long) -> Unit,
    onOpenNotebook: (Long) -> Unit
) {
    val palette = LocalFolioPalette.current
    val motion = LocalLumaMotion.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val socialRepo = remember(context) { SocialRepository.getInstance(context) }

    // Narrow state slices: avoid collecting full ReaderPreferences or dictionarySources at Library root
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playlistRefs by viewModel.playlistRefs.collectAsStateWithLifecycle()
    val preferencesLoaded by viewModel.preferencesLoaded.collectAsStateWithLifecycle()
    val hasCompletedOnboardingFlow = remember(viewModel) {
        viewModel.preferences.map { it.hasCompletedOnboarding }.distinctUntilChanged()
    }
    val hasCompletedOnboarding by hasCompletedOnboardingFlow.collectAsStateWithLifecycle(
        initialValue = viewModel.preferences.value.hasCompletedOnboarding
    )
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()

    // Signal Time-to-Full-Display (TTFD) for ART Baseline Profile & Macrobenchmark accuracy
    ReportDrawnWhen { preferencesLoaded }

    var activeTab by remember { mutableStateOf(LibraryViewTab.BOOKS) }
    var selectedFilter by remember { mutableStateOf("Todos") }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var openedSeriesId by remember { mutableStateOf<Long?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    var showProfileMenu by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showFriendsDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showAppGuide by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showCreateNotebookDialog by remember { mutableStateOf(false) }
    var optionsNotebook by remember { mutableStateOf<NotebookSummaryItem?>(null) }
    var isCreatingNotebook by remember { mutableStateOf(false) }

    // Strictly lazy notebook list: NotebookRepository is NOT instantiated until user enters Cuadernos tab!
    var notebooksActivated by remember { mutableStateOf(false) }
    LaunchedEffect(activeTab) {
        if (activeTab == LibraryViewTab.NOTEBOOKS) {
            notebooksActivated = true
        }
    }
    var notebooks by remember { mutableStateOf<List<NotebookSummaryItem>>(emptyList()) }
    LaunchedEffect(notebooksActivated) {
        if (!notebooksActivated) return@LaunchedEffect
        notebookRepositoryProvider().notebookSummaries.collect { list ->
            notebooks = list
        }
    }

    var showPermissionsOnboarding by remember { mutableStateOf(false) }
    var showInteractiveTour by remember { mutableStateOf(false) }
    var onboardingAutoChecked by remember { mutableStateOf(false) }

    LaunchedEffect(preferencesLoaded, hasCompletedOnboarding) {
        if (preferencesLoaded && !onboardingAutoChecked) {
            onboardingAutoChecked = true
            if (!hasCompletedOnboarding) {
                showPermissionsOnboarding = true
            }
        }
    }

    // Measured root coordinates & live spotlight target bounds
    var rootLeftWindowPx by remember { mutableFloatStateOf(0f) }
    var rootTopWindowPx by remember { mutableFloatStateOf(0f) }
    var profileSphereTopInRootPx by remember { mutableFloatStateOf(0f) }
    var profileSphereRightInRootPx by remember { mutableFloatStateOf(0f) }
    var filterButtonBottomInRootPx by remember { mutableFloatStateOf(0f) }
    var tourTargets by remember { mutableStateOf(TourTargetBounds()) }

    fun Rect.toRootRelative(): Rect = Rect(
        left = (left - rootLeftWindowPx).coerceAtLeast(0f),
        top = (top - rootTopWindowPx).coerceAtLeast(0f),
        right = (right - rootLeftWindowPx).coerceAtLeast(0f),
        bottom = (bottom - rootTopWindowPx).coerceAtLeast(0f)
    )

    // Long-press floating book menu & sub-modals
    var optionsBook by remember { mutableStateOf<LibraryDocumentItem?>(null) }
    var bookForPlaylistModal by remember { mutableStateOf<LibraryDocumentItem?>(null) }
    var bookForMetadataModal by remember { mutableStateOf<LibraryDocumentItem?>(null) }
    var pendingCoverBook by remember { mutableStateOf<LibraryDocumentItem?>(null) }

    val anyFloatingMenuOpen by remember {
        derivedStateOf {
            showProfileMenu || filterMenuExpanded || showAccountDialog || showFriendsDialog ||
                showSettingsSheet || showAppGuide || showPermissionsOnboarding ||
                optionsBook != null || showCreateNotebookDialog || optionsNotebook != null
        }
    }

    BackHandler(enabled = showInteractiveTour) {
        showInteractiveTour = false
        viewModel.updatePreferences { it.copy(hasCompletedOnboarding = true) }
    }
    BackHandler(enabled = !showInteractiveTour && (showProfileMenu || filterMenuExpanded)) {
        showProfileMenu = false
        filterMenuExpanded = false
    }
    BackHandler(
        enabled = !showInteractiveTour && !showProfileMenu && !filterMenuExpanded &&
            activeTab == LibraryViewTab.SERIES && openedSeriesId != null
    ) {
        openedSeriesId = null
    }

    // Zero-permission SAF document picker (Play Policy compliant)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importDocument(uri, onImportedOpen = onOpenDocument)
        }
    }

    // Zero-permission Android Photo Picker for custom book cover selection
    val coverPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val target = pendingCoverBook
        if (uri != null && target != null) {
            viewModel.updateBookCoverFromUri(target, uri)
        }
        pendingCoverBook = null
    }

    // Zero-permission Android Photo Picker for first-launch quick profile photo
    val quickAvatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            socialRepo.updateAvatarFromUri(uri)
        }
    }

    // Zero-permission SAF PDF picker for creating a Study Notebook over PDF pages
    val notebookPdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && !isCreatingNotebook) {
            isCreatingNotebook = true
            notebooksActivated = true
            coroutineScope.launch {
                try {
                    val info = withContext(Dispatchers.IO) { viewModel.resolveExternalFileInfo(uri) }
                    val tempFile = withContext(Dispatchers.IO) {
                        val tmp = File(context.cacheDir, "nb_pdf_${System.currentTimeMillis()}.pdf")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(tmp).use { out -> input.copyTo(out) }
                        }
                        tmp
                    }
                    val title = info.displayName.substringBeforeLast('.').ifBlank { "Apuntes PDF" }
                    val newNbId = notebookRepositoryProvider().createNotebookFromPdf(title, tempFile)
                    runCatching { tempFile.delete() }
                    onOpenNotebook(newNbId)
                } finally {
                    isCreatingNotebook = false
                }
            }
        }
    }

    // Zero-permission Android Photo Picker for creating a Study Notebook over an image/slide
    val notebookImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null && !isCreatingNotebook) {
            isCreatingNotebook = true
            notebooksActivated = true
            coroutineScope.launch {
                try {
                    val info = withContext(Dispatchers.IO) { viewModel.resolveExternalFileInfo(uri) }
                    val title = info.displayName.substringBeforeLast('.').ifBlank { "Apunte visual" }
                    val newNbId = notebookRepositoryProvider().createNotebookFromImageUri(title, uri)
                    onOpenNotebook(newNbId)
                } finally {
                    isCreatingNotebook = false
                }
            }
        }
    }

    val launchNotebookPdfPicker = remember(notebookPdfPickerLauncher) {
        { notebookPdfPickerLauncher.launch(arrayOf("application/pdf")) }
    }
    val launchNotebookImagePicker = remember(notebookImagePickerLauncher) {
        {
            notebookImagePickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    val launchImport = remember(importLauncher) {
        {
            importLauncher.launch(
                arrayOf(
                    "application/epub+zip",
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "text/plain",
                    "application/x-fictionbook+xml",
                    "*/*"
                )
            )
        }
    }

    // Pre-index playlist memberships & lookup map once instead of filtering per grid item
    val docsById = remember(documents) { documents.associateBy { it.id } }
    val docIdsByPlaylistId = remember(playlistRefs) {
        playlistRefs.groupBy({ it.playlistId }, { it.documentId })
    }
    val activeSeries by remember(playlists) {
        derivedStateOf { playlists.firstOrNull { it.id == openedSeriesId } }
    }
    val docIdsInActiveSeries by remember(docIdsByPlaylistId) {
        derivedStateOf {
            val sid = openedSeriesId ?: return@derivedStateOf null
            docIdsByPlaylistId[sid]?.toSet().orEmpty()
        }
    }
    val filteredDocs by remember(documents) {
        derivedStateOf {
            val seriesIds = docIdsInActiveSeries
            val filter = selectedFilter
            val query = searchQuery
            val inSeriesTab = activeTab == LibraryViewTab.SERIES && seriesIds != null
            documents.filter { doc ->
                val inSeries = if (inSeriesTab) doc.id in seriesIds!! else true
                val matchesFilter = when (filter) {
                    "Todos" -> true
                    "Otros" -> doc.format == "TXT" || doc.format == "FB2"
                    else -> doc.format.equals(filter, ignoreCase = true)
                }
                val matchesQuery = query.isBlank() ||
                    doc.title.contains(query, ignoreCase = true) ||
                    doc.author.contains(query, ignoreCase = true) ||
                    doc.genre.contains(query, ignoreCase = true)
                inSeries && matchesFilter && matchesQuery
            }
        }
    }
    val filteredSeries by remember(playlists) {
        derivedStateOf {
            val query = searchQuery
            if (query.isBlank()) playlists
            else playlists.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true)
            }
        }
    }
    val filteredNotebooks by remember(notebooks) {
        derivedStateOf {
            val query = searchQuery
            if (query.isBlank()) notebooks
            else notebooks.filter { it.title.contains(query, ignoreCase = true) }
        }
    }
    val continueDoc = remember(documents) { documents.firstOrNull() }

    Scaffold(
        containerColor = palette.background
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    rootLeftWindowPx = pos.x
                    rootTopWindowPx = pos.y
                }
        ) {
            val isTabletLayout = maxWidth >= 720.dp
            if (isTabletLayout) {
                val leftPaneWidth = (maxWidth * 0.36f).coerceIn(300.dp, 400.dp)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .displayCutoutPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    // Left Panel: Compact Luma Brand Header + Proportioned Continue Reading + Quick Import
                    Column(
                        modifier = Modifier
                            .width(leftPaneWidth)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LibraryHeaderRow(
                            bookCount = documents.size,
                            seriesCount = playlists.size,
                            socialRepo = socialRepo,
                            isImporting = isImporting,
                            onOpenGuide = { showAppGuide = true },
                            onToggleProfileMenu = { showProfileMenu = !showProfileMenu },
                            onHeaderRect = { rect -> tourTargets = tourTargets.copy(headerRect = rect.toRootRelative()) },
                            onGuideRect = { rect -> tourTargets = tourTargets.copy(guideButtonRect = rect.toRootRelative()) },
                            onProfileBounds = { topY, rect ->
                                profileSphereTopInRootPx = (topY - rootTopWindowPx).coerceAtLeast(0f)
                                profileSphereRightInRootPx = (rect.right - rootLeftWindowPx).coerceAtLeast(0f)
                                tourTargets = tourTargets.copy(profileSphereRect = rect.toRootRelative())
                            }
                        )

                        if (continueDoc != null) {
                            Box(
                                modifier = Modifier.onGloballyPositioned { coords ->
                                    tourTargets = tourTargets.copy(
                                        continueRect = coords.boundsInWindow().toRootRelative()
                                    )
                                }
                            ) {
                                ContinueReadingSoftBanner(
                                    doc = continueDoc,
                                    onClick = { onOpenDocument(continueDoc.id) }
                                )
                            }
                        }

                        // Compact tablet studio quick-action card for importing books or creating notebooks/series
                        TactileCard(
                            containerColor = palette.surface,
                            borderColor = palette.border,
                            shadowColor = palette.borderShadow,
                            cornerRadius = 22.dp,
                            lipDepth = 2.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TactileButton(
                                        onClick = launchImport,
                                        cornerRadius = 14.dp,
                                        lipDepth = 2.dp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("tablet_quick_import_button")
                                    ) {
                                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text("Importar", fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(palette.accentSoft)
                                            .border(1.dp, palette.accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                            .clickable {
                                                notebooksActivated = true
                                                showCreateNotebookDialog = true
                                            }
                                            .padding(horizontal = 14.dp, vertical = 11.dp)
                                            .testTag("tablet_quick_new_notebook_button"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Draw,
                                                contentDescription = "Nuevo cuaderno",
                                                tint = palette.accent,
                                                modifier = Modifier.size(17.dp)
                                            )
                                            Text(
                                                text = "Cuaderno",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = palette.accent
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Right Panel: Libros / Series / Cuadernos Switcher + Search/Filter + Catalog Grid
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .statusBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        LibraryTabsAndSearchControls(
                            activeTab = activeTab,
                            isSeriesOverview = activeTab == LibraryViewTab.SERIES && activeSeries == null,
                            searchQuery = searchQuery,
                            selectedFilter = selectedFilter,
                            onSelectTab = { tab ->
                                activeTab = tab
                                openedSeriesId = null
                            },
                            onSearchQueryChange = { searchQuery = it },
                            onCreatePlaylist = { showCreatePlaylistDialog = true },
                            onCreateNotebook = {
                                notebooksActivated = true
                                showCreateNotebookDialog = true
                            },
                            onToggleFilterMenu = { filterMenuExpanded = !filterMenuExpanded },
                            onTabsRect = { rect -> tourTargets = tourTargets.copy(tabsRect = rect.toRootRelative()) },
                            onSearchFilterRect = { rect -> tourTargets = tourTargets.copy(searchFilterRect = rect.toRootRelative()) },
                            onFilterButtonBottom = { bottomY ->
                                filterButtonBottomInRootPx = (bottomY - rootTopWindowPx).coerceAtLeast(0f)
                            }
                        )

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 142.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 2.dp, bottom = 28.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (activeTab == LibraryViewTab.NOTEBOOKS) {
                                if (filteredNotebooks.isEmpty()) {
                                    item(key = "lib_notebooks_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty_notebooks") {
                                        EmptyNotebooksCard(
                                            onCreateBlank = { showCreateNotebookDialog = true },
                                            onImportPdf = launchNotebookPdfPicker,
                                            onImportImage = launchNotebookImagePicker
                                        )
                                    }
                                } else {
                                    items(
                                        items = filteredNotebooks,
                                        key = { "nb_${it.id}" },
                                        contentType = { "notebook_card" }
                                    ) { nb ->
                                        Box(
                                            modifier = Modifier.animateItem(
                                                fadeInSpec = motion.tweenSpec(215),
                                                fadeOutSpec = motion.exitTween(185),
                                                placementSpec = motion.glideSpring()
                                            )
                                        ) {
                                            NotebookGridCard(
                                                notebook = nb,
                                                onOpen = { onOpenNotebook(nb.id) },
                                                onLongPress = { optionsNotebook = nb }
                                            )
                                        }
                                    }
                                }
                            } else if (activeTab == LibraryViewTab.SERIES && activeSeries == null) {
                                if (filteredSeries.isEmpty()) {
                                    item(key = "lib_series_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty_series") {
                                        EmptySeriesCard(onCreate = { showCreatePlaylistDialog = true })
                                    }
                                } else {
                                    items(
                                        items = filteredSeries,
                                        key = { "series_${it.id}" },
                                        contentType = { "series_card" }
                                    ) { series ->
                                        val memberIds = docIdsByPlaylistId[series.id].orEmpty()
                                        val leadCoverPath = remember(memberIds, docsById) {
                                            memberIds.firstNotNullOfOrNull { docsById[it]?.coverImagePath }
                                        }
                                        Box(
                                            modifier = Modifier.animateItem(
                                                fadeInSpec = motion.tweenSpec(215),
                                                fadeOutSpec = motion.exitTween(185),
                                                placementSpec = motion.glideSpring()
                                            )
                                        ) {
                                            SeriesSteamCard(
                                                series = series,
                                                memberCount = memberIds.size,
                                                leadCoverPath = leadCoverPath,
                                                onClick = { openedSeriesId = series.id }
                                            )
                                        }
                                    }
                                }
                            } else {
                                if (activeTab == LibraryViewTab.SERIES && activeSeries != null) {
                                    val currentSeries = activeSeries!!
                                    item(key = "lib_series_detail_header", span = { GridItemSpan(maxLineSpan) }, contentType = "series_header") {
                                        SeriesDetailHeaderBar(
                                            series = currentSeries,
                                            docCount = filteredDocs.size,
                                            onBack = { openedSeriesId = null },
                                            onDelete = {
                                                viewModel.deletePlaylist(currentSeries)
                                                openedSeriesId = null
                                            }
                                        )
                                    }
                                }

                                if (filteredDocs.isEmpty()) {
                                    item(key = "lib_books_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty_books") {
                                        EmptyBooksCard(isInSeries = activeSeries != null)
                                    }
                                } else {
                                    items(
                                        items = filteredDocs,
                                        key = { "book_${it.id}" },
                                        contentType = { "book_card" }
                                    ) { doc ->
                                        Box(
                                            modifier = Modifier.animateItem(
                                                fadeInSpec = motion.tweenSpec(215),
                                                fadeOutSpec = motion.exitTween(185),
                                                placementSpec = motion.glideSpring()
                                            )
                                        ) {
                                            SteamCoverBookTile(
                                                doc = doc,
                                                onOpen = { onOpenDocument(doc.id) },
                                                onLongPress = { optionsBook = doc }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 148.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .displayCutoutPadding()
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Minimalist Full-Span "LUMA" Wordmark + Interactive Profile Sphere
                    item(key = "lib_header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                        LibraryHeaderRow(
                            bookCount = documents.size,
                            seriesCount = playlists.size,
                            socialRepo = socialRepo,
                            isImporting = isImporting,
                            onOpenGuide = { showAppGuide = true },
                            onToggleProfileMenu = { showProfileMenu = !showProfileMenu },
                            onHeaderRect = { rect -> tourTargets = tourTargets.copy(headerRect = rect.toRootRelative()) },
                            onGuideRect = { rect -> tourTargets = tourTargets.copy(guideButtonRect = rect.toRootRelative()) },
                            onProfileBounds = { topY, rect ->
                                profileSphereTopInRootPx = (topY - rootTopWindowPx).coerceAtLeast(0f)
                                profileSphereRightInRootPx = (rect.right - rootLeftWindowPx).coerceAtLeast(0f)
                                tourTargets = tourTargets.copy(profileSphereRect = rect.toRootRelative())
                            }
                        )
                    }

                    // 2. Soft Continue Reading Banner
                    if (continueDoc != null) {
                        item(key = "lib_continue_reading", span = { GridItemSpan(maxLineSpan) }, contentType = "continue") {
                            Box(
                                modifier = Modifier.onGloballyPositioned { coords ->
                                    tourTargets = tourTargets.copy(
                                        continueRect = coords.boundsInWindow().toRootRelative()
                                    )
                                }
                            ) {
                                ContinueReadingSoftBanner(
                                    doc = continueDoc,
                                    onClick = { onOpenDocument(continueDoc.id) }
                                )
                            }
                        }
                    }

                    // 3. Three-Tab Switcher ("Libros" | "Series" | "Cuadernos") ABOVE Search Bar + Action Button
                    item(key = "lib_tabs_and_search", span = { GridItemSpan(maxLineSpan) }, contentType = "controls") {
                        LibraryTabsAndSearchControls(
                            activeTab = activeTab,
                            isSeriesOverview = activeTab == LibraryViewTab.SERIES && activeSeries == null,
                            searchQuery = searchQuery,
                            selectedFilter = selectedFilter,
                            onSelectTab = { tab ->
                                activeTab = tab
                                openedSeriesId = null
                            },
                            onSearchQueryChange = { searchQuery = it },
                            onCreatePlaylist = { showCreatePlaylistDialog = true },
                            onCreateNotebook = {
                                notebooksActivated = true
                                showCreateNotebookDialog = true
                            },
                            onToggleFilterMenu = { filterMenuExpanded = !filterMenuExpanded },
                            onTabsRect = { rect -> tourTargets = tourTargets.copy(tabsRect = rect.toRootRelative()) },
                            onSearchFilterRect = { rect -> tourTargets = tourTargets.copy(searchFilterRect = rect.toRootRelative()) },
                            onFilterButtonBottom = { bottomY ->
                                filterButtonBottomInRootPx = (bottomY - rootTopWindowPx).coerceAtLeast(0f)
                            }
                        )
                    }

                    // 4. Content Area: Cuadernos OR Series Overview OR Steam-Style Book Cover Grid
                    if (activeTab == LibraryViewTab.NOTEBOOKS) {
                        if (filteredNotebooks.isEmpty()) {
                            item(key = "lib_notebooks_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty_notebooks") {
                                EmptyNotebooksCard(
                                    onCreateBlank = { showCreateNotebookDialog = true },
                                    onImportPdf = launchNotebookPdfPicker,
                                    onImportImage = launchNotebookImagePicker
                                )
                            }
                        } else {
                            items(
                                items = filteredNotebooks,
                                key = { "nb_${it.id}" },
                                contentType = { "notebook_card" }
                            ) { nb ->
                                Box(
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = motion.tweenSpec(215),
                                        fadeOutSpec = motion.exitTween(185),
                                        placementSpec = motion.glideSpring()
                                    )
                                ) {
                                    NotebookGridCard(
                                        notebook = nb,
                                        onOpen = { onOpenNotebook(nb.id) },
                                        onLongPress = { optionsNotebook = nb }
                                    )
                                }
                            }
                        }
                    } else if (activeTab == LibraryViewTab.SERIES && activeSeries == null) {
                        if (filteredSeries.isEmpty()) {
                            item(key = "lib_series_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty_series") {
                                EmptySeriesCard(onCreate = { showCreatePlaylistDialog = true })
                            }
                        } else {
                            items(
                                items = filteredSeries,
                                key = { "series_${it.id}" },
                                contentType = { "series_card" }
                            ) { series ->
                                val memberIds = docIdsByPlaylistId[series.id].orEmpty()
                                val leadCoverPath = remember(memberIds, docsById) {
                                    memberIds.firstNotNullOfOrNull { docsById[it]?.coverImagePath }
                                }
                                Box(
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = motion.tweenSpec(215),
                                        fadeOutSpec = motion.exitTween(185),
                                        placementSpec = motion.glideSpring()
                                    )
                                ) {
                                    SeriesSteamCard(
                                        series = series,
                                        memberCount = memberIds.size,
                                        leadCoverPath = leadCoverPath,
                                        onClick = { openedSeriesId = series.id }
                                    )
                                }
                            }
                        }
                    } else {
                        if (activeTab == LibraryViewTab.SERIES && activeSeries != null) {
                            val currentSeries = activeSeries!!
                            item(key = "lib_series_detail_header", span = { GridItemSpan(maxLineSpan) }, contentType = "series_header") {
                                SeriesDetailHeaderBar(
                                    series = currentSeries,
                                    docCount = filteredDocs.size,
                                    onBack = { openedSeriesId = null },
                                    onDelete = {
                                        viewModel.deletePlaylist(currentSeries)
                                        openedSeriesId = null
                                    }
                                )
                            }
                        }

                        if (filteredDocs.isEmpty()) {
                            item(key = "lib_books_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty_books") {
                                EmptyBooksCard(isInSeries = activeSeries != null)
                            }
                        } else {
                            items(
                                items = filteredDocs,
                                key = { "book_${it.id}" },
                                contentType = { "book_card" }
                            ) { doc ->
                                Box(
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = motion.tweenSpec(215),
                                        fadeOutSpec = motion.exitTween(185),
                                        placementSpec = motion.glideSpring()
                                    )
                                ) {
                                    SteamCoverBookTile(
                                        doc = doc,
                                        onOpen = { onOpenDocument(doc.id) },
                                        onLongPress = { optionsBook = doc }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Draw-phase frosted scrim replacing expensive animated full-screen Modifier.blur()
            LibraryFrostedMenuScrim(visible = anyFloatingMenuOpen)

            // Scoped Profile Dropdown Overlay (only collects social profile/friends when rendered)
            LibraryProfileDropdownHost(
                visible = showProfileMenu,
                anchorTopPx = profileSphereTopInRootPx,
                anchorRightPx = profileSphereRightInRootPx,
                socialRepo = socialRepo,
                isImporting = isImporting,
                onDismiss = { showProfileMenu = false },
                onOpenAccount = { showAccountDialog = true },
                onOpenFriends = { showFriendsDialog = true },
                onOpenSettings = { showSettingsSheet = true },
                onOpenGuide = { showAppGuide = true },
                onImportDocument = launchImport
            )

            LibraryFilterDropdownOverlay(
                visible = filterMenuExpanded,
                anchorBottomPx = filterButtonBottomInRootPx,
                selectedFilter = selectedFilter,
                onSelectFilter = { selectedFilter = it },
                onDismiss = { filterMenuExpanded = false }
            )

            // Isolated feedback host so action toast events never recompose LibraryScreen
            LibraryFeedbackHost(viewModel = viewModel)

            LumaInteractiveTourOverlay(
                visible = showInteractiveTour,
                targets = tourTargets,
                onFinish = {
                    showInteractiveTour = false
                    viewModel.updatePreferences { it.copy(hasCompletedOnboarding = true) }
                }
            )
        }
    }

    // Long-press Floating Book Action Window
    optionsBook?.let { doc ->
        BookOptionsFloatingDialog(
            doc = doc,
            loadSynopsis = viewModel::loadDocumentSynopsis,
            onAddToPlaylist = {
                optionsBook = null
                bookForPlaylistModal = doc
            },
            onChangeCoverPhoto = {
                optionsBook = null
                pendingCoverBook = doc
                coverPhotoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onRegenerateCover = {
                optionsBook = null
                viewModel.regenerateBookCover(doc)
            },
            onEditMetadata = {
                optionsBook = null
                bookForMetadataModal = doc
            },
            onDelete = {
                optionsBook = null
                viewModel.deleteDocument(doc)
            },
            onDismiss = { optionsBook = null }
        )
    }

    bookForMetadataModal?.let { doc ->
        EditBookMetadataDialog(
            doc = doc,
            loadSynopsis = viewModel::loadDocumentSynopsis,
            onSave = { title, author, genre, year, synopsis, badgeTag ->
                viewModel.updateBookMetadata(doc, title, author, genre, year, synopsis, badgeTag)
                bookForMetadataModal = null
            },
            onDismiss = { bookForMetadataModal = null }
        )
    }

    if (showAccountDialog) {
        AccountProfileDialog(
            socialRepo = socialRepo,
            currentOrRecentBookTitle = continueDoc?.title.orEmpty(),
            currentOrRecentBookProgressPct = ((continueDoc?.progressPercent ?: 0f) * 100f).roundToInt(),
            onDismiss = { showAccountDialog = false }
        )
    }

    if (showFriendsDialog) {
        FriendsChatDialog(
            socialRepo = socialRepo,
            currentOrRecentBookTitle = continueDoc?.title.orEmpty(),
            onDismiss = { showFriendsDialog = false }
        )
    }

    // Isolated Settings Host: collects full ReaderPreferences & dictionarySources ONLY while open!
    if (showSettingsSheet) {
        LibrarySettingsSheetHost(
            viewModel = viewModel,
            onReplayOnboarding = {
                showSettingsSheet = false
                showPermissionsOnboarding = true
            },
            onDismiss = { showSettingsSheet = false }
        )
    }

    if (showAppGuide) {
        LumaAppGuideDialog(
            documents = documents,
            onOpenSampleDocument = { docId ->
                showAppGuide = false
                viewModel.updatePreferences { it.copy(hasSeenGuide = true) }
                onOpenDocument(docId)
            },
            onDismiss = {
                showAppGuide = false
                viewModel.updatePreferences { it.copy(hasSeenGuide = true) }
            }
        )
    }

    if (showPermissionsOnboarding) {
        LumaPermissionsOnboardingDialog(
            onStartTour = {
                showPermissionsOnboarding = false
                showInteractiveTour = true
            },
            onSkipAll = {
                showPermissionsOnboarding = false
                showInteractiveTour = false
                viewModel.updatePreferences { it.copy(hasCompletedOnboarding = true) }
            }
        )
    }

    // Isolated Quick Profile Setup Host
    LibraryQuickProfileHost(
        socialRepo = socialRepo,
        canShow = preferencesLoaded &&
            hasCompletedOnboarding &&
            !showPermissionsOnboarding &&
            !showInteractiveTour &&
            !showAccountDialog &&
            !showFriendsDialog &&
            !showSettingsSheet &&
            !showAppGuide,
        onPickAvatar = {
            quickAvatarPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    )

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onCreate = { name, desc, colorHex ->
                viewModel.createPlaylist(name, desc, colorHex)
                showCreatePlaylistDialog = false
            },
            onDismiss = { showCreatePlaylistDialog = false }
        )
    }

    if (showCreateNotebookDialog) {
        CreateNotebookDialog(
            onCreate = { title, template, widthPx, heightPx, bgColor ->
                showCreateNotebookDialog = false
                notebooksActivated = true
                coroutineScope.launch {
                    val newId = notebookRepositoryProvider().createNotebook(
                        title = title,
                        template = template,
                        pageWidthPx = widthPx,
                        pageHeightPx = heightPx,
                        backgroundColor = bgColor
                    )
                    onOpenNotebook(newId)
                }
            },
            onCreateFromPdf = launchNotebookPdfPicker,
            onCreateFromImage = launchNotebookImagePicker,
            onDismiss = { showCreateNotebookDialog = false }
        )
    }

    optionsNotebook?.let { nb ->
        NotebookOptionsDialog(
            notebook = nb,
            onRename = { newTitle ->
                coroutineScope.launch {
                    notebookRepositoryProvider().renameNotebook(nb.id, newTitle)
                }
            },
            onDelete = {
                coroutineScope.launch {
                    notebookRepositoryProvider().deleteNotebook(nb.id)
                }
            },
            onDismiss = { optionsNotebook = null }
        )
    }

    bookForPlaylistModal?.let { doc ->
        val memberships = remember(playlistRefs, doc.id) {
            playlistRefs.filter { it.documentId == doc.id }.map { it.playlistId }.toSet()
        }
        AssignBookPlaylistsDialog(
            doc = doc,
            playlists = playlists,
            memberPlaylistIds = memberships,
            onToggleMembership = { plId, isMember ->
                viewModel.toggleBookInPlaylist(plId, doc.id, isMember)
            },
            onCreateNewPlaylist = {
                bookForPlaylistModal = null
                showCreatePlaylistDialog = true
            },
            onDismiss = { bookForPlaylistModal = null }
        )
    }
}

@Composable
private fun LibraryFrostedMenuScrim(visible: Boolean) {
    val palette = LocalFolioPalette.current
    val motion = LocalLumaMotion.current
    val scrimAlphaState = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = motion.tweenSpec(205),
        label = "library_menu_scrim_alpha"
    )
    val scrimBrush = remember(palette.isDark, palette.background) {
        val base = if (palette.isDark) Color.Black else palette.background
        Brush.verticalGradient(
            colors = listOf(
                base.copy(alpha = if (palette.isDark) 0.34f else 0.24f),
                Color.Black.copy(alpha = if (palette.isDark) 0.24f else 0.14f),
                base.copy(alpha = if (palette.isDark) 0.34f else 0.24f)
            )
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val a = scrimAlphaState.value
                if (a > 0.005f) {
                    drawRect(brush = scrimBrush, alpha = a)
                }
            }
    )
}

@Composable
private fun LibraryHeaderRow(
    bookCount: Int,
    seriesCount: Int,
    socialRepo: SocialRepository,
    isImporting: Boolean,
    onOpenGuide: () -> Unit,
    onToggleProfileMenu: () -> Unit,
    onHeaderRect: (Rect) -> Unit,
    onGuideRect: (Rect) -> Unit,
    onProfileBounds: (Float, Rect) -> Unit
) {
    val palette = LocalFolioPalette.current
    val userProfile by socialRepo.profile.collectAsStateWithLifecycle()
    val friendsList by socialRepo.friends.collectAsStateWithLifecycle()
    val totalUnreadMessages by remember(friendsList) {
        derivedStateOf { friendsList.sumOf { it.unreadCount } }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LumaWideWordmark(
            bookCount = bookCount,
            seriesCount = seriesCount,
            modifier = Modifier
                .weight(1f)
                .onGloballyPositioned { coords -> onHeaderRect(coords.boundsInWindow()) }
        )

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(48.dp)
                .onGloballyPositioned { coords -> onGuideRect(coords.boundsInWindow()) }
                .clip(CircleShape)
                .clickable(onClick = onOpenGuide)
                .testTag("library_guide_button"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(palette.surfaceAlt.copy(alpha = 0.85f))
                    .border(1.dp, palette.border, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.HelpOutline,
                    contentDescription = "Abrir guía de uso de Luma",
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.width(4.dp))

        Box(
            modifier = Modifier
                .size(48.dp)
                .onGloballyPositioned { coords ->
                    onProfileBounds(coords.positionInWindow().y, coords.boundsInWindow())
                }
                .clip(CircleShape)
                .clickable(onClick = onToggleProfileMenu)
                .testTag("library_account_button"),
            contentAlignment = Alignment.Center
        ) {
            UserAvatarCircle(
                displayName = userProfile.username.ifBlank { "C" },
                avatarPath = userProfile.avatarPath,
                avatarBase64 = "",
                size = 44.dp,
                showOnlineDot = userProfile.privacy.showOnlineStatus,
                unreadBadge = totalUnreadMessages
            )
            if (isImporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 2.4.dp,
                    color = palette.accent
                )
            }
        }
    }
}

@Composable
private fun LibraryTabsAndSearchControls(
    activeTab: LibraryViewTab,
    isSeriesOverview: Boolean,
    searchQuery: String,
    selectedFilter: String,
    onSelectTab: (LibraryViewTab) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCreatePlaylist: () -> Unit,
    onCreateNotebook: () -> Unit,
    onToggleFilterMenu: () -> Unit,
    onTabsRect: (Rect) -> Unit,
    onSearchFilterRect: (Rect) -> Unit,
    onFilterButtonBottom: (Float) -> Unit
) {
    val palette = LocalFolioPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords -> onTabsRect(coords.boundsInWindow()) }
                .clip(SwitcherOuterShape)
                .background(palette.surfaceAlt.copy(alpha = 0.75f))
                .border(1.dp, palette.border, SwitcherOuterShape)
                .padding(4.dp)
                .testTag("library_mode_switcher"),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (tab in LibraryViewTab.entries) {
                val selected = activeTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(SwitcherTabShape)
                        .background(if (selected) palette.accent else Color.Transparent)
                        .clickable { onSelectTab(tab) }
                        .testTag("library_tab_${tab.name}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) palette.onAccent else palette.inkMuted,
                        maxLines = 1
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords -> onSearchFilterRect(coords.boundsInWindow()) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = when {
                            activeTab == LibraryViewTab.NOTEBOOKS -> "Buscar cuaderno de estudio…"
                            isSeriesOverview -> "Buscar serie o saga…"
                            else -> "Buscar título o autor…"
                        },
                        color = palette.inkMuted
                    )
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = palette.inkMuted)
                },
                trailingIcon = if (searchQuery.isNotBlank()) {
                    {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Limpiar búsqueda",
                            tint = palette.inkMuted,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onSearchQueryChange("") }
                        )
                    }
                } else null,
                singleLine = true,
                shape = SearchInputShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.accent,
                    unfocusedBorderColor = palette.border,
                    focusedContainerColor = palette.surface,
                    unfocusedContainerColor = palette.surface
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("library_search_input")
            )

            if (activeTab == LibraryViewTab.NOTEBOOKS) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(SearchInputShape)
                        .background(palette.accent)
                        .clickable(onClick = onCreateNotebook)
                        .testTag("create_notebook_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Nuevo cuaderno",
                        tint = palette.onAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else if (isSeriesOverview) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(SearchInputShape)
                        .background(palette.accentSoft)
                        .border(1.dp, palette.accent.copy(alpha = 0.4f), SearchInputShape)
                        .clickable(onClick = onCreatePlaylist)
                        .testTag("create_playlist_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Nueva serie",
                        tint = palette.accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                val filterActive = selectedFilter != "Todos"
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .onGloballyPositioned { coords ->
                            onFilterButtonBottom(coords.positionInWindow().y + coords.size.height)
                        }
                        .clip(SearchInputShape)
                        .background(if (filterActive) palette.accent else palette.surface)
                        .border(
                            width = 1.dp,
                            color = if (filterActive) palette.accent else palette.border,
                            shape = SearchInputShape
                        )
                        .clickable(onClick = onToggleFilterMenu)
                        .testTag("library_filter_square_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Filtrar por formato",
                        tint = if (filterActive) palette.onAccent else palette.ink,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SeriesDetailHeaderBar(
    series: PlaylistEntity,
    docCount: Int,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalFolioPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SeriesHeaderShape)
            .background(palette.accentSoft)
            .border(1.dp, palette.accent.copy(alpha = 0.25f), SeriesHeaderShape)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Volver a series",
                tint = palette.ink,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = series.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = palette.ink
            )
            Text(
                text = series.description.ifBlank { "$docCount libros en esta serie" },
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Eliminar serie",
                tint = palette.inkMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun EmptySeriesCard(onCreate: () -> Unit) {
    val palette = LocalFolioPalette.current
    TactileCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.QueueMusic,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = "Aún no tienes series o playlists",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.ink
            )
            Text(
                text = "Crea una serie para agrupar tus sagas, manhwas o lecturas por tema.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkMuted
            )
            TactileButton(
                onClick = onCreate,
                cornerRadius = 16.dp,
                lipDepth = 2.dp
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Crear primera serie", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyBooksCard(isInSeries: Boolean) {
    val palette = LocalFolioPalette.current
    TactileCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoStories,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = if (isInSeries) "Esta serie aún está vacía" else "Sin resultados",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.ink
            )
            Text(
                text = if (isInSeries) {
                    "Mantén pulsado cualquier libro en la pestaña Libros para agregarlo aquí."
                } else {
                    "Ajusta el filtro o pulsa Importar para añadir un nuevo título."
                },
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkMuted
            )
        }
    }
}

@Composable
private fun LibraryProfileDropdownHost(
    visible: Boolean,
    anchorTopPx: Float,
    anchorRightPx: Float,
    socialRepo: SocialRepository,
    isImporting: Boolean,
    onDismiss: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit,
    onImportDocument: () -> Unit
) {
    val userProfile by socialRepo.profile.collectAsStateWithLifecycle()
    val friendsList by socialRepo.friends.collectAsStateWithLifecycle()
    val totalUnreadMessages by remember(friendsList) {
        derivedStateOf { friendsList.sumOf { it.unreadCount } }
    }
    ProfileDropdownCapsuleOverlay(
        visible = visible,
        anchorTopPx = anchorTopPx,
        anchorRightPx = anchorRightPx,
        userProfile = userProfile,
        unreadMessages = totalUnreadMessages,
        isImporting = isImporting,
        onDismiss = onDismiss,
        onOpenAccount = onOpenAccount,
        onOpenFriends = onOpenFriends,
        onOpenSettings = onOpenSettings,
        onOpenGuide = onOpenGuide,
        onImportDocument = onImportDocument
    )
}

@Composable
private fun LibraryFeedbackHost(viewModel: LibraryViewModel) {
    val feedbackEvent by viewModel.feedbackEvent.collectAsStateWithLifecycle()
    LumaActionFeedbackOverlay(
        event = feedbackEvent,
        onConsumed = viewModel::clearFeedbackEvent
    )
}

@Composable
private fun LibrarySettingsSheetHost(
    viewModel: LibraryViewModel,
    onReplayOnboarding: () -> Unit,
    onDismiss: () -> Unit
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val dictSources by viewModel.dictionarySources.collectAsStateWithLifecycle()
    AppSettingsSheet(
        prefs = prefs,
        sources = dictSources,
        onUpdatePrefs = viewModel::updatePreferences,
        onAddSource = viewModel::addDictionarySource,
        onToggleSource = viewModel::toggleDictionarySource,
        onDeleteSource = viewModel::deleteDictionarySource,
        onRegenerateCovers = viewModel::regenerateMissingCovers,
        onReplayOnboarding = onReplayOnboarding,
        onDismiss = onDismiss
    )
}

@Composable
private fun LibraryQuickProfileHost(
    socialRepo: SocialRepository,
    canShow: Boolean,
    onPickAvatar: () -> Unit
) {
    if (!canShow) return
    val userProfile by socialRepo.profile.collectAsStateWithLifecycle()
    if (!userProfile.profileConfigured) {
        QuickProfileSetupDialog(
            profile = userProfile,
            onPickAvatar = onPickAvatar,
            onSave = { chosenName -> socialRepo.updateProfileName(chosenName) },
            onSkip = { socialRepo.markProfilePromptDismissed() }
        )
    }
}
