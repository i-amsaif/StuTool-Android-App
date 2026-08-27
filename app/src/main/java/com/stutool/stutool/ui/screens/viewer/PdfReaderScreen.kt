package com.stutool.stutool.ui.screens.viewer

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.unit.Velocity

const val MAX_ZOOM = 8f
const val MIN_ZOOM = 1f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    navController: NavController,
    uriString: String,
    viewModel: PdfReaderViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val currentPdfUri by viewModel.currentPdfUri.collectAsState()
    val displayUri by viewModel.displayUri.collectAsState()
    val pageCount by viewModel.pageCount.collectAsState()
    val pdfName by viewModel.pdfName.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val isPasswordRequired by viewModel.isPasswordRequired.collectAsState()
    val isUnlocking by viewModel.isUnlocking.collectAsState()
    val passwordErrorMessage by viewModel.passwordErrorMessage.collectAsState()
    val isLargeFile by viewModel.isLargeFile.collectAsState()

    val backgroundMode by viewModel.backgroundMode.collectAsState()
    var sharedScale by remember(currentPdfUri) { mutableFloatStateOf(1f) }
    var sharedOffset by remember(currentPdfUri) { mutableStateOf(Offset.Zero) }
    val readingDirection by viewModel.readingDirection.collectAsState()
    val isReflow by viewModel.isReflowEnabled.collectAsState()
    val isKeepScreenOn by viewModel.isKeepScreenOn.collectAsState()

    val isFavorite by viewModel.isFavorite.collectAsState()

    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val currentMatchIndex by viewModel.currentMatchIndex.collectAsState()
    val isSearchParsing by viewModel.isSearchParsing.collectAsState()
    val searchNoTextFound by viewModel.searchNoTextFound.collectAsState()

    val bookmarks by viewModel.bookmarks.collectAsState()
    val restoredPage by viewModel.restoredPage.collectAsState()

    var showBookmarksSheet by remember { mutableStateOf(false) }
    var jumpToPage by remember { mutableStateOf<Int?>(null) }
    var currentPage by remember { mutableIntStateOf(0) }

    LaunchedEffect(currentPage) {
        viewModel.preloadTextWindow(currentPage)
    }

    var showBottomSheet by remember { mutableStateOf(false) }
    var showTextSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showGoToPageDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var barsVisible by remember { mutableStateOf(true) }
    var showGuide by remember { mutableStateOf(false) }
    var guideStep by remember { mutableIntStateOf(0) }
    val themePrefs = com.stutool.stutool.data.local.ThemePreferences.getInstance(context)

    LaunchedEffect(Unit) {
        if (!themePrefs.isPdfGuideComplete()) {
            showGuide = true
        }
    }

    LaunchedEffect(showGuide) {
        if (showGuide) {
            barsVisible = true
        }
    }

    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val view = LocalView.current
    val window = (view.context as? android.app.Activity)?.window

    val textBounds = remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val viewBounds = remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val shareBounds = remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    LaunchedEffect(isLandscape, barsVisible) {
        window?.let { win ->
            val controller = WindowInsetsControllerCompat(win, view)
            if (isLandscape && !barsVisible) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            window?.let { win ->
                val controller = WindowInsetsControllerCompat(win, view)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val isContinuousView by viewModel.isContinuousView.collectAsState()
    val continuousListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Ã¢â€â‚¬Ã¢â€â‚¬ Keep Screen On Ã¢â€â‚¬Ã¢â€â‚¬
    DisposableEffect(isKeepScreenOn) {
        if (isKeepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Enable sensor orientation while in this screen, restore on leave
    DisposableEffect(Unit) {
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
        onDispose {
            activity?.requestedOrientation = previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(uriString) {
        val decodedString = String(
            android.util.Base64.decode(uriString, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
        )
        val uri = Uri.parse(decodedString)
        viewModel.openPdf(uri, context, null)
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.inversePrimary,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        topBar = {
            AnimatedVisibility(
                visible = barsVisible,
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it }
            ) {
                if (isSearchActive) {
                    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.closeSearch() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Close search", tint = MaterialTheme.colorScheme.onSurface)
                        }

                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            placeholder = { Text("Find in document", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            singleLine = true,
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        )

                        if (isSearchParsing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else if (searchResults.isNotEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${currentMatchIndex + 1}/${searchResults.size}",
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.previousSearchResult() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match", tint = MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(
                                onClick = { viewModel.nextSearchResult() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        } else if (searchQuery.isNotEmpty() && !isSearchParsing) {
                            Text(
                                text = "No results",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Navigate back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        Text(
                            text = if (currentPdfUri != null) pdfName ?: "Document" else "Loadingâ€¦",
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                        val isCurrentPageBookmarked = bookmarks.contains(currentPage)
                        IconButton(
                            onClick = { viewModel.toggleBookmark(currentPage) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (isCurrentPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (isCurrentPageBookmarked) "Remove bookmark" else "Add bookmark",
                                tint = if (isCurrentPageBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                            )
                        }
                        IconButton(
                            onClick = { viewModel.openSearch() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search in document", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        IconButton(
                            onClick = {
                                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                                activity?.requestedOrientation = if (isLandscape) {
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.ScreenRotation, contentDescription = "Toggle orientation", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = MaterialTheme.colorScheme.onBackground)
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Bookmarks", color = MaterialTheme.colorScheme.onSurface) },
                                    leadingIcon = { Icon(Icons.Default.Bookmarks, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        showMenu = false
                                        showBookmarksSheet = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Rename", color = MaterialTheme.colorScheme.onSurface) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        showMenu = false
                                        showRenameDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (isFavorite) "Unmark Favorite" else "Mark Favorite", color = MaterialTheme.colorScheme.onSurface) },
                                    leadingIcon = {
                                        Icon(
                                            if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = null,
                                            tint = if (isFavorite) Color(0xFFFFD700) else MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        viewModel.toggleFavorite()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Go to Page", color = MaterialTheme.colorScheme.onSurface) },
                                    leadingIcon = { Icon(Icons.Default.FindInPage, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        showMenu = false
                                        showGoToPageDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Print", color = MaterialTheme.colorScheme.onSurface) },
                                    leadingIcon = { Icon(Icons.Default.Print, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.printPdf(context)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("PDF Viewer Guide", color = MaterialTheme.colorScheme.onSurface) },
                                    leadingIcon = { Icon(Icons.Default.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        showMenu = false
                                        guideStep = 0
                                        showGuide = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = when (backgroundMode) {
            BackgroundMode.Paper -> Color(0xFFF4ECD8)
            BackgroundMode.EyeComfort -> Color(0xFFFCE9D2)
            BackgroundMode.Invert -> Color.Black
            else -> MaterialTheme.colorScheme.background
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                ,
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Opening documentâ€¦",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else if (isPasswordRequired) {
                PasswordDialog(
                    fileName = pdfName ?: "Document",
                    errorMessage = passwordErrorMessage,
                    isUnlocking = isUnlocking,
                    onUnlock = { viewModel.unlockPdf(it, context) },
                    onCancel = { navController.popBackStack() }
                )
            } else if (displayUri != null && pageCount > 0) {
                var storedPage by rememberSaveable { mutableIntStateOf(0) }

                if (isLargeFile) {
                    LaunchedEffect(Unit) {
                        snackbarHostState.showSnackbar("Large file detected. Some features may be slower.")
                    }
                }
                key(readingDirection) {
                    val pagerState = rememberPagerState(initialPage = storedPage, pageCount = { pageCount })
                    val pagerCoroutineScope = rememberCoroutineScope()
                    var previousMode by remember { mutableStateOf(isContinuousView) }

                    LaunchedEffect(isContinuousView) {
                        if (previousMode != isContinuousView) {
                            if (isContinuousView) {
                                continuousListState.scrollToItem(currentPage)
                            } else {
                                pagerState.scrollToPage(currentPage)
                            }
                            previousMode = isContinuousView
                        }

                        if (isContinuousView) {
                            androidx.compose.runtime.snapshotFlow { continuousListState.firstVisibleItemIndex }.collect { index ->
                                storedPage = index
                                currentPage = index
                                viewModel.saveReadingProgress(index)
                            }
                        } else {
                            androidx.compose.runtime.snapshotFlow { pagerState.currentPage }.collect { index ->
                                storedPage = index
                                currentPage = index
                                viewModel.saveReadingProgress(index)
                            }
                        }
                    }

                    LaunchedEffect(pageCount, restoredPage) {
                        val pageToRestore = restoredPage
                        if (pageCount > 0 && pageToRestore != null) {
                            if (pageToRestore in 0 until pageCount) {
                                if (isContinuousView) {
                                    continuousListState.scrollToItem(pageToRestore)
                                } else {
                                    pagerState.scrollToPage(pageToRestore)
                                }
                            }
                            viewModel.consumeRestoredPage()
                        }
                    }

                    LaunchedEffect(jumpToPage) {
                        jumpToPage?.let { page ->
                            if (page in 0 until pageCount) {
                                if (isContinuousView) {
                                    continuousListState.animateScrollToItem(page)
                                } else {
                                    pagerState.animateScrollToPage(page)
                                }
                            }
                            jumpToPage = null
                        }
                    }

                    // Auto-navigation for Search
                    LaunchedEffect(currentMatchIndex, searchResults, isSearchActive) {
                        if (searchResults.isNotEmpty() && isSearchActive && currentMatchIndex in searchResults.indices) {
                            val match = searchResults[currentMatchIndex]
                            if (pagerState.currentPage != match.pageIndex) {
                                pagerState.animateScrollToPage(match.pageIndex)
                            }
                        }
                    }

                    if (isContinuousView) {
                        ContinuousPdfViewer(
                            pageCount = pageCount,
                            viewModel = viewModel,
                            backgroundMode = backgroundMode,
                            readingDirection = readingDirection,
                            listState = continuousListState,
                            onSingleTap = { barsVisible = !barsVisible },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (readingDirection == ReadingDirection.Vertical) {
                        VerticalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            pageSpacing = 12.dp,
                            userScrollEnabled = true
                        ) { index ->
                            val isActive = index == pagerState.currentPage
                            val isSettled = index == pagerState.settledPage
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val viewerWidth = constraints.maxWidth.toFloat()
                                val viewerHeight = constraints.maxHeight.toFloat()

                                if (isReflow) {
                                    ReflowPdfPage(
                                        pageIndex = index,
                                        viewModel = viewModel,
                                        backgroundMode = backgroundMode,
                                        isSettled = isSettled,
                                        onSingleTap = { barsVisible = !barsVisible }
                                    )
                                } else {
                                    ZoomablePdfPage(
                                        pageIndex = index,
                                        viewModel = viewModel,
                                        backgroundMode = backgroundMode,
                                        viewerWidth = viewerWidth,
                                        viewerHeight = viewerHeight,
                                        isActive = isActive,
                                        isSettled = isSettled,
                                        onSingleTap = { barsVisible = !barsVisible },
                                        sharedScale = sharedScale,
                                        onScaleChange = { sharedScale = it },
                                        sharedOffset = sharedOffset,
                                        onOffsetChange = { sharedOffset = it },
                                        currentPage = pagerState.currentPage,
                                        isHorizontalPager = false,
                                        onBoundaryPush = { isNext ->
                                            pagerCoroutineScope.launch {
                                                val target = if (isNext) pagerState.currentPage + 1 else pagerState.currentPage - 1
                                                if (target in 0 until pageCount) {
                                                    pagerState.animateScrollToPage(target)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            pageSpacing = 12.dp,
                            userScrollEnabled = true
                        ) { index ->
                            val isActive = index == pagerState.currentPage
                            val isSettled = index == pagerState.settledPage
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val viewerWidth = constraints.maxWidth.toFloat()
                                val viewerHeight = constraints.maxHeight.toFloat()

                                if (isReflow) {
                                    ReflowPdfPage(
                                        pageIndex = index,
                                        viewModel = viewModel,
                                        backgroundMode = backgroundMode,
                                        isSettled = isSettled,
                                        onSingleTap = { barsVisible = !barsVisible }
                                    )
                                } else {
                                    ZoomablePdfPage(
                                        pageIndex = index,
                                        viewModel = viewModel,
                                        backgroundMode = backgroundMode,
                                        viewerWidth = viewerWidth,
                                        viewerHeight = viewerHeight,
                                        isActive = isActive,
                                        isSettled = isSettled,
                                        onSingleTap = { barsVisible = !barsVisible },
                                        sharedScale = sharedScale,
                                        onScaleChange = { sharedScale = it },
                                        sharedOffset = sharedOffset,
                                        onOffsetChange = { sharedOffset = it },
                                        currentPage = pagerState.currentPage,
                                        isHorizontalPager = true,
                                        onBoundaryPush = { isNext ->
                                            pagerCoroutineScope.launch {
                                                val target = if (isNext) pagerState.currentPage + 1 else pagerState.currentPage - 1
                                                if (target in 0 until pageCount) {
                                                    pagerState.animateScrollToPage(target)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Page Number Badge (clickable to open go-to-page)
                    AnimatedVisibility(
                        visible = barsVisible,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars),
                        enter = slideInVertically { it } + fadeIn(animationSpec = tween(150)),
                        exit = slideOutVertically { it } + fadeOut(animationSpec = tween(150))
                    ) {
                        Surface(
                            onClick = { showGoToPageDialog = true },
                            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(16.dp),
                            shadowElevation = 6.dp,
                            modifier = Modifier.padding(bottom = 96.dp)
                        ) {
                            Text(
                                text = "${currentPage + 1} / $pageCount",
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // â”€â”€ Scrubber â”€â”€
                    var scrubJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
                    var lastTargetPage by remember { mutableIntStateOf(-1) }
                    AnimatedVisibility(
                        visible = barsVisible,
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.systemBars),
                        enter = fadeIn(animationSpec = tween(150)),
                        exit = fadeOut(animationSpec = tween(150))
                    ) {
                        PdfScrubber(
                            pageCount = pageCount,
                            currentPage = currentPage,
                            onPageSelect = { targetPage ->
                                if (targetPage != lastTargetPage) {
                                    lastTargetPage = targetPage
                                    scrubJob?.cancel()
                                    scrubJob = coroutineScope.launch {
                                        if (isContinuousView) {
                                            continuousListState.animateScrollToItem(targetPage)
                                        } else {
                                            pagerState.animateScrollToPage(targetPage)
                                        }
                                        lastTargetPage = -1
                                    }
                                }
                            },
                            isVertical = readingDirection == ReadingDirection.Vertical
                        )
                    }

                    // Ã¢â€â‚¬Ã¢â€â‚¬ Go to Page Dialog Ã¢â€â‚¬Ã¢â€â‚¬
                    if (showGoToPageDialog) {
                        GoToPageDialog(
                            currentPage = pagerState.currentPage + 1,
                            totalPages = pageCount,
                            onDismiss = { showGoToPageDialog = false },
                            onGoToPage = { targetPage ->
                                showGoToPageDialog = false
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(targetPage - 1)
                                }
                            }
                        )
                    }

                    // â”€â”€ Text Mode Bottom Sheet â”€â”€
                    if (showTextSheet) {
                        TextSelectionSheet(
                            pageIndex = currentPage,
                            pageCount = pageCount,
                            viewModel = viewModel,
                            onDismiss = { showTextSheet = false }
                        )
                    }
                }

            } else if (errorMessage != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Failed to load PDF",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "The document may be corrupted or unsupported",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }

            // Bottom Controls Overlay
            AnimatedVisibility(
                visible = barsVisible,
                modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 16.dp),
                enter = slideInVertically { it } + fadeIn(animationSpec = tween(150)),
                exit = slideOutVertically { it } + fadeOut(animationSpec = tween(150))
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 8.dp,
                    modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(0.92f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showTextSheet = true },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                                .onGloballyPositioned { textBounds.value = it.boundsInWindow() },
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Description, contentDescription = "Extract text from page", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Text", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        TextButton(
                            onClick = { showBottomSheet = true },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                                .onGloballyPositioned { viewBounds.value = it.boundsInWindow() },
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "View settings", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("View", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        TextButton(
                            onClick = {
                                val uri = currentPdfUri
                                if (uri != null) {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
                                }
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                                .onGloballyPositioned { shareBounds.value = it.boundsInWindow() },
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share PDF", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Share", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }

        // â”€â”€ Bookmarks Bottom Sheet â”€â”€
        if (showBookmarksSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
            ModalBottomSheet(
                onDismissRequest = { showBookmarksSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(24.dp).fillMaxHeight(0.6f)) {
                    Text(
                        "Bookmarks",
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (bookmarks.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.BookmarkBorder,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No bookmarks yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Tap the bookmark icon to save pages",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn {
                            items(bookmarks) { page ->
                                val isCurrent = page == currentPage
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showBookmarksSheet = false
                                            jumpToPage = page
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Bookmark,
                                        contentDescription = "Bookmarked page ${page + 1}",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        "Page ${page + 1}",
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (isCurrent) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                "Current",
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    IconButton(
                                        onClick = { viewModel.removeBookmark(page) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Remove bookmark for page ${page + 1}",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // â”€â”€ View Mode Bottom Sheet â”€â”€
        if (showBottomSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "View Mode",
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Text("Background", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ModeButton("Original", backgroundMode == BackgroundMode.Original, Modifier.weight(1f)) { viewModel.setBackgroundMode(BackgroundMode.Original) }
                        ModeButton("Paper", backgroundMode == BackgroundMode.Paper, Modifier.weight(1f)) { viewModel.setBackgroundMode(BackgroundMode.Paper) }
                        ModeButton("Eye", backgroundMode == BackgroundMode.EyeComfort, Modifier.weight(1f)) { viewModel.setBackgroundMode(BackgroundMode.EyeComfort) }
                        ModeButton("Invert", backgroundMode == BackgroundMode.Invert, Modifier.weight(1f)) { viewModel.setBackgroundMode(BackgroundMode.Invert) }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Scrolling Mode", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Continuous Scroll", color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Normal)
                            Text("Scroll through pages continuously", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        Switch(
                            checked = isContinuousView,
                            onCheckedChange = { viewModel.toggleContinuousView() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Reading Direction", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ModeButton("Vertical", readingDirection == ReadingDirection.Vertical, Modifier.weight(1f)) {
                            viewModel.setReadingDirection(ReadingDirection.Vertical)
                        }
                        ModeButton("Horizontal", readingDirection == ReadingDirection.Horizontal, Modifier.weight(1f)) {
                            viewModel.setReadingDirection(ReadingDirection.Horizontal)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Options", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))

                    ToggleRow(
                        label = "Keep Screen On",
                        icon = Icons.Default.LightMode,
                        checked = isKeepScreenOn,
                        onToggle = { viewModel.toggleKeepScreenOn() }
                    )
                    ToggleRow(
                        label = "Reflow (Preview)",
                        icon = Icons.Default.WrapText,
                        checked = isReflow,
                        onToggle = { viewModel.toggleReflow() }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // Ã¢â€â‚¬Ã¢â€â‚¬ Rename Dialog Ã¢â€â‚¬Ã¢â€â‚¬
        if (showRenameDialog) {
            var newName by remember { mutableStateOf(pdfName ?: "") }
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename PDF", color = MaterialTheme.colorScheme.onBackground) },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.renamePdf(newName)
                        }
                        showRenameDialog = false
                    }) {
                        Text("Rename", color = MaterialTheme.colorScheme.primary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        }

        // â”€â”€ PDF Viewer Guide Overlay â”€â”€
        if (showGuide) {
            val targetRect = when (guideStep) {
                0 -> textBounds.value
                1 -> viewBounds.value
                2 -> shareBounds.value
                else -> null
            }
            val title = when (guideStep) {
                0 -> "Copy Text"
                1 -> "View Settings"
                2 -> "Share PDF"
                else -> ""
            }
            val desc = when (guideStep) {
                0 -> "Copy text directly from PDF pages."
                1 -> "Change reading settings, like Continuous Scroll and other viewing options."
                2 -> "Share this document instantly with others."
                else -> ""
            }

            val density = LocalDensity.current
            val spotlightPadding = with(density) { 8.dp.toPx() }
            val spotlightCorner = with(density) { 20.dp.toPx() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {}) // Consume touches
                    }
            ) {
                // Dimmed overlay with spotlight cutout
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val dimPath = Path().apply {
                        addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                    }
                    targetRect?.let { rect ->
                        val spotlightRect = androidx.compose.ui.geometry.Rect(
                            left = rect.left - spotlightPadding,
                            top = rect.top - spotlightPadding,
                            right = rect.right + spotlightPadding,
                            bottom = rect.bottom + spotlightPadding
                        )
                        val spotlightPath = Path().apply {
                            addRoundRect(
                                androidx.compose.ui.geometry.RoundRect(
                                    spotlightRect,
                                    androidx.compose.ui.geometry.CornerRadius(spotlightCorner, spotlightCorner)
                                )
                            )
                        }
                        dimPath.op(dimPath, spotlightPath, androidx.compose.ui.graphics.PathOperation.Difference)
                    }
                    drawPath(dimPath, Color.Black.copy(alpha = 0.7f), style = Fill)
                }

                // Guide card positioned above the spotlight
                if (targetRect != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .align(Alignment.Center)
                            .offset(y = (-48).dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp)
                        ) {
                            Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = desc, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp)
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = {
                                    showGuide = false
                                    themePrefs.setPdfGuideComplete()
                                }) {
                                    Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Button(
                                    onClick = {
                                        if (guideStep < 2) {
                                            guideStep++
                                        } else {
                                            showGuide = false
                                            themePrefs.setPdfGuideComplete()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = com.stutool.stutool.ui.theme.BrandRed)
                                ) {
                                    Text(if (guideStep < 2) "Next" else "Done", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val BOUNDARY_PUSH_MULTIPLIER = 6f
private const val BOUNDARY_PUSH_DAMPEN = 0.5f

// Search highlight vertical polish: extend the highlight rectangle slightly
// upward so it fully covers taller characters, while preserving bottom alignment.
private const val HIGHLIGHT_VERTICAL_PAD_RATIO = 0.28f
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZoomablePdfPage(
    pageIndex: Int,
    viewModel: PdfReaderViewModel,
    backgroundMode: BackgroundMode,
    viewerWidth: Float,
    viewerHeight: Float,
    isActive: Boolean,
    isSettled: Boolean,
    onSingleTap: () -> Unit,
    sharedScale: Float,
    onScaleChange: (Float) -> Unit,
    sharedOffset: Offset,
    onOffsetChange: (Offset) -> Unit,
    currentPage: Int,
    isHorizontalPager: Boolean,
    onBoundaryPush: (isNext: Boolean) -> Unit
) {

    var isTransforming by remember { mutableStateOf(false) }
    var zoomAnimationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val currentSharedScale by rememberUpdatedState(sharedScale)

    val coroutineScope = rememberCoroutineScope()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE


    var pageRatio by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(pageIndex) {
        val size = viewModel.getPageSize(pageIndex)
        if (size != null && size.second != 0) {
            pageRatio = size.first.toFloat() / size.second.toFloat()
        }
    }

    val dstRatio = viewerWidth / viewerHeight
    val actualWidth: Float
    val actualHeight: Float
    if (pageRatio > dstRatio) {
        actualWidth = viewerWidth
        actualHeight = viewerWidth / pageRatio
    } else {
        actualHeight = viewerHeight
        actualWidth = viewerHeight * pageRatio
    }
    val currentViewerWidth by rememberUpdatedState(viewerWidth)
    val currentViewerHeight by rememberUpdatedState(viewerHeight)

    val maxX = ((actualWidth * sharedScale - viewerWidth) / 2f).coerceAtLeast(0f)
    val maxY = ((actualHeight * sharedScale - viewerHeight) / 2f).coerceAtLeast(0f)

    // BUG FIX: Never recreate the MutableState object, otherwise pointerInput(Unit) holds a stale reference.
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Handle return to 1x and page-switching initial offsets
    LaunchedEffect(sharedScale == 1f, currentPage) {
        if (sharedScale == 1f) {
            offset = Offset.Zero
        } else if (pageIndex != currentPage) {
            // When zoomed in, neighbor pages start at their boundaries so panning feels continuous
            val newMaxX = ((actualWidth * sharedScale - viewerWidth) / 2f).coerceAtLeast(0f)
            val newMaxY = ((actualHeight * sharedScale - viewerHeight) / 2f).coerceAtLeast(0f)
            offset = Offset(
                x = if (isHorizontalPager) {
                    if (pageIndex > currentPage) newMaxX else -newMaxX
                } else offset.x,
                y = if (!isHorizontalPager) {
                    if (pageIndex > currentPage) newMaxY else -newMaxY
                } else offset.y
            )
        }
    }

    // Sync active offset to global, and global to inactive neighbors for perfect continuity
    LaunchedEffect(offset) {
        if (isActive) {
            onOffsetChange(offset)
        }
    }

    LaunchedEffect(sharedOffset) {
        if (!isActive && sharedScale > 1f) {
            offset = Offset(
                x = if (isHorizontalPager) offset.x else sharedOffset.x.coerceIn(-maxX, maxX),
                y = if (!isHorizontalPager) offset.y else sharedOffset.y.coerceIn(-maxY, maxY)
            )
        }
    }

    LaunchedEffect(viewerWidth, viewerHeight, pageRatio, sharedScale, isTransforming) {
        if (!isTransforming) {
            val targetX = offset.x.coerceIn(-maxX, maxX)
            val targetY = offset.y.coerceIn(-maxY, maxY)
            if (targetX != offset.x || targetY != offset.y) {
                androidx.compose.animation.core.Animatable(
                    initialValue = offset,
                    typeConverter = androidx.compose.animation.core.TwoWayConverter(
                        convertToVector = { androidx.compose.animation.core.AnimationVector2D(it.x, it.y) },
                        convertFromVector = { Offset(it.v1, it.v2) }
                    )
                ).animateTo(
                    targetValue = Offset(targetX, targetY),
                    animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)
                ) {
                    offset = this.value
                }
            }
        }
    }

    // â”€â”€ Pan Engine v2: Clean gesture ownership â”€â”€
    // Rule: scale<=1 â†’ Pager owns all. scale>1 â†’ Page owns all panning.
    // Pager only receives gestures when at the pager-axis boundary AND pushing past it.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val touchSlop = viewConfiguration.touchSlop
                val triggerThreshold = touchSlop * BOUNDARY_PUSH_MULTIPLIER
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isTransforming = true

                    var pastSlop = false
                    var accZoom = 1f
                    var accPan = Offset.Zero
                    var boundaryTriggered = false
                    var unconsumedBoundaryPush = 0f

                    do {
                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        if (event.changes.any { it.isConsumed }) break

                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val centroid = event.calculateCentroid()
                        val pointerCount = event.changes.count { it.pressed }

                        // â”€â”€ Touch slop gate â”€â”€
                        if (!pastSlop) {
                            accZoom *= zoomChange
                            accPan += panChange
                            val centroidSize = event.calculateCentroidSize(useCurrent = false)
                            val zoomMotion = kotlin.math.abs(1 - accZoom) * centroidSize
                            val panMotion = accPan.getDistance()

                            // When zoomed or multi-finger, consume immediately so the Pager
                            // never sees these events. Offset updates still wait for slop.
                            if (currentSharedScale > 1f || pointerCount > 1) {
                                event.changes.forEach { if (it.pressed) it.consume() }
                            }

                            pastSlop = when {
                                pointerCount > 1 -> true                                          // Pinch: always claim
                                currentSharedScale > 1f && panMotion > touchSlop -> true           // Zoomed pan: always claim
                                else -> false                                                      // 1x single-finger: Pager owns it
                            }
                            if (!pastSlop) continue
                        }

                        // â”€â”€ Helper: calculate page boundaries at a given scale â”€â”€
                        fun pageBounds(scale: Float): Pair<Float, Float> {
                            val dstR = currentViewerWidth / currentViewerHeight
                            val aW = if (pageRatio > dstR) currentViewerWidth else currentViewerHeight * pageRatio
                            val aH = if (pageRatio > dstR) currentViewerWidth / pageRatio else currentViewerHeight
                            return Pair(
                                ((aW * scale - currentViewerWidth) / 2f).coerceAtLeast(0f),
                                ((aH * scale - currentViewerHeight) / 2f).coerceAtLeast(0f)
                            )
                        }

                        // â”€â”€ Gesture processing â”€â”€
                        when {
                            // â”€â”€ Multi-finger: pinch-zoom (always consume) â”€â”€
                            pointerCount > 1 -> {
                                event.changes.forEach { if (it.pressed) it.consume() }

                                val zoomFactor = 1.5f
                                val actualZoom = 1f + (zoomChange - 1f) * zoomFactor
                                val newScale = (currentSharedScale * actualZoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                val scaleRatio = if (currentSharedScale > 0f) newScale / currentSharedScale else 1f
                                val rel = Offset(centroid.x - currentViewerWidth / 2f, centroid.y - currentViewerHeight / 2f)
                                val (mx, my) = pageBounds(newScale)

                                offset = Offset(
                                    x = (offset.x * scaleRatio - rel.x * (scaleRatio - 1f) + panChange.x).coerceIn(-mx, mx),
                                    y = (offset.y * scaleRatio - rel.y * (scaleRatio - 1f) + panChange.y).coerceIn(-my, my)
                                )
                                if (currentSharedScale != newScale) onScaleChange(newScale)
                            }

                            // â”€â”€ Single-finger while zoomed: pan â”€â”€
                            currentSharedScale > 1f -> {
                                val (mx, my) = pageBounds(currentSharedScale)
                                event.changes.forEach { if (it.pressed) it.consume() }

                                val atBoundaryAndPushing = if (isHorizontalPager) {
                                    (panChange.x > 0f && offset.x >= mx) || (panChange.x < 0f && offset.x <= -mx)
                                } else {
                                    (panChange.y > 0f && offset.y >= my) || (panChange.y < 0f && offset.y <= -my)
                                }

                                if (!atBoundaryAndPushing) {
                                    offset = Offset(
                                        x = (offset.x + panChange.x).coerceIn(-mx, mx),
                                        y = (offset.y + panChange.y).coerceIn(-my, my)
                                    )
                                    if (unconsumedBoundaryPush != 0f) {
                                        android.util.Log.d("PdfPanLog", "Page $pageIndex | boundary push reset | moved away")
                                    }
                                    unconsumedBoundaryPush = 0f
                                } else if (!boundaryTriggered) {
                                    val pushDelta = if (isHorizontalPager) panChange.x else panChange.y
                                    val dampenedDelta = pushDelta * BOUNDARY_PUSH_DAMPEN
                                    unconsumedBoundaryPush += dampenedDelta

                                    // Visual overscroll (edge resistance) on paging axis, regular pan on cross axis
                                    offset = Offset(
                                        x = if (isHorizontalPager) offset.x + dampenedDelta else (offset.x + panChange.x).coerceIn(-mx, mx),
                                        y = if (!isHorizontalPager) offset.y + dampenedDelta else (offset.y + panChange.y).coerceIn(-my, my)
                                    )

                                    android.util.Log.d("PdfPanLog", "Page $pageIndex | boundary pushing | acc=$unconsumedBoundaryPush | delta=$pushDelta | threshold=$triggerThreshold")

                                    if (kotlin.math.abs(unconsumedBoundaryPush) > triggerThreshold) {
                                        boundaryTriggered = true
                                        val isNext = pushDelta < 0f
                                        android.util.Log.d("PdfPanLog", "Page $pageIndex | boundary TRIGGERED | isNext=$isNext")
                                        onBoundaryPush(isNext)
                                    }
                                }
                            }

                            // â”€â”€ 1x single-finger: don't consume, Pager owns scrolling â”€â”€
                        }
                    } while (event.changes.any { it.pressed })

                    isTransforming = false
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        val targetScale = when {
                            currentSharedScale < 2.4f -> 2.5f
                            currentSharedScale < 4.9f -> 5f
                            else -> 1f
                        }

                        val startScale = currentSharedScale
                        val startOffset = offset
                        val zoomFactor = targetScale / startScale

                        val limitX = ((actualWidth * targetScale - currentViewerWidth) / 2f).coerceAtLeast(0f)
                        val limitY = ((actualHeight * targetScale - currentViewerHeight) / 2f).coerceAtLeast(0f)

                        val targetOffsetX = (startOffset.x * zoomFactor - (tapOffset.x - currentViewerWidth / 2f) * (zoomFactor - 1f)).coerceIn(-limitX, limitX)
                        val targetOffsetY = (startOffset.y * zoomFactor - (tapOffset.y - currentViewerHeight / 2f) * (zoomFactor - 1f)).coerceIn(-limitY, limitY)

                        zoomAnimationJob?.cancel()
                        zoomAnimationJob = coroutineScope.launch {
                            try {
                                isTransforming = true
                                androidx.compose.animation.core.Animatable(0f).animateTo(
                                    targetValue = 1f,
                                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 250, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                                ) {
                                    val frac = this.value
                                    onScaleChange(androidx.compose.ui.util.lerp(startScale, targetScale, frac))
                                    offset = Offset(
                                        androidx.compose.ui.util.lerp(startOffset.x, targetOffsetX, frac),
                                        androidx.compose.ui.util.lerp(startOffset.y, targetOffsetY, frac)
                                    )
                                }
                            } finally {
                                isTransforming = false
                            }
                        }
                    },
                    onTap = {
                        onSingleTap()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        PdfReaderPageItem(
            pageIndex = pageIndex,
            viewModel = viewModel,
            scale = sharedScale,
            offset = offset,
            isTransforming = isTransforming,
            backgroundMode = backgroundMode,
            viewerWidth = viewerWidth,
            viewerHeight = viewerHeight,
            isActive = isActive,
            isSettled = isSettled
        )
    }
}

// Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â
// Ã¢â€â‚¬Ã¢â€â‚¬ PDF Bitmap Page Renderer + Selection Overlay Ã¢â€â‚¬Ã¢â€â‚¬
// Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â

@Composable
fun ReflowPdfPage(
    pageIndex: Int,
    viewModel: PdfReaderViewModel,
    backgroundMode: BackgroundMode,
    isSettled: Boolean,
    onSingleTap: () -> Unit
) {
    val pageText = produceState(initialValue = "Extracting text...", pageIndex, isSettled) {
        if (isSettled) {
            value = viewModel.getPageText(pageIndex)
        }
    }

    val textColor = when (backgroundMode) {
        BackgroundMode.Invert -> Color.White
        else -> MaterialTheme.colorScheme.onBackground
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onSingleTap() })
            }
            .padding(16.dp),
        contentAlignment = Alignment.TopStart
    ) {
        SelectionContainer {
            Text(
                text = pageText.value,
                color = textColor,
                fontSize = 15.sp,
                lineHeight = 24.sp,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
fun PdfReaderPageItem(
    pageIndex: Int,
    viewModel: PdfReaderViewModel,
    scale: Float,
    offset: Offset,
    isTransforming: Boolean,
    backgroundMode: BackgroundMode,
    viewerWidth: Float,
    viewerHeight: Float,
    isActive: Boolean,
    isSettled: Boolean
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val viewerWidthInt = viewerWidth.toInt()
    val coroutineScope = rememberCoroutineScope()

    // â”€â”€ Layer 1: Base bitmap (two-phase: fast 1Ã— first, then upgrade to 1.5Ã—) â”€â”€
    var baseBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(pageIndex) {
        // Phase 1: Render at 1Ã— width for instant display
        val fastBitmap = viewModel.getPageBitmap(pageIndex, viewerWidthInt)
        if (baseBitmap == null) {
            baseBitmap = fastBitmap
        }
    }

    // Phase 2: Upgrade to 1.5Ã— for sharper text (non-blocking, only for settled page)
    LaunchedEffect(pageIndex, isSettled) {
        if (isSettled && baseBitmap != null) {
            val hqBitmap = viewModel.getPageBitmap(pageIndex, (viewerWidthInt * 1.5f).toInt())
            if (hqBitmap != null) {
                baseBitmap = hqBitmap
            }
        }
    }

    // â”€â”€ Layer 2: High-res zoom-level bitmap (rendered ONLY on gesture end) â”€â”€
    var zoomBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    // Track the zoom level the current zoomBitmap was rendered at
    var renderedZoomLevel by remember { mutableFloatStateOf(1f) }

    // Detect gesture end: isTransforming transitions trueâ†’false
    var prevIsTransforming by remember { mutableStateOf(false) }

    LaunchedEffect(isTransforming, scale) {
        val gestureEnded = prevIsTransforming && !isTransforming

        prevIsTransforming = isTransforming

        if (scale <= 1.05f) {
            // Zoomed back to ~1x, discard high-res
            zoomBitmap = null
            renderedZoomLevel = 1f
            return@LaunchedEffect
        }

        if (!isTransforming) {
            // If the scale change was NOT triggered by an explicit action (gesture end),
            // wait briefly to avoid race conditions.
            if (!gestureEnded) {
                delay(350)
            }

            val targetZoom = viewModel.snapToZoomLevel(scale)
            // Only re-render if we need a different zoom level
            if (targetZoom != renderedZoomLevel || zoomBitmap == null) {
                val bmp = viewModel.getZoomLevelBitmap(pageIndex, targetZoom, viewerWidthInt)
                if (bmp != null) {
                    zoomBitmap = bmp
                    renderedZoomLevel = targetZoom
                }
            }
        }
    }

    // â”€â”€ Defer text layout extraction until bitmap is ready AND page is settled â”€â”€
    // This prevents PDFBox from competing for I/O during initial render and scrolling
    val layoutState = produceState<PdfPageLayout?>(initialValue = null, pageIndex, baseBitmap != null, isSettled) {
        if (baseBitmap != null && isSettled) {
            value = viewModel.getPageLayout(pageIndex)
        }
    }

    val colorFilter = if (backgroundMode == BackgroundMode.Invert) {
        ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            -1f,  0f,  0f, 0f, 255f,
             0f, -1f,  0f, 0f, 255f,
             0f,  0f, -1f, 0f, 255f,
             0f,  0f,  0f, 1f,   0f
        )))
    } else if (backgroundMode == BackgroundMode.EyeComfort) {
        ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            0.988f, 0f,  0f, 0f, 0f,
            0f, 0.913f, 0f, 0f, 0f,
            0f,  0f, 0.823f, 0f, 0f,
            0f,  0f,  0f, 1f, 0f
        )))
    } else if (backgroundMode == BackgroundMode.Paper) {
        ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            0.957f, 0f,  0f, 0f, 0f,
            0f, 0.925f, 0f, 0f, 0f,
            0f,  0f, 0.847f, 0f, 0f,
            0f,  0f,  0f, 1f, 0f
        )))
    } else null

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val customTextSelectionColors = TextSelectionColors(
        handleColor = MaterialTheme.colorScheme.primary,
        backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    )

    // Always use the raw scale for graphicsLayer. The zoomBitmap has the same
    // aspect ratio as baseBitmap (same PDF page, just higher pixel resolution)
    // so ContentScale.Fit inside the composable handles resolution differences
    // without needing any scale correction.

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val layout = layoutState.value

        CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
            SelectionContainer {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

                    // Main bitmap layer: graphicsLayer scales/translates the existing bitmap.
                    // During active zoom this is the ONLY work doneâ€”no rendering, no allocation.
                    // Use baseBitmap for layout (aspect ratio), display best available bitmap.
                    val layoutBitmap = baseBitmap
                    val currentDisplay = zoomBitmap ?: baseBitmap

                    if (layoutBitmap != null && currentDisplay != null) {
                        Box(
                            modifier = Modifier
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                )
                                .then(if (isLandscape) Modifier.fillMaxHeight() else Modifier.fillMaxWidth())
                                .aspectRatio(layoutBitmap.width.toFloat() / layoutBitmap.height.toFloat())
                                .background(Color.White)
                        ) {
                            Image(
                                bitmap = currentDisplay.asImageBitmap(),
                                contentDescription = "PDF page ${pageIndex + 1}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                                colorFilter = colorFilter
                            )
                        }

                    } else {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Search Overlay
                    val isSearchActive by viewModel.isSearchActive.collectAsState()
                    val searchResults by viewModel.searchResults.collectAsState()
                    val currentMatchIndex by viewModel.currentMatchIndex.collectAsState()

                    if (isSearchActive && layout != null && layoutBitmap != null) {
                        val pageMatches = searchResults.filter { it.pageIndex == pageIndex }
                        if (pageMatches.isNotEmpty()) {
                            val activeMatch = searchResults.getOrNull(currentMatchIndex)
                            Box(
                                modifier = Modifier
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offset.x,
                                        translationY = offset.y
                                    )
                                    .then(if (isLandscape) Modifier.fillMaxHeight() else Modifier.fillMaxWidth())
                                    .aspectRatio(layoutBitmap.width.toFloat() / layoutBitmap.height.toFloat())
                            ) {
                                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                    val scaleX = size.width / layout.pageWidth
                                    val scaleY = size.height / layout.pageHeight

                                    for (match in pageMatches) {
                                        val isActiveMatch = match === activeMatch
                                        val highlightColor = if (isActiveMatch) Color(0x80FF9800) else Color(0x80FFEB3B)

                                        for (bound in match.bounds) {
                                            val rawHeight = (bound.bottom - bound.top) * scaleY
                                            val padPx = rawHeight * HIGHLIGHT_VERTICAL_PAD_RATIO
                                            drawRect(
                                                color = highlightColor,
                                                topLeft = androidx.compose.ui.geometry.Offset(
                                                    bound.left * scaleX,
                                                    bound.top * scaleY - padPx
                                                ),
                                                size = androidx.compose.ui.geometry.Size(
                                                    (bound.right - bound.left) * scaleX,
                                                    rawHeight + padPx
                                                )
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
    }
}

@Composable
fun SelectionOverlay(
    layout: PdfPageLayout,
    bitmapWidth: Float,
    bitmapHeight: Float
) {
    val density = LocalDensity.current

    // Scale factors between PDF points and Bitmap pixels
    val scaleX = bitmapWidth / layout.pageWidth
    val scaleY = bitmapHeight / layout.pageHeight

    Box(modifier = Modifier.fillMaxSize()) {
        layout.elements.forEach { element ->
            val left = element.x * scaleX
            val top = element.y * scaleY
            val width = element.width * scaleX
            val height = element.height * scaleY

            val leftDp = with(density) { left.toDp() }
            val topDp = with(density) { top.toDp() }
            val widthDp = with(density) { (width + 2).toDp() } // Slight horizontal overlap for continuity
            val heightDp = with(density) { height.toDp() }

            Text(
                text = element.text,
                modifier = Modifier
                    .offset(x = leftDp, y = topDp)
                    .size(width = widthDp, height = heightDp),
                color = Color.Transparent, // Use transparent color instead of alpha
                fontSize = with(density) { (height * 0.8f).toSp() }, // Match visual height roughly
                lineHeight = with(density) { height.toSp() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextSelectionSheet(
    pageIndex: Int,
    pageCount: Int,
    viewModel: PdfReaderViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var includeNearby by remember { mutableStateOf(false) }

    val pageText = produceState(initialValue = "Extracting text...", pageIndex, includeNearby) {
        value = "Extracting text..."
        if (!includeNearby) {
            value = viewModel.getPageText(pageIndex)
        } else {
            val maxIndex = pageCount - 1
            if (maxIndex < 0) {
                value = "No pages available."
                return@produceState
            }
            var end = minOf(maxIndex, pageIndex + 1)
            val start = maxOf(0, end - 2)
            end = minOf(maxIndex, start + 2)

            val combinedText = buildString {
                for (i in start..end) {
                    append("--- Page ${i + 1} ---\n\n")
                    append(viewModel.getPageText(i))
                    if (i < end) append("\n\n")
                }
            }
            value = combinedText
        }
    }

    val isExtracting = pageText.value == "Extracting text..."

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (!includeNearby) "Page ${pageIndex + 1} Text" else "Pages Text",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("PDF Page Text", pageText.value)
                        clipboard.setPrimaryClip(clip)
                    },
                    enabled = !isExtracting
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = if (isExtracting) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy All", color = if (isExtracting) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = includeNearby,
                    onCheckedChange = { includeNearby = it },
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Include Nearby Pages", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = pageText.value,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

// Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â
// Ã¢â€â‚¬Ã¢â€â‚¬ Bottom Sheet Components Ã¢â€â‚¬Ã¢â€â‚¬
// Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â

@Composable
fun ModeButton(title: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (isSelected) 2.dp else 0.dp
        )
    ) {
        Text(
            title,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun ToggleRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                label,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

// -----------------------------------------------------------------
// -- Go-to-Page Dialog --
// -----------------------------------------------------------------

@Composable
fun GoToPageDialog(
    currentPage: Int,
    totalPages: Int,
    onDismiss: () -> Unit,
    onGoToPage: (Int) -> Unit
) {
    var pageInput by remember { mutableStateOf(currentPage.toString()) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to Page", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Enter a page number (1 - $totalPages)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = {
                        pageInput = it
                        isError = false
                    },
                    label = { Text("Page") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = isError,
                    supportingText = {
                        if (isError) Text("Enter a number between 1 and $totalPages", color = MaterialTheme.colorScheme.error)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        errorBorderColor = MaterialTheme.colorScheme.error
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val page = pageInput.toIntOrNull()
                if (page != null && page in 1..totalPages) {
                    onGoToPage(page)
                } else {
                    isError = true
                }
            }) {
                Text("Go", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    )
}

@Composable
fun PasswordDialog(
    fileName: String,
    errorMessage: String?,
    isUnlocking: Boolean,
    onUnlock: (String) -> Unit,
    onCancel: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Password Protected", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "'$fileName' is encrypted. Please enter the password to open it.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    singleLine = true,
                    enabled = !isUnlocking,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        if (password.isNotEmpty()) onUnlock(password)
                    }),
                    isError = errorMessage != null,
                    supportingText = {
                        if (errorMessage != null) {
                            Text(errorMessage, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        errorBorderColor = MaterialTheme.colorScheme.error
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onUnlock(password) },
                enabled = password.isNotEmpty() && !isUnlocking,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isUnlocking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Open")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !isUnlocking) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    )
}





