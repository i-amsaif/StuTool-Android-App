package com.stutool.stutool.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stutool.stutool.R
import com.stutool.stutool.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TOTAL_PAGES = 5

// ── Responsive helpers ────────────────────────────────────────────────────────

private enum class ScreenBucket { COMPACT, NORMAL, LARGE }

@Composable
private fun screenBucket(): ScreenBucket {
    val heightDp = LocalConfiguration.current.screenHeightDp
    return when {
        heightDp < 640 -> ScreenBucket.COMPACT
        heightDp < 780 -> ScreenBucket.NORMAL
        else -> ScreenBucket.LARGE
    }
}

@Composable
private fun adaptiveHorizontalPadding(): Dp {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp < 360 -> 16.dp
        widthDp < 400 -> 20.dp
        else -> 24.dp
    }
}

// ── Main Onboarding Screen ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val isDark = LocalAppThemeIsDark.current
    val backgroundColor = if (isDark) DarkBackground else LightBackground
    val pagerState = rememberPagerState(pageCount = { TOTAL_PAGES })
    val scope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage
    val bucket = screenBucket()

    // Bottom area height for pager content padding
    val bottomAreaHeight = when (bucket) {
        ScreenBucket.COMPACT -> 96.dp
        ScreenBucket.NORMAL -> 112.dp
        ScreenBucket.LARGE -> 120.dp
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .systemBarsPadding()
    ) {
        // ── Top bar: Skip button ─────────────────────────────────────────────
        AnimatedVisibility(
            visible = currentPage < TOTAL_PAGES - 1,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .zIndex(2f)
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 8.dp)
        ) {
            TextButton(
                onClick = onComplete,
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Text(
                    text = "Skip",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ── Pager ────────────────────────────────────────────────────────────
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomAreaHeight)
        ) { page ->
            when (page) {
                0 -> WelcomePage(isDark, bucket)
                1 -> ViewerScannerPage(isDark, bucket)
                2 -> ResumeBuilderPage(isDark, bucket)
                3 -> PdfToolsPage(isDark, bucket)
                4 -> GetStartedPage(isDark, bucket)
            }
        }

        // ── Bottom navigation area with soft gradient backdrop ───────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.28f to backgroundColor.copy(alpha = 0.88f),
                        0.58f to backgroundColor,
                        1.0f to backgroundColor
                    )
                )
                .padding(bottom = when (bucket) {
                    ScreenBucket.COMPACT -> 12.dp
                    ScreenBucket.NORMAL -> 18.dp
                    ScreenBucket.LARGE -> 22.dp
                })
                .padding(top = 16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Dot indicators (animated Box composables)
                PageIndicators(
                    totalPages = TOTAL_PAGES,
                    currentPage = currentPage
                )

                Spacer(modifier = Modifier.height(when (bucket) {
                    ScreenBucket.COMPACT -> 12.dp
                    ScreenBucket.NORMAL -> 18.dp
                    ScreenBucket.LARGE -> 22.dp
                }))

                // Navigation buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = adaptiveHorizontalPadding()),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button with animated entrance
                    AnimatedVisibility(
                        visible = currentPage > 0,
                        enter = fadeIn(tween(200)) + scaleIn(
                            initialScale = 0.8f,
                            animationSpec = tween(200)
                        ),
                        exit = fadeOut(tween(150)) + scaleOut(
                            targetScale = 0.8f,
                            animationSpec = tween(150)
                        )
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(
                                        currentPage - 1,
                                        animationSpec = tween(350, easing = FastOutSlowInEasing)
                                    )
                                }
                            },
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (isDark) DarkSurfaceContainer else Color(0xFFF2F2F7)
                            )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    // Invisible spacer when back is hidden
                    if (currentPage == 0) {
                        Spacer(modifier = Modifier.size(48.dp))
                    }

                    // Next / Get Started with crossfade
                    AnimatedContent(
                        targetState = currentPage < TOTAL_PAGES - 1,
                        transitionSpec = {
                            fadeIn(tween(250)) togetherWith fadeOut(tween(200))
                        },
                        label = "nav_button"
                    ) { isNextButton ->
                        if (isNextButton) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(
                                            currentPage + 1,
                                            animationSpec = tween(350, easing = FastOutSlowInEasing)
                                        )
                                    }
                                },
                                modifier = Modifier.height(48.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrandRed,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    text = "Next",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            Button(
                                onClick = onComplete,
                                modifier = Modifier.height(48.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrandRed,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    text = "Get Started",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Page Indicators (animated Box composables + spring physics) ──────────────

@Composable
private fun PageIndicators(totalPages: Int, currentPage: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalPages) { index ->
            val isSelected = index == currentPage
            val width by animateDpAsState(
                targetValue = if (isSelected) 28.dp else 8.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "indicator_width"
            )
            val color by animateColorAsState(
                targetValue = if (isSelected) BrandRed else {
                    if (LocalAppThemeIsDark.current) Color(0xFF3A3A3C) else Color(0xFFD1D1D6)
                },
                animationSpec = tween(250),
                label = "indicator_color"
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

// ── Page 1: Welcome ──────────────────────────────────────────────────────────

@Composable
private fun WelcomePage(isDark: Boolean, bucket: ScreenBucket) {
    var logoVisible by remember { mutableStateOf(false) }
    var textVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80)
        logoVisible = true
        delay(250)
        textVisible = true
    }

    val logoScale by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0.85f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "logo_scale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0f,
        animationSpec = tween(500),
        label = "logo_alpha"
    )

    val logoSize = when (bucket) {
        ScreenBucket.COMPACT -> 100.dp
        ScreenBucket.NORMAL -> 125.dp
        ScreenBucket.LARGE -> 145.dp
    }
    val titleSize = when (bucket) {
        ScreenBucket.COMPACT -> 28.sp
        ScreenBucket.NORMAL -> 34.sp
        ScreenBucket.LARGE -> 38.sp
    }

    val horizontalPadding = adaptiveHorizontalPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // App logo with scale + fade entrance
            Image(
                painter = painterResource(id = R.drawable.logo_comp),
                contentDescription = "StuTool Logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .widthIn(max = logoSize)
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                        alpha = logoAlpha
                    }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Subtle red accent line
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(BrandRed.copy(alpha = 0.6f))
                    .graphicsLayer { alpha = logoAlpha }
            )

            Spacer(modifier = Modifier.height(when (bucket) {
                ScreenBucket.COMPACT -> 20.dp
                ScreenBucket.NORMAL -> 28.dp
                ScreenBucket.LARGE -> 36.dp
            }))

            AnimatedVisibility(
                visible = textVisible,
                enter = fadeIn(tween(500)) + slideInVertically(
                    initialOffsetY = { it / 6 },
                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Welcome to",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "StuTool",
                        fontSize = titleSize,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Your fast, offline-first workstation for document management, scanning, and professional resumes.",
                        fontSize = when (bucket) {
                            ScreenBucket.COMPACT -> 14.sp
                            else -> 15.5.sp
                        },
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 23.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(when (bucket) {
                        ScreenBucket.COMPACT -> 20.dp
                        ScreenBucket.NORMAL -> 28.dp
                        ScreenBucket.LARGE -> 32.dp
                    }))

                    // Highlight badges row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WelcomeBadge(label = "Offline Core", isDark = isDark)
                        WelcomeBadge(label = "No Account", isDark = isDark)
                        WelcomeBadge(label = "Private & Free", isDark = isDark)
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeBadge(label: String, isDark: Boolean) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) DarkSurfaceContainer else Color(0xFFF2F2F7),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isDark) Color.White.copy(alpha = 0.08f) else LightOutline
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = SuccessColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ── Page 2: PDF Viewer & Scanner ─────────────────────────────────────────────

@Composable
private fun ViewerScannerPage(isDark: Boolean, bucket: ScreenBucket) {
    FeaturePage(
        title = "Viewer & Scanner",
        subtitle = "Read documents smoothly and scan with your camera.",
        features = listOf(
            FeatureItem(
                icon = Icons.Outlined.PictureAsPdf,
                title = "Fast PDF Viewer",
                description = "Smooth continuous scroll & page navigation"
            ),
            FeatureItem(
                icon = Icons.Outlined.Search,
                title = "In-Document Search",
                description = "Find keywords & navigate matches instantly"
            ),
            FeatureItem(
                icon = Icons.Outlined.FolderOpen,
                title = "Multi-Format Docs",
                description = "Open Word (DOCX), Excel (XLSX) & text files"
            ),
            FeatureItem(
                icon = Icons.Outlined.DocumentScanner,
                title = "Smart Scanner",
                description = "Auto boundary detection & precise edge crop"
            ),
            FeatureItem(
                icon = Icons.Outlined.AutoAwesome,
                title = "Color & B&W Filters",
                description = "Magic Color, grayscale & multi-page export"
            ),
            FeatureItem(
                icon = Icons.Outlined.FindInPage,
                title = "OCR Text Extraction",
                description = "Extract text from scans locally on-device"
            )
        ),
        isDark = isDark,
        bucket = bucket
    )
}

// ── Page 3: Resume Builder ───────────────────────────────────────────────────

@Composable
private fun ResumeBuilderPage(isDark: Boolean, bucket: ScreenBucket) {
    FeaturePage(
        title = "Resume Builder",
        subtitle = "Craft job-winning resumes in minutes.",
        features = listOf(
            FeatureItem(
                icon = Icons.Outlined.Dashboard,
                title = "6 Pro Templates",
                description = "Modern, classic, timeline & executive layouts"
            ),
            FeatureItem(
                icon = Icons.Outlined.Verified,
                title = "ATS-Friendly",
                description = "Engineered to pass automated screening"
            ),
            FeatureItem(
                icon = Icons.Outlined.EditNote,
                title = "Structured Editor",
                description = "Experience, projects, skills & custom sections"
            ),
            FeatureItem(
                icon = Icons.Outlined.Visibility,
                title = "Live Preview",
                description = "Real-time updates as you type and format"
            ),
            FeatureItem(
                icon = Icons.Outlined.AutoAwesome,
                title = "AI Objective Enhance",
                description = "Polish career summaries with AI suggestions"
            ),
            FeatureItem(
                icon = Icons.Outlined.PictureAsPdf,
                title = "Instant PDF Export",
                description = "High-resolution vector PDF export & sharing"
            )
        ),
        isDark = isDark,
        bucket = bucket
    )
}

// ── Page 4: PDF Tools ────────────────────────────────────────────────────────

@Composable
private fun PdfToolsPage(isDark: Boolean, bucket: ScreenBucket) {
    FeaturePage(
        title = "PDF Tools",
        subtitle = "Everything you need to edit, convert, and manage PDFs.",
        features = listOf(
            FeatureItem(
                icon = Icons.AutoMirrored.Outlined.MergeType,
                title = "Merge PDF",
                description = "Combine multiple PDFs into a single file"
            ),
            FeatureItem(
                icon = Icons.Outlined.Compress,
                title = "Compress PDF",
                description = "Significantly reduce file size on-device"
            ),
            FeatureItem(
                icon = Icons.Outlined.Image,
                title = "Image to PDF",
                description = "Convert photos and gallery images to PDF"
            ),
            FeatureItem(
                icon = Icons.Outlined.ContentCut,
                title = "Extract Pages",
                description = "Select and split specific pages into new files"
            ),
            FeatureItem(
                icon = Icons.Outlined.Layers,
                title = "Pages Per Sheet",
                description = "Print 2 or 4 pages per sheet for handouts"
            ),
            FeatureItem(
                icon = Icons.Outlined.Transform,
                title = "Format Conversions",
                description = "Convert PDFs to Word, Excel, and images"
            )
        ),
        isDark = isDark,
        bucket = bucket
    )
}

// ── Page 5: Ready to Get Started ─────────────────────────────────────────────

private data class WorkflowValue(
    val icon: ImageVector,
    val title: String,
    val description: String
)

@Composable
private fun GetStartedPage(isDark: Boolean, bucket: ScreenBucket) {
    var iconVisible by remember { mutableStateOf(false) }
    var titleVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80)
        iconVisible = true
        delay(200)
        titleVisible = true
    }

    val iconScale by animateFloatAsState(
        targetValue = if (iconVisible) 1f else 0.6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "icon_scale"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (iconVisible) 1f else 0f,
        animationSpec = tween(400),
        label = "icon_alpha"
    )

    val workflowValues = listOf(
        WorkflowValue(
            icon = Icons.Outlined.PersonOff,
            title = "No Account or Sign-Up",
            description = "Start using all tools immediately without logins, passwords, or emails"
        ),
        WorkflowValue(
            icon = Icons.Outlined.Lock,
            title = "On-Device Processing",
            description = "Your documents and camera scans process locally on your phone"
        ),
        WorkflowValue(
            icon = Icons.Outlined.WifiOff,
            title = "Offline Reliability",
            description = "Core document utilities and viewing work anytime without internet"
        )
    )

    val iconContainerSize = when (bucket) {
        ScreenBucket.COMPACT -> 56.dp
        ScreenBucket.NORMAL -> 64.dp
        ScreenBucket.LARGE -> 72.dp
    }
    val titleSize = when (bucket) {
        ScreenBucket.COMPACT -> 22.sp
        ScreenBucket.NORMAL -> 26.sp
        ScreenBucket.LARGE -> 28.sp
    }
    val horizontalPadding = adaptiveHorizontalPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Rocket icon with spring scale entrance
            Box(
                modifier = Modifier
                    .size(iconContainerSize)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                        alpha = iconAlpha
                    }
                    .clip(CircleShape)
                    .background(
                        if (isDark) BrandRed.copy(alpha = 0.12f)
                        else BrandRed.copy(alpha = 0.08f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.RocketLaunch,
                    contentDescription = null,
                    tint = BrandRed,
                    modifier = Modifier.size(iconContainerSize * 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(when (bucket) {
                ScreenBucket.COMPACT -> 14.dp
                ScreenBucket.NORMAL -> 20.dp
                ScreenBucket.LARGE -> 24.dp
            }))

            AnimatedVisibility(
                visible = titleVisible,
                enter = fadeIn(tween(400)) + slideInVertically(
                    initialOffsetY = { it / 6 },
                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Ready to Get Started?",
                        fontSize = titleSize,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Fast, dependable tools engineered for privacy.",
                        fontSize = 14.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(when (bucket) {
                ScreenBucket.COMPACT -> 18.dp
                ScreenBucket.NORMAL -> 24.dp
                ScreenBucket.LARGE -> 28.dp
            }))

            // Workflow values cards list
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                workflowValues.forEachIndexed { index, item ->
                    var itemVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(280L + (index * 90L))
                        itemVisible = true
                    }

                    val itemAlpha by animateFloatAsState(
                        targetValue = if (itemVisible) 1f else 0f,
                        animationSpec = tween(350),
                        label = "item_alpha_$index"
                    )
                    val itemOffsetY by animateDpAsState(
                        targetValue = if (itemVisible) 0.dp else 12.dp,
                        animationSpec = tween(350, easing = FastOutSlowInEasing),
                        label = "item_offset_$index"
                    )

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDark) DarkSurfaceContainer else Color.White,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isDark) Color.White.copy(alpha = 0.06f) else LightOutline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = itemAlpha }
                            .offset(y = itemOffsetY)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SuccessColor.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = SuccessColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.description,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(when (bucket) {
                ScreenBucket.COMPACT -> 14.dp
                ScreenBucket.NORMAL -> 20.dp
                ScreenBucket.LARGE -> 24.dp
            }))

            // Privacy note
            var privacyVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(750)
                privacyVisible = true
            }
            AnimatedVisibility(
                visible = privacyVisible,
                enter = fadeIn(tween(500))
            ) {
                Text(
                    text = "Documents process locally on your device. Optional AI features are strictly opt-in.",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

// ── Shared Feature Page Layout ───────────────────────────────────────────────

private data class FeatureItem(
    val icon: ImageVector,
    val title: String,
    val description: String
)

@Composable
private fun FeaturePage(
    title: String,
    subtitle: String,
    features: List<FeatureItem>,
    isDark: Boolean,
    bucket: ScreenBucket
) {
    var headerVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(80); headerVisible = true }

    val cardBackground = if (isDark) DarkSurfaceContainer else Color.White
    val cardBorder = if (isDark) Color.White.copy(alpha = 0.06f) else LightOutline

    val horizontalPadding = adaptiveHorizontalPadding()
    val topPadding = when (bucket) {
        ScreenBucket.COMPACT -> 16.dp
        ScreenBucket.NORMAL -> 24.dp
        ScreenBucket.LARGE -> 32.dp
    }
    val headerToCards = when (bucket) {
        ScreenBucket.COMPACT -> 14.dp
        ScreenBucket.NORMAL -> 18.dp
        ScreenBucket.LARGE -> 22.dp
    }
    val cardGap = when (bucket) {
        ScreenBucket.COMPACT -> 8.dp
        ScreenBucket.NORMAL -> 10.dp
        ScreenBucket.LARGE -> 12.dp
    }
    val cardPaddingInner = when (bucket) {
        ScreenBucket.COMPACT -> 10.dp
        ScreenBucket.NORMAL -> 12.dp
        ScreenBucket.LARGE -> 14.dp
    }
    val iconSize = when (bucket) {
        ScreenBucket.COMPACT -> 32.dp
        ScreenBucket.NORMAL -> 36.dp
        ScreenBucket.LARGE -> 38.dp
    }
    val titleFontSize = when (bucket) {
        ScreenBucket.COMPACT -> 22.sp
        ScreenBucket.NORMAL -> 26.sp
        ScreenBucket.LARGE -> 28.sp
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding)
            .padding(top = topPadding, bottom = 24.dp)
    ) {
        // Header with fade + slide
        AnimatedVisibility(
            visible = headerVisible,
            enter = fadeIn(tween(400, easing = FastOutSlowInEasing)) + slideInVertically(
                initialOffsetY = { it / 8 },
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            )
        ) {
            Column {
                // Subtle red accent dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(BrandRed)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = title,
                    fontSize = titleFontSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    fontSize = when (bucket) {
                        ScreenBucket.COMPACT -> 13.5.sp
                        else -> 14.5.sp
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(headerToCards))

        // Feature cards in 2-column grid — staggered per card
        val chunked = features.chunked(2)
        Column(
            verticalArrangement = Arrangement.spacedBy(cardGap),
            modifier = Modifier.fillMaxWidth()
        ) {
            chunked.forEachIndexed { rowIndex, rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(cardGap)
                ) {
                    rowItems.forEachIndexed { colIndex, feature ->
                        val cardIndex = rowIndex * 2 + colIndex
                        var cardVisible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            delay(160L + (cardIndex * 70L))
                            cardVisible = true
                        }

                        val cardAlpha by animateFloatAsState(
                            targetValue = if (cardVisible) 1f else 0f,
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                            label = "card_alpha_$cardIndex"
                        )
                        val cardOffsetY by animateDpAsState(
                            targetValue = if (cardVisible) 0.dp else 16.dp,
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                            label = "card_offset_$cardIndex"
                        )
                        val cardScale by animateFloatAsState(
                            targetValue = if (cardVisible) 1f else 0.95f,
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                            label = "card_scale_$cardIndex"
                        )

                        OnboardingFeatureCard(
                            icon = feature.icon,
                            title = feature.title,
                            description = feature.description,
                            cardBackground = cardBackground,
                            cardBorder = cardBorder,
                            innerPadding = cardPaddingInner,
                            iconContainerSize = iconSize,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    alpha = cardAlpha
                                    scaleX = cardScale
                                    scaleY = cardScale
                                    translationY = cardOffsetY.toPx()
                                }
                        )
                    }
                    // Fill remaining space if odd number
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ── Feature Card Component ───────────────────────────────────────────────────

@Composable
private fun OnboardingFeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    cardBackground: Color,
    cardBorder: Color,
    innerPadding: Dp = 12.dp,
    iconContainerSize: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
        ) {
            // Icon container (BrandRed bg, white icon)
            Box(
                modifier = Modifier
                    .size(iconContainerSize)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BrandRed),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(iconContainerSize * 0.55f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = description,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}
