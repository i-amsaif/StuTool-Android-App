package com.stutool.stutool

import android.os.Bundle
import android.content.Intent
import android.util.Base64
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.animation.ObjectAnimator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import com.stutool.stutool.navigation.AppNavigation
import com.stutool.stutool.ui.theme.StuToolTheme
import com.stutool.stutool.ui.theme.ThemeViewModel
import com.stutool.stutool.ui.theme.ThemeViewModelFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.net.Uri

class MainActivity : ComponentActivity() {
    private val externalPdfFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    
    companion object {
        internal fun extractUriFromIntent(intent: Intent?): String? {
            if (intent == null) return null
            return when (intent.action) {
                Intent.ACTION_VIEW -> intent.data?.toString()
                Intent.ACTION_SEND -> {
                    val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) 
                        ?: intent.clipData?.let { if (it.itemCount > 0) it.getItemAt(0).uri else null }
                    uri?.toString()
                }
                else -> null
            }
        }
        
        internal fun getUriFromIntent(intent: Intent?): Uri? {
            if (intent == null) return null
            return when (intent.action) {
                Intent.ACTION_VIEW -> intent.data
                Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    ?: intent.clipData?.let { if (it.itemCount > 0) it.getItemAt(0).uri else null }
                else -> null
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        // Prevent native splash fade out so Compose takes over immediately (matching pure background)
        splashScreen.setOnExitAnimationListener { splashScreenProvider ->
            splashScreenProvider.remove()
        }

        enableEdgeToEdge()

        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.stutool.stutool.data.local.ThemePreferences.ensureDefaultFolder()
        }
        val uriStr = extractUriFromIntent(intent)
        val actualUri = getUriFromIntent(intent)
        var initialEncodedUri: String? = null
        if (uriStr != null) {
            try {
                // Read-only persists without requiring read/write both if applicable
                actualUri?.let {
                    contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } catch (e: Exception) {
                // Harmless exception if OS denies persistence from external temp-provider
                e.printStackTrace()
            }
            initialEncodedUri = Base64.encodeToString(
                uriStr.toByteArray(),
                Base64.URL_SAFE or Base64.NO_WRAP
            )
        }

        setContent {
            val themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModelFactory(application))
            val isDarkPref by themeViewModel.themeFlow.collectAsState()
            val isDarkMode = isDarkPref ?: isSystemInDarkTheme()

            StuToolTheme(darkTheme = isDarkMode) {
                AppNavigation(
                    initialPdfUriEncoded = initialEncodedUri,
                    externalPdfFlow = externalPdfFlow,
                    isDarkMode = isDarkMode,
                    onThemeToggle = {
                        themeViewModel.setDarkMode(!isDarkMode)
                    },
                    themeViewModel = themeViewModel
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uriStr = extractUriFromIntent(intent)
        val actualUri = getUriFromIntent(intent)
        if (uriStr != null) {
            try {
                actualUri?.let {
                    contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val encodedUri = Base64.encodeToString(
                uriStr.toByteArray(),
                Base64.URL_SAFE or Base64.NO_WRAP
            )
            lifecycleScope.launch {
                externalPdfFlow.emit(encodedUri)
            }
        }
    }
}