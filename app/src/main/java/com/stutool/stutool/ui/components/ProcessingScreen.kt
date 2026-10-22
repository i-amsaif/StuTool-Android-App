package com.stutool.stutool.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stutool.stutool.ui.theme.SuccessColor

sealed class ProcessingState {
    data object Idle : ProcessingState()
    data class Preparing(val message: String = "Preparing document...") : ProcessingState()
    data class Processing(
        val progress: Float, // 0.0 to 1.0
        val message: String = "Processing...",
        val subtitle: String? = null
    ) : ProcessingState()
    data class Success(
        val title: String = "Completed!",
        val subtitle: String = "Operation completed successfully.",
        val primaryActionLabel: String = "Open",
        val secondaryActionLabel: String? = "Share",
        val tertiaryActionLabel: String? = "Start Over"
    ) : ProcessingState()
    data class Error(
        val title: String = "Something went wrong",
        val message: String = "An error occurred. Please try again.",
        val retryLabel: String = "Retry",
        val cancelLabel: String = "Cancel"
    ) : ProcessingState()
    data object Cancelled : ProcessingState()
}

private enum class ProcessingPhase {
    IDLE,
    IN_PROGRESS,
    SUCCESS,
    ERROR,
    CANCELLED
}

private fun ProcessingState.toPhase(): ProcessingPhase = when (this) {
    is ProcessingState.Idle -> ProcessingPhase.IDLE
    is ProcessingState.Preparing,
    is ProcessingState.Processing -> ProcessingPhase.IN_PROGRESS
    is ProcessingState.Success -> ProcessingPhase.SUCCESS
    is ProcessingState.Error -> ProcessingPhase.ERROR
    is ProcessingState.Cancelled -> ProcessingPhase.CANCELLED
}

