package com.stutool.stutool.ui.screens.resume

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.stutool.stutool.ui.screens.resume.models.Project
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResumeViewModelTest {

    private lateinit var viewModel: ResumeViewModel

    @Before
    fun setup() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        viewModel = ResumeViewModel(application)
        viewModel.resetData()
    }

    @Test
    fun `test add update remove project link`() = runTest {
        // Add a project
        viewModel.addProject()
        var state = viewModel.uiState.value
        assertEquals(1, state.resumeData.projects.size)

        val projId = state.resumeData.projects[0].id

        // newly created project should have one empty link by default
        assertEquals(1, state.resumeData.projects[0].links.size)
        assertEquals("", state.resumeData.projects[0].links[0])

        // Add a new link
        viewModel.addProjectLink(projId)
        state = viewModel.uiState.value
        assertEquals(2, state.resumeData.projects[0].links.size)

        // Update the second link
        viewModel.updateProjectLink(projId, 1, "https://github.com/newlink")
        state = viewModel.uiState.value
        assertEquals("https://github.com/newlink", state.resumeData.projects[0].links[1])

        // Remove the first link
        viewModel.removeProjectLink(projId, 0)
        state = viewModel.uiState.value
        assertEquals(1, state.resumeData.projects[0].links.size)
        assertEquals("https://github.com/newlink", state.resumeData.projects[0].links[0])
    }

    @Test
    fun `test loadData with corrupted json returns default resume data`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val prefs = application.getSharedPreferences("resume_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("resume_draft", "{ corrupted_json").commit()

        // Re-initialize ViewModel to trigger loadData()
        val newViewModel = ResumeViewModel(application)

        // It should not crash and should have an empty default ResumeData
        val state = newViewModel.uiState.value
        assertEquals(0, state.resumeData.projects.size)
    }
}
