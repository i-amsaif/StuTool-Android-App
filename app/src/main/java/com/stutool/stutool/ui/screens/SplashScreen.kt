package com.stutool.stutool.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stutool.stutool.R
import com.stutool.stutool.ui.theme.BrandRed
import com.stutool.stutool.ui.theme.DarkBackground
import com.stutool.stutool.ui.theme.LightBackground
import com.stutool.stutool.ui.theme.LocalAppThemeIsDark
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onSplashComplete: () -> Unit) {
    val isDark = LocalAppThemeIsDark.current
    val backgroundColor = if (isDark) DarkBackground else LightBackground
    val textColor = if (isDark) Color.White else Color(0xFF0F172A)
    val subtitleColor = if (isDark) Color.White.copy(alpha = 0.72f) else Color(0xFF475569)
    val trackColor = if (isDark) Color(0xFF262626) else Color(0xFFE2E8F0)

    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Initial hold (150ms): Ensure user sees the intact document before the slice begins
        delay(150L)
        // Extended animation duration: 1000ms (25% increase from previous 800ms for smoother pacing)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = LinearEasing)
        )
        // Settled hold (350ms): Allow the user to see the complete sliced logo before transition
        delay(350L)
        onSplashComplete()
    }

    val p = animProgress.value

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val isShortScreen = screenHeight < 600.dp

        // Proportional logo size, keeping bounds balanced across screen sizes
        val logoSize = minOf(200.dp, screenWidth * 0.52f, screenHeight * 0.26f).coerceAtLeast(140.dp)

        // 1. Native Responsive Compose Background with Ambient Glows & Diagonal Accents
        SplashBackground(
            isDark = isDark,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Main Content Container: Optically centered (slightly raised for visual balance)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .offset(y = if (isShortScreen) 0.dp else (-24).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated Logo (Layered Vector Composition)
            SlicingLogo(
                progress = p,
                logoSize = logoSize
            )

            // Brand Title
            Text(
                text = "StuTool",
                fontWeight = FontWeight.Bold,
                fontSize = if (isShortScreen) 26.sp else 32.sp,
                fontFamily = FontFamily.SansSerif,
                color = textColor,
                modifier = Modifier.padding(top = if (isShortScreen) 16.dp else 24.dp)
            )

            // Brand Subtitle
            Text(
                text = "Your All-in-One Student Toolkit",
                fontWeight = FontWeight.Medium,
                fontSize = if (isShortScreen) 13.sp else 15.sp,
                fontFamily = FontFamily.SansSerif,
                color = subtitleColor,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        // Modern Minimal Loading Bar at bottom
        LinearProgressIndicator(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (isShortScreen) 24.dp else 52.dp)
                .width(72.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(50)),
            color = BrandRed,
            trackColor = trackColor
        )
    }
}

/**
 * Animated slicing logo with 6 layered vector components:
 * Intact document -> slicing blade -> split halves -> flying shards.
 */
