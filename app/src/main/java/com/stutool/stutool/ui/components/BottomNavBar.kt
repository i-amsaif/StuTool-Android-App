package com.stutool.stutool.ui.components

import androidx.compose.material3.MaterialTheme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.stutool.stutool.navigation.Screen
import com.stutool.stutool.ui.theme.LocalGlassTokens


@Composable
fun BottomNavBar(
    navController: NavController
) {
    val items = listOf(
        Screen.Home,
        Screen.Viewer,
        Screen.Scan,
        Screen.Tools,
        Screen.Resume
    )

    val glass = LocalGlassTokens.current

    // Derive selected index from the current navigation backstack
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

    val density = LocalDensity.current

    val pillShape = RoundedCornerShape(28.dp)
    val barHeight = 68.dp
    val scannerContainerSize = 60.dp
    val scannerCornerRadius = 16.dp
    val scannerColor = Color(0xFFE53935)
    val scannerIndex = 2

    // Pre-compute scanner vertical offset in px (graphicsLayer operates in px)
    val scannerOffsetPx = with(density) { (-18).dp.toPx() }

    // Shared navigation handler — preserves exact existing nav behavior
    val onNavigate: (Screen) -> Unit = { screen ->
        navController.navigate(screen.route) {
            popUpTo(Screen.Home.route) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        // ── Pill-shaped bar ─────────────────────────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 420.dp)
                .height(barHeight),
            shape = pillShape,
            color = glass.navSurface,
            tonalElevation = 12.dp,
            shadowElevation = 16.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, glass.navBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, screen ->
                    if (index == scannerIndex) {
                        // Center gap reserved for the floating scanner button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = selectedIndex == scannerIndex,
                                enter = androidx.compose.animation.fadeIn(tween(200)),
                                exit = androidx.compose.animation.fadeOut(tween(200))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(bottom = 6.dp)
                                        .width(24.dp)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(1.5.dp))
                                        .background(MaterialTheme.colorScheme.onSurface)
                                )
                            }
                        }
                    } else {
                        val selected = index == selectedIndex

                        // Smooth color interpolation for selection state
                        val selectionFraction by animateFloatAsState(
                            targetValue = if (selected) 1f else 0f,
                            animationSpec = tween(durationMillis = 200),
                            label = "selection_$index"
                        )

                        val iconColor = lerp(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.onSurface,
                            selectionFraction
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(percent = 50))
                                .clickable { onNavigate(screen) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title,
                                    tint = iconColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = selected,
                                    enter = androidx.compose.animation.fadeIn(tween(150)) + androidx.compose.animation.expandVertically(tween(150)),
                                    exit = androidx.compose.animation.fadeOut(tween(150)) + androidx.compose.animation.shrinkVertically(tween(150))
                                ) {
                                    androidx.compose.material3.Text(
                                        text = screen.title,
                                        fontSize = 10.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                        color = iconColor,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            
                            androidx.compose.animation.AnimatedVisibility(
                                visible = selected,
                                enter = androidx.compose.animation.fadeIn(tween(200)),
                                exit = androidx.compose.animation.fadeOut(tween(200)),
                                modifier = Modifier.align(Alignment.BottomCenter)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(bottom = 6.dp)
                                        .width(24.dp)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(1.5.dp))
                                        .background(MaterialTheme.colorScheme.onSurface)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Floating Scanner Button ─────────────────────────────────────
        // Uses graphicsLayer for visual-only translation so it protrudes
        // above the bar without affecting layout measurement or Scaffold
        // bottom padding calculations.
        Surface(
            onClick = { onNavigate(Screen.Scan) },
            modifier = Modifier
                .graphicsLayer { translationY = scannerOffsetPx }
                .size(scannerContainerSize),
            shape = RoundedCornerShape(scannerCornerRadius),
            color = scannerColor,
            shadowElevation = 12.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Screen.Scan.icon,
                    contentDescription = Screen.Scan.title,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(57.dp)
                )
            }
        }
    }
}

/** Linear interpolation between two colors using a fraction [0..1]. */
private fun lerp(start: Color, stop: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (stop.red - start.red) * f,
        green = start.green + (stop.green - start.green) * f,
        blue = start.blue + (stop.blue - start.blue) * f,
        alpha = start.alpha + (stop.alpha - start.alpha) * f
    )
}
