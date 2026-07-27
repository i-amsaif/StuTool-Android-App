package com.stutool.stutool.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stutool.stutool.ui.screens.scanner.ScannerViewModel
import com.stutool.stutool.ui.theme.ScannerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropDecisionOverlay(viewModel: ScannerViewModel) {
    val showSheet by viewModel.showCropDecisionSheet.collectAsState()
    val isAutoCropping by viewModel.isAutoCropping.collectAsState()
    val context = LocalContext.current
    
    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scannedPages by viewModel.scannedPages.collectAsState()
        val idleCount = scannedPages.count { it.state == com.stutool.stutool.ui.screens.scanner.models.ScanPageState.IDLE }
        val recommendAuto = idleCount >= 15

        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissCropDecisionSheet(context) },
            sheetState = sheetState,
            containerColor = ScannerTheme.colors.scannerBackground,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Process $idleCount Pages", 
                    fontSize = 20.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = ScannerTheme.colors.scannerTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "How would you like to crop these pages?",
                    fontSize = 14.sp,
                    color = ScannerTheme.colors.scannerTextSecondary
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                CropOptionCard(
                    title = "Auto Crop",
                    description = "Automatically detect edges and crop all pages. Fastest for large batches.",
                    icon = Icons.Default.AutoAwesome,
                    isRecommended = recommendAuto,
                    onClick = { viewModel.handleCropDecision(context, isAuto = true) }
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                CropOptionCard(
                    title = "Manual Crop",
                    description = "Review and adjust crop borders for each page individually.",
                    icon = Icons.Default.Crop,
                    isRecommended = !recommendAuto && idleCount < 15,
                    onClick = { viewModel.handleCropDecision(context, isAuto = false) }
                )
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun CropOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    isRecommended: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isRecommended) ScannerTheme.colors.scannerActionPrimary.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .border(
                width = if (isRecommended) 2.dp else 1.dp,
                color = if (isRecommended) ScannerTheme.colors.scannerActionPrimary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isRecommended) ScannerTheme.colors.scannerActionPrimary else ScannerTheme.colors.scannerTextPrimary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title, 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 16.sp,
                        color = ScannerTheme.colors.scannerTextPrimary
                    )
                    if (isRecommended) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(ScannerTheme.colors.scannerActionPrimary, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Recommended", 
                                fontSize = 10.sp, 
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description, 
                    fontSize = 13.sp, 
                    color = ScannerTheme.colors.scannerTextSecondary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
