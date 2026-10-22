package com.stutool.stutool

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.stutool.stutool.ui.screens.resume.getResumeTemplateSelectorOptions
import com.stutool.stutool.ui.screens.resume.models.Declaration
import com.stutool.stutool.ui.screens.resume.models.Education
import com.stutool.stutool.ui.screens.resume.models.Experience
import com.stutool.stutool.ui.screens.resume.models.ExperiencedResumeData
import com.stutool.stutool.ui.screens.resume.models.FresherResumeData
import com.stutool.stutool.ui.screens.resume.models.LinkItem
import com.stutool.stutool.ui.screens.resume.models.PersonalInfo
import com.stutool.stutool.ui.screens.resume.models.Project
import com.stutool.stutool.ui.screens.resume.models.ResumeData
import com.stutool.stutool.ui.screens.resume.models.VisibleSections
import com.stutool.stutool.ui.screens.resume.models.formatForExport
import com.stutool.stutool.ui.screens.resume.templates.CareerTimelineTemplate
import com.stutool.stutool.ui.screens.resume.templates.RenderResume
import com.stutool.stutool.ui.screens.resume.templates.ResumeTemplateRenderer
import com.stutool.stutool.ui.screens.resume.templates.buildCareerTimelineModel
import com.stutool.stutool.ui.screens.resume.templates.resolveResumeTemplateKey
import com.stutool.stutool.ui.screens.resume.utils.LocalPaginationRegistry
import com.stutool.stutool.ui.screens.resume.utils.PaginationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CareerTimelineTemplateUnitTest {

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
    fun templateSelectorAndDispatcher_registerCareerTimelineAfterPersona() {
        val options = getResumeTemplateSelectorOptions()
        assertTrue(options.size >= 5)
        assertEquals(
            listOf("minimal", "modern", "classic", "persona", "career_timeline"),
            options.take(5).map { it.first }
        )

        val careerTimelineOption = options[4]
        assertEquals("career_timeline", careerTimelineOption.first)
        assertEquals("Career Timeline", careerTimelineOption.second)
        assertEquals(
            "Chronological milestone layout for experience, education, and projects.",
            careerTimelineOption.third
        )

        assertEquals("career_timeline", resolveResumeTemplateKey("career_timeline"))
        assertEquals("career_timeline", resolveResumeTemplateKey("CAREER_TIMELINE"))
        assertEquals("minimal", resolveResumeTemplateKey("unknown_template"))

        composeTestRule.setContent {
            Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                RenderResume(
                    data = ExperiencedResumeData.formatForExport(),
                    template = "career_timeline",
                    pageWidth = 595.dp
                )
            }
        }
        composeTestRule.onNodeWithText("Mohammed Saif (Experienced Sample)").assertExists()
        composeTestRule.onNodeWithText("Senior Software Engineer").assertExists()
        composeTestRule.onNodeWithText("Apex Cloud Technologies (Sample)").assertExists()
    }

    @Test
    fun resumeTemplateRenderer_dispatchesCareerTimelineConsistently() {
        composeTestRule.setContent {
            Box(
                modifier = Modifier
                    .width(595.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                ResumeTemplateRenderer(
                    template = "career_timeline",
                    data = FresherResumeData.formatForExport()
                )
            }
        }
        composeTestRule.onNodeWithText("Mohammed Saif (Fresher Sample)").assertExists()
        composeTestRule.onNodeWithText("National Degree College (Sample)").assertExists()
        composeTestRule.onNodeWithText("CampusFlow — Student Document & Utility Suite").assertExists()
    }

    @Test
    fun emptyOrMissingOptionalFields_renderSafelyWithoutCrashingOrEmptyRows() {
        val emptyData = ResumeData()
        val emptyModel = buildCareerTimelineModel(emptyData)

        assertFalse(emptyModel.header.hasContent)
        assertNull(emptyModel.careerObjective)
        assertTrue(emptyModel.experienceItems.isEmpty())
        assertTrue(emptyModel.educationItems.isEmpty())
        assertTrue(emptyModel.projectItems.isEmpty())
        assertTrue(emptyModel.skills.isEmpty())
        assertTrue(emptyModel.certificationItems.isEmpty())
        assertTrue(emptyModel.achievementItems.isEmpty())
        assertTrue(emptyModel.extracurricularItems.isEmpty())
        assertNull(emptyModel.languages)
        assertNull(emptyModel.declaration)

        val sparseData = ResumeData(
            personal = PersonalInfo(name = "Aisha Khan", phoneNumber = ""),
            experience = listOf(
                Experience(company = "", position = "", startDate = "", endDate = "", description = ""),
                Experience(company = "Acme Corp", position = "", startDate = "2023", endDate = "", description = "")
            ),
            education = listOf(
                Education(institution = "", level = "", degree = "", board = "", marks = "", year = ""),
                Education(institution = "", level = "Undergraduate", degree = "B.Sc", board = "", marks = "88", marksType = "Percentage", year = "")
            ),
            projects = listOf(
                Project(name = "", links = listOf(""), description = ""),
                Project(name = "Solo Project", links = listOf(""), description = "")
            ),
            skills = listOf("  ", "Kotlin", "")
        )

        val sparseModel = buildCareerTimelineModel(sparseData)
        assertTrue(sparseModel.header.hasContent)
        assertTrue(sparseModel.header.contactItems.isEmpty())
        assertEquals(1, sparseModel.experienceItems.size)
        assertEquals("Acme Corp", sparseModel.experienceItems[0].primaryTitle)
        assertEquals("", sparseModel.experienceItems[0].secondarySubtitle)
        assertEquals("2023", sparseModel.experienceItems[0].dateRange)
        assertFalse(sparseModel.experienceItems[0].hasConnectorBelow)

        assertEquals(1, sparseModel.educationItems.size)
        assertEquals("B.Sc  •  Undergraduate", sparseModel.educationItems[0].primaryTitle)
        assertEquals("Percentage: 88%", sparseModel.educationItems[0].marksLine)

        assertEquals(1, sparseModel.projectItems.size)
        assertEquals("Solo Project", sparseModel.projectItems[0].name)
        assertEquals(listOf("Kotlin"), sparseModel.skills)

        composeTestRule.setContent {
            Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                RenderResume(
                    data = sparseData,
                    template = "career_timeline",
                    pageWidth = 595.dp
                )
            }
        }
        composeTestRule.onNodeWithText("Aisha Khan").assertExists()
        composeTestRule.onNodeWithText("Acme Corp").assertExists()
        composeTestRule.onNodeWithText("Solo Project").assertExists()
    }

    @Test
    fun hiddenSections_respectVisibilitySettings() {
        val fullData = ExperiencedResumeData.copy(
            personal = ExperiencedResumeData.personal.copy(
                dob = "01/01/1998",
                gender = "Male",
                maritalStatus = "Single",
                languages = "English, Hindi"
            ),
            declaration = Declaration(enabled = true, place = "Mumbai", date = "11/10/2026"),
            visibleSections = VisibleSections(
                experience = false,
                education = false,
                projects = false,
                skills = false,
                achievements = false,
                certifications = false,
                extracurriculars = false,
                declaration = false
            )
        )

        val model = buildCareerTimelineModel(fullData)
        assertTrue(model.experienceItems.isEmpty())
        assertTrue(model.educationItems.isEmpty())
        assertTrue(model.projectItems.isEmpty())
        assertTrue(model.skills.isEmpty())
        assertTrue(model.certificationItems.isEmpty())
        assertTrue(model.achievementItems.isEmpty())
        assertTrue(model.extracurricularItems.isEmpty())
        assertNull(model.declaration)
        assertNotNull(model.careerObjective)
        assertEquals("English, Hindi", model.languages)
        assertEquals(listOf("DOB: 01/01/1998", "Male", "Single"), model.header.personalDetailItems)

        composeTestRule.setContent {
            Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                RenderResume(
                    data = fullData,
                    template = "career_timeline",
                    pageWidth = 595.dp
                )
            }
        }
        composeTestRule.onNodeWithText("CAREER OBJECTIVE").assertExists()
        composeTestRule.onNodeWithText("LANGUAGES").assertExists()
        composeTestRule.onNodeWithText("EXPERIENCE").assertDoesNotExist()
        composeTestRule.onNodeWithText("EDUCATION").assertDoesNotExist()
        composeTestRule.onNodeWithText("PROJECTS").assertDoesNotExist()
        composeTestRule.onNodeWithText("SKILLS").assertDoesNotExist()
        composeTestRule.onNodeWithText("CERTIFICATIONS").assertDoesNotExist()
        composeTestRule.onNodeWithText("ACHIEVEMENTS").assertDoesNotExist()
        composeTestRule.onNodeWithText("EXTRACURRICULAR ACTIVITIES").assertDoesNotExist()
        composeTestRule.onNodeWithText("DECLARATION").assertDoesNotExist()
    }

    @Test
    fun multipleEntries_preserveOrderAndTerminateConnectorsAtSectionEnd() {
        val orderedData = ResumeData(
            personal = PersonalInfo(
                name = "Rohan Sharma",
                links = listOf(LinkItem(type = "GitHub", url = "https://www.github.com/rohan"))
            ),
            experience = listOf(
                Experience(id = "exp-1", position = "Staff Engineer", company = "Alpha", startDate = "2024", endDate = "Present"),
                Experience(id = "exp-empty", position = "", company = "", startDate = "", endDate = ""),
                Experience(id = "exp-2", position = "Senior Engineer", company = "Beta", startDate = "2021", endDate = "2024"),
                Experience(id = "exp-3", position = "Junior Engineer", company = "Gamma", startDate = "2019", endDate = "2021")
            ),
            education = listOf(
                Education(id = "edu-1", institution = "University One", degree = "M.Tech", level = "Postgraduate", year = "2019"),
                Education(id = "edu-2", institution = "College Two", degree = "B.Tech", level = "Undergraduate", year = "2017")
            ),
            projects = listOf(
                Project(id = "proj-1", name = "Project One", links = listOf("https://example.com/one"), description = "First"),
                Project(id = "proj-2", name = "Project Two", links = listOf("https://example.com/two"), description = "Second"),
                Project(id = "proj-3", name = "Project Three", links = listOf("https://example.com/three"), description = "Third")
            )
        )

        val model = buildCareerTimelineModel(orderedData)

        assertEquals(listOf("Staff Engineer", "Senior Engineer", "Junior Engineer"), model.experienceItems.map { it.primaryTitle })
        assertEquals(listOf(true, true, false), model.experienceItems.map { it.hasConnectorBelow })

        assertEquals(listOf("University One", "College Two"), model.educationItems.map { it.primaryTitle })
        assertEquals(listOf(true, false), model.educationItems.map { it.hasConnectorBelow })

        assertEquals(listOf("Project One", "Project Two", "Project Three"), model.projectItems.map { it.name })
        assertEquals(listOf(true, true, false), model.projectItems.map { it.hasConnectorBelow })
    }

    @Test
    fun paginationRegistration_registersHeadersAndBodiesCorrectlyAndPreventsBlankTrailingPage() {
        val registry = PaginationRegistry()
        val data = ExperiencedResumeData.copy(
            personal = ExperiencedResumeData.personal.copy(languages = "English, Kannada, Hindi"),
            declaration = Declaration(enabled = true, place = "Bengaluru", date = "03/04/2026")
        ).formatForExport()

        composeTestRule.setContent {
            CompositionLocalProvider(LocalPaginationRegistry provides registry) {
                Box(
                    modifier = Modifier
                        .width(595.dp)
                        .verticalScroll(rememberScrollState())
                        .defaultMinSize(minHeight = 842.dp)
                        .height(IntrinsicSize.Max)
                ) {
                    CareerTimelineTemplate(data = data)
                }
            }
        }
        composeTestRule.waitForIdle()

        val distinctBlocks = registry.blocks.distinctBy { it.top.toInt() }.sortedBy { it.top }
        assertTrue("Expected registered blocks in PaginationRegistry", distinctBlocks.isNotEmpty())

        // 1. First block is the Header (isHeader = false)
        assertFalse("Document header block must not be marked as a section header", distinctBlocks.first().isHeader)

        // 2. Final block must be a content block (isHeader = false), never an orphan section header
        assertFalse("Final block in document must be a content body, not an orphan header", distinctBlocks.last().isHeader)

        // 3. Every section header (isHeader = true) must be immediately followed by a non-header block (isHeader = false)
        val headerCount = distinctBlocks.count { it.isHeader }
        assertEquals("Should have 10 section headers (Objective, Experience, Education, Projects, Skills, Certifications, Achievements, Extracurriculars, Languages, Declaration)", 10, headerCount)

        for (i in distinctBlocks.indices) {
            if (distinctBlocks[i].isHeader) {
                assertTrue(
                    "Section header at index $i must be followed by a content block",
                    i + 1 < distinctBlocks.size && !distinctBlocks[i + 1].isHeader
                )
            }
        }

        // 4. Verify that trailing container padding after the last registered block does not create a blank page
        val lastBlockBottom = distinctBlocks.maxOf { it.bottom }
        val singlePageHeightPx = lastBlockBottom + 100f
        val measuredHeightWithTrailingPadding = singlePageHeightPx + 32f

        val slices = simulateExporterPageSlices(
            measuredHeight = measuredHeightWithTrailingPadding,
            pdfHeightPx = singlePageHeightPx,
            registry = registry
        )
        assertEquals("Trailing padding must not create an extra blank page", 1, slices.size)

        // 5. Verify multi-page slicing does not split a timeline entry or orphan a section header
        val smallerPageHeightPx = distinctBlocks[4].bottom + 10f // Cuts mid-document
        val totalMeasuredHeight = lastBlockBottom + 32f
        val multiPageSlices = simulateExporterPageSlices(
            measuredHeight = totalMeasuredHeight,
            pdfHeightPx = smallerPageHeightPx,
            registry = registry
        )
        assertTrue("Expected multiple pages when page height is smaller than content", multiPageSlices.size >= 2)
        for ((startY, endY) in multiPageSlices) {
            val lastBlockInSlice = distinctBlocks.lastOrNull { it.top >= startY && it.top < endY }
            if (lastBlockInSlice != null) {
                assertFalse("No page slice may end on an orphaned section header", lastBlockInSlice.isHeader)
            }
        }
    }
}

