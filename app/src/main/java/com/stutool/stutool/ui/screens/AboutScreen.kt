package com.stutool.stutool.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.stutool.stutool.BuildConfig
import com.stutool.stutool.ui.theme.LocalGlassTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navController: NavController) {
    val glass = LocalGlassTokens.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("About StuTool", fontSize = 20.sp, fontWeight = FontWeight.Medium) },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = glass.topBarSurface,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = glass.topBarDivider
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(glass.backgroundBrush)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // (A) App Overview
            AboutSectionCard(
                title = "App Overview",
                icon = Icons.Default.Widgets
            ) {
                Text(
                    text = "StuTool is a fast, minimal, and privacy-focused document workstation designed for students and professionals. It combines a multi-format Document Viewer, PDF Tools, Document Scanner, and an ATS-friendly Resume Builder in a clean, offline-first app.",
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // (B) Key Features
            AboutSectionCard(
                title = "Key Features",
                icon = Icons.Default.AutoAwesome
            ) {
                val features = listOf(
                    "Document Viewer (smooth continuous scroll, text search, hyperlinks & multi-format support)",
                    "Document Scanner (clean camera scan, smart edge detection, color filters & PDF export)",
                    "PDF Tools Suite (merge, compress, extract pages, image to PDF, pages per sheet & conversions)",
                    "Resume Builder (6 ATS-friendly templates, live preview & instant PDF export)",
                    "AI Resume Assistant (optional career objective enhancement)",
                    "OCR & Text Recognition (on-device ML Kit & Tesseract with optional cloud fallback)"
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    features.forEach { feature ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(3.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = feature,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // (C) Philosophy
            AboutSectionCard(
                title = "Philosophy",
                icon = Icons.Default.Lightbulb
            ) {
                val principles = listOf(
                    "No account or sign-in required",
                    "Offline-first: documents are processed locally on your device",
                    "Fast, lightweight, and distraction-free"
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    principles.forEach { principle ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF22C55E),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = principle,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // (D) AI Transparency
            AboutSectionCard(
                title = "AI Transparency",
                icon = Icons.Default.Psychology
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "StuTool offers an optional AI assistant to help polish career objectives in the Resume Builder.",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "AI enhancement is strictly opt-in and triggered only when you tap Enhance. Only the specific text snippet you enter is sent to third-party AI providers (such as Google Gemini, Groq, or OpenRouter) to generate suggestions. Your documents and personal files are never sent to AI services.",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // (E) Developer Note
            AboutSectionCard(
                title = "Developer Note",
                icon = Icons.Default.Code
            ) {
                Text(
                    text = "Built to simplify everyday document workflows, provide dependable offline utilities, and help students and professionals create standout resumes without friction.",
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // (F) App Info
            AboutSectionCard(
                title = "App Info",
                icon = Icons.Default.Info
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AboutInfoRow("App Name", "StuTool")
                    AboutInfoRow("Version", BuildConfig.VERSION_NAME)
                    AboutInfoRow("Developer", "Mohammed Saif")
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun AboutSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    val glass = LocalGlassTokens.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = glass.cardSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, glass.cardBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun AboutInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
