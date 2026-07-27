package com.stutool.stutool.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Scanner specific semantic colors that derive from MaterialTheme.
 */
data class ScannerColors(
    val scannerBackground: Color,
    val scannerSurface: Color,
    val scannerSurfaceVariant: Color,
    val scannerElevatedSurface: Color,
    val scannerOverlaySurface: Color,
    val scannerToolbar: Color,
    val scannerBottomBar: Color,
    val scannerPreviewOverlay: Color,
    val scannerTextPrimary: Color,
    val scannerTextSecondary: Color,
    val scannerIconPrimary: Color,
    val scannerActionPrimary: Color,
    val scannerActionSecondary: Color,
    val scannerCropOverlay: Color,
    val scannerCropHandle: Color,
    
    // Phase 1: New Surface & Elevation Tokens
    val floatingSurfaceColor: Color,
    val modalSurface: Color,
    val chipSelectedBackground: Color,
    val chipUnselectedBackground: Color,
    val viewportDimColor: Color,
    val successGreen: Color,
    
    // Phase 1: Opacity representation
    val dividerColor: Color,
    val overlayBackdropColor: Color
)

object ScannerTheme {
    val colors: ScannerColors
        @Composable
        get() = if (LocalAppThemeIsDark.current) darkScannerColors() else lightScannerColors()
}

@Composable
private fun darkScannerColors() = ScannerColors(
    scannerBackground = Color(0xFF000000), // True dark scanner background
    scannerSurface = Color(0xFF1C1C1E), // Surface 1
    scannerSurfaceVariant = Color(0xFF2A2A2C), // Surface 2
    scannerElevatedSurface = Color(0xFF1C1C1E),
    scannerOverlaySurface = Color(0x99000000),
    scannerToolbar = Color(0xFF000000), // Blends with background
    scannerBottomBar = Color(0xFF1C1C1E), // Matches Surface 1
    scannerPreviewOverlay = Color(0xB3000000),
    scannerTextPrimary = Color(0xFFFFFFFF),
    scannerTextSecondary = Color(0xFF8E8E93),
    scannerIconPrimary = Color(0xFFFFFFFF),
    scannerActionPrimary = Color(0xFFE63946), // StuTool Red
    scannerActionSecondary = Color(0xFF2C2C2E),
    scannerCropOverlay = Color(0x99000000),
    scannerCropHandle = Color(0xFFE63946),
    
    // New Surface & Elevation Tokens
    floatingSurfaceColor = Color(0xFF1C1C1E),
    modalSurface = Color(0xFF121212),
    chipSelectedBackground = Color(0xFF351A1A),
    chipUnselectedBackground = Color.Transparent,
    viewportDimColor = Color(0xE6000000),
    successGreen = Color(0xFF22C55E),
    
    dividerColor = Color(0xFF2C2C2E),
    overlayBackdropColor = Color(0x99000000)
)

@Composable
private fun lightScannerColors() = ScannerColors(
    scannerBackground = Color(0xFFF9F9FB), 
    scannerSurface = Color(0xFFFFFFFF), // Surface 1
    scannerSurfaceVariant = Color(0xFFF2F2F7), // Surface 2
    scannerElevatedSurface = Color(0xFFFFFFFF),
    scannerOverlaySurface = Color(0x66000000),
    scannerToolbar = Color(0xFFF9F9FB), // Blends with background
    scannerBottomBar = Color(0xFFFFFFFF), // Matches Surface 1
    scannerPreviewOverlay = Color(0x66000000), 
    scannerTextPrimary = Color(0xFF1C1C1E),
    scannerTextSecondary = Color(0xFF6B6B70),
    scannerIconPrimary = Color(0xFF1C1C1E),
    scannerActionPrimary = Color(0xFFE63946), // StuTool Red
    scannerActionSecondary = Color(0xFFE5E5EA),
    scannerCropOverlay = Color(0x99000000),
    scannerCropHandle = Color(0xFFE63946),
    
    // New Surface & Elevation Tokens
    floatingSurfaceColor = Color(0xFFFFFFFF),
    modalSurface = Color(0xFFFFFFFF),
    chipSelectedBackground = Color(0xFFFDEAEA),
    chipUnselectedBackground = Color.Transparent,
    viewportDimColor = Color(0xCC000000),
    successGreen = Color(0xFF10B981),
    
    dividerColor = Color(0xFFE5E5EA),
    overlayBackdropColor = Color(0x66000000)
)
