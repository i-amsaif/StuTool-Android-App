package com.stutool.stutool.navigation

import androidx.compose.ui.platform.LocalContext

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.compose.runtime.collectAsState
import com.stutool.stutool.ui.components.BottomNavBar
import com.stutool.stutool.ui.components.DrawerContent
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import android.app.Activity
import com.stutool.stutool.ui.components.TopBar
import kotlinx.coroutines.launch
import com.stutool.stutool.ui.screens.HomeScreen
import com.stutool.stutool.ui.screens.resume.ResumeBuilderScreen
import com.stutool.stutool.ui.screens.resume.ResumeSplashScreen
import com.stutool.stutool.ui.screens.ToolsScreen
import com.stutool.stutool.ui.screens.library.PdfLibraryScreen
import com.stutool.stutool.ui.screens.viewer.PdfReaderScreen
import com.stutool.stutool.ui.screens.tools.MergePdfScreen
import com.stutool.stutool.ui.screens.tools.ImageToPdfScreen
import com.stutool.stutool.ui.screens.tools.PagesPerSheetScreen
import com.stutool.stutool.ui.screens.tools.ExtractPagesScreen
import com.stutool.stutool.ui.screens.tools.CompressPdfScreen
import com.stutool.stutool.ui.screens.AboutScreen
import com.stutool.stutool.ui.screens.PrivacyPolicyScreen
import com.stutool.stutool.ui.screens.scanner.ScannerScreen
import com.stutool.stutool.ui.screens.scanner.DocumentReviewScreen
import com.stutool.stutool.ui.screens.scanner.EdgeCropScreen
import com.stutool.stutool.ui.screens.scanner.ScannerViewModel
import com.stutool.stutool.ui.screens.scanner.BatchFilterScreen       // @Deprecated: kept as fallback
import com.stutool.stutool.ui.screens.scanner.SinglePageFilterEditorScreen // @Deprecated: kept as fallback
import com.stutool.stutool.ui.screens.scanner.BatchFilterPagerScreen
import androidx.lifecycle.viewmodel.compose.viewModel


sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Outlined.Home)
    object Viewer : Screen("viewer", "PDF Viewer", Icons.Outlined.PictureAsPdf)
    object Scan : Screen("scanner_camera", "Document Scanner", com.stutool.stutool.ui.screens.scanner.ScannerTokens.NavBarScannerIcon)
    object Tools : Screen("tools", "PDF Tools", Icons.Outlined.Build)
    object Resume : Screen("resume", "Resume Builder", Icons.Outlined.Person)
    object ResumeBuilder : Screen("resume_builder", "Resume Editor", Icons.Default.Person)
    object MergePdf : Screen("merge_pdf", "Merge PDF", Icons.Default.Add)
    object ImageToPdf : Screen("image_to_pdf", "Image to PDF", Icons.Default.Add)
    object PagesPerSheet : Screen("pages_per_sheet", "Multiple Pages per Sheet", Icons.Default.Add)
    object ExtractPages : Screen("extract_pages", "Extract Pages", Icons.Default.Add)
    object CompressPdf : Screen("compress_pdf", "Compress PDF", Icons.Default.Add)
    object PdfToImage : Screen("pdf_to_image", "PDF to Image", Icons.Default.Add)
    object OcrPdf : Screen("ocr_pdf", "Searchable PDF", Icons.Default.Add)
    object PdfToWord : Screen("pdf_to_word", "PDF to Word", Icons.Default.Add)
    object PdfToExcel : Screen("pdf_to_excel", "PDF to Excel", Icons.Default.Add)
}

// Routes where the shell TopBar is visible
private val TOP_BAR_ROUTES = listOf(
    Screen.Home.route,
    Screen.Viewer.route,
    Screen.Tools.route
)

// Routes where the BottomNavBar is visible
private val BOTTOM_BAR_ROUTES = listOf(
    Screen.Home.route,
    Screen.Viewer.route,
    Screen.Tools.route
)

// Routes that belong to an isolated splash experience — the shell must never
// render while ANY of these routes is still visible (including during animations).
private val SPLASH_ROUTES = setOf(
    "splash_screen",
    "onboarding",              // First-launch onboarding
    Screen.Resume.route,       // ResumeSplashScreen entry
    "resume_exit_splash"       // ResumeSplashScreen exit
)

