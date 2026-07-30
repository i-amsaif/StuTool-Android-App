package com.stutool.stutool.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.stutool.stutool.BuildConfig
import com.stutool.stutool.ui.theme.BrandRed
import com.stutool.stutool.ui.theme.LocalGlassTokens
import com.stutool.stutool.ui.theme.ThemeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    isDarkMode: Boolean,
    onThemeToggle: () -> Unit,
    themeViewModel: ThemeViewModel
) {
    val glass = LocalGlassTokens.current
    val context = LocalContext.current
    val saveLocationUri by themeViewModel.saveLocationFlow.collectAsState()

    // SAF folder picker launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Persist read/write permission across reboots
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, flags)
            themeViewModel.setSaveLocation(it.toString())
        }
    }

    // Derive a human-readable display path from the URI
    val displayPath = if (saveLocationUri != null) {
        try {
            val decoded = Uri.decode(saveLocationUri)
            // SAF tree URIs typically end with /document/primary:FolderName
            val lastSegment = decoded.substringAfterLast(":", decoded)
                .substringAfterLast("/")
            if (lastSegment.isNotBlank()) lastSegment else "Custom folder"
        } catch (_: Exception) {
            "Custom folder"
        }
    } else {
        com.stutool.stutool.data.local.ThemePreferences.DEFAULT_DISPLAY_PATH + " (default)"
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Medium) },
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
                    ),
                    windowInsets = WindowInsets(0)
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = glass.topBarDivider
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(glass.backgroundBrush)
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── General ──────────────────────────────────────────────
            item { Spacer(modifier = Modifier.height(4.dp)) }
            item { SectionHeader("General") }
            item {
                SettingsClickableItem(
                    title = "View Introduction Again",
                    subtitle = "Replay the onboarding walkthrough",
                    onClick = { navController.navigate("onboarding") }
                )
            }

            // ── Appearance ───────────────────────────────────────────
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item { SectionHeader("Appearance") }
            item {
                SettingsToggleItem(
                    title = "Dark Mode",
                    isChecked = isDarkMode,
                    onToggle = { onThemeToggle() }
                )
            }

            // ── Storage ──────────────────────────────────────────────
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item { SectionHeader("Storage") }
            item {
                SettingsClickableItem(
                    title = "Save Location",
                    subtitle = displayPath,
                    onClick = { folderPickerLauncher.launch(null) }
                )
            }

            // ── About ────────────────────────────────────────────────
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item { SectionHeader("About") }
            item {
                SettingsItem(
                    title = "App Version",
                    subtitle = BuildConfig.VERSION_NAME
                )
            }
            item {
                SettingsClickableItem(
                    title = "Privacy Policy",
                    subtitle = "How your data is handled",
                    onClick = { navController.navigate("privacy_policy") }
                )
            }
            item {
                SettingsClickableItem(
                    title = "About",
                    subtitle = "Learn more about StuTool",
                    onClick = { navController.navigate("about") }
                )
            }
            item {
                SettingsClickableItem(
                    title = "Feedback",
                    subtitle = "Help us improve",
                    onClick = { navController.navigate("feedback") }
                )
            }

            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}

// ── Static display-only item ─────────────────────────────────────────────────
@Composable
private fun SettingsItem(title: String, subtitle: String?) {
    val glass = LocalGlassTokens.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = glass.cardSurface),
        border = BorderStroke(1.dp, glass.cardBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Toggle switch item ───────────────────────────────────────────────────────
@Composable
private fun SettingsToggleItem(title: String, isChecked: Boolean, onToggle: () -> Unit) {
    val glass = LocalGlassTokens.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = glass.cardSurface),
        border = BorderStroke(1.dp, glass.cardBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = isChecked,
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
}

// ── Clickable item (e.g. folder picker) ──────────────────────────────────────
@Composable
private fun SettingsClickableItem(title: String, subtitle: String, onClick: () -> Unit) {
    val glass = LocalGlassTokens.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = glass.cardSurface),
        border = BorderStroke(1.dp, glass.cardBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Section header ───────────────────────────────────────────────────────────
@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
    )
}
