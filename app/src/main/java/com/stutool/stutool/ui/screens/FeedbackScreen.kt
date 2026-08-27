package com.stutool.stutool.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.stutool.stutool.BuildConfig
import com.stutool.stutool.ui.theme.BrandRed
import com.stutool.stutool.ui.theme.LocalGlassTokens
import kotlinx.coroutines.launch

const val SUPPORT_EMAIL = "i.am.mohd.saif2006@gmail.com"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(navController: NavController) {
    val context = LocalContext.current
    val glass = LocalGlassTokens.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var rating by remember { mutableStateOf(0) }
    var feedbackText by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    val maxChars = 1000

    val sendEmail: () -> Unit = {
        val subject = "StuTool Feedback (${rating}★)"
        val appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        val androidVersion = Build.VERSION.RELEASE
        val device = Build.MODEL
        val manufacturer = Build.MANUFACTURER

        val body = """
            StuTool Feedback

            Rating:
            ${"★".repeat(rating)}${"☆".repeat(5 - rating)}

            ------------------------

            Feedback:

            ${feedbackText.ifBlank { "No comment provided." }}

            ------------------------

            App Version:
            $appVersion

            Android:
            $androidVersion

            Device:
            $device

            Manufacturer:
            $manufacturer

            ------------------------

            Thank you for helping improve StuTool!
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        
        try {
            context.startActivity(intent)
            navController.navigateUp()
        } catch (e: Exception) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "No compatible email application found.",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Add a comment?") },
            text = { Text("Would you like to add a short comment? It helps us improve StuTool.") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                }) {
                    Text("Add Comment", color = BrandRed)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    sendEmail()
                }) {
                    Text("Send Anyway", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Feedback", fontSize = 20.sp, fontWeight = FontWeight.Medium) },
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
                HorizontalDivider(thickness = 0.5.dp, color = glass.topBarDivider)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(glass.backgroundBrush)
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Your feedback helps improve StuTool.",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Rating Stars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                for (i in 1..5) {
                    val isSelected = i <= rating
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.2f else 1f,
                        animationSpec = tween(150),
                        label = "star_scale_$i"
                    )
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Star $i",
                        tint = if (isSelected) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(48.dp)
                            .padding(4.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                rating = i
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = feedbackText,
                onValueChange = { if (it.length <= maxChars) feedbackText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                placeholder = { Text("What would you like to share? (Optional)") },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    focusedBorderColor = BrandRed
                )
            )

            Text(
                text = "${feedbackText.length} / $maxChars",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (rating == 5 && feedbackText.isBlank()) {
                        showConfirmDialog = true
                    } else {
                        sendEmail()
                    }
                },
                enabled = rating > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandRed,
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Text("Send Feedback", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
