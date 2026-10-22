package com.stutool.stutool

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.stutool.stutool.ui.screens.OnboardingScreen
import com.stutool.stutool.ui.theme.LocalAppThemeIsDark
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class OnboardingExpansionAndResponsivenessUnitTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testCompleteFiveSlideOnboardingFlowAndContent() {
        var completed = false
        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppThemeIsDark provides false) {
                OnboardingScreen(onComplete = { completed = true })
            }
        }

        // Advance animations for Slide 0
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()

        // Slide 0: Welcome Page
        composeTestRule.onNodeWithText("Welcome to").assertIsDisplayed()
        composeTestRule.onNodeWithText("StuTool").assertIsDisplayed()
        composeTestRule.onNodeWithText("Offline Core").assertIsDisplayed()
        composeTestRule.onNodeWithText("No Account").assertIsDisplayed()
        composeTestRule.onNodeWithText("Private & Free").assertIsDisplayed()
        composeTestRule.onNodeWithText("Skip").assertIsDisplayed()

        // Advance to Slide 1: Viewer & Scanner
        composeTestRule.onNodeWithText("Next").performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Viewer & Scanner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Fast PDF Viewer").assertIsDisplayed()
        composeTestRule.onNodeWithText("In-Document Search").assertIsDisplayed()
        composeTestRule.onNodeWithText("Multi-Format Docs").assertIsDisplayed()
        composeTestRule.onNodeWithText("Smart Scanner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Color & B&W Filters").assertIsDisplayed()
        composeTestRule.onNodeWithText("OCR Text Extraction").assertIsDisplayed()

        // Advance to Slide 2: Resume Builder
        composeTestRule.onNodeWithText("Next").performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Resume Builder").assertIsDisplayed()
        composeTestRule.onNodeWithText("6 Pro Templates").assertIsDisplayed()
        composeTestRule.onNodeWithText("ATS-Friendly").assertIsDisplayed()
        composeTestRule.onNodeWithText("Structured Editor").assertIsDisplayed()
        composeTestRule.onNodeWithText("Live Preview").assertIsDisplayed()
        composeTestRule.onNodeWithText("AI Objective Enhance").assertIsDisplayed()
        composeTestRule.onNodeWithText("Instant PDF Export").assertIsDisplayed()

        // Advance to Slide 3: PDF Tools
        composeTestRule.onNodeWithText("Next").performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("PDF Tools").assertIsDisplayed()
        composeTestRule.onNodeWithText("Merge PDF").assertIsDisplayed()
        composeTestRule.onNodeWithText("Compress PDF").assertIsDisplayed()
        composeTestRule.onNodeWithText("Image to PDF").assertIsDisplayed()
        composeTestRule.onNodeWithText("Extract Pages").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pages Per Sheet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Format Conversions").assertIsDisplayed()

        // Advance to Slide 4: Ready to Get Started
        composeTestRule.onNodeWithText("Next").performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Ready to Get Started?").assertIsDisplayed()
        composeTestRule.onNodeWithText("No Account or Sign-Up").assertIsDisplayed()
        composeTestRule.onNodeWithText("On-Device Processing").assertIsDisplayed()
        composeTestRule.onNodeWithText("Offline Reliability").assertIsDisplayed()
        composeTestRule.onNodeWithText("Get Started").assertIsDisplayed()

        // Tap Get Started
        composeTestRule.onNodeWithText("Get Started").performClick()
        assertTrue("onComplete should be invoked after clicking Get Started", completed)
    }

    @Test
    fun testSkipButtonInvokesOnCompleteImmediately() {
        var completed = false
        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppThemeIsDark provides false) {
                OnboardingScreen(onComplete = { completed = true })
            }
        }

        composeTestRule.onNodeWithText("Skip").assertIsDisplayed()
        composeTestRule.onNodeWithText("Skip").performClick()
        assertTrue("Skip button must invoke onComplete", completed)
    }

    @Test
    fun testBackButtonNavigatesBackwards() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppThemeIsDark provides false) {
                OnboardingScreen(onComplete = {})
            }
        }

        // Initially on Slide 0
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Welcome to").assertIsDisplayed()

        // Move to Slide 1
        composeTestRule.onNodeWithText("Next").performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Viewer & Scanner").assertIsDisplayed()

        // Click Back
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()

        // Should return to Slide 0
        composeTestRule.onNodeWithText("Welcome to").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h520dp-mdpi")
    fun testCompactScreenWithScrolling() {
        // Test compact screen height (520dp) with scrolling
        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppThemeIsDark provides false) {
                OnboardingScreen(onComplete = {})
            }
        }

        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()

        // Advance to Slide 1 (Viewer & Scanner)
        composeTestRule.onNodeWithText("Next").performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()

        // Check top cards are displayed
        composeTestRule.onNodeWithText("Fast PDF Viewer").assertIsDisplayed()

        // Scroll to bottom card on compact screen
        composeTestRule.onNodeWithText("OCR Text Extraction")
            .performScrollTo()
            .assertIsDisplayed()

        // Verify navigation controls remain accessible while scrolled
        composeTestRule.onNodeWithText("Next").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun testAccessibilityLargeFontScaleRendering() {
        // Test with 1.8x accessibility font scale
        composeTestRule.setContent {
            val baseDensity = LocalDensity.current.density
            val largeFontDensity = Density(density = baseDensity, fontScale = 1.8f)

            CompositionLocalProvider(
                LocalDensity provides largeFontDensity,
                LocalAppThemeIsDark provides false
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    OnboardingScreen(onComplete = {})
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()

        // Slide 0 renders without crashes or layout errors
        composeTestRule.onNodeWithText("Welcome to").assertIsDisplayed()
        composeTestRule.onNodeWithText("StuTool").assertIsDisplayed()

        // Move to Slide 1 and verify cards render properly under 1.8x font scale
        composeTestRule.onNodeWithText("Next").performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Viewer & Scanner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Fast PDF Viewer").assertIsDisplayed()

        // Scroll to lower card under large font scale
        composeTestRule.onNodeWithText("OCR Text Extraction")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun testDarkThemeCompatibility() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppThemeIsDark provides true) {
                OnboardingScreen(onComplete = {})
            }
        }

        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Welcome to").assertIsDisplayed()
        composeTestRule.onNodeWithText("Next").assertIsDisplayed()
    }
}