@Composable
fun AppNavigation(
    initialPdfUriEncoded: String? = null,
    externalPdfFlow: kotlinx.coroutines.flow.SharedFlow<String>? = null,
    isDarkMode: Boolean = false,
    onThemeToggle: () -> Unit = {},
    themeViewModel: com.stutool.stutool.ui.theme.ThemeViewModel? = null
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isAtHomeRoot = currentRoute == Screen.Home.route
    var showExitConfirmationDialog by rememberSaveable { mutableStateOf(false) }

    // Intercept system Back only when Home is the root destination
    BackHandler(enabled = isAtHomeRoot) {
        showExitConfirmationDialog = true
    }

    val showTopBar = currentRoute in TOP_BAR_ROUTES
    val showBottomBar = currentRoute in BOTTOM_BAR_ROUTES

    val appContext = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(externalPdfFlow) {
        externalPdfFlow?.collect { encodedUri ->
            val targetRoute = resolveReaderRouteForEncodedUri(appContext, encodedUri)
            navController.navigate(targetRoute) {
                launchSingleTop = true
            }
        }
    }

    // Pre-compute navigation bar padding before Scaffold consumes insets
    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // â”€â”€ Drawer state (only interactive when TopBar is visible) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = showTopBar, // Prevent swipe on splash / tool screens
        drawerContent = { 
            DrawerContent(
                drawerState = drawerState, 
                currentRoute = currentRoute,
                onNavigate = { route ->
                    // Resume Builder from drawer should go through splash
                    val actualRoute = if (route == "resume_builder") "resume" else route
                    navController.navigate(actualRoute) {
                        popUpTo(Screen.Home.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onThemeToggle = onThemeToggle,
                isDarkMode = isDarkMode,
                onExitClick = {
                    showExitConfirmationDialog = true
                }
            ) 
        }
    ) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (showTopBar || showBottomBar) WindowInsets.systemBars else WindowInsets(0.dp), // Root handles all insets
        topBar = {
            if (showTopBar) {
                TopBar(
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onSettingsClick = { navController.navigate("settings") }
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                // Ensure padding is applied to float it, as previously it was absolute aligned
                Box(modifier = Modifier.padding(bottom = navBarBottomPadding)) {
                    BottomNavBar(navController = navController)
                }
            }
        }
    ) { innerPadding ->
        if (showExitConfirmationDialog) {
            val activity = (appContext as? Activity)
                ?: ((appContext as? android.content.ContextWrapper)?.baseContext as? Activity)
            AlertDialog(
                onDismissRequest = { showExitConfirmationDialog = false },
                title = {
                    Text(
                        text = "Exit StuTool",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(text = "Are you sure you want to exit the app?")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showExitConfirmationDialog = false
                            activity?.finish()
                        }
                    ) {
                        Text(
                            text = "Exit",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitConfirmationDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = "splash_screen",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = if (showTopBar) innerPadding.calculateTopPadding() else 0.dp,
                        bottom = 0.dp
                    ),
                enterTransition = {
                    slideInHorizontally(
                        animationSpec = tween(250, easing = FastOutSlowInEasing)
                    ) { fullWidth -> fullWidth } + fadeIn(
                        animationSpec = tween(180, easing = FastOutSlowInEasing),
                        initialAlpha = 0.6f
                    )
                },
                exitTransition = {
                    slideOutHorizontally(
                        animationSpec = tween(250, easing = FastOutSlowInEasing)
                    ) { fullWidth -> -fullWidth / 4 } + fadeOut(
                        animationSpec = tween(150),
                        targetAlpha = 0.6f
                    )
                },
                popEnterTransition = {
                    slideInHorizontally(
                        animationSpec = tween(250, easing = FastOutSlowInEasing)
                    ) { fullWidth -> -fullWidth } + fadeIn(
                        animationSpec = tween(180, easing = FastOutSlowInEasing),
                        initialAlpha = 0.6f
                    )
                },
                popExitTransition = {
                    slideOutHorizontally(
                        animationSpec = tween(250, easing = FastOutSlowInEasing)
                    ) { fullWidth -> fullWidth / 4 } + fadeOut(
                        animationSpec = tween(150),
                        targetAlpha = 0.6f
                    )
                }
            ) {
            // â”€â”€ Splash Screen â”€â”€
            composable("splash_screen") {
                val context = LocalContext.current
                com.stutool.stutool.ui.screens.SplashScreen(
                    onSplashComplete = {
                        val target = if (initialPdfUriEncoded != null) {
                            // External document intent always bypasses onboarding
                            resolveReaderRouteForEncodedUri(context, initialPdfUriEncoded)
                        } else {
                            val prefs = com.stutool.stutool.data.local.ThemePreferences.getInstance(context)
                            if (prefs.isOnboardingComplete()) {
                                Screen.Home.route
                            } else {
                                "onboarding"
                            }
                        }
                        navController.navigate(target) {
                            popUpTo("splash_screen") { inclusive = true }
                        }
                    }
                )
            }

            // â”€â”€ Onboarding (first-launch or replay from Settings) â”€â”€
            // Exit transition uses a slow crossfade so Home dissolves in seamlessly.
            composable(
                "onboarding",
                enterTransition = { fadeIn(tween(350, easing = FastOutSlowInEasing)) },
                exitTransition = {
                    fadeOut(tween(420, easing = FastOutSlowInEasing))
                },
                popEnterTransition = { fadeIn(tween(300, easing = FastOutSlowInEasing)) },
                popExitTransition = {
                    fadeOut(tween(350, easing = FastOutSlowInEasing))
                }
            ) {
                val context = LocalContext.current
                com.stutool.stutool.ui.screens.OnboardingScreen(
                    onComplete = {
                        com.stutool.stutool.data.local.ThemePreferences.getInstance(context)
                            .setOnboardingComplete()
                        navController.navigate(Screen.Home.route) {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }

            composable(
                Screen.Home.route,
                enterTransition = {
                    // From onboarding: smooth dissolve-in matching the onboarding fade-out
                    fadeIn(tween(400, easing = FastOutSlowInEasing))
                }
            ) {
                HomeScreen(navController)
            }

            composable(Screen.Viewer.route) {
                PdfLibraryScreen(navController)
            }

            composable(Screen.Tools.route) {
                ToolsScreen(navController)
            }

            composable(
                Screen.Scan.route,
                enterTransition = { fadeIn(tween(180, easing = FastOutSlowInEasing)) + slideInVertically(initialOffsetY = { it / 10 }, animationSpec = tween(180, easing = FastOutSlowInEasing)) },
                exitTransition = { fadeOut(tween(140)) },
                popEnterTransition = { fadeIn(tween(160, easing = FastOutSlowInEasing)) },
                popExitTransition = { fadeOut(tween(140)) + slideOutVertically(targetOffsetY = { it / 10 }, animationSpec = tween(140, easing = FastOutSlowInEasing)) }
            ) {
                val scannerViewModel = sharedScannerViewModel(navController)
                ScannerScreen(navController, scannerViewModel)
            }
            
            composable(
                "edge_crop",
                enterTransition = { fadeIn(tween(200)) + scaleIn(initialScale = 0.97f, animationSpec = tween(200)) },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { fadeOut(tween(150)) }
            ) {
                val scannerViewModel = sharedScannerViewModel(navController)
                EdgeCropScreen(navController, scannerViewModel)
            }


            
            // DEPRECATED: batch_filter kept temporarily as fallback during pager migration
            composable(
                "batch_filter",
                enterTransition = { fadeIn(tween(250)) + slideInHorizontally(initialOffsetX = { it / 4 }) },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { fadeOut(tween(150)) + slideOutHorizontally(targetOffsetX = { it / 4 }) }
            ) {
                // Redirect to new pager screen if navigated here directly
                val scannerViewModel = sharedScannerViewModel(navController)
                BatchFilterPagerScreen(navController, scannerViewModel)
            }

            composable(
                "batch_crop_review",
                enterTransition = { fadeIn(tween(280)) + slideInHorizontally(initialOffsetX = { it / 5 }) },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(targetOffsetX = { it / 5 }) }
            ) {
                val scannerViewModel = sharedScannerViewModel(navController)
                com.stutool.stutool.ui.screens.scanner.BatchCropReviewScreen(navController, scannerViewModel)
            }

            composable(
                "batch_filter_pager",
                enterTransition = { fadeIn(tween(280)) + slideInHorizontally(initialOffsetX = { it / 5 }) },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(targetOffsetX = { it / 5 }) }
            ) {
                val scannerViewModel = sharedScannerViewModel(navController)
                BatchFilterPagerScreen(navController, scannerViewModel)
            }

            composable(
                "filter_crop",
                enterTransition = { fadeIn(tween(280)) + slideInHorizontally(initialOffsetX = { it / 5 }) },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(targetOffsetX = { it / 5 }) }
            ) {
                val scannerViewModel = sharedScannerViewModel(navController)
                com.stutool.stutool.ui.screens.scanner.FilterCropScreen(navController, scannerViewModel)
            }

            composable(
                route = "single_page_filter_editor/{pageId}",
                arguments = listOf(navArgument("pageId") { type = NavType.StringType }),
                enterTransition = { fadeIn(tween(200)) },
                exitTransition = { fadeOut(tween(150)) }
            ) { backStackEntry ->
                val scannerViewModel = sharedScannerViewModel(navController)
                val pageId = backStackEntry.arguments?.getString("pageId") ?: ""
                SinglePageFilterEditorScreen(navController, scannerViewModel, pageId)
            }

            composable(
                "document_review",
                enterTransition = { fadeIn(tween(250)) + slideInHorizontally(initialOffsetX = { it / 4 }) },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { fadeOut(tween(150)) + slideOutHorizontally(targetOffsetX = { it / 4 }) }
            ) {
                val scannerViewModel = sharedScannerViewModel(navController)
                DocumentReviewScreen(navController, scannerViewModel)
            }

            composable(Screen.Resume.route) {
                // Initial Resume tab splash
                ResumeSplashScreen(
                    animate = true,
                    onSplashComplete = {
                        navController.navigate(Screen.ResumeBuilder.route) {
                            // Replace current splash in backstack
                            popUpTo(Screen.Resume.route) { inclusive = true }
                        }
                    }
                )
            }

            // â”€â”€ Resume Builder (Full screen Editor) â”€â”€
            composable(Screen.ResumeBuilder.route) {
                ResumeBuilderScreen(onNavigateBack = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.ResumeBuilder.route) { inclusive = true }
                        launchSingleTop = true
                    }
                })
            }

            // â”€â”€ Resume Exit Splash (closing transition) â”€â”€
            composable("resume_exit_splash") {
                ResumeSplashScreen(
                    animate = true,
                    onSplashComplete = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo("resume_exit_splash") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            // â”€â”€ Tool Routes (full-screen, respect NavHost padding) â”€â”€
            composable(Screen.MergePdf.route) { MergePdfScreen(navController) }
            composable(Screen.ImageToPdf.route) { ImageToPdfScreen(navController) }
            composable(Screen.PagesPerSheet.route) { PagesPerSheetScreen(navController) }
            composable(Screen.ExtractPages.route) { ExtractPagesScreen(navController) }
            composable(Screen.CompressPdf.route) { CompressPdfScreen(navController) }
            composable(Screen.PdfToImage.route) { com.stutool.stutool.ui.screens.tools.PdfToImageScreen(navController) }
            composable(Screen.OcrPdf.route) { com.stutool.stutool.ui.screens.tools.OcrPdfScreen(navController) }
            composable(Screen.PdfToWord.route) { com.stutool.stutool.ui.screens.tools.PdfToWordScreen(navController) }
            composable(Screen.PdfToExcel.route) { com.stutool.stutool.ui.screens.tools.PdfToExcelScreen(navController) }

            // ── PDF Reader (full-screen) ──
            composable(
                route = "reader_screen/{uri}",
                arguments = listOf(navArgument("uri") { type = NavType.StringType })
            ) { backStackEntry ->
                val uriStr = backStackEntry.arguments?.getString("uri") ?: ""
                PdfReaderScreen(navController = navController, uriString = uriStr)
            }

            // ── TXT Reader (isolated full-screen) ──
            composable(
                route = "txt_reader/{uri}",
                arguments = listOf(navArgument("uri") { type = NavType.StringType })
            ) { backStackEntry ->
                val uriStr = backStackEntry.arguments?.getString("uri") ?: ""
                com.stutool.stutool.ui.screens.viewer.TxtReaderScreen(
                    navController = navController,
                    uriString = uriStr
                )
            }

            // ── DOCX Reader (isolated full-screen) ──
            composable(
                route = "docx_reader/{uri}",
                arguments = listOf(navArgument("uri") { type = NavType.StringType })
            ) { backStackEntry ->
                val uriStr = backStackEntry.arguments?.getString("uri") ?: ""
                com.stutool.stutool.ui.screens.viewer.DocxReaderScreen(
                    navController = navController,
                    uriString = uriStr
                )
            }

            // ── XLSX Reader (isolated full-screen) ──
            composable(
                route = "xlsx_reader/{uri}",
                arguments = listOf(navArgument("uri") { type = NavType.StringType })
            ) { backStackEntry ->
                val uriStr = backStackEntry.arguments?.getString("uri") ?: ""
                com.stutool.stutool.ui.screens.viewer.XlsxReaderScreen(
                    navController = navController,
                    uriString = uriStr
                )
            }
            composable("settings") {
                com.stutool.stutool.ui.screens.SettingsScreen(
                    navController = navController,
                    isDarkMode = isDarkMode,
                    onThemeToggle = onThemeToggle,
                    themeViewModel = themeViewModel!!
                )
            }
            composable("about") {
                AboutScreen(navController = navController)
            }
            composable("privacy_policy") {
                PrivacyPolicyScreen(navController = navController)
            }
            composable("feedback") {
                com.stutool.stutool.ui.screens.FeedbackScreen(navController = navController)
            }

            } // end NavHost
        } // end Box
    } // end Scaffold
    } // end ModalNavigationDrawer
}

@Composable
private fun sharedScannerViewModel(navController: androidx.navigation.NavController): ScannerViewModel {
    val navBackStackEntry = androidx.compose.runtime.remember(navController) {
        navController.getBackStackEntry(navController.graph.id)
    }
    return androidx.lifecycle.viewmodel.compose.viewModel(navBackStackEntry)
}

internal fun resolveReaderRouteForEncodedUri(
    context: android.content.Context,
    encodedUri: String
): String {
    return try {
        val decodedUriString = String(
            android.util.Base64.decode(encodedUri, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP),
            Charsets.UTF_8
        )
        val uri = android.net.Uri.parse(decodedUriString)
        val mimeType = context.contentResolver.getType(uri)
        var displayName: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) {
                        displayName = cursor.getString(idx)
                    }
                }
            }
        } catch (_: Exception) {
        }
        val candidateName = displayName ?: uri.lastPathSegment ?: uri.path ?: ""

        val isXlsxOrExcel = candidateName.endsWith(".xlsx", ignoreCase = true) ||
            candidateName.endsWith(".xls", ignoreCase = true) ||
            mimeType.equals(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                ignoreCase = true
            ) ||
            mimeType.equals("application/vnd.ms-excel", ignoreCase = true)

        val isDocxOrWord = !isXlsxOrExcel && (
            candidateName.endsWith(".docx", ignoreCase = true) ||
                candidateName.endsWith(".doc", ignoreCase = true) ||
                mimeType.equals(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    ignoreCase = true
                ) ||
                mimeType.equals("application/msword", ignoreCase = true)
            )

        val isTxt = !isXlsxOrExcel && !isDocxOrWord && (
            (mimeType != null && mimeType.startsWith("text/", ignoreCase = true)) ||
                candidateName.endsWith(".txt", ignoreCase = true)
            )

        when {
            isXlsxOrExcel -> "xlsx_reader/$encodedUri"
            isDocxOrWord -> "docx_reader/$encodedUri"
            isTxt -> "txt_reader/$encodedUri"
            else -> "reader_screen/$encodedUri"
        }
    } catch (_: Exception) {
        "reader_screen/$encodedUri"
    }
}



