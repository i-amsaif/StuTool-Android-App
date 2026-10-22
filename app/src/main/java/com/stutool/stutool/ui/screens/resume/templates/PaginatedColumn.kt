package com.stutool.stutool.ui.screens.resume.templates
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun PaginatedColumn(
    modifier: Modifier = Modifier,
    pageHeight: Dp = 841.45.dp,
    startYOffset: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val pageHeightPx = pageHeight.roundToPx()
        val startYOffsetPx = startYOffset.roundToPx()
        
        val placeables = measurables.map { it.measure(constraints) }
        
        var currentY = 0
        val positions = mutableListOf<Int>()
        
        for (placeable in placeables) {
            val childHeight = placeable.height
            val absoluteY = currentY + startYOffsetPx
            val currentPage = absoluteY / pageHeightPx
            val pageEndAbsolute = (currentPage + 1) * pageHeightPx
            
            // If child crosses boundary and can fit on a single page, push it to next page
            if (absoluteY + childHeight > pageEndAbsolute && childHeight <= pageHeightPx) {
                currentY += (pageEndAbsolute - absoluteY)
            }
            
            positions.add(currentY)
            currentY += childHeight
        }
        
        layout(constraints.maxWidth, currentY) {
            placeables.forEachIndexed { index, placeable ->
                placeable.placeRelative(x = 0, y = positions[index])
            }
        }
    }
}