@Composable
fun SlicingLogo(
    progress: Float,
    logoSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val p = progress

    // 1. Intact Logo Alpha: 1.0 until 350ms (p=0.35), fades out by 520ms (p=0.52)
    val intactAlpha = when {
        p < 0.35f -> 1f
        p > 0.52f -> 0f
        else -> 1f - ((p - 0.35f) / (0.52f - 0.35f))
    }

    // 2. Diagonal Slash Blade: Slices from 200ms (p=0.20) to 450ms (p=0.45)
    val slashRaw = ((p - 0.20f) / (0.45f - 0.20f)).coerceIn(0f, 1f)
    val slashEase = FastOutSlowInEasing.transform(slashRaw)
    val slashScale = slashEase
    val slashAlpha = slashEase

    // 3. Top-Left & Bottom-Right Halves: Reveal at 350ms (p=0.35) and split outward to 1000ms (p=1.0)
    val splitAlpha = ((p - 0.35f) / (0.45f - 0.35f)).coerceIn(0f, 1f)
    val splitRaw = ((p - 0.35f) / (1f - 0.35f)).coerceIn(0f, 1f)
    val splitEase = FastOutSlowInEasing.transform(splitRaw)
    val shiftOffset = (1f - splitEase)

    val tlX = 4.4.dp * shiftOffset
    val tlY = (-4.4).dp * shiftOffset
    val brX = (-4.4).dp * shiftOffset
    val brY = 4.4.dp * shiftOffset

    // 4. Shards: Fly outward from center to final resting positions
    val shardsAlpha = ((p - 0.35f) / (0.52f - 0.35f)).coerceIn(0f, 1f)
    val shlX = 16.dp * shiftOffset
    val shlY = (-10).dp * shiftOffset
    val shrX = (-16).dp * shiftOffset
    val shrY = 10.dp * shiftOffset

    Box(
        modifier = modifier
            .size(logoSize)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Layer 1: Intact Document
        if (intactAlpha > 0f) {
            Image(
                painter = painterResource(id = R.drawable.ic_logo_layer_intact),
                contentDescription = "Intact Document Logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = intactAlpha }
            )
        }

        // Layer 2: Top-Left Sliced Half
        if (splitAlpha > 0f) {
            Image(
                painter = painterResource(id = R.drawable.ic_logo_layer_top_left),
                contentDescription = "Top-Left Document Half",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = splitAlpha
                        translationX = tlX.toPx()
                        translationY = tlY.toPx()
                    }
            )
        }

        // Layer 3: Bottom-Right Sliced Half
        if (splitAlpha > 0f) {
            Image(
                painter = painterResource(id = R.drawable.ic_logo_layer_bottom_right),
                contentDescription = "Bottom-Right Document Half",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = splitAlpha
                        translationX = brX.toPx()
                        translationY = brY.toPx()
                    }
            )
        }

        // Layer 4: Diagonal Slash Blade
        if (slashAlpha > 0f) {
            Image(
                painter = painterResource(id = R.drawable.ic_logo_layer_slash),
                contentDescription = "Diagonal Slash Blade",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = slashAlpha
                        scaleX = slashScale
                        scaleY = slashScale
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
            )
        }

        // Layer 5: Left Flying Shards
        if (shardsAlpha > 0f) {
            Image(
                painter = painterResource(id = R.drawable.ic_logo_layer_shards_left),
                contentDescription = "Left Flying Shards",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = shardsAlpha
                        translationX = shlX.toPx()
                        translationY = shlY.toPx()
                    }
            )
        }

        // Layer 6: Right Flying Shards
        if (shardsAlpha > 0f) {
            Image(
                painter = painterResource(id = R.drawable.ic_logo_layer_shards_right),
                contentDescription = "Right Flying Shards",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = shardsAlpha
                        translationX = shrX.toPx()
                        translationY = shrY.toPx()
                    }
            )
        }
    }
}

/**
 * Native Jetpack Compose background recreating the reference design.
 * Features corner ambient glow gradients, translucent rounded corner card accents,
 * thin diagonal lines, and elongated rounded capsules parallel to the logo cut angle.
 */
