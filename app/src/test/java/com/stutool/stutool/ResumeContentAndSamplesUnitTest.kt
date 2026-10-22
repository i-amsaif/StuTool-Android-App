package com.stutool.stutool

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.stutool.stutool.ui.screens.resume.ResumeViewModel
import com.stutool.stutool.ui.screens.resume.models.Achievement
import com.stutool.stutool.ui.screens.resume.models.Certification
import com.stutool.stutool.ui.screens.resume.models.ExperiencedResumeData
import com.stutool.stutool.ui.screens.resume.models.Extracurricular
import com.stutool.stutool.ui.screens.resume.models.FresherResumeData
import com.stutool.stutool.ui.screens.resume.models.PersonalInfo
import com.stutool.stutool.ui.screens.resume.models.ResumeData
import com.stutool.stutool.ui.screens.resume.models.VisibleSections
import com.stutool.stutool.ui.screens.resume.models.formatForExport
import com.stutool.stutool.ui.screens.resume.templates.RenderResume
import com.stutool.stutool.ui.screens.resume.utils.LocalPaginationRegistry
import com.stutool.stutool.ui.screens.resume.utils.PaginationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResumeContentAndSamplesUnitTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun simulateExporterPageSlices(
        measuredHeight: Float,
        pdfHeightPx: Float,
        registry: PaginationRegistry
    ): List<Pair<Float, Float>> {
        val slices = mutableListOf<Pair<Float, Float>>()
        var pageIndex = 0
        var currentStartY = 0f

        while (currentStartY < measuredHeight) {
            val distinctBlocks = registry.blocks.distinctBy { it.top.toInt() }.sortedBy { it.top }

            if (pageIndex > 0 && distinctBlocks.isNotEmpty() && distinctBlocks.none { it.bottom > currentStartY }) {
                break
            }

            var sliceEndY = currentStartY + pdfHeightPx

            for (block in distinctBlocks) {
                if (block.top > currentStartY && block.top < sliceEndY && block.bottom > sliceEndY) {
                    val blockHeight = block.bottom - block.top
                    if (blockHeight <= pdfHeightPx) {
                        sliceEndY = block.top
                    }
                }
            }

            val lastBlockOnPage = distinctBlocks.lastOrNull { it.top >= currentStartY && it.top < sliceEndY }
            if (lastBlockOnPage != null && lastBlockOnPage.isHeader) {
                if (lastBlockOnPage.top > currentStartY) {
                    sliceEndY = lastBlockOnPage.top
                }
            }

            if (sliceEndY <= currentStartY) {
                sliceEndY = currentStartY + pdfHeightPx
            }

            val actualContentEndY = sliceEndY.coerceAtMost(measuredHeight)
            slices.add(currentStartY to actualContentEndY)

            currentStartY = sliceEndY
            pageIndex++
        }

        return slices
    }

    @Test
    fun legacySavedDraftJson_loadsBackwardCompatiblyWithDefaultNewSectionsAndVisibility() {
        val legacyJson = JSONObject().apply {
            put("personal", JSONObject().apply {
                put("name", "Legacy Student")
                put("email", "legacy@example.com")
            })
            put("careerObjective", "Seeking an entry-level developer role.")
            put("visibleSections", JSONObject().apply {
                put("experience", true)
                put("education", true)
                put("projects", true)
                put("skills", true)
                put("declaration", false)
                // Intentionally omitting achievements, certifications, extracurriculars
            })
            // Intentionally omitting achievements, certifications, extracurriculars arrays
        }.toString()

        val loaded = ResumeData.fromJson(legacyJson)
        assertEquals("Legacy Student", loaded.personal.name)
        assertEquals("legacy@example.com", loaded.personal.email)
        assertTrue("Missing achievements array should default to emptyList()", loaded.achievements.isEmpty())
        assertTrue("Missing certifications array should default to emptyList()", loaded.certifications.isEmpty())
        assertTrue("Missing extracurriculars array should default to emptyList()", loaded.extracurriculars.isEmpty())

        assertTrue("Missing visibleSections.achievements should default to true", loaded.visibleSections.achievements)
        assertTrue("Missing visibleSections.certifications should default to true", loaded.visibleSections.certifications)
        assertTrue("Missing visibleSections.extracurriculars should default to true", loaded.visibleSections.extracurriculars)
        assertFalse(loaded.visibleSections.declaration)
    }

    @Test
    fun jsonRoundTrip_preservesAchievementsCertificationsExtracurricularsAndVisibilityFlags() {
        val original = ResumeData(
            personal = PersonalInfo(name = "Priya Nair (Sample)"),
            achievements = listOf(
                Achievement(
                    id = "ach-1",
                    title = "Best Capstone Project Award",
                    date = "May 2025",
                    description = "Awarded first place among 40 teams.\nRecognized for offline-first architecture."
                )
            ),
            certifications = listOf(
                Certification(
                    id = "cert-1",
                    name = "Cloud Developer Associate (Sample)",
                    issuer = "OpenCloud Academy (Sample)",
                    date = "Jan 2025",
                    link = "Credential ID: OCA-101"
                )
            ),
            extracurriculars = listOf(
                Extracurricular(
                    id = "ext-1",
                    title = "Technical Lead",
                    organization = "Campus Coding Club (Sample)",
                    date = "2024 - 2025",
                    description = "Organized weekly algorithm study groups."
                )
            ),
            visibleSections = VisibleSections(
                experience = true,
                education = true,
                projects = true,
                skills = true,
                achievements = false,
                certifications = true,
                extracurriculars = false,
                declaration = true
            )
        )

        val json = original.toJson()
        val restored = ResumeData.fromJson(json)

        assertEquals(1, restored.achievements.size)
        assertEquals("ach-1", restored.achievements[0].id)
        assertEquals("Best Capstone Project Award", restored.achievements[0].title)
        assertEquals("May 2025", restored.achievements[0].date)
        assertTrue(restored.achievements[0].description.contains("offline-first architecture"))

        assertEquals(1, restored.certifications.size)
        assertEquals("cert-1", restored.certifications[0].id)
        assertEquals("Cloud Developer Associate (Sample)", restored.certifications[0].name)
        assertEquals("OpenCloud Academy (Sample)", restored.certifications[0].issuer)
        assertEquals("Jan 2025", restored.certifications[0].date)
        assertEquals("Credential ID: OCA-101", restored.certifications[0].link)

        assertEquals(1, restored.extracurriculars.size)
        assertEquals("ext-1", restored.extracurriculars[0].id)
        assertEquals("Technical Lead", restored.extracurriculars[0].title)
        assertEquals("Campus Coding Club (Sample)", restored.extracurriculars[0].organization)
        assertEquals("2024 - 2025", restored.extracurriculars[0].date)
        assertEquals("Organized weekly algorithm study groups.", restored.extracurriculars[0].description)

        assertFalse(restored.visibleSections.achievements)
        assertTrue(restored.visibleSections.certifications)
        assertFalse(restored.visibleSections.extracurriculars)
    }

    @Test
    fun resumeViewModel_supportsCrudAndVisibilityToggleForAllThreeNewSections() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ResumeViewModel(app)
        viewModel.resetData()

        // Achievements CRUD
        viewModel.addAchievement()
        assertEquals(1, viewModel.uiState.value.resumeData.achievements.size)
        val achId = viewModel.uiState.value.resumeData.achievements[0].id
        viewModel.updateAchievement(achId, "title", "Hackathon Winner")
        viewModel.updateAchievement(achId, "date", "2025")
        viewModel.updateAchievement(achId, "description", "1st Place")
        assertEquals("Hackathon Winner", viewModel.uiState.value.resumeData.achievements[0].title)
        assertEquals("2025", viewModel.uiState.value.resumeData.achievements[0].date)
        viewModel.removeAchievement(achId)
        assertTrue(viewModel.uiState.value.resumeData.achievements.isEmpty())

        // Certifications CRUD
        viewModel.addCertification()
        assertEquals(1, viewModel.uiState.value.resumeData.certifications.size)
        val certId = viewModel.uiState.value.resumeData.certifications[0].id
        viewModel.updateCertification(certId, "name", "Kotlin Specialist")
        viewModel.updateCertification(certId, "issuer", "JetBrains (Sample)")
        viewModel.updateCertification(certId, "date", "2024")
        viewModel.updateCertification(certId, "link", "ID-99")
        assertEquals("Kotlin Specialist", viewModel.uiState.value.resumeData.certifications[0].name)
        assertEquals("ID-99", viewModel.uiState.value.resumeData.certifications[0].link)
        viewModel.removeCertification(certId)
        assertTrue(viewModel.uiState.value.resumeData.certifications.isEmpty())

        // Extracurriculars CRUD
        viewModel.addExtracurricular()
        assertEquals(1, viewModel.uiState.value.resumeData.extracurriculars.size)
        val extId = viewModel.uiState.value.resumeData.extracurriculars[0].id
        viewModel.updateExtracurricular(extId, "title", "Volunteer")
        viewModel.updateExtracurricular(extId, "organization", "Tech Guild (Sample)")
        viewModel.updateExtracurricular(extId, "date", "2023")
        viewModel.updateExtracurricular(extId, "description", "Mentored students")
        assertEquals("Volunteer", viewModel.uiState.value.resumeData.extracurriculars[0].title)
        viewModel.removeExtracurricular(extId)
        assertTrue(viewModel.uiState.value.resumeData.extracurriculars.isEmpty())

        // Visibility Toggles
        assertTrue(viewModel.uiState.value.resumeData.visibleSections.achievements)
        viewModel.toggleVisibility("achievements")
        assertFalse(viewModel.uiState.value.resumeData.visibleSections.achievements)

        assertTrue(viewModel.uiState.value.resumeData.visibleSections.certifications)
        viewModel.toggleVisibility("certifications")
        assertFalse(viewModel.uiState.value.resumeData.visibleSections.certifications)

        assertTrue(viewModel.uiState.value.resumeData.visibleSections.extracurriculars)
        viewModel.toggleVisibility("extracurriculars")
        assertFalse(viewModel.uiState.value.resumeData.visibleSections.extracurriculars)
    }

    @Test
    fun fresherAndExperiencedSamples_displayMohammedSaifSampleIdentitiesAndPreserveUserDrafts() {
        // Verify Fresher Sample displays Mohammed Saif (Fresher Sample)
        assertEquals("Mohammed Saif (Fresher Sample)", FresherResumeData.personal.name)
        assertTrue(FresherResumeData.personal.name.contains("Sample"))
        assertTrue("Fresher sample must have empty experience list", FresherResumeData.experience.isEmpty())
        assertEquals(3, FresherResumeData.education.size)
        assertEquals(2, FresherResumeData.projects.size)
        assertTrue(FresherResumeData.skills.size >= 8)
        assertEquals(2, FresherResumeData.achievements.size)
        assertEquals(2, FresherResumeData.certifications.size)
        assertEquals(1, FresherResumeData.extracurriculars.size)
        assertTrue(FresherResumeData.declaration.enabled)

        // Verify Experienced Sample displays Mohammed Saif (Experienced Sample)
        assertEquals("Mohammed Saif (Experienced Sample)", ExperiencedResumeData.personal.name)
        assertTrue(ExperiencedResumeData.personal.name.contains("Sample"))
        assertEquals(2, ExperiencedResumeData.experience.size)
        assertEquals(2, ExperiencedResumeData.education.size)
        assertEquals(2, ExperiencedResumeData.projects.size)
        assertTrue(ExperiencedResumeData.skills.size >= 8)
        assertEquals(2, ExperiencedResumeData.achievements.size)
        assertEquals(2, ExperiencedResumeData.certifications.size)
        assertEquals(1, ExperiencedResumeData.extracurriculars.size)
        assertTrue(ExperiencedResumeData.declaration.enabled)

        // Verify a user's saved draft is never automatically overwritten by sample data on ViewModel init,
        // and explicit sample loading actions work when invoked
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("resume_prefs", android.content.Context.MODE_PRIVATE)
        val userDraft = ResumeData(personal = PersonalInfo(name = "Real Student User"))
        prefs.edit().putString("resume_draft", userDraft.toJson()).commit()

        val viewModel = ResumeViewModel(app)
        assertEquals("Real Student User", viewModel.uiState.value.resumeData.personal.name)

        viewModel.loadFresherSample()
        assertEquals("Mohammed Saif (Fresher Sample)", viewModel.uiState.value.resumeData.personal.name)

        viewModel.loadExperiencedSample()
        assertEquals("Mohammed Saif (Experienced Sample)", viewModel.uiState.value.resumeData.personal.name)
    }

    @Test
    fun personaTemplate_rendersProjectsAndDeclarationEvenWhenExperienceIsEmpty() {
        val fresherFormatted = FresherResumeData.formatForExport()

        composeTestRule.setContent {
            Box(
                modifier = Modifier
                    .width(595.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                RenderResume(
                    data = fresherFormatted,
                    template = "persona",
                    pageWidth = 595.dp
                )
            }
        }

        // Projects and Declaration must both appear in Persona template for a Fresher resume (no work experience)
        composeTestRule.onNodeWithText("PROJECTS").assertExists()
        composeTestRule.onNodeWithText("CampusFlow — Student Document & Utility Suite", substring = true).assertExists()
        composeTestRule.onNodeWithText("DECLARATION").assertExists()
        composeTestRule.onNodeWithText("CERTIFICATIONS").assertExists()
        composeTestRule.onNodeWithText("ACHIEVEMENTS").assertExists()
        composeTestRule.onNodeWithText("EXTRACURRICULAR ACTIVITIES").assertExists()
    }

    @Test
    fun allSixTemplates_renderThreeNewSectionsAndRespectVisibilityAndBlankFiltering() {
        val templates = listOf("minimal", "modern", "classic", "persona", "career_timeline", "executive_slate")

        val testData = ResumeData(
            personal = PersonalInfo(name = "Test Candidate (Sample)"),
            achievements = listOf(
                Achievement(title = "", date = "", description = ""), // Blank entry should be filtered out
                Achievement(title = "National Coding Finalist", date = "2025", description = "Top 10 out of 500 teams")
            ),
            certifications = listOf(
                Certification(name = "", issuer = "", date = "", link = ""), // Blank entry should be filtered out
                Certification(name = "Associate Android Developer (Sample)", issuer = "DevInstitute", date = "2024", link = "CRED-42")
            ),
            extracurriculars = listOf(
                Extracurricular(title = "", organization = "", date = "", description = ""), // Blank entry should be filtered out
                Extracurricular(title = "Open Source Maintainer", organization = "University Club", date = "2023 - 2025", description = "Reviewed student PRs")
            )
        ).formatForExport()

        val hiddenData = testData.copy(
            visibleSections = VisibleSections(
                achievements = false,
                certifications = false,
                extracurriculars = false
            )
        )

        var currentTemplate by mutableStateOf("minimal")
        var currentData by mutableStateOf(testData)

        composeTestRule.setContent {
            key(currentTemplate, currentData.visibleSections) {
                Box(
                    modifier = Modifier
                        .width(595.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    RenderResume(
                        data = currentData,
                        template = currentTemplate,
                        pageWidth = 595.dp
                    )
                }
            }
        }

        for (templateKey in templates) {
            composeTestRule.runOnIdle {
                currentTemplate = templateKey
                currentData = testData
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("National Coding Finalist", substring = true).assertExists()
            composeTestRule.onNodeWithText("Associate Android Developer (Sample)", substring = true).assertExists()
            composeTestRule.onNodeWithText("Open Source Maintainer", substring = true).assertExists()
        }

        // Verify hiding all 3 sections suppresses them across all 6 templates
        for (templateKey in templates) {
            composeTestRule.runOnIdle {
                currentTemplate = templateKey
                currentData = hiddenData
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("National Coding Finalist", substring = true).assertDoesNotExist()
            composeTestRule.onNodeWithText("Associate Android Developer (Sample)", substring = true).assertDoesNotExist()
            composeTestRule.onNodeWithText("Open Source Maintainer", substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun allSixTemplates_paginateFresherAndExperiencedSamplesWithoutOrphanHeaders() {
        val templates = listOf("minimal", "modern", "classic", "persona", "career_timeline", "executive_slate")
        val samples = listOf(
            FresherResumeData.formatForExport(),
            ExperiencedResumeData.formatForExport()
        )

        var currentTemplate by mutableStateOf("minimal")
        var currentSampleIndex by mutableStateOf(0)
        var currentRegistry by mutableStateOf(PaginationRegistry())

        composeTestRule.setContent {
            key(currentTemplate, currentSampleIndex) {
                CompositionLocalProvider(LocalPaginationRegistry provides currentRegistry) {
                    Box(
                        modifier = Modifier
                            .width(595.dp)
                            .verticalScroll(rememberScrollState())
                            .defaultMinSize(minHeight = 842.dp)
                            .height(IntrinsicSize.Max)
                    ) {
                        RenderResume(
                            data = samples[currentSampleIndex],
                            template = currentTemplate,
                            pageWidth = 595.dp
                        )
                    }
                }
            }
        }

        for (templateKey in templates) {
            for (sampleIdx in samples.indices) {
                val freshRegistry = PaginationRegistry()
                composeTestRule.runOnIdle {
                    currentRegistry = freshRegistry
                    currentTemplate = templateKey
                    currentSampleIndex = sampleIdx
                }
                composeTestRule.waitForIdle()

                val distinctBlocks = freshRegistry.blocks.distinctBy { it.top.toInt() }.sortedBy { it.top }
                assertTrue("Expected registered pagination blocks for $templateKey (sample $sampleIdx)", distinctBlocks.isNotEmpty())

                // Final block must never be an orphaned section header
                assertFalse(
                    "Final block in $templateKey (sample $sampleIdx) must not be a section header",
                    distinctBlocks.last().isHeader
                )

                // Simulate multi-page slicing and verify no slice ends on an orphaned section header
                val lastBlockBottom = distinctBlocks.maxOf { it.bottom }
                val slices = simulateExporterPageSlices(
                    measuredHeight = lastBlockBottom + 24f,
                    pdfHeightPx = 842f,
                    registry = freshRegistry
                )
                assertTrue("Expected at least 1 page slice for $templateKey (sample $sampleIdx)", slices.isNotEmpty())
                for ((startY, endY) in slices) {
                    val lastBlockOnPage = distinctBlocks.lastOrNull { it.top >= startY && it.top < endY }
                    if (lastBlockOnPage != null) {
                        assertFalse(
                            "Page slice [$startY..$endY] in $templateKey (sample $sampleIdx) must not end on an orphaned section header",
                            lastBlockOnPage.isHeader
                        )
                    }
                }
            }
        }
    }
}