@Composable
fun FullScreenProcessingView(
    state: ProcessingState,
    onCancel: () -> Unit,
    onSuccessPrimaryAction: () -> Unit = {},
    onSuccessSecondaryAction: () -> Unit = {},
    onSuccessTertiaryAction: () -> Unit = {},
    onErrorRetry: () -> Unit = {},
    onErrorCancel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isProcessing = state is ProcessingState.Preparing || state is ProcessingState.Processing
    BackHandler(enabled = isProcessing) {
        onCancel()
    }

    // Fixed-size container prevents layout shift — the overlay occupies
    // the entire screen from frame one, so its geometry never changes.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Crossfade switches only on major phase changes (e.g. In-Progress -> Success/Error)
        // rather than every progress tick or status message change.
        // This prevents violent screen flashing and recomposition thrashing on rapid multi-page progress.
        Crossfade(
            targetState = state.toPhase(),
            animationSpec = tween(350, easing = FastOutSlowInEasing),
            modifier = Modifier.fillMaxSize(),
            label = "ProcessingPhaseCrossfade"
        ) { phase ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                when (phase) {
                    ProcessingPhase.IDLE -> {
                        // Invisible placeholder — keeps container stable.
                    }
                    ProcessingPhase.IN_PROGRESS -> {
                        val currentProgress = when (val s = state) {
                            is ProcessingState.Processing -> s.progress
                            else -> -1f
                        }
                        val currentMessage = when (val s = state) {
                            is ProcessingState.Preparing -> s.message
                            is ProcessingState.Processing -> s.message
                            else -> "Processing..."
                        }
                        val currentSubtitle = when (val s = state) {
                            is ProcessingState.Processing -> s.subtitle
                            else -> null
                        }
                        ProcessingStatusView(
                            progress = currentProgress,
                            isIndeterminate = currentProgress < 0f,
                            message = currentMessage,
                            subtitle = currentSubtitle,
                            onCancel = onCancel
                        )
                    }
                    ProcessingPhase.SUCCESS -> {
                        val successState = state as? ProcessingState.Success ?: ProcessingState.Success()
                        SuccessStatusView(
                            title = successState.title,
                            subtitle = successState.subtitle,
                            primaryActionLabel = successState.primaryActionLabel,
                            secondaryActionLabel = successState.secondaryActionLabel,
                            tertiaryActionLabel = successState.tertiaryActionLabel,
                            onPrimaryAction = onSuccessPrimaryAction,
                            onSecondaryAction = onSuccessSecondaryAction,
                            onTertiaryAction = onSuccessTertiaryAction
                        )
                    }
                    ProcessingPhase.ERROR -> {
                        val errorState = state as? ProcessingState.Error ?: ProcessingState.Error()
                        ErrorStatusView(
                            title = errorState.title,
                            message = errorState.message,
                            retryLabel = errorState.retryLabel,
                            cancelLabel = errorState.cancelLabel,
                            onRetry = onErrorRetry,
                            onCancel = onErrorCancel
                        )
                    }
                    ProcessingPhase.CANCELLED -> {
                        ErrorStatusView(
                            title = "Cancelled",
                            message = "The operation was cancelled.",
                            retryLabel = "Try Again",
                            cancelLabel = "Close",
                            onRetry = onErrorRetry,
                            onCancel = onErrorCancel
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// ── Processing Status View ──
// ═══════════════════════════════════════════════

@Composable
private fun ProcessingStatusView(
    progress: Float,
    isIndeterminate: Boolean,
    message: String,
    subtitle: String?,
    onCancel: () -> Unit
) {
    // Gentle icon pulse — starts at 1.0 so there's no visible jump
    // when the view first appears.
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_pulse"
    )

    // Subtle glow opacity for the icon background
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // Two-group layout: centered content (Group 1) + bottom button (Group 2)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
    ) {
        // ── Group 1: Centered Content ──
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f)
        ) {
            // ── Animated Icon ──
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // ── Progress Bar ──
            AnimatedProgressBar(
                progress = progress,
                isIndeterminate = isIndeterminate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Status Text ──
            Text(
                text = message,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            if (subtitle != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }

        // ── Group 2: Bottom Cancel Button ──
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .height(44.dp)
                .widthIn(min = 140.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true)
        ) {
            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Cancel", fontWeight = FontWeight.Medium, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

// ═══════════════════════════════════════════════
// ── Animated Progress Bar ──
// ═══════════════════════════════════════════════

@Composable
fun AnimatedProgressBar(
    progress: Float,
    isIndeterminate: Boolean,
    modifier: Modifier = Modifier
) {
    // Smooth progress interpolation — long duration + linear-out-slow-in
    // ensures coarse progress updates appear as one continuous movement
    // rather than disconnected jumps.
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "progress_anim"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "progress_effects")

    // Indeterminate bar uses smooth ease-in-out for organic feel
    val indeterminateOffset by infiniteTransition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "indeterminate_anim"
    )

    // Subtle shimmer highlight on the progress fill
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_anim"
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val fillColor = MaterialTheme.colorScheme.primary
    val highlightColor = Color.White.copy(alpha = 0.3f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(trackColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val h = size.height
            val w = size.width
            val cr = CornerRadius(h / 2, h / 2)

            if (isIndeterminate) {
                // Smooth indeterminate bar
                val barWidth = w * 0.35f
                val startX = w * indeterminateOffset - barWidth * 0.5f
                val clampedStart = startX.coerceIn(-barWidth, w)
                val clampedEnd = (startX + barWidth).coerceIn(0f, w + barWidth)
                val drawWidth = (clampedEnd - clampedStart).coerceAtLeast(0f)

                if (drawWidth > 0f) {
                    drawRoundRect(
                        color = fillColor,
                        topLeft = Offset(clampedStart.coerceAtLeast(0f), 0f),
                        size = Size(drawWidth.coerceAtMost(w), h),
                        cornerRadius = cr
                    )
                }
            } else if (animatedProgress > 0f) {
                // Determinate progress fill
                val fillWidth = w * animatedProgress
                drawRoundRect(
                    color = fillColor,
                    size = Size(fillWidth, h),
                    cornerRadius = cr
                )

                // Subtle shimmer highlight
                val shimmerX = w * shimmerOffset
                val shimmerWidth = w * 0.3f
                val brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        highlightColor,
                        Color.Transparent
                    ),
                    start = Offset(shimmerX, 0f),
                    end = Offset(shimmerX + shimmerWidth, 0f)
                )
                drawRoundRect(
                    brush = brush,
                    size = Size(fillWidth, h),
                    cornerRadius = cr
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════
// ── Success Status View ──
// ═══════════════════════════════════════════════

@Composable
private fun SuccessStatusView(
    title: String,
    subtitle: String,
    primaryActionLabel: String,
    secondaryActionLabel: String?,
    tertiaryActionLabel: String?,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit,
    onTertiaryAction: () -> Unit
) {
    // Gentle scale-in for the check icon
    val scaleAnim by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "success_scale"
    )

    // Two-group layout: centered content (Group 1) + bottom buttons (Group 2)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
    ) {
        // ── Group 1: Centered Content ──
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .graphicsLayer {
                        scaleX = scaleAnim
                        scaleY = scaleAnim
                    }
                    .clip(CircleShape)
                    .background(SuccessColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SuccessColor,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }

        // ── Group 2: Bottom Action Buttons ──
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 48.dp)
        ) {
            // ── Primary Action ──
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(primaryActionLabel, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            // ── Secondary Action ──
            if (secondaryActionLabel != null) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onSecondaryAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(secondaryActionLabel, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }

            // ── Tertiary Action ──
            if (tertiaryActionLabel != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onTertiaryAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(tertiaryActionLabel, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// ── Error Status View ──
// ═══════════════════════════════════════════════

@Composable
private fun ErrorStatusView(
    title: String,
    message: String,
    retryLabel: String,
    cancelLabel: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    // Two-group layout: centered content (Group 1) + bottom buttons (Group 2)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
    ) {
        // ── Group 1: Centered Content ──
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = message,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }

        // ── Group 2: Bottom Action Buttons ──
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 48.dp)
        ) {
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(retryLabel, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(50)
            ) {
                Text(cancelLabel, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            }
        }
    }
}
