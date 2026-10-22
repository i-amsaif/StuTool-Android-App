package com.stutool.stutool.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.stutool.stutool.ui.theme.LocalGlassTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(navController: NavController) {
    val glass = LocalGlassTokens.current
    val uriHandler = LocalUriHandler.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Privacy Policy", fontSize = 20.sp, fontWeight = FontWeight.Medium) },
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

            // 1. Overview & Offline-First Core
            PolicySection(title = "1. Overview & Offline-First Core") {
                PolicyText("StuTool is developed by Mohammed Saif as a student utility and document workstation. The application is built from the ground up on an offline-first architecture.")
                Spacer(modifier = Modifier.height(6.dp))
                PolicyBulletList(
                    listOf(
                        "No account registration, user profile, or sign-in is required to use the app.",
                        "Core PDF reading, rendering, compression, page extraction, image conversion, and resume generation execute 100% locally on your Android device.",
                        "No personal documents or file contents are uploaded to remote servers during standard local operations."
                    )
                )
            }

            // 2. OCR & Document Text Extraction
            PolicySection(title = "2. OCR & Text Recognition") {
                PolicyText("StuTool can extract text from PDFs and images on your device:")
                Spacer(modifier = Modifier.height(6.dp))
                PolicyBulletList(
                    listOf(
                        "On-Device Processing: StuTool can process documents locally using on-device OCR. Text recognition runs locally on your device using embedded Google ML Kit and Tesseract 5 OCR engines, without transmitting data off your device.",
                        "Online OCR: When online OCR is used with your consent, the selected document or image is sent over HTTPS to OCR.space for text recognition under OCR.space's terms and privacy policy.",
                        "Offline Control: You can choose to process documents strictly on-device at any time. When offline or in airplane mode, StuTool automatically performs text recognition locally without sending network requests.",
                        "Privacy & Limits: StuTool does not associate OCR requests with any personal user account or identifier. Online OCR requests are subject to service limits (files up to 1 MB and 3 pages)."
                    )
                )
            }

            // 3. AI Resume Assistant & Third-Party LLMs
            PolicySection(title = "3. AI Resume Assistant & Third-Party LLMs") {
                PolicyText("StuTool offers an optional AI Career Objective enhancement tool inside the Resume Builder:")
                Spacer(modifier = Modifier.height(6.dp))
                PolicyBulletList(
                    listOf(
                        "AI enhancement is triggered only when you explicitly tap the enhance button.",
                        "Only the specific text snippet you enter in the objective field is transmitted over HTTPS to the selected AI provider (Google Gemini API, GroqCloud, or OpenRouter).",
                        "Entire resumes, contact information, personal identity details, and files are never sent to AI providers.",
                        "Age Requirement: Under Google's API terms of service, direct interactive use of the Gemini API is restricted to users 18 years of age or older."
                    )
                )
            }

            // 4. Feedback & Technical Diagnostics
            PolicySection(title = "4. Feedback & Technical Diagnostics") {
                PolicyText("If you choose to submit feedback through the in-app Feedback screen, your message is sent securely via Web3Forms over HTTPS:")
                Spacer(modifier = Modifier.height(6.dp))
                PolicyBulletList(
                    listOf(
                        "Data transmitted includes: star rating, comment text, app version, Android OS version, and device model/manufacturer.",
                        "This diagnostic metadata is used exclusively to troubleshoot bugs and improve app compatibility across devices.",
                        "Feedback submission is completely voluntary and never collected in the background."
                    )
                )
            }

            // 5. Scoped Storage & System Permissions
            PolicySection(title = "5. Scoped Storage & Permissions") {
                PolicyBulletList(
                    listOf(
                        "Storage: StuTool uses Android's Storage Access Framework (SAF) system picker and scoped application directories. The app only accesses files you explicitly pick and saves outputs where you choose.",
                        "Camera: Camera access is requested solely for the Document Scanner feature via CameraX. Live camera frames are analyzed on-device for boundary detection and are never uploaded.",
                        "Sharing: Exporting or sharing files is handled strictly through the native Android system share sheet initiated by your explicit action."
                    )
                )
            }

            // 6. Advertising, Analytics & Tracking
            PolicySection(title = "6. Advertising & Analytics") {
                PolicyBulletList(
                    listOf(
                        "Zero Advertisements: StuTool contains no third-party ad networks, banners, or tracking SDKs.",
                        "Zero Behavioral Analytics: We do not integrate Google Firebase Analytics, Mixpanel, or third-party user behavior trackers.",
                        "Zero Background Telemetry: The application does not collect background location, telemetry, or device identifiers."
                    )
                )
            }

            // 7. Security & Local Storage
            PolicySection(title = "7. Security & Data Protection") {
                PolicyText("Temporary working files created during PDF processing are stored in app-specific cache directories and cleaned up periodically. All external communications for optional cloud features (Cloud OCR, AI Enhance, Feedback) are strictly encrypted in transit using TLS 1.3/1.2 over HTTPS.")
            }

            // 8. Data Retention, Rights & Inquiries
            PolicySection(title = "8. Data Retention & Privacy Inquiries") {
                PolicyText("Because StuTool operates without user accounts or central databases, there are no remote user accounts to delete. Uninstalling the app or clearing app storage immediately deletes all local cache and saved preferences.")
                Spacer(modifier = Modifier.height(6.dp))
                PolicyText("For privacy inquiries, support, or data deletion requests regarding feedback submissions, contact Mohammed Saif at:")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "i.am.mohd.saif2006@gmail.com",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }

            // 9. Online Policy & Updates
            PolicySection(title = "9. Online Privacy Policy & Updates") {
                PolicyText("This in-app policy is kept synchronized with our published web privacy policies. You can view the live, complete documentation online:")
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        try {
                            uriHandler.openUri("https://www.stutool.in/android-privacy-policy")
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Online Android Privacy Policy")
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun PolicySection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
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
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun PolicyText(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun PolicyBulletList(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Row {
                Text(
                    text = "•",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = item,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