@Composable
fun SplashBackground(
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    // Subtle, gentle ambient motion that breathes without heavy recomposition overhead
    val infiniteTransition = rememberInfiniteTransition(label = "SplashBackgroundTransition")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientPulse"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val cx = width / 2f
        val cy = height / 2f

        // 1. Base Background Fill
        val baseColor = if (isDark) Color(0xFF09090C) else Color(0xFFFFFFFF)
        drawRect(color = baseColor)

        // 2. Corner Ambient Radial Glows
        // Top-Left Glow
        val tlGlowRadius = width * 0.85f
        val tlGlowCenter = Offset(x = width * 0.05f, y = height * 0.04f)
        val tlGlowColor = if (isDark) {
            Color(0xFFDC2626).copy(alpha = 0.28f + 0.06f * pulse)
        } else {
            Color(0xFFFF5252).copy(alpha = 0.18f + 0.04f * pulse)
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(tlGlowColor, Color.Transparent),
                center = tlGlowCenter,
                radius = tlGlowRadius
            ),
            center = tlGlowCenter,
            radius = tlGlowRadius
        )

        // Bottom-Right Glow
        val brGlowRadius = width * 0.90f
        val brGlowCenter = Offset(x = width * 0.95f, y = height * 0.96f)
        val brGlowColor = if (isDark) {
            Color(0xFFB91C1C).copy(alpha = 0.26f + 0.06f * pulse)
        } else {
            Color(0xFFFF5252).copy(alpha = 0.16f + 0.04f * pulse)
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(brGlowColor, Color.Transparent),
                center = brGlowCenter,
                radius = brGlowRadius
            ),
            center = brGlowCenter,
            radius = brGlowRadius
        )

        // 3. Corner Rounded Overlays (Large Glass Cards in TL and BR)
        // Top-Left Card
        val tlCardWidth = width * 0.60f
        val tlCardHeight = height * 0.24f
        val tlCardCornerRadius = CornerRadius(56.dp.toPx(), 56.dp.toPx())
        val tlCardTopLeft = Offset(-width * 0.10f, -height * 0.04f)

        val tlFillBrush = if (isDark) {
            Brush.linearGradient(
                colors = listOf(Color(0x457F1D1D), Color(0x1418181B)),
                start = Offset(0f, 0f),
                end = Offset(tlCardWidth, tlCardHeight)
            )
        } else {
            Brush.linearGradient(
                colors = listOf(Color(0x28FF5252), Color(0x06FFFFFF)),
                start = Offset(0f, 0f),
                end = Offset(tlCardWidth, tlCardHeight)
            )
        }
        drawRoundRect(
            brush = tlFillBrush,
            topLeft = tlCardTopLeft,
            size = Size(tlCardWidth, tlCardHeight),
            cornerRadius = tlCardCornerRadius
        )

        // Top-Left Highlight Rim along curved edge
        val tlBorderColor = if (isDark) Color(0xFFEF4444).copy(alpha = 0.55f) else Color(0xFFFF5252).copy(alpha = 0.35f)
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.Transparent, tlBorderColor),
                start = Offset(0f, 0f),
                end = Offset(tlCardWidth * 0.95f, tlCardHeight * 0.95f)
            ),
            topLeft = tlCardTopLeft,
            size = Size(tlCardWidth, tlCardHeight),
            cornerRadius = tlCardCornerRadius,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Bottom-Right Card
        val brCardWidth = width * 0.62f
        val brCardHeight = height * 0.25f
        val brCardCornerRadius = CornerRadius(56.dp.toPx(), 56.dp.toPx())
        val brCardTopLeft = Offset(width * 0.50f, height * 0.78f)

        val brFillBrush = if (isDark) {
            Brush.linearGradient(
                colors = listOf(Color(0x1418181B), Color(0x45991B1B)),
                start = brCardTopLeft,
                end = Offset(width + width * 0.1f, height + height * 0.05f)
            )
        } else {
            Brush.linearGradient(
                colors = listOf(Color(0x06FFFFFF), Color(0x28FF5252)),
                start = brCardTopLeft,
                end = Offset(width + width * 0.1f, height + height * 0.05f)
            )
        }
        drawRoundRect(
            brush = brFillBrush,
            topLeft = brCardTopLeft,
            size = Size(brCardWidth, brCardHeight),
            cornerRadius = brCardCornerRadius
        )

        // Bottom-Right Highlight Rim along curved edge
        val brBorderColor = if (isDark) Color(0xFFEF4444).copy(alpha = 0.55f) else Color(0xFFFF5252).copy(alpha = 0.35f)
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(brBorderColor, Color.Transparent, Color.Transparent),
                start = brCardTopLeft,
                end = Offset(width, height)
            ),
            topLeft = brCardTopLeft,
            size = Size(brCardWidth, brCardHeight),
            cornerRadius = brCardCornerRadius,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // 4. Diagonal Geometric Accents (Rotated -45° to match the logo slice cut)
        val shiftX = (pulse - 0.5f) * 8.dp.toPx()

        val capsuleColor = if (isDark) {
            Color(0xFF26262B).copy(alpha = 0.50f)
        } else {
            Color(0xFFFF8A80).copy(alpha = 0.15f)
        }
        val capsuleAccentColor = if (isDark) {
            Color(0xFFDC2626).copy(alpha = 0.25f)
        } else {
            Color(0xFFFF5252).copy(alpha = 0.18f)
        }

        val lineColor = if (isDark) {
            Color(0xFFEF4444).copy(alpha = 0.40f)
        } else {
            Color(0xFFEF4444).copy(alpha = 0.26f)
        }

        withTransform({
            rotate(degrees = -45f, pivot = Offset(cx, cy))
        }) {
            // Upper-Left Quadrant Elements (floating above the center branding)
            // Long primary capsule
            val cap1Width = 145.dp.toPx()
            val cap1Height = 22.dp.toPx()
            val cap1X = cx - 180.dp.toPx() + shiftX
            val cap1Y = cy - 170.dp.toPx()
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(capsuleColor, capsuleAccentColor, Color.Transparent),
                    startX = cap1X,
                    endX = cap1X + cap1Width
                ),
                topLeft = Offset(cap1X, cap1Y),
                size = Size(cap1Width, cap1Height),
                cornerRadius = CornerRadius(cap1Height / 2f, cap1Height / 2f)
            )

            // Secondary outer capsule
            val cap2Width = 100.dp.toPx()
            val cap2Height = 15.dp.toPx()
            val cap2X = cx - 260.dp.toPx() + shiftX * 0.7f
            val cap2Y = cy - 230.dp.toPx()
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, capsuleColor),
                    startX = cap2X,
                    endX = cap2X + cap2Width
                ),
                topLeft = Offset(cap2X, cap2Y),
                size = Size(cap2Width, cap2Height),
                cornerRadius = CornerRadius(cap2Height / 2f, cap2Height / 2f)
            )

            // Thin accent line 1
            val line1StartX = cx - 210.dp.toPx() + shiftX * 1.2f
            val line1EndX = cx - 40.dp.toPx() + shiftX * 1.2f
            val line1Y = cy - 200.dp.toPx()
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, lineColor, Color.Transparent),
                    startX = line1StartX,
                    endX = line1EndX
                ),
                start = Offset(line1StartX, line1Y),
                end = Offset(line1EndX, line1Y),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Lower-Right Quadrant Elements (floating below the center branding)
            // Long primary capsule
            val cap3Width = 165.dp.toPx()
            val cap3Height = 24.dp.toPx()
            val cap3X = cx + 25.dp.toPx() - shiftX
            val cap3Y = cy + 175.dp.toPx()
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, capsuleAccentColor, capsuleColor),
                    startX = cap3X,
                    endX = cap3X + cap3Width
                ),
                topLeft = Offset(cap3X, cap3Y),
                size = Size(cap3Width, cap3Height),
                cornerRadius = CornerRadius(cap3Height / 2f, cap3Height / 2f)
            )

            // Secondary outer capsule
            val cap4Width = 115.dp.toPx()
            val cap4Height = 16.dp.toPx()
            val cap4X = cx + 130.dp.toPx() - shiftX * 0.8f
            val cap4Y = cy + 235.dp.toPx()
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(capsuleColor, Color.Transparent),
                    startX = cap4X,
                    endX = cap4X + cap4Width
                ),
                topLeft = Offset(cap4X, cap4Y),
                size = Size(cap4Width, cap4Height),
                cornerRadius = CornerRadius(cap4Height / 2f, cap4Height / 2f)
            )

            // Thin accent line 2
            val line2StartX = cx - 20.dp.toPx() - shiftX * 1.2f
            val line2EndX = cx + 180.dp.toPx() - shiftX * 1.2f
            val line2Y = cy + 205.dp.toPx()
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, lineColor, Color.Transparent),
                    startX = line2StartX,
                    endX = line2EndX
                ),
                start = Offset(line2StartX, line2Y),
                end = Offset(line2EndX, line2Y),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
