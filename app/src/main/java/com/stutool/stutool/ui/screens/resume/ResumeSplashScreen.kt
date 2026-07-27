package com.stutool.stutool.ui.screens.resume

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.stutool.stutool.R
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.clip
import com.stutool.stutool.ui.theme.BrandRed
import com.stutool.stutool.ui.theme.DarkBackground
import com.stutool.stutool.ui.theme.LightBackground
import com.stutool.stutool.ui.theme.LocalAppThemeIsDark

@Composable
fun ResumeSplashScreen(animate: Boolean = true, onSplashComplete: () -> Unit) {
    val isDark = LocalAppThemeIsDark.current
    val backgroundColor = if (isDark) DarkBackground else LightBackground
    val textColor = if (isDark) Color.White else Color.Black
    val trackColor = if (isDark) Color(0xFF262626) else Color(0xFFEAEAEA)

    LaunchedEffect(key1 = animate) {
        if (animate) {
            // Extended delay to 800ms. Since Native is transparent, Compose is now the FULL splash experience.
            delay(800L)
            onSplashComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        // Main Content Container: Optically centered (slightly raised)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .offset(y = (-32).dp), // Optical centering
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 1. Primary Logo (new high-quality asset with built-in branding)
            Image(
                painter = painterResource(id = R.drawable.resume_builder1),
                contentDescription = "Resume Builder Logo",
                contentScale = ContentScale.Fit, // STRICT aspect ratio preservation – no stretching, no cropping
                modifier = Modifier
                    .widthIn(max = 280.dp) // Larger for premium splash presence
                    .padding(horizontal = 24.dp) // Safe inner padding
            )

            // 2. Subtitle (title text is baked into the logo asset)
            Text(
                text = "Your All-in-One Resume Builder",
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                fontFamily = FontFamily.SansSerif,
                color = textColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        // 3. Modern Minimal Loading Bar at bottom
        LinearProgressIndicator(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
                .width(72.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(50)),
            color = BrandRed,
            trackColor = trackColor
        )
    }
}
