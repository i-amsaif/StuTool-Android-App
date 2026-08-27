package com.stutool.stutool.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.stutool.stutool.ui.theme.LocalGlassTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(navController: NavController) {
    val glass = LocalGlassTokens.current

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

            // 1. Introduction
            PolicySection(title = "1. Introduction") {
                PolicyText("StuTool respects your privacy. This app is designed to function with a strict offline-first approach, ensuring minimal data collection.")
            }

            // 2. Data Collection & Processing
            PolicySection(title = "2. Data Collection & Processing") {
                PolicyBulletList(
                    listOf(
                        "No account or sign-in required",
                        "All PDF processing, text extraction, generation, and Document Scanner tasks occur offline and locally on your device",
                        "Resume Builder data is processed locally",
                        "No personal data or documents are stored on remote servers"
                    )
                )
            }

            // 3. Permissions & File Access
            PolicySection(title = "3. Permissions & File Access") {
                PolicyBulletList(
                    listOf(
                        "Storage/Media permissions are used only to read files you explicitly select and to save the processed files back to your device",
                        "Camera permissions are used solely for the Document Scanner feature to capture images locally",
                        "Sharing or exporting files is initiated only by your explicit action using the system share sheet"
                    )
                )
            }

            // 4. AI Features & Internet Usage
            PolicySection(title = "4. AI Features & Internet Usage") {
                PolicyText("StuTool operates entirely offline with one exception: AI Enhance.")
                Spacer(modifier = Modifier.height(6.dp))
                PolicyBulletList(
                    listOf(
                        "The text you enter (e.g., career objective) is sent to third-party AI services when you explicitly request enhancement",
                        "This requires internet access to generate improved content",
                        "No other personal data, resumes, or PDFs are ever uploaded"
                    )
                )
            }

            // 5. Third-Party Services
            PolicySection(title = "5. Third-Party Services") {
                PolicyText("When using AI Enhance, StuTool may use services such as:")
                Spacer(modifier = Modifier.height(6.dp))
                PolicyBulletList(
                    listOf(
                        "Google AI Studio (Gemini)",
                        "GroqCloud",
                        "OpenRouter"
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                PolicyText("These services only process the specific text input you provide.")
            }

            // 6. Security
            PolicySection(title = "6. Security") {
                PolicyText("By keeping document processing entirely local, your files remain fully secure on your device. We aim to protect user data, but external AI services follow their own policies for the text you choose to enhance.")
            }

            // 7. Changes
            PolicySection(title = "7. Changes") {
                PolicyText("This privacy policy may be updated in future versions to reflect new offline tools or features.")
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
