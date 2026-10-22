package com.stutool.stutool.ui.screens.resume

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stutool.stutool.ui.screens.resume.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.stutool.stutool.ui.screens.resume.utils.FormatUtils.formatName
import com.stutool.stutool.ui.screens.resume.utils.FormatUtils.formatSentence
import com.stutool.stutool.ui.screens.resume.utils.FormatUtils.formatPhoneNumber
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import com.stutool.stutool.BuildConfig
import java.util.concurrent.TimeUnit

sealed class EnhanceState {
    object Idle : EnhanceState()
    object Loading : EnhanceState()
    data class Success(val suggestedText: String) : EnhanceState()
    data class Error(val message: String) : EnhanceState()
}

data class ResumeUiState(
    val resumeData: ResumeData = ResumeData(),
    val selectedTemplate: String = "minimal",
    val activeTab: String = "edit", // "edit", "layout", "preview"
    val saveStatus: String = "" // "Saved", "Saving...", ""
)

class ResumeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("resume_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(ResumeUiState())
    val uiState: StateFlow<ResumeUiState> = _uiState.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _enhanceState = MutableStateFlow<EnhanceState>(EnhanceState.Idle)
    val enhanceState: StateFlow<EnhanceState> = _enhanceState.asStateFlow()

    private var saveJob: Job? = null
    
    private var isEnhanceInProgress = false
    private var lastEnhanceTime: Long = 0L
    private val COOLDOWN_MS = 12000L
    private val enhanceCache = mutableMapOf<String, String>()
    private var enhanceJob: Job? = null

    init {
        loadData()
    }

    /**
     * Repairs legacy mojibake sequences in saved JSON.
     * Earlier versions stored corrupted UTF-8 characters due to a
     * CP-1252/UTF-8 source-file encoding mismatch.
     */
    private fun migrateEncoding(json: String): String {
        return json
            .replace("\u00e2\u20ac\u00a2", "\u2022")   // corrupted bullet -> bullet
            .replace("\u00e2\u20ac\u201d", "\u2014")   // corrupted em-dash -> em-dash
            .replace("\u00e2\u20ac\u201c", "\u2013")   // corrupted en-dash -> en-dash
            .replace("\u00e2\u20ac\u00a6", "\u2026")   // corrupted ellipsis -> ellipsis
            .replace("\u00e2\u2014\u2020", "\u25c6")   // corrupted diamond -> diamond
    }

    private fun loadData() {
        try {
            val savedJson = prefs.getString("resume_draft", null)
            val savedTemplate = prefs.getString("resume_template", "minimal")
            if (savedJson != null) {
                val cleanJson = migrateEncoding(savedJson)
                _uiState.update {
                    it.copy(
                        resumeData = ResumeData.fromJson(cleanJson),
                        selectedTemplate = savedTemplate ?: "minimal"
                    )
                }
                // Persist the cleaned JSON so migration runs only once
                if (cleanJson != savedJson) {
                    prefs.edit().putString("resume_draft", cleanJson).apply()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ResumeVM", "Failed to load saved resume data", e)
            // Retain default empty ResumeData — user data is preserved in prefs
            // for manual recovery if needed
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(saveStatus = "Saving...") }
            delay(500)
            val state = _uiState.value
            prefs.edit()
                .putString("resume_draft", state.resumeData.toJson())
                .putString("resume_template", state.selectedTemplate)
                .apply()
            _uiState.update { it.copy(saveStatus = "Saved") }
            delay(2000)
            if (_uiState.value.saveStatus == "Saved") {
                _uiState.update { it.copy(saveStatus = "") }
            }
        }
    }

    fun setActiveTab(tab: String) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setSelectedTemplate(template: String) {
        _uiState.update { it.copy(selectedTemplate = template) }
        prefs.edit().putString("resume_template", template).apply()
    }

    fun updatePersonal(field: String, value: String) {
        _uiState.update { state ->
            val p = state.resumeData.personal
            val newP = when (field) {
                "name" -> p.copy(name = formatName(value))
                "email" -> p.copy(email = value.lowercase())
                "countryCode" -> p.copy(countryCode = value)
                "phoneNumber" -> p.copy(phoneNumber = formatPhoneNumber(value))
                "location" -> p.copy(location = formatName(value))
                "dob" -> p.copy(dob = value)
                "gender" -> p.copy(gender = value)
                "maritalStatus" -> p.copy(maritalStatus = value)
                "languages" -> p.copy(languages = formatName(value))
                else -> p
            }
            state.copy(resumeData = state.resumeData.copy(personal = newP))
        }
        scheduleSave()
    }

    fun updateProfileImage(uri: String) {
        _uiState.update { state ->
            val p = state.resumeData.personal
            state.copy(resumeData = state.resumeData.copy(personal = p.copy(profileImageUri = uri)))
        }
        scheduleSave()
    }

    fun addLink() {
        _uiState.update { state ->
            val p = state.resumeData.personal
            if (p.links.size >= 5) return@update state
            val list = p.links.toMutableList()
            list.add(LinkItem())
            state.copy(resumeData = state.resumeData.copy(personal = p.copy(links = list)))
        }
        scheduleSave()
    }

    fun updateLink(id: String, field: String, value: String) {
        _uiState.update { state ->
            val p = state.resumeData.personal
            val list = p.links.map {
                if (it.id == id) {
                    when (field) {
                        "type" -> it.copy(type = value)
                        "url" -> it.copy(url = value)
                        else -> it
                    }
                } else it
            }
            state.copy(resumeData = state.resumeData.copy(personal = p.copy(links = list)))
        }
        scheduleSave()
    }

    fun removeLink(id: String) {
        _uiState.update { state ->
            val p = state.resumeData.personal
            state.copy(resumeData = state.resumeData.copy(personal = p.copy(links = p.links.filter { it.id != id })))
        }
        scheduleSave()
    }

    fun updateCareerObjective(value: String) {
        _uiState.update { state ->
            state.copy(resumeData = state.resumeData.copy(careerObjective = formatSentence(value)))
        }
        scheduleSave()
    }

    private suspend fun enhanceWithFallback(text: String): String {
        val providers = listOf(
            com.stutool.stutool.data.remote.ai.GeminiProvider(client),
            com.stutool.stutool.data.remote.ai.GroqProvider(client),
            com.stutool.stutool.data.remote.ai.OpenRouterProvider(client)
        )
        
        for (provider in providers) {
            val result = provider.enhance(text)
            if (result.isSuccess) {
                return result.getOrThrow()
            }
        }
        
        throw Exception("ALL_FAILED")
    }

    fun enhanceCareerObjective(input: String) {
        if (isEnhanceInProgress) return

        if (input.isBlank()) {
            _enhanceState.value = EnhanceState.Error("Please enter a career objective first")
            return
        }

        val key = input.trim()
        if (enhanceCache.containsKey(key)) {
            _enhanceState.value = EnhanceState.Success(enhanceCache[key]!!)
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastEnhanceTime < COOLDOWN_MS) {
            _enhanceState.value = EnhanceState.Error("Please wait before trying again")
            return
        }

        isEnhanceInProgress = true
        _enhanceState.value = EnhanceState.Loading

        enhanceJob?.cancel()
        enhanceJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val cleanText = enhanceWithFallback(input).replace(Regex("\n+"), " ").trim()
                
                if (cleanText.isBlank()) {
                    lastEnhanceTime = System.currentTimeMillis()
                    _enhanceState.value = EnhanceState.Error("No response generated. Try again.")
                } else {
                    lastEnhanceTime = System.currentTimeMillis()
                    enhanceCache[key] = cleanText
                    _enhanceState.value = EnhanceState.Success(cleanText)
                }
            } catch (e: Exception) {
                lastEnhanceTime = System.currentTimeMillis() + 18000L // 30s penalty on total failure
                _enhanceState.value = EnhanceState.Error("AI is currently busy. Please try again shortly.")
            } finally {
                isEnhanceInProgress = false
            }
        }
    }

    fun resetEnhanceState() {
        _enhanceState.value = EnhanceState.Idle
    }

    fun addExperience() {
        _uiState.update { state ->
            val list = state.resumeData.experience.toMutableList()
            list.add(Experience())
            state.copy(resumeData = state.resumeData.copy(experience = list))
        }
        scheduleSave()
    }

    fun updateExperience(id: String, field: String, value: String) {
        _uiState.update { state ->
            val list = state.resumeData.experience.map {
                if (it.id == id) {
                    when (field) {
                        "company" -> it.copy(company = formatName(value))
                        "position" -> it.copy(position = formatName(value))
                        "startDate" -> it.copy(startDate = value)
                        "endDate" -> it.copy(endDate = value)
                        "description" -> it.copy(description = formatSentence(value))
                        else -> it
                    }
                } else it
            }
            state.copy(resumeData = state.resumeData.copy(experience = list))
        }
        scheduleSave()
    }

    fun removeExperience(id: String) {
        _uiState.update { state ->
            state.copy(resumeData = state.resumeData.copy(experience = state.resumeData.experience.filter { it.id != id }))
        }
        scheduleSave()
    }

    fun reorderExperience(oldIndex: Int, newIndex: Int) {
        _uiState.update { state ->
            val list = state.resumeData.experience.toMutableList()
            val item = list.removeAt(oldIndex)
            list.add(newIndex, item)
            state.copy(resumeData = state.resumeData.copy(experience = list))
        }
        scheduleSave()
    }

    fun addEducation() {
        _uiState.update { state ->
            val list = state.resumeData.education.toMutableList()
            list.add(Education())
            state.copy(resumeData = state.resumeData.copy(education = list))
        }
        scheduleSave()
    }

    fun updateEducation(id: String, field: String, value: String) {
        _uiState.update { state ->
            val list = state.resumeData.education.map {
                if (it.id == id) {
                    when (field) {
                        "level" -> it.copy(level = value)
                        "institution" -> it.copy(institution = formatName(value))
                        "board" -> it.copy(board = formatName(value))
                        "degree" -> it.copy(degree = formatName(value))
                        "marksType" -> it.copy(marksType = value)
                        "marks" -> it.copy(marks = value)
                        "year" -> it.copy(year = value)
                        "description" -> it.copy(description = formatSentence(value))
                        else -> it
                    }
                } else it
            }
            state.copy(resumeData = state.resumeData.copy(education = list))
        }
        scheduleSave()
    }

    fun removeEducation(id: String) {
        _uiState.update { state ->
            state.copy(resumeData = state.resumeData.copy(education = state.resumeData.education.filter { it.id != id }))
        }
        scheduleSave()
    }

    fun reorderEducation(oldIndex: Int, newIndex: Int) {
        _uiState.update { state ->
            val list = state.resumeData.education.toMutableList()
            val item = list.removeAt(oldIndex)
            list.add(newIndex, item)
            state.copy(resumeData = state.resumeData.copy(education = list))
        }
        scheduleSave()
    }

    fun addProject() {
        _uiState.update { state ->
            val list = state.resumeData.projects.toMutableList()
            list.add(Project(links = listOf("")))
            state.copy(resumeData = state.resumeData.copy(projects = list))
        }
        scheduleSave()
    }

    fun updateProject(id: String, field: String, value: String) {
        _uiState.update { state ->
            val list = state.resumeData.projects.map {
                if (it.id == id) {
                    when (field) {
                        "name" -> it.copy(name = formatName(value))
                        "description" -> it.copy(description = formatSentence(value))
                        else -> it
                    }
                } else it
            }
            state.copy(resumeData = state.resumeData.copy(projects = list))
        }
        scheduleSave()
    }

    fun addProjectLink(id: String) {
        _uiState.update { state ->
            val list = state.resumeData.projects.map { proj ->
                if (proj.id == id) {
                    val linksList = proj.links.toMutableList()
                    linksList.add("")
                    proj.copy(links = linksList)
                } else proj
            }
            state.copy(resumeData = state.resumeData.copy(projects = list))
        }
        scheduleSave()
    }

    fun updateProjectLink(id: String, index: Int, url: String) {
        _uiState.update { state ->
            val list = state.resumeData.projects.map { proj ->
                if (proj.id == id && index in proj.links.indices) {
                    val linksList = proj.links.toMutableList()
                    linksList[index] = url
                    proj.copy(links = linksList)
                } else proj
            }
            state.copy(resumeData = state.resumeData.copy(projects = list))
        }
        scheduleSave()
    }

    fun removeProjectLink(id: String, index: Int) {
        _uiState.update { state ->
            val list = state.resumeData.projects.map { proj ->
                if (proj.id == id && index in proj.links.indices) {
                    val linksList = proj.links.toMutableList()
                    linksList.removeAt(index)
                    proj.copy(links = linksList)
                } else proj
            }
            state.copy(resumeData = state.resumeData.copy(projects = list))
        }
        scheduleSave()
    }

    fun removeProject(id: String) {
        _uiState.update { state ->
            state.copy(resumeData = state.resumeData.copy(projects = state.resumeData.projects.filter { it.id != id }))
        }
        scheduleSave()
    }

    fun reorderProject(oldIndex: Int, newIndex: Int) {
        _uiState.update { state ->
            val list = state.resumeData.projects.toMutableList()
            val item = list.removeAt(oldIndex)
            list.add(newIndex, item)
            state.copy(resumeData = state.resumeData.copy(projects = list))
        }
        scheduleSave()
    }

    fun updateSkills(skills: List<String>) {
        _uiState.update { state ->
            state.copy(resumeData = state.resumeData.copy(skills = skills))
        }
        scheduleSave()
    }

    fun addAchievement() {
        _uiState.update { state ->
            val list = state.resumeData.achievements.toMutableList()
            list.add(Achievement())
            state.copy(resumeData = state.resumeData.copy(achievements = list))
        }
        scheduleSave()
    }

    fun updateAchievement(id: String, field: String, value: String) {
        _uiState.update { state ->
            val list = state.resumeData.achievements.map {
                if (it.id == id) {
                    when (field) {
                        "title" -> it.copy(title = formatName(value))
                        "date" -> it.copy(date = value)
                        "description" -> it.copy(description = formatSentence(value))
                        else -> it
                    }
                } else it
            }
            state.copy(resumeData = state.resumeData.copy(achievements = list))
        }
        scheduleSave()
    }

    fun removeAchievement(id: String) {
        _uiState.update { state ->
            state.copy(resumeData = state.resumeData.copy(achievements = state.resumeData.achievements.filter { it.id != id }))
        }
        scheduleSave()
    }

    fun addCertification() {
        _uiState.update { state ->
            val list = state.resumeData.certifications.toMutableList()
            list.add(Certification())
            state.copy(resumeData = state.resumeData.copy(certifications = list))
        }
        scheduleSave()
    }

    fun updateCertification(id: String, field: String, value: String) {
        _uiState.update { state ->
            val list = state.resumeData.certifications.map {
                if (it.id == id) {
                    when (field) {
                        "name" -> it.copy(name = formatName(value))
                        "issuer" -> it.copy(issuer = formatName(value))
                        "date" -> it.copy(date = value)
                        "link" -> it.copy(link = value)
                        else -> it
                    }
                } else it
            }
            state.copy(resumeData = state.resumeData.copy(certifications = list))
        }
        scheduleSave()
    }

    fun removeCertification(id: String) {
        _uiState.update { state ->
            state.copy(resumeData = state.resumeData.copy(certifications = state.resumeData.certifications.filter { it.id != id }))
        }
        scheduleSave()
    }

    fun addExtracurricular() {
        _uiState.update { state ->
            val list = state.resumeData.extracurriculars.toMutableList()
            list.add(Extracurricular())
            state.copy(resumeData = state.resumeData.copy(extracurriculars = list))
        }
        scheduleSave()
    }

    fun updateExtracurricular(id: String, field: String, value: String) {
        _uiState.update { state ->
            val list = state.resumeData.extracurriculars.map {
                if (it.id == id) {
                    when (field) {
                        "title" -> it.copy(title = formatName(value))
                        "organization" -> it.copy(organization = formatName(value))
                        "date" -> it.copy(date = value)
                        "description" -> it.copy(description = formatSentence(value))
                        else -> it
                    }
                } else it
            }
            state.copy(resumeData = state.resumeData.copy(extracurriculars = list))
        }
        scheduleSave()
    }

    fun removeExtracurricular(id: String) {
        _uiState.update { state ->
            state.copy(resumeData = state.resumeData.copy(extracurriculars = state.resumeData.extracurriculars.filter { it.id != id }))
        }
        scheduleSave()
    }

    fun toggleVisibility(section: String) {
        _uiState.update { state ->
            val vs = state.resumeData.visibleSections
            val newVs = when (section) {
                "experience" -> vs.copy(experience = !vs.experience)
                "education" -> vs.copy(education = !vs.education)
                "projects" -> vs.copy(projects = !vs.projects)
                "skills" -> vs.copy(skills = !vs.skills)
                "achievements" -> vs.copy(achievements = !vs.achievements)
                "certifications" -> vs.copy(certifications = !vs.certifications)
                "extracurriculars" -> vs.copy(extracurriculars = !vs.extracurriculars)
                "declaration" -> vs.copy(declaration = !vs.declaration)
                else -> vs
            }
            state.copy(resumeData = state.resumeData.copy(visibleSections = newVs))
        }
        scheduleSave()
    }

    fun updateDeclaration(enabled: Boolean? = null, place: String? = null, date: String? = null) {
        _uiState.update { state ->
            val dec = state.resumeData.declaration
            val newDec = dec.copy(
                enabled = enabled ?: dec.enabled,
                place = place ?: dec.place,
                date = date ?: dec.date
            )
            state.copy(resumeData = state.resumeData.copy(declaration = newDec))
        }
        scheduleSave()
    }

    fun resetData() {
        val newData = ResumeData()
        _uiState.update { it.copy(resumeData = newData) }
        prefs.edit().putString("resume_draft", newData.toJson()).apply()
    }

    fun loadFresherSample() {
        val newData = FresherResumeData
        _uiState.update { it.copy(resumeData = newData) }
        prefs.edit().putString("resume_draft", newData.toJson()).apply()
    }

    fun loadExperiencedSample() {
        val newData = ExperiencedResumeData
        _uiState.update { it.copy(resumeData = newData) }
        prefs.edit().putString("resume_draft", newData.toJson()).apply()
    }
}
