package com.stutool.stutool.ui.screens.scanner

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared design tokens for the Scanner workflow screens.
 * Strictly maps to the StuTool Design System without relying on global overrides.
 */
object ScannerTokens {
    
    // ── Motion & Animation (Phase 6) ────────────────
    const val FastDuration = 150
    const val MediumDuration = 250
    const val ScreenTransitionDuration = 300

    // ── Touch Targets & Minimums ────────────────────
    val MinTouchTarget = 48.dp
    val DenseTouchTarget = 40.dp // For dense toolbars where 48dp makes UI bloated

    // ── Typography (Semantic Tokens) ────────────────
    // Avoiding hardcoded global scales by explicitly assigning utility-focused sizes
    val ToolbarTitle = 18.sp
    val ScreenTitle = 28.sp
    val SectionTitle = 20.sp
    val CardTitle = 16.sp
    val ButtonText = 16.sp
    val CaptionText = 14.sp
    val LabelText = 13.sp
    val Microcopy = 12.sp

    // ── Icons ───────────────────────────────────────
    val IconSize = 22.dp
    val IconSizeSmall = 18.dp

    // ── Buttons & Corners (Geometry) ────────────────
    val ButtonCornerRadius = 999.dp // Pill-shaped for primary actions
    val CardCornerRadius = 16.dp // Large rounded corners for cards/features
    val CardCornerRadiusSmall = 8.dp
    val ChipCornerRadius = 12.dp // Selection chips/toggles

    // ── Overlay & Backgrounds ───────────────────────
    const val OverlayAlpha = 0.85f
    const val CardBackgroundAlpha = 0.5f

    // ── Scanner-Specific Spacing (Phase 1) ──────────
    val viewportPadding = 16.dp
    val cropCanvasInset = 24.dp
    val floatingToolbarPadding = 4.dp // Reduced from 12dp to compress top margin
    val thumbnailRailSpacing = 8.dp
    val bottomDockPadding = 20.dp
    val actionGroupSpacing = 16.dp
    val overlayContentPadding = 24.dp

    // ── Bottom Action Bar ───────────────────────────
    val BottomActionPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    val BottomActionSpacing = 12.dp

    // ── Thumbnails ──────────────────────────────────
    val ThumbnailSize = 56.dp
    val ThumbnailSizeLarge = 56.dp
    val BadgeSize = 22.dp

    // ── Capture Dock ────────────────────────────────
    val CaptureButtonOuterSize = 72.dp
    val CaptureButtonInnerSize = 58.dp
    val GalleryFabSize = 56.dp
    val GalleryFabIconSize = 26.dp
    val CaptureDockSpacing = 16.dp

    // ── Crop Toolbar ────────────────────────────────
    val CropToolbarIconSize = 24.dp
    val CropToolbarIconSpacing = 12.dp
    val CropToolbarHorizontalPadding = 12.dp

    // ── Card Layout ─────────────────────────────────
    val CardHeight = 72.dp
    val CardPaddingHorizontal = 12.dp
    val CardPaddingVertical = 8.dp
    val CardContentSpacing = 16.dp

    // ── List Layout ─────────────────────────────────
    val ListItemSpacing = 12.dp
    val ListContentPaddingTop = 16.dp
    val ListContentPaddingBottom = 80.dp
    val ListContentPaddingHorizontal = 16.dp

    // ── Compact Controls ────────────────────────────
    val CompactIconButtonSize = 32.dp
    val TopBarVerticalPadding = 8.dp
    val TopBarHorizontalPadding = 12.dp

    // ── Adaptive Filter Controls ────────────────────
    val IntensityLabelMinWidth = 70.dp
    val PercentageLabelMinWidth = 42.dp

