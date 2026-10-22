package com.stutool.stutool.ui.screens.resume

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stutool.stutool.R
import com.stutool.stutool.ui.screens.SlicingLogo
import com.stutool.stutool.ui.screens.SplashBackground
import com.stutool.stutool.ui.theme.BrandRed
import com.stutool.stutool.ui.theme.DarkBackground
import com.stutool.stutool.ui.theme.LightBackground
import com.stutool.stutool.ui.theme.LocalAppThemeIsDark
import kotlinx.coroutines.delay

@Composable
fun ResumeSplashScreen(animate: Boolean = true, onSplashComplete: () -> Unit) {
    val isDark = LocalAppThemeIsDark.current
    val backgroundColor = if (isDark) DarkBackground else LightBackground
    val subtitleColor = if (isDark) Color.White.copy(alpha = 0.72f) else Color(0xFF475569)
    val trackColor = if (isDark) Color(0xFF262626) else Color(0xFFE2E8F0)

    val animProgress = remember { Animatable(if (animate) 0f else 1f) }

    LaunchedEffect(key1 = animate) {
        if (animate) {
            // Initial hold (150ms): Intact document displayed before slice begins
            delay(150L)
            // Slicing animation (1000ms): synced with text slide-up and shard explosion
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1000, easing = LinearEasing)
            )
            // Settled hold (350ms): Fully revealed brand composition
            delay(350L)
            onSplashComplete()
        }
    }

    val p = animProgress.value

    // Text & Tagline Entrance Animation synced with slice expansion
    val textEntranceRaw = if (animate) ((p - 0.35f) / (0.80f - 0.35f)).coerceIn(0f, 1f) else 1f
    val textEntrance = FastOutSlowInEasing.transform(textEntranceRaw)
    val textAlpha = textEntrance
    val textOffsetY = 18.dp * (1f - textEntrance)

    val taglineEntranceRaw = if (animate) ((p - 0.45f) / (0.90f - 0.45f)).coerceIn(0f, 1f) else 1f
    val taglineEntrance = FastOutSlowInEasing.transform(taglineEntranceRaw)
    val taglineAlpha = taglineEntrance
    val taglineOffsetY = 14.dp * (1f - taglineEntrance)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val isShortScreen = screenHeight < 600.dp

        // Proportional logo size, keeping bounds balanced across screen sizes
        val logoSize = minOf(190.dp, screenWidth * 0.48f, screenHeight * 0.24f).coerceAtLeast(130.dp)

        // Text SVG sizing: 5:1 aspect ratio (1000 x 200 viewport)
        val textWidth = minOf(260.dp, screenWidth * 0.68f)
        val textHeight = textWidth / 5f

        // 1. Creative Responsive Compose Background with Ambient Glows & Diagonal Accents
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
            // Slicing Animated Logo
            SlicingLogo(
                progress = p,
                logoSize = logoSize
            )

            Spacer(modifier = Modifier.height(if (isShortScreen) 14.dp else 20.dp))

            // 3D "RESUME BUILDER" Text Logo (ic_resume_builder_text)
            Image(
                painter = painterResource(id = R.drawable.ic_resume_builder_text),
                contentDescription = "Resume Builder",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(textWidth)
                    .height(textHeight)
                    .graphicsLayer {
                        alpha = textAlpha
                        translationY = textOffsetY.toPx()
                    }
            )

            Spacer(modifier = Modifier.height(if (isShortScreen) 6.dp else 10.dp))

            // Existing Resume Builder Tagline (Preserved)
            Text(
                text = "Your All-in-One Resume Builder",
                fontWeight = FontWeight.Medium,
                fontSize = if (isShortScreen) 13.sp else 15.sp,
                fontFamily = FontFamily.SansSerif,
                color = subtitleColor,
                modifier = Modifier.graphicsLayer {
                    alpha = taglineAlpha
                    translationY = taglineOffsetY.toPx()
                }
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
