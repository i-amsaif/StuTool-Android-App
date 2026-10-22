package com.stutool.stutool.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
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
import com.stutool.stutool.data.remote.feedback.FeedbackRepository
import com.stutool.stutool.data.remote.feedback.FeedbackSubmissionResult
import com.stutool.stutool.ui.theme.BrandRed
import com.stutool.stutool.ui.theme.LocalGlassTokens
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(navController: NavController) {
    val context = LocalContext.current
    val glass = LocalGlassTokens.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val repository = remember { FeedbackRepository(context) }

    var rating by remember { mutableIntStateOf(0) }
    var feedbackText by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isSubmitted by remember { mutableStateOf(false) }
    var remainingCooldownMinutes by remember { mutableLongStateOf(repository.getRemainingCooldownMinutes()) }
    val maxChars = 1000

    val isInputLocked = isSubmitting || isSubmitted || remainingCooldownMinutes > 0

    val submitFeedback = {
        if (!isSubmitting && !isSubmitted && remainingCooldownMinutes <= 0) {
            isSubmitting = true
            scope.launch {
                when (val result = repository.submitFeedback(rating, feedbackText.trim())) {
                    is FeedbackSubmissionResult.Success -> {
                        isSubmitting = false
                        isSubmitted = true
                        remainingCooldownMinutes = repository.getRemainingCooldownMinutes()
                        snackbarHostState.showSnackbar(
                            message = "Review submitted successfully! Thank you for your feedback.",
                            duration = SnackbarDuration.Short
                        )
                    }
                    is FeedbackSubmissionResult.RateLimited -> {
                        isSubmitting = false
                        remainingCooldownMinutes = result.remainingMinutes
                        snackbarHostState.showSnackbar(
                            message = "Feedback submitted recently. Please wait ${result.remainingMinutes} min before submitting again.",
                            duration = SnackbarDuration.Short
                        )
                    }
                    is FeedbackSubmissionResult.Error -> {
                        isSubmitting = false
                        snackbarHostState.showSnackbar(
                            message = result.message,
                            duration = SnackbarDuration.Long
                        )
                    }
                }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showConfirmDialog = false },
            title = { Text("Add a comment?") },
            text = { Text("Would you like to add a short comment? It helps us improve StuTool.") },
            confirmButton = {
                TextButton(
                    onClick = { showConfirmDialog = false },
                    enabled = !isSubmitting
                ) {
                    Text("Add Comment", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        submitFeedback()
                    },
                    enabled = !isSubmitting
                ) {
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

            Spacer(modifier = Modifier.height(24.dp))

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
                                enabled = !isInputLocked,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                rating = i
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = feedbackText,
                onValueChange = { if (it.length <= maxChars && !isInputLocked) feedbackText = it },
                readOnly = isInputLocked,
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

            if (isSubmitted || remainingCooldownMinutes > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Review Submitted",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = if (isSubmitted) {
                                    "Thank you! Your feedback helps us improve StuTool."
                                } else {
                                    "Feedback submitted recently. You can submit another in ${remainingCooldownMinutes} min."
                                },
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (!isInputLocked) {
                        if (rating == 5 && feedbackText.isBlank()) {
                            showConfirmDialog = true
                        } else {
                            submitFeedback()
                        }
                    }
                },
                enabled = rating > 0 && !isInputLocked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                if (isSubmitting) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Submitting...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (isSubmitted || remainingCooldownMinutes > 0) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Review Submitted", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text("Send Feedback", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