    // ── Custom Icons ────────────────────────────────
    // Fullscreen expand icon: 4 corner brackets with outward arrows
    // Matches the reference visual: ⌜↗ ⌝↗ ⌟↘ ⌞↙
    private var _openInFull: androidx.compose.ui.graphics.vector.ImageVector? = null
    val OpenInFull: androidx.compose.ui.graphics.vector.ImageVector
        get() {
            if (_openInFull != null) return _openInFull!!
            _openInFull = androidx.compose.ui.graphics.vector.ImageVector.Builder(
                name = "FullscreenExpand",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                // Top Left
                addPath(
                    pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M10 10L5 5M5 5H9M5 5V9").toNodes(),
                    fill = null,
                    stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black),
                    strokeLineWidth = 2.2f,
                    strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
                )
                // Top Right
                addPath(
                    pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M14 10L19 5M19 5H15M19 5V9").toNodes(),
                    fill = null,
                    stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black),
                    strokeLineWidth = 2.2f,
                    strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
                )
                // Bottom Left
                addPath(
                    pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M10 14L5 19M5 19H9M5 19V15").toNodes(),
                    fill = null,
                    stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black),
                    strokeLineWidth = 2.2f,
                    strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
                )
                // Bottom Right
                addPath(
                    pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M14 14L19 19M19 19H15M19 19V15").toNodes(),
                    fill = null,
                    stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black),
                    strokeLineWidth = 2.2f,
                    strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            }.build()
            return _openInFull!!
        }

    val NavBarScannerIcon: androidx.compose.ui.graphics.vector.ImageVector
        get() {
            if (_navBarScannerIcon != null) {
                return _navBarScannerIcon!!
            }
            _navBarScannerIcon = androidx.compose.ui.graphics.vector.ImageVector.Builder(
                name = "NavBarScannerIcon",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 256f,
                viewportHeight = 256f
            ).apply {
                // Top Left
                addPath(
                    pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M68 92V68H92").toNodes(),
                    stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color(0xFFD9D9DF)),
                    strokeLineWidth = 7f,
                    strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
                )
                // Top Right
                addPath(
                    pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M164 68H188V92").toNodes(),
                    stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color(0xFFD9D9DF)),
                    strokeLineWidth = 7f,
                    strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
                )
                // Bottom Left
                addPath(
                    pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M92 188H68V164").toNodes(),
                    stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color(0xFFD9D9DF)),
                    strokeLineWidth = 7f,
                    strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
                )
                // Bottom Right
                addPath(
                    pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M188 164V188H164").toNodes(),
                    stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color(0xFFD9D9DF)),
                    strokeLineWidth = 7f,
                    strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
                )
                // Document Fill
                addPath(
                    pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M106 72 H144 L168 96 V162 C168 170 162 176 154 176 H106 C98 176 92 170 92 162 V86 C92 78 98 72 106 72 Z").toNodes(),
                    fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color(0xFFF3F3F5))
                )
                // Fold Fill
                addPath(
                    pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M144 72 L168 96 H152 C147 96 144 93 144 88 Z").toNodes(),
                    fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color(0xFFDADADF))
                )
                // Text Line 1 (x=112 y=112 w=36 h=4.5 rx=2.25)
                addPath(
                    pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M114.25 114.25 L145.75 114.25").toNodes(),
                    stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color(0xFF171717)),
                    strokeLineWidth = 4.5f,
                    strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                // Text Line 2 (x=112 y=126 w=48 h=4.5 rx=2.25)
                addPath(
                    pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M114.25 128.25 L157.75 128.25").toNodes(),
                    stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color(0xFF171717)),
                    strokeLineWidth = 4.5f,
                    strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                // Text Line 3 (x=112 y=140 w=42 h=4.5 rx=2.25)
                addPath(
                    pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M114.25 142.25 L151.75 142.25").toNodes(),
                    stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color(0xFF171717)),
                    strokeLineWidth = 4.5f,
                    strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                
                // Glow effect (simulating feGaussianBlur stdDeviation=4)
                val glowPath = "M62.5 124.5 L193.5 124.5"
                // Wide blur
                addPath(
                    pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(glowPath).toNodes(),
                    stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color(0xFFFF4B4B)),
                    strokeAlpha = 0.15f,
                    strokeLineWidth = 17f,
                    strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                // Mid blur
                addPath(
                    pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(glowPath).toNodes(),
                    stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color(0xFFFF4B4B)),
                    strokeAlpha = 0.3f,
                    strokeLineWidth = 11f,
                    strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                // Base glow
                addPath(
                    pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(glowPath).toNodes(),
                    stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color(0xFFFF4B4B)),
                    strokeAlpha = 0.6f,
                    strokeLineWidth = 7f,
                    strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                // Actual Red Line
                addPath(
                    pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(glowPath).toNodes(),
                    stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color(0xFFFF4B4B)),
                    strokeAlpha = 0.9f,
                    strokeLineWidth = 5f,
                    strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                
                // Bright Core
                addPath(
                    pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M71.5 124.5 L184.5 124.5").toNodes(),
                    stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color(0xFFFF5A5A)),
                    strokeLineWidth = 3f,
                    strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                
            }.build()
            return _navBarScannerIcon!!
        }
    private var _navBarScannerIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
}
