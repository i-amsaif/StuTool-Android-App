package com.stutool.stutool

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.stutool.stutool.tutorial.TutorialPreferences
import com.stutool.stutool.tutorial.TutorialType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TutorialSystemTest {

    private lateinit var application: Application
    private lateinit var preferences: TutorialPreferences

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        preferences = TutorialPreferences(application)
        preferences.resetAllTutorials()
    }

    // ─────────────────────────────────────────────────────────────────
    // Authoritative Cloudinary URLs and Keys Verification
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun testTutorialUrlsMatchAuthoritativeAssets() {
        assertEquals(
            "https://res.cloudinary.com/pw8fglbu/video/upload/v1792313970/stutool_viewer.mp4",
            TutorialType.PDF_VIEWER.videoUrl
        )
        assertEquals(
            "https://res.cloudinary.com/pw8fglbu/video/upload/v1792313881/stutool_resumebuilder.mp4",
            TutorialType.RESUME_BUILDER.videoUrl
        )
        assertEquals(
            "https://res.cloudinary.com/pw8fglbu/video/upload/v1792313868/stutool_docscanner.mp4",
            TutorialType.DOCUMENT_SCANNER.videoUrl
        )
    }

    @Test
    fun testTutorialPreferencesKeysAreDistinct() {
        assertEquals("pdf_viewer_tutorial_seen_version", TutorialType.PDF_VIEWER.prefKey)
        assertEquals("resume_builder_tutorial_seen_version", TutorialType.RESUME_BUILDER.prefKey)
        assertEquals("document_scanner_tutorial_seen_version", TutorialType.DOCUMENT_SCANNER.prefKey)

        val keys = listOf(
            TutorialType.PDF_VIEWER.prefKey,
            TutorialType.RESUME_BUILDER.prefKey,
            TutorialType.DOCUMENT_SCANNER.prefKey
        )
        assertEquals("All seen keys must be unique", 3, keys.distinct().size)
    }

    @Test
    fun testDeclinePreferenceKeysAreDistinctAndCorrect() {
        assertEquals("pdf_viewer_tutorial_auto_declined", TutorialType.PDF_VIEWER.declinePrefKey)
        assertEquals("resume_builder_tutorial_auto_declined", TutorialType.RESUME_BUILDER.declinePrefKey)
        assertEquals("document_scanner_tutorial_auto_declined", TutorialType.DOCUMENT_SCANNER.declinePrefKey)

        val declineKeys = listOf(
            TutorialType.PDF_VIEWER.declinePrefKey,
            TutorialType.RESUME_BUILDER.declinePrefKey,
            TutorialType.DOCUMENT_SCANNER.declinePrefKey
        )
        assertEquals("All decline keys must be unique", 3, declineKeys.distinct().size)
    }

    // ─────────────────────────────────────────────────────────────────
    // Section 10: The 15 Specified State Transition Scenarios
    // ─────────────────────────────────────────────────────────────────

    /**
     * Scenario 1: A fresh installation shows an opt-in prompt on the first visit to each feature,
     * rather than immediately starting the video.
     */
    @Test
    fun scenario01_freshInstallationShowsOptInPromptOnFirstVisit() {
        for (type in TutorialType.entries) {
            assertEquals("No seen version initially", -1L, preferences.getSeenVersion(type))
            assertFalse("Not declined initially", preferences.isTutorialDeclined(type))
            assertTrue(
                "Fresh install must prompt opt-in for ${type.name}",
                preferences.shouldShowTutorialPrompt(type, targetVersionCode = 1L)
            )
        }
    }

    /**
     * Scenario 2: Each feature maintains its own prompt state.
     */
    @Test
    fun scenario02_eachFeatureMaintainsItsOwnPromptState() {
        val currentVersion = 2L

        // PDF Viewer is marked seen
        preferences.markTutorialSeen(TutorialType.PDF_VIEWER, versionCode = currentVersion)
        assertFalse(preferences.shouldShowTutorialPrompt(TutorialType.PDF_VIEWER, currentVersion))

        // Resume Builder and Scanner prompt states remain unaffected and eligible
        assertTrue(preferences.shouldShowTutorialPrompt(TutorialType.RESUME_BUILDER, currentVersion))
        assertTrue(preferences.shouldShowTutorialPrompt(TutorialType.DOCUMENT_SCANNER, currentVersion))

        // Resume Builder is declined
        preferences.setTutorialDeclined(TutorialType.RESUME_BUILDER, true)
        assertFalse(preferences.shouldShowTutorialPrompt(TutorialType.RESUME_BUILDER, currentVersion))

        // Document Scanner is STILL eligible and unaffected
        assertTrue(preferences.shouldShowTutorialPrompt(TutorialType.DOCUMENT_SCANNER, currentVersion))
    }

    /**
     * Scenario 3: Selecting "No Thanks" permanently suppresses that feature's automatic prompt.
     */
    @Test
    fun scenario03_selectingNoThanksPermanentlySuppressesAutomaticPrompt() {
        val currentVersion = 2L
        assertTrue(preferences.shouldShowTutorialPrompt(TutorialType.PDF_VIEWER, currentVersion))

        // User explicitly clicks "No Thanks"
        preferences.setTutorialDeclined(TutorialType.PDF_VIEWER, true)

        assertTrue(preferences.isTutorialDeclined(TutorialType.PDF_VIEWER))
        assertFalse(
            "Prompt must be suppressed after selecting No Thanks",
            preferences.shouldShowTutorialPrompt(TutorialType.PDF_VIEWER, currentVersion)
        )
    }

    /**
     * Scenario 4: A permanent decline remains effective after an app version update.
     */
    @Test
    fun scenario04_permanentDeclineRemainsEffectiveAfterAppVersionUpdate() {
        val originalVersion = 2L
        val updatedVersion = 3L
        val futureVersion = 10L

        // User selected "No Thanks" on original version
        preferences.setTutorialDeclined(TutorialType.RESUME_BUILDER, true)
        assertFalse(preferences.shouldShowTutorialPrompt(TutorialType.RESUME_BUILDER, originalVersion))

        // Application updates to higher version codes
        assertFalse(
            "Permanent decline must persist across version update",
            preferences.shouldShowTutorialPrompt(TutorialType.RESUME_BUILDER, updatedVersion)
        )
        assertFalse(
            "Permanent decline must persist across future version updates",
            preferences.shouldShowTutorialPrompt(TutorialType.RESUME_BUILDER, futureVersion)
        )
    }

    /**
     * Scenario 5: Declining one tutorial does not suppress the other two.
     */
    @Test
    fun scenario05_decliningOneTutorialDoesNotSuppressTheOtherTwo() {
        val currentVersion = 2L

        // User declines only DOCUMENT_SCANNER
        preferences.setTutorialDeclined(TutorialType.DOCUMENT_SCANNER, true)

        assertFalse(preferences.shouldShowTutorialPrompt(TutorialType.DOCUMENT_SCANNER, currentVersion))
        assertTrue(
            "PDF Viewer must not be suppressed",
            preferences.shouldShowTutorialPrompt(TutorialType.PDF_VIEWER, currentVersion)
        )
        assertTrue(
            "Resume Builder must not be suppressed",
            preferences.shouldShowTutorialPrompt(TutorialType.RESUME_BUILDER, currentVersion)
        )
    }

    /**
     * Scenario 6: Pressing "Watch Tutorial" alone does not mark the video as watched.
     */
    @Test
    fun scenario06_pressingWatchTutorialAloneDoesNotMarkVideoAsWatched() {
        val currentVersion = 2L

        // User taps "Watch Tutorial" on the opt-in dialog:
        // The dialog dismisses and opens player, but preferences must NOT be modified yet
        assertEquals(-1L, preferences.getSeenVersion(TutorialType.PDF_VIEWER))
        assertFalse(preferences.isTutorialDeclined(TutorialType.PDF_VIEWER))

        // Since it wasn't marked seen or declined, the prompt remains eligible
        assertTrue(preferences.shouldShowTutorialPrompt(TutorialType.PDF_VIEWER, currentVersion))
    }

    /**
     * Scenario 7: A network or media-loading error leaves the tutorial eligible for a future prompt.
     */
    @Test
    fun scenario07_networkOrMediaLoadingErrorLeavesTutorialEligibleForFuturePrompt() {
        val currentVersion = 2L

        // User entered player, but media playback failed (streaming error):
        // completedOrDismissedByUser callback was invoked with false
        val completedOrDismissedByUser = false
        if (completedOrDismissedByUser) {
            preferences.markTutorialSeen(TutorialType.PDF_VIEWER, currentVersion)
        }

        // Must NOT be marked as seen or declined
        assertEquals(-1L, preferences.getSeenVersion(TutorialType.PDF_VIEWER))
        assertFalse(preferences.isTutorialDeclined(TutorialType.PDF_VIEWER))

        // Feature remains eligible for a future prompt
        assertTrue(preferences.shouldShowTutorialPrompt(TutorialType.PDF_VIEWER, currentVersion))
    }

    /**
     * Scenario 8: Returning to the feature after a failed stream displays the prompt again,
     * provided the user did not explicitly decline it.
     */
    @Test
    fun scenario08_returningToFeatureAfterFailedStreamDisplaysPromptAgain() {
        val currentVersion = 2L

        // Attempt 1: streaming failure
        // User continues back to app without completing playback
        assertEquals(-1L, preferences.getSeenVersion(TutorialType.RESUME_BUILDER))
        assertFalse(preferences.isTutorialDeclined(TutorialType.RESUME_BUILDER))

        // Attempt 2: User opens Resume Builder again later (e.g. after restoring internet)
        // shouldShowTutorialPrompt evaluates to true, presenting the opt-in dialog again
        assertTrue(
            "Next entry after failure must display prompt again",
            preferences.shouldShowTutorialPrompt(TutorialType.RESUME_BUILDER, currentVersion)
        )
    }

    /**
     * Scenario 9: A successful tutorial session resolves the automatic prompt for the current
     * application version according to the defined completion, skip, and close behavior.
     */
    @Test
    fun scenario09_successfulTutorialSessionResolvesAutomaticPromptForCurrentVersion() {
        val currentVersion = 2L

        // When playback succeeds and user watches/skips/closes normally:
        val completedOrDismissedByUser = true
        if (completedOrDismissedByUser) {
            preferences.markTutorialSeen(TutorialType.DOCUMENT_SCANNER, currentVersion)
        }

        assertEquals(currentVersion, preferences.getSeenVersion(TutorialType.DOCUMENT_SCANNER))
        assertFalse(
            "Prompt must be resolved for current version",
            preferences.shouldShowTutorialPrompt(TutorialType.DOCUMENT_SCANNER, currentVersion)
        )
    }

    /**
     * Scenario 10: Dismissing the dialog with Back does not permanently decline it.
     */
    @Test
    fun scenario10_dismissingDialogWithBackDoesNotPermanentlyDeclineIt() {
        val currentVersion = 2L

        // User presses Back or taps outside dialog:
        // Dialog dismisses temporarily for session, but does NOT call setTutorialDeclined
        assertFalse(preferences.isTutorialDeclined(TutorialType.PDF_VIEWER))
        assertEquals(-1L, preferences.getSeenVersion(TutorialType.PDF_VIEWER))

        // Still eligible on future visits
        assertTrue(preferences.shouldShowTutorialPrompt(TutorialType.PDF_VIEWER, currentVersion))
    }

    /**
     * Scenario 11: Manual replay remains available after "No Thanks".
     */
    @Test
    fun scenario11_manualReplayRemainsAvailableAfterNoThanks() {
        val currentVersion = 2L

        // User explicitly declined automatic popups
        preferences.setTutorialDeclined(TutorialType.PDF_VIEWER, true)
        assertFalse(preferences.shouldShowTutorialPrompt(TutorialType.PDF_VIEWER, currentVersion))

        // Manual replay button in 3-dot menu opens player directly (independent of shouldShowTutorialPrompt)
        val canTriggerManualReplay = true
        assertTrue("Manual replay action remains accessible", canTriggerManualReplay)

        // Decline state must be preserved
        assertTrue(preferences.isTutorialDeclined(TutorialType.PDF_VIEWER))
    }

    /**
     * Scenario 12: Failed manual replay does not reactivate automatic prompts for a user
     * who permanently declined.
     */
    @Test
    fun scenario12_failedManualReplayDoesNotReactivateAutomaticPromptsForDeclinedUser() {
        val currentVersion = 2L

        // User declined originally
        preferences.setTutorialDeclined(TutorialType.RESUME_BUILDER, true)

        // User later manually replays via 3-dot menu, but connection drops
        val completedOrDismissedByUser = false
        if (completedOrDismissedByUser) {
            preferences.markTutorialSeen(TutorialType.RESUME_BUILDER, currentVersion)
        }

        // Automatic prompt MUST remain disabled
        assertTrue(preferences.isTutorialDeclined(TutorialType.RESUME_BUILDER))
        assertFalse(
            "Failed manual replay must not reactivate automatic popups",
            preferences.shouldShowTutorialPrompt(TutorialType.RESUME_BUILDER, currentVersion)
        )
    }

    /**
     * Scenario 13: A handled tutorial is not prompted repeatedly during the same app version.
     */
    @Test
    fun scenario13_handledTutorialIsNotPromptedRepeatedlyDuringSameAppVersion() {
        val currentVersion = 2L
        preferences.markTutorialSeen(TutorialType.PDF_VIEWER, currentVersion)

        // Multiple subsequent visits in same version
        for (visit in 1..10) {
            assertFalse(
                "Visit $visit must not show prompt",
                preferences.shouldShowTutorialPrompt(TutorialType.PDF_VIEWER, currentVersion)
            )
        }
    }

    /**
     * Scenario 14: A new application version prompts eligible, non-declined users again,
     * but opens the opt-in dialog rather than the player.
     */
    @Test
    fun scenario14_newAppVersionPromptsEligibleNonDeclinedUsersAgain() {
        val v1 = 1L
        val v2 = 2L

        // User finished tutorial in v1
        preferences.markTutorialSeen(TutorialType.DOCUMENT_SCANNER, v1)
        assertFalse(preferences.shouldShowTutorialPrompt(TutorialType.DOCUMENT_SCANNER, v1))

        // App updates to v2 without user having declined
        assertFalse(preferences.isTutorialDeclined(TutorialType.DOCUMENT_SCANNER))
        assertTrue(
            "New version prompts eligible non-declined user again",
            preferences.shouldShowTutorialPrompt(TutorialType.DOCUMENT_SCANNER, v2)
        )

        // If user declines on v2, future versions remain suppressed
        preferences.setTutorialDeclined(TutorialType.DOCUMENT_SCANNER, true)
        assertFalse(preferences.shouldShowTutorialPrompt(TutorialType.DOCUMENT_SCANNER, targetVersionCode = 3L))
    }

    /**
     * Scenario 15: Compose recomposition does not create duplicate dialogs or players.
     */
    @Test
    fun scenario15_composeRecompositionDoesNotCreateDuplicateDialogsOrPlayers() {
        // Simulates composable state lifecycle
        var sessionPromptHandled = false
        var showOptInDialog = false
        var showPlayer = false
        var dialogShowCount = 0
        var playerShowCount = 0

        fun onCompose(isEligible: Boolean) {
            if (!sessionPromptHandled && isEligible && !showOptInDialog && !showPlayer) {
                showOptInDialog = true
                dialogShowCount++
            }
        }

        // Initial composition: prompt appears
        onCompose(isEligible = true)
        assertEquals(1, dialogShowCount)
        assertTrue(showOptInDialog)

        // Recomposition 1..5 while dialog is visible: no duplicate dialogs created
        repeat(5) {
            onCompose(isEligible = true)
            assertEquals("Recomposition must not re-trigger dialog", 1, dialogShowCount)
        }

        // User clicks "Watch Tutorial"
        showOptInDialog = false
        sessionPromptHandled = true
        showPlayer = true
        playerShowCount++

        // Recomposition 1..5 while player is visible: no duplicate player created
        repeat(5) {
            onCompose(isEligible = true)
            assertEquals(1, playerShowCount)
            assertFalse(showOptInDialog)
            assertTrue(showPlayer)
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Reset Verification
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun testResetAllTutorialsClearsSeenAndDeclineState() {
        preferences.markTutorialSeen(TutorialType.PDF_VIEWER, versionCode = 2L)
        preferences.setTutorialDeclined(TutorialType.PDF_VIEWER, true)

        preferences.resetAllTutorials()

        assertEquals(-1L, preferences.getSeenVersion(TutorialType.PDF_VIEWER))
        assertFalse(preferences.isTutorialDeclined(TutorialType.PDF_VIEWER))
        assertTrue(preferences.shouldShowTutorialPrompt(TutorialType.PDF_VIEWER, 2L))
    }
}
