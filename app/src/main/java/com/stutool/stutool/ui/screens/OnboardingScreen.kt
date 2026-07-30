package com.stutool.stutool.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.scale
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

// ── Responsive helpers ───────────────────────────────────────────────────────

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

    // Bottom area height adapts to screen size
    val bottomAreaHeight = when (bucket) {
        ScreenBucket.COMPACT -> 100.dp
        ScreenBucket.NORMAL -> 120.dp
        ScreenBucket.LARGE -> 130.dp
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .systemBarsPadding()
    ) {
        // ── Top bar: Skip button ─────────────────────────────────────────
        AnimatedVisibility(
            visible = currentPage < TOTAL_PAGES - 1,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
        ) {
            TextButton(onClick = onComplete) {
                Text(
                    text = "Skip",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ── Pager ────────────────────────────────────────────────────────
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

        // ── Bottom navigation area ───────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = when (bucket) {
                    ScreenBucket.COMPACT -> 12.dp
                    ScreenBucket.NORMAL -> 20.dp
                    ScreenBucket.LARGE -> 24.dp
                }),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Dot indicators (animated Box composables)
            PageIndicators(
                totalPages = TOTAL_PAGES,
                currentPage = currentPage
            )

            Spacer(modifier = Modifier.height(when (bucket) {
                ScreenBucket.COMPACT -> 16.dp
                ScreenBucket.NORMAL -> 24.dp
                ScreenBucket.LARGE -> 28.dp
            }))

            // Navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
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
                            Spacer(modifier = Modifier.width(4.dp))
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
    // Staggered entrance: logo → accent → text
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
        ScreenBucket.NORMAL -> 130.dp
        ScreenBucket.LARGE -> 150.dp
    }
    val titleSize = when (bucket) {
        ScreenBucket.COMPACT -> 30.sp
        ScreenBucket.NORMAL -> 34.sp
        ScreenBucket.LARGE -> 38.sp
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
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
            ScreenBucket.COMPACT -> 24.dp
            ScreenBucket.NORMAL -> 32.dp
            ScreenBucket.LARGE -> 40.dp
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
                    text = "Your all-in-one toolkit for PDFs,\ndocument scanning and\nprofessional resumes.",
                    fontSize = when (bucket) {
                        ScreenBucket.COMPACT -> 15.sp
                        else -> 16.sp
                    },
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
            }
        }
    }
}

// ── Page 2: PDF Tools ────────────────────────────────────────────────────────

@Composable
private fun PdfToolsPage(isDark: Boolean, bucket: ScreenBucket) {
    FeaturePage(
        title = "PDF Tools",
        subtitle = "Everything you need to manage your PDFs.",
        features = listOf(
            FeatureItem(Icons.AutoMirrored.Outlined.MergeType, "Merge PDF", "Combine multiple files into one"),
            FeatureItem(Icons.Outlined.Compress, "Compress PDF", "Reduce file size instantly"),
            FeatureItem(Icons.Outlined.Image, "Image to PDF", "Convert images in seconds"),
            FeatureItem(Icons.Outlined.ContentCut, "Extract Pages", "Pull specific pages out")
        ),
        isDark = isDark,
        bucket = bucket
    )
}

// ── Page 3: PDF Viewer & Scanner ─────────────────────────────────────────────

@Composable
private fun ViewerScannerPage(isDark: Boolean, bucket: ScreenBucket) {
    FeaturePage(
        title = "Viewer & Scanner",
        subtitle = "Read documents and scan with your camera.",
        features = listOf(
            FeatureItem(Icons.Outlined.PictureAsPdf, "Fast PDF Viewer", "Smooth, responsive reading"),
            FeatureItem(Icons.Outlined.DocumentScanner, "Smart Scanner", "Automatic edge detection"),
            FeatureItem(Icons.Outlined.History, "Recent Files", "Quick access to your docs"),
            FeatureItem(Icons.Outlined.WifiOff, "Offline Processing", "No internet required")
        ),
        isDark = isDark,
        bucket = bucket
    )
}

// ── Page 4: Resume Builder ───────────────────────────────────────────────────

@Composable
private fun ResumeBuilderPage(isDark: Boolean, bucket: ScreenBucket) {
    FeaturePage(
        title = "Resume Builder",
        subtitle = "Create professional resumes in minutes.",
        features = listOf(
            FeatureItem(Icons.Outlined.Verified, "ATS Friendly", "Passes automated screening"),
            FeatureItem(Icons.Outlined.Dashboard, "Professional Templates", "Clean, modern designs"),
            FeatureItem(Icons.Outlined.Visibility, "Live Preview", "See changes in real time"),
            FeatureItem(Icons.Outlined.PictureAsPdf, "Export to PDF", "Download and share instantly")
        ),
        isDark = isDark,
        bucket = bucket
    )
}

// ── Page 5: Ready to Get Started ─────────────────────────────────────────────

