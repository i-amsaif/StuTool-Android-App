package com.stutool.stutool.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.stutool.stutool.navigation.Screen
import com.stutool.stutool.ui.components.SectionHeader
import com.stutool.stutool.ui.components.ToolCard
import com.stutool.stutool.ui.theme.SuccessColor
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import com.stutool.stutool.ui.theme.PrimaryRed
import com.stutool.stutool.ui.theme.BrandRedDark
import com.stutool.stutool.ui.theme.BrandRedLight
import com.stutool.stutool.ui.theme.LocalGlassTokens
import androidx.compose.foundation.border

@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val glass = LocalGlassTokens.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(glass.backgroundBrush)
            .drawBehind {
                // Subtle primary tint overlay on top third
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            PrimaryRed.copy(alpha = 0.03f),
                            Color.Transparent
                        ),
                        endY = size.height * 0.4f
                    )
                )
            }
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 32.dp, end = 16.dp, bottom = 16.dp)
    ) {
        // ── Hero Section (matches web screenshot) ──

        // Small header label
        Text(
            text = "StuTool Resume Builder",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Large headline
        Text(
            text = buildAnnotatedString {
                append("Build Your Resume in\n")
                withStyle(
                    style = SpanStyle(
                        brush = Brush.linearGradient(
                            colors = listOf(BrandRedLight, PrimaryRed)
                        )
                    )
                ) {
                    append("Seconds")
                }
            },
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 44.sp
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Subtitle
        Text(
            text = "Free, fast, no signup required. Create a\nprofessional design, ready for ATS.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                lineHeight = 24.sp
            )
        )

        Spacer(modifier = Modifier.height(32.dp))

        // CTA Button
        Button(
            onClick = {
                navController.navigate(Screen.Resume.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = CircleShape
                )
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(PrimaryRed, BrandRedDark)
                    ),
                    shape = CircleShape
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.2f), Color.Transparent)
                    ),
                    shape = CircleShape
                ),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // Gloss highlight on the top half of the button
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    glass.ctaHighlight,
                                    Color.Transparent
                                ),
                                endY = size.height * 0.6f
                            )
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Create Resume  →",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Feature badges row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FeatureBadge(label = "ATS-Friendly")
            FeatureBadge(label = "Instant PDF")
            FeatureBadge(label = "No Login")
        }

        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader(title = "Featured Tools")

        Spacer(modifier = Modifier.height(16.dp))

        // Tool cards with navigation
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ToolCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    icon = Icons.Default.PictureAsPdf,
                    title = "PDF Viewer",
                    description = "View, read and organize your PDF files safely.",
                    onClick = {
                        navController.navigate(Screen.Viewer.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                ToolCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    icon = Icons.Default.Build,
                    title = "PDF Tools",
                    description = "Merge, split, compress and format your PDFs easily.",
                    onClick = {
                        navController.navigate(Screen.Tools.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ToolCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    icon = Icons.Default.Person,
                    title = "Resume Builder",
                    description = "Create ATS-friendly resumes in minutes.",
                    onClick = {
                        navController.navigate(Screen.Resume.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                ToolCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    icon = Icons.Default.DocumentScanner,
                    title = "Document Scanner",
                    description = "Scan documents cleanly directly from your camera.",
                    onClick = {
                        navController.navigate(Screen.Scan.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

/**
 * A small feature badge with a green check circle + label.
 */
@Composable
private fun FeatureBadge(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(SuccessColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
