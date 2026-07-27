package com.stutool.stutool.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stutool.stutool.ui.theme.BrandRed
import com.stutool.stutool.ui.theme.LocalGlassTokens
import kotlinx.coroutines.launch

// ── Data class for a single drawer entry ─────────────────────────────────────
private data class DrawerItem(
    val label: String,
    val icon: ImageVector,
    val route: String? = null // non-null for items that map to a nav route
)

// ── Public drawer‑content composable ─────────────────────────────────────────
@Composable
fun DrawerContent(
    drawerState: DrawerState,
    currentRoute: String? = null,
    onNavigate: (String) -> Unit,
    onThemeToggle: () -> Unit,
    isDarkMode: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val glass = LocalGlassTokens.current

    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
        drawerContainerColor = glass.drawerSurface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Header ───────────────────────────────────────────────────
            DrawerHeader()

            // Section 1 — Navigation
            DrawerSectionLabel("Navigation")
            DrawerMenuItem(DrawerItem("Home", Icons.Default.Home, route = "home"), currentRoute) {
                onNavigate("home")
                scope.launch { drawerState.close() }
            }
            DrawerMenuItem(DrawerItem("PDF Tools", Icons.Default.Build, route = "tools"), currentRoute) {
                onNavigate("tools")
                scope.launch { drawerState.close() }
            }
            DrawerMenuItem(DrawerItem("Resume Builder", Icons.Default.Person, route = "resume_builder"), currentRoute) {
                onNavigate("resume_builder")
                scope.launch { drawerState.close() }
            }

            DrawerDivider()

            // Section 2 — Preferences
            DrawerSectionLabel("Preferences")
            DrawerThemeToggle(isDarkMode = isDarkMode, onToggle = onThemeToggle)
            DrawerMenuItem(DrawerItem("Settings", Icons.Default.Settings, route = "settings"), currentRoute) {
                onNavigate("settings")
                scope.launch { drawerState.close() }
            }

            DrawerDivider()

            // Section 3 — Utility
            DrawerSectionLabel("Utility")
            DrawerMenuItem(DrawerItem("Rate App", Icons.Default.Star), currentRoute) {
                Toast.makeText(context, "Coming Soon", Toast.LENGTH_SHORT).show()
                scope.launch { drawerState.close() }
            }
            DrawerMenuItem(DrawerItem("Share App", Icons.Default.Share), currentRoute) {
                val sendIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, "Check out StuTool - PDF & Resume Builder app!")
                    type = "text/plain"
                }
                val shareIntent = android.content.Intent.createChooser(sendIntent, "Share App")
                context.startActivity(shareIntent)
                scope.launch { drawerState.close() }
            }
            DrawerMenuItem(DrawerItem("Send Feedback", Icons.Default.Email, route = "feedback"), currentRoute) {
                onNavigate("feedback")
                scope.launch { drawerState.close() }
            }

            DrawerDivider()

            // Section 4 — Info
            DrawerSectionLabel("Info")
            DrawerMenuItem(DrawerItem("About", Icons.Default.Info, route = "about"), currentRoute) {
                onNavigate("about")
                scope.launch { drawerState.close() }
            }
            DrawerMenuItem(DrawerItem("Privacy Policy", Icons.Default.Info, route = "privacy_policy"), currentRoute) {
                onNavigate("privacy_policy")
                scope.launch { drawerState.close() }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ── Drawer header ────────────────────────────────────────────────────────────
@Composable
private fun DrawerHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 8.dp)
    ) {
        Text(
            text = "StuTool",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Your All-in-One PDF & Resume Tool",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    }
}

// ── Reusable menu‑item row with active‑state highlight ───────────────────────
@Composable
private fun DrawerMenuItem(
    item: DrawerItem,
    currentRoute: String?,
    onClick: () -> Unit
) {
    val isSelected = item.route != null && item.route == currentRoute

    val labelColor = if (isSelected) BrandRed else MaterialTheme.colorScheme.onSurface
    val iconTint = if (isSelected) BrandRed else MaterialTheme.colorScheme.onSurfaceVariant

    NavigationDrawerItem(
        label = {
            Text(
                text = item.label,
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = labelColor
            )
        },
        icon = {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        },
        selected = isSelected,
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = BrandRed.copy(alpha = 0.10f),
            unselectedContainerColor = Color.Transparent,
            selectedTextColor = BrandRed,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface,
            selectedIconColor = BrandRed,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp)
    )
}

// ── Section label ────────────────────────────────────────────────────────────
@Composable
private fun DrawerSectionLabel(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 6.dp)
    )
}

// ── Subtle divider ───────────────────────────────────────────────────────────
@Composable
private fun DrawerDivider() {
    HorizontalDivider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    )
}

// ── Theme toggle row with Switch ─────────────────────────────────────────────
@Composable
private fun DrawerThemeToggle(isDarkMode: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.DarkMode,
            contentDescription = "Dark Mode",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = "Dark Mode",
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = isDarkMode,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = BrandRed,
                checkedTrackColor = BrandRed.copy(alpha = 0.3f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}