@Composable
private fun GetStartedPage(isDark: Boolean, bucket: ScreenBucket) {
    // Staggered entrance: icon → title → items → privacy
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

    val checkmarkItems = listOf(
        "PDF Tools" to Icons.Outlined.Build,
        "Document Scanner" to Icons.Outlined.DocumentScanner,
        "Resume Builder" to Icons.Outlined.Person,
        "100% Offline" to Icons.Outlined.Lock
    )

    val iconContainerSize = when (bucket) {
        ScreenBucket.COMPACT -> 60.dp
        ScreenBucket.NORMAL -> 72.dp
        ScreenBucket.LARGE -> 80.dp
    }
    val titleSize = when (bucket) {
        ScreenBucket.COMPACT -> 24.sp
        ScreenBucket.NORMAL -> 28.sp
        ScreenBucket.LARGE -> 30.sp
    }
    val checkSpacing = when (bucket) {
        ScreenBucket.COMPACT -> 12.dp
        ScreenBucket.NORMAL -> 16.dp
        ScreenBucket.LARGE -> 20.dp
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
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
            ScreenBucket.COMPACT -> 20.dp
            ScreenBucket.NORMAL -> 28.dp
            ScreenBucket.LARGE -> 32.dp
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

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Everything you need, right on your device.",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(when (bucket) {
            ScreenBucket.COMPACT -> 24.dp
            ScreenBucket.NORMAL -> 32.dp
            ScreenBucket.LARGE -> 36.dp
        }))

        // Checkmark list with staggered entrance + icon
        Column(
            verticalArrangement = Arrangement.spacedBy(checkSpacing),
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            checkmarkItems.forEachIndexed { index, (label, icon) ->
                var itemVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(350L + (index * 120L))
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .graphicsLayer { alpha = itemAlpha }
                        .offset(y = itemOffsetY)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SuccessColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = label,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(when (bucket) {
            ScreenBucket.COMPACT -> 12.dp
            ScreenBucket.NORMAL -> 20.dp
            ScreenBucket.LARGE -> 24.dp
        }))

        // Privacy note
        var privacyVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(900)
            privacyVisible = true
        }
        AnimatedVisibility(
            visible = privacyVisible,
            enter = fadeIn(tween(500))
        ) {
            Text(
                text = "Your files never leave your device. No sign-in required.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
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
    // Staggered entrance: header → row1 → row2
    var headerVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(80); headerVisible = true }

    val cardBackground = if (isDark) DarkSurfaceContainer else Color.White
    val cardBorder = if (isDark) Color.White.copy(alpha = 0.06f) else LightOutline

    val topPadding = when (bucket) {
        ScreenBucket.COMPACT -> 48.dp
        ScreenBucket.NORMAL -> 64.dp
        ScreenBucket.LARGE -> 80.dp
    }
    val headerToCards = when (bucket) {
        ScreenBucket.COMPACT -> 20.dp
        ScreenBucket.NORMAL -> 28.dp
        ScreenBucket.LARGE -> 36.dp
    }
    val cardGap = when (bucket) {
        ScreenBucket.COMPACT -> 10.dp
        ScreenBucket.NORMAL -> 12.dp
        ScreenBucket.LARGE -> 14.dp
    }
    val cardPaddingInner = when (bucket) {
        ScreenBucket.COMPACT -> 14.dp
        ScreenBucket.NORMAL -> 16.dp
        ScreenBucket.LARGE -> 20.dp
    }
    val iconSize = when (bucket) {
        ScreenBucket.COMPACT -> 36.dp
        ScreenBucket.NORMAL -> 40.dp
        ScreenBucket.LARGE -> 44.dp
    }
    val titleFontSize = when (bucket) {
        ScreenBucket.COMPACT -> 24.sp
        ScreenBucket.NORMAL -> 28.sp
        ScreenBucket.LARGE -> 30.sp
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = topPadding)
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

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = title,
                    fontSize = titleFontSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = subtitle,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(headerToCards))

        // Feature cards in 2×2 grid — staggered per card
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
                        // Each card has its own staggered delay
                        val cardIndex = rowIndex * 2 + colIndex
                        var cardVisible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            delay(200L + (cardIndex * 100L))
                            cardVisible = true
                        }

                        val cardAlpha by animateFloatAsState(
                            targetValue = if (cardVisible) 1f else 0f,
                            animationSpec = tween(350, easing = FastOutSlowInEasing),
                            label = "card_alpha_$cardIndex"
                        )
                        val cardOffsetY by animateDpAsState(
                            targetValue = if (cardVisible) 0.dp else 20.dp,
                            animationSpec = tween(350, easing = FastOutSlowInEasing),
                            label = "card_offset_$cardIndex"
                        )
                        val cardScale by animateFloatAsState(
                            targetValue = if (cardVisible) 1f else 0.95f,
                            animationSpec = tween(350, easing = FastOutSlowInEasing),
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
    innerPadding: Dp = 16.dp,
    iconContainerSize: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
        ) {
            // Icon container (BrandRed bg, white icon — matching ToolCard pattern)
            Box(
                modifier = Modifier
                    .size(iconContainerSize)
                    .clip(RoundedCornerShape(10.dp))
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

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )
        }
    }
}
