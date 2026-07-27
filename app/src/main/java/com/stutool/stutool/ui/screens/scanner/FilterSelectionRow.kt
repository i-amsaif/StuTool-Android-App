package com.stutool.stutool.ui.screens.scanner

import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stutool.stutool.ui.screens.scanner.utils.ScanFilter
import com.stutool.stutool.ui.theme.ScannerTheme

@Composable
fun FilterSelectionRow(
    selectedFilter: ScanFilter,
    onFilterSelected: (ScanFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = ScannerTokens.viewportPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ScanFilter.values().toList()) { filter ->
            val isSelected = selectedFilter == filter
            
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) ScannerTheme.colors.scannerActionPrimary else ScannerTheme.colors.scannerOverlaySurface,
                animationSpec = tween(ScannerTokens.FastDuration),
                label = "FilterChipBg"
            )
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(ScannerTokens.ChipCornerRadius))
                    .background(bgColor)
                    .clickable { onFilterSelected(filter) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = filter.label,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                    fontSize = ScannerTokens.LabelText,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}
