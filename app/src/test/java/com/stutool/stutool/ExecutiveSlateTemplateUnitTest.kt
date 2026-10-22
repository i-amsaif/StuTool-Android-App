package com.stutool.stutool

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
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
import com.stutool.stutool.ui.screens.resume.templates.ExecutiveSlateTemplate
import com.stutool.stutool.ui.screens.resume.templates.RenderResume
import com.stutool.stutool.ui.screens.resume.templates.ResumeTemplateRenderer
import com.stutool.stutool.ui.screens.resume.templates.buildExecutiveSlateModel
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
class ExecutiveSlateTemplateUnitTest {

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
    fun templateSelectorAndDispatcher_registerExecutiveSlateAfterCareerTimeline() {
        val options = getResumeTemplateSelectorOptions()
        assertEquals(6, options.size)
        assertEquals(
            listOf("minimal", "modern", "classic", "persona", "career_timeline", "executive_slate"),
            options.map { it.first }
        )

        val executiveSlateOption = options[5]
        assertEquals("executive_slate", executiveSlateOption.first)
        assertEquals("Executive Slate", executiveSlateOption.second)
        assertTrue(executiveSlateOption.third.isNotBlank())

        assertEquals("executive_slate", resolveResumeTemplateKey("executive_slate"))
        assertEquals("executive_slate", resolveResumeTemplateKey("EXECUTIVE_SLATE"))
        assertEquals("minimal", resolveResumeTemplateKey("unknown_template"))

        composeTestRule.setContent {
            Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                RenderResume(
                    data = ExperiencedResumeData.formatForExport(),
                    template = "executive_slate",
                    pageWidth = 595.dp
                )
            }
        }
        composeTestRule.onNodeWithText("MOHAMMED SAIF (EXPERIENCED SAMPLE)").assertExists()
        composeTestRule.onNodeWithText("Apex Cloud Technologies (Sample)").assertExists()
    }

    @Test
    fun resumeTemplateRenderer_dispatchesExecutiveSlateConsistently() {
        composeTestRule.setContent {
            Box(
                modifier = Modifier
                    .width(595.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                ResumeTemplateRenderer(
                    template = "executive_slate",
                    data = FresherResumeData.formatForExport()
                )
            }
        }
        composeTestRule.onNodeWithText("MOHAMMED SAIF (FRESHER SAMPLE)").assertExists()
        composeTestRule.onNodeWithText("National Degree College (Sample)").assertExists()
        composeTestRule.onNodeWithText("CampusFlow — Student Document & Utility Suite").assertExists()
    }

    @Test
    fun emptyOrMissingOptionalFields_renderSafelyWithoutCrashingOrEmptyRows() {
        val emptyData = ResumeData()
        val emptyModel = buildExecutiveSlateModel(emptyData)

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
            personal = PersonalInfo(name = "Vikram Mehta", phoneNumber = ""),
            experience = listOf(
                Experience(company = "", position = "", startDate = "", endDate = "", description = ""),
                Experience(company = "Apex Holdings", position = "", startDate = "2022", endDate = "", description = "")
            ),
            education = listOf(
                Education(institution = "", level = "", degree = "", board = "", marks = "", year = ""),
                Education(institution = "", level = "Postgraduate", degree = "MBA", board = "", marks = "9.0", marksType = "CGPA", year = "")
            ),
            projects = listOf(
                Project(name = "", links = listOf(""), description = ""),
                Project(name = "Strategic Transformation", links = listOf(""), description = "")
            ),
            skills = listOf("  ", "Corporate Strategy", "")
        )

        val sparseModel = buildExecutiveSlateModel(sparseData)
        assertTrue(sparseModel.header.hasContent)
        assertEquals("", sparseModel.header.professionalTitle)
        assertTrue(sparseModel.header.contactItems.isEmpty())
        assertEquals(1, sparseModel.experienceItems.size)
        assertEquals("Apex Holdings", sparseModel.experienceItems[0].primaryTitle)
        assertEquals("", sparseModel.experienceItems[0].organization)
        assertEquals("2022", sparseModel.experienceItems[0].dateRange)

        assertEquals(1, sparseModel.educationItems.size)
        assertEquals("MBA  |  Postgraduate", sparseModel.educationItems[0].primaryTitle)
        assertEquals("CGPA: 9.0", sparseModel.educationItems[0].marksLine)

        assertEquals(1, sparseModel.projectItems.size)
        assertEquals("Strategic Transformation", sparseModel.projectItems[0].name)
        assertEquals(listOf("Corporate Strategy"), sparseModel.skills)

        composeTestRule.setContent {
            Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                RenderResume(
                    data = sparseData,
                    template = "executive_slate",
                    pageWidth = 595.dp
                )
            }
        }
        composeTestRule.onNodeWithText("VIKRAM MEHTA").assertExists()
        composeTestRule.onNodeWithText("Apex Holdings").assertExists()
        composeTestRule.onNodeWithText("Strategic Transformation").assertExists()
    }

    @Test
    fun hiddenSections_respectVisibilitySettingsIncludingMastheadTitle() {
        val fullData = ExperiencedResumeData.copy(
            personal = ExperiencedResumeData.personal.copy(
                dob = "15/08/1995",
                gender = "Male",
                maritalStatus = "Married",
                languages = "English, Hindi"
            ),
            declaration = Declaration(enabled = true, place = "New Delhi", date = "12/10/2026"),
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

        val model = buildExecutiveSlateModel(fullData)
        assertTrue(model.experienceItems.isEmpty())
        assertEquals("When experience is hidden, masthead professional title must not leak from hidden experience", "", model.header.professionalTitle)
        assertTrue(model.educationItems.isEmpty())
        assertTrue(model.projectItems.isEmpty())
        assertTrue(model.skills.isEmpty())
        assertTrue(model.certificationItems.isEmpty())
        assertTrue(model.achievementItems.isEmpty())
        assertTrue(model.extracurricularItems.isEmpty())
        assertNull(model.declaration)
        assertNotNull(model.careerObjective)
        assertEquals("English, Hindi", model.languages)
        assertEquals(listOf("DOB: 15/08/1995", "Male", "Married"), model.header.personalDetailItems)

        composeTestRule.setContent {
            Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                RenderResume(
                    data = fullData,
                    template = "executive_slate",
                    pageWidth = 595.dp
                )
            }
        }
        composeTestRule.onNodeWithText("EXECUTIVE SUMMARY").assertExists()
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
    fun multipleEntries_preserveOrderAcrossAllSections() {
        val orderedData = ResumeData(
            personal = PersonalInfo(
                name = "Ananya Rao",
                links = listOf(LinkItem(type = "LinkedIn", url = "https://www.linkedin.com/in/ananya"))
            ),
            experience = listOf(
                Experience(id = "exp-1", position = "VP of Engineering", company = "OneCorp", startDate = "2023", endDate = "Present"),
                Experience(id = "exp-blank", position = "", company = "", startDate = "", endDate = ""),
                Experience(id = "exp-2", position = "Director of Engineering", company = "TwoCorp", startDate = "2020", endDate = "2023"),
                Experience(id = "exp-3", position = "Engineering Manager", company = "ThreeCorp", startDate = "2018", endDate = "2020")
            ),
            education = listOf(
                Education(id = "edu-1", institution = "IIM Bangalore", degree = "MBA", level = "Postgraduate", year = "2018"),
                Education(id = "edu-2", institution = "NIT Surathkal", degree = "B.Tech", level = "Undergraduate", year = "2016")
            ),
            projects = listOf(
                Project(id = "proj-1", name = "Cloud Migration", links = listOf("https://example.com/p1"), description = "First"),
                Project(id = "proj-2", name = "Zero Trust Security", links = listOf("https://example.com/p2"), description = "Second")
            )
        )

        val model = buildExecutiveSlateModel(orderedData)

        assertEquals("VP of Engineering", model.header.professionalTitle)
        assertEquals(
            listOf("VP of Engineering", "Director of Engineering", "Engineering Manager"),
            model.experienceItems.map { it.primaryTitle }
        )
        assertEquals(listOf("IIM Bangalore", "NIT Surathkal"), model.educationItems.map { it.primaryTitle })
        assertEquals(listOf("Cloud Migration", "Zero Trust Security"), model.projectItems.map { it.name })
    }

    @Test
    fun paginationRegistration_registersMastheadHeadersAndBodiesAndPaginatesCleanly() {
        val registry = PaginationRegistry()
        val data = ExperiencedResumeData.copy(
            personal = ExperiencedResumeData.personal.copy(languages = "English, Hindi"),
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
                    ExecutiveSlateTemplate(data = data)
                }
            }
        }
        composeTestRule.waitForIdle()

        val distinctBlocks = registry.blocks.distinctBy { it.top.toInt() }.sortedBy { it.top }
        assertTrue("Expected registered blocks in PaginationRegistry", distinctBlocks.isNotEmpty())

        // 1. First block is the Masthead (isHeader = false) and must remain proportionate (< 25% of 842px virtual A4 height)
        val mastheadBlock = distinctBlocks.first()
        assertFalse("Masthead block must not be marked as a section header", mastheadBlock.isHeader)
        val mastheadHeight = mastheadBlock.bottom - mastheadBlock.top
        assertTrue("Masthead must have positive height", mastheadHeight > 0f)
        assertTrue("Masthead must be proportionate (< 210px in 595x842 coordinate space), was $mastheadHeight", mastheadHeight < 210f)

        // 2. Final block must be a content block (isHeader = false), never an orphan section header
        assertFalse("Final block in document must be a content body, not an orphan header", distinctBlocks.last().isHeader)

        // 3. Every section header (isHeader = true) must be immediately followed by a non-header block (isHeader = false)
        val headerCount = distinctBlocks.count { it.isHeader }
        assertEquals("Should have 10 section headers (Summary, Experience, Education, Projects, Skills, Certifications, Achievements, Extracurriculars, Languages, Declaration)", 10, headerCount)

        for (i in distinctBlocks.indices) {
            if (distinctBlocks[i].isHeader) {
                assertTrue(
                    "Section header at index $i must be followed by a content block",
                    i + 1 < distinctBlocks.size && !distinctBlocks[i + 1].isHeader
                )
            }
        }

        // 4. Verify trailing container padding does not create an extra blank page
        val lastBlockBottom = distinctBlocks.maxOf { it.bottom }
        val singlePageHeightPx = lastBlockBottom + 100f
        val measuredHeightWithTrailingPadding = singlePageHeightPx + 32f

        val slices = simulateExporterPageSlices(
            measuredHeight = measuredHeightWithTrailingPadding,
            pdfHeightPx = singlePageHeightPx,
            registry = registry
        )
        assertEquals("Trailing padding must not create an extra blank page", 1, slices.size)

        // 5. Verify multi-page slicing does not repeat the masthead or orphan a section header
        val smallerPageHeightPx = distinctBlocks[4].bottom + 10f
        val totalMeasuredHeight = lastBlockBottom + 22f
        val multiPageSlices = simulateExporterPageSlices(
            measuredHeight = totalMeasuredHeight,
            pdfHeightPx = smallerPageHeightPx,
            registry = registry
        )
        assertTrue("Expected multiple pages when page height is smaller than content", multiPageSlices.size >= 2)
        assertTrue("Continuation page must start well below the masthead", multiPageSlices[1].first >= mastheadBlock.bottom)
        for ((startY, endY) in multiPageSlices) {
            val lastBlockInSlice = distinctBlocks.lastOrNull { it.top >= startY && it.top < endY }
            if (lastBlockInSlice != null) {
                assertFalse("No page slice may end on an orphaned section header", lastBlockInSlice.isHeader)
            }
        }
    }
}
