package com.stutool.stutool.ui.screens.resume.models

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ResumeData(
    val personal: PersonalInfo = PersonalInfo(),
    val careerObjective: String = "",
    val experience: List<Experience> = emptyList(),
    val education: List<Education> = emptyList(),
    val projects: List<Project> = emptyList(),
    val skills: List<String> = emptyList(),
    val declaration: Declaration = Declaration(),
    val visibleSections: VisibleSections = VisibleSections()
) {
    fun calculateScore(): Int {
        var score = 0
        // Personal Info
        if (personal.name.isNotBlank()) score += 5
        if (personal.email.isNotBlank()) score += 5
        if (personal.phoneNumber.isNotBlank()) score += 5
        if (personal.location.isNotBlank()) score += 5

        // Objective
        if (careerObjective.isNotBlank() && careerObjective.length > 20) score += 10

        // Experience
        if (experience.isNotEmpty()) {
            val validExp = experience.filter { it.company.isNotBlank() && it.position.isNotBlank() }
            score += (validExp.size * 15).coerceAtMost(30)
        }

        // Education
        if (education.isNotEmpty()) {
            val validEdu = education.filter { it.institution.isNotBlank() && it.level.isNotBlank() }
            score += (validEdu.size * 10).coerceAtMost(20)
        }

        // Projects
        if (projects.isNotEmpty()) {
            val validProj = projects.filter { it.name.isNotBlank() && it.description.isNotBlank() }
            score += (validProj.size * 10).coerceAtMost(10)
        }

        // Skills
        if (skills.isNotEmpty()) {
            score += (skills.size * 2).coerceAtMost(10)
        }

        return score
    }

    fun toJson(): String {
        val json = JSONObject()
        val pJson = JSONObject().apply {
            put("name", personal.name)
            put("email", personal.email)
            put("countryCode", personal.countryCode)
            put("phoneNumber", personal.phoneNumber)
            put("location", personal.location)
            put("profileImageUri", personal.profileImageUri)

            val linksArray = JSONArray()
            personal.links.forEach { link ->
                linksArray.put(JSONObject().apply {
                    put("id", link.id)
                    put("type", link.type)
                    put("url", link.url)
                })
            }
            put("links", linksArray)

            put("dob", personal.dob)
            put("gender", personal.gender)
            put("maritalStatus", personal.maritalStatus)
            put("languages", personal.languages)
        }
        json.put("personal", pJson)
        json.put("careerObjective", careerObjective)

        val expArray = JSONArray()
        experience.forEach { exp ->
            expArray.put(JSONObject().apply {
                put("id", exp.id)
                put("company", exp.company)
                put("position", exp.position)
                put("startDate", exp.startDate)
                put("endDate", exp.endDate)
                put("description", exp.description)
            })
        }
        json.put("experience", expArray)

        val eduArray = JSONArray()
        education.forEach { edu ->
            eduArray.put(JSONObject().apply {
                put("id", edu.id)
                put("level", edu.level)
                put("institution", edu.institution)
                put("board", edu.board)
                put("degree", edu.degree)
                put("marksType", edu.marksType)
                put("marks", edu.marks)
                put("year", edu.year)
                put("description", edu.description)
            })
        }
        json.put("education", eduArray)

        val projArray = JSONArray()
        projects.forEach { proj ->
            projArray.put(JSONObject().apply {
                put("id", proj.id)
                put("name", proj.name)
                val projLinksArray = JSONArray()
                proj.links.forEach { projLinksArray.put(it) }
                put("links", projLinksArray)
                put("description", proj.description)
            })
        }
        json.put("projects", projArray)

        val skillArray = JSONArray()
        skills.forEach { skillArray.put(it) }
        json.put("skills", skillArray)

        val decJson = JSONObject().apply {
            put("enabled", declaration.enabled)
            put("place", declaration.place)
            put("date", declaration.date)
        }
        json.put("declaration", decJson)

        val vJson = JSONObject().apply {
            put("experience", visibleSections.experience)
            put("education", visibleSections.education)
            put("projects", visibleSections.projects)
            put("skills", visibleSections.skills)
            put("declaration", visibleSections.declaration)
        }
        json.put("visibleSections", vJson)

        return json.toString()
    }

    companion object {
        fun fromJson(jsonString: String): ResumeData {
            return try {
                val json = JSONObject(jsonString)
                val pJson = json.optJSONObject("personal")

                val linksList = mutableListOf<LinkItem>()
                val linksArray = pJson?.optJSONArray("links")
                if (linksArray != null) {
                    for (i in 0 until linksArray.length()) {
                        val obj = linksArray.getJSONObject(i)
                        linksList.add(LinkItem(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            type = obj.optString("type", "LinkedIn"),
                            url = obj.optString("url", "")
                        ))
                    }
                } else {
                    // Fallback for old data migration
                    val li = pJson?.optString("linkedin", "") ?: ""
                    val gh = pJson?.optString("github", "") ?: ""
                    if (li.isNotBlank()) linksList.add(LinkItem(type = "LinkedIn", url = li))
                    if (gh.isNotBlank()) linksList.add(LinkItem(type = "GitHub", url = gh))
                }

                // Backward compatibility for old "phone" field
                val oldPhone = pJson?.optString("phone", "") ?: ""
                val defaultCountryCode = if (oldPhone.startsWith("+")) oldPhone.substringBefore(" ") else "+91"
                val defaultPhoneRaw = if (oldPhone.startsWith("+")) oldPhone.substringAfter(" ") else oldPhone

                val personal = PersonalInfo(
                    name = pJson?.optString("name", "") ?: "",
                    email = pJson?.optString("email", "") ?: "",
                    countryCode = pJson?.optString("countryCode", defaultCountryCode) ?: "+91",
                    phoneNumber = pJson?.optString("phoneNumber", defaultPhoneRaw) ?: "",
                    location = pJson?.optString("location", "") ?: "",
                    profileImageUri = pJson?.optString("profileImageUri", "") ?: "",
                    links = linksList,
                    dob = pJson?.optString("dob", "") ?: "",
                    gender = pJson?.optString("gender", "") ?: "",
                    maritalStatus = pJson?.optString("maritalStatus", "") ?: "",
                    languages = pJson?.optString("languages", "") ?: ""
                )
                val parsedCareerObjective = json.optString("careerObjective", json.optString("objective", ""))

                val experience = mutableListOf<Experience>()
                val expArray = json.optJSONArray("experience")
                if (expArray != null) {
                    for (i in 0 until expArray.length()) {
                        val obj = expArray.getJSONObject(i)
                        experience.add(
                            Experience(
                                id = obj.optString("id", UUID.randomUUID().toString()),
                                company = obj.optString("company", ""),
                                position = obj.optString("position", ""),
                                startDate = obj.optString("startDate", ""),
                                endDate = obj.optString("endDate", ""),
                                description = obj.optString("description", "")
                            )
                        )
                    }
                }

                val education = mutableListOf<Education>()
                val eduArray = json.optJSONArray("education")
                if (eduArray != null) {
                    for (i in 0 until eduArray.length()) {
                        val obj = eduArray.getJSONObject(i)
                        education.add(
                            Education(
                                id = obj.optString("id", UUID.randomUUID().toString()),
                                level = obj.optString("level", ""),
                                institution = obj.optString("institution", obj.optString("school", "")), // Fallback for old data
                                board = obj.optString("board", ""),
                                degree = obj.optString("degree", ""),
                                marksType = obj.optString("marksType", ""),
                                marks = obj.optString("marks", ""),
                                year = obj.optString("year", obj.optString("endDate", "")),
                                description = obj.optString("description", "")
                            )
                        )
                    }
                }

                val projects = mutableListOf<Project>()
                val projArray = json.optJSONArray("projects")
                if (projArray != null) {
                    for (i in 0 until projArray.length()) {
                        val obj = projArray.getJSONObject(i)
                        val oldLink = obj.optString("link", "").trim()
                        val projLinksList = mutableListOf<String>()
                        val projLinksArr = obj.optJSONArray("links")
                        if (projLinksArr != null) {
                            for (j in 0 until projLinksArr.length()) {
                                projLinksList.add(projLinksArr.getString(j))
                            }
                        } else if (oldLink.isNotBlank()) {
                            if (!oldLink.contains(" ")) {
                                projLinksList.add(oldLink)
                            } else {
                                val urlRegex = """(https?://[^\s]+|www\.[^\s]+|[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}[^\s]*)""".toRegex()
                                val extracted = urlRegex.findAll(oldLink).map { it.value }.toList()
                                if (extracted.isNotEmpty()) {
                                    projLinksList.addAll(extracted)
                                } else {
                                    projLinksList.add(oldLink)
                                }
                            }
                        }

                        projects.add(
                            Project(
                                id = obj.optString("id", UUID.randomUUID().toString()),
                                name = obj.optString("name", ""),
                                links = projLinksList,
                                description = obj.optString("description", "")
                            )
                        )
                    }
                }

                val skills = mutableListOf<String>()
                val skillArray = json.optJSONArray("skills")
                if (skillArray != null) {
                    for (i in 0 until skillArray.length()) {
                        skills.add(skillArray.getString(i))
                    }
                }

                val decJson = json.optJSONObject("declaration")
                val declaration = Declaration(
                    enabled = decJson?.optBoolean("enabled", false) ?: false,
                    place = decJson?.optString("place", "") ?: "",
                    date = decJson?.optString("date", "") ?: ""
                )

                val vJson = json.optJSONObject("visibleSections")
                val visibleSections = VisibleSections(
                    experience = vJson?.optBoolean("experience", true) ?: true,
                    education = vJson?.optBoolean("education", true) ?: true,
                    projects = vJson?.optBoolean("projects", true) ?: true,
                    skills = vJson?.optBoolean("skills", true) ?: true,
                    declaration = vJson?.optBoolean("declaration", true) ?: true
                )

                ResumeData(personal, parsedCareerObjective, experience, education, projects, skills, declaration, visibleSections)
            } catch (e: Exception) {
                e.printStackTrace()
                ResumeData()
            }
        }
    }
}

fun ResumeData.formatForExport(): ResumeData {
    fun cleanBulletPoints(text: String): String {
        return text.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                var cleanLine = line
                while (cleanLine.startsWith("-") || cleanLine.startsWith("•") || cleanLine.startsWith("*")) {
                    cleanLine = cleanLine.substring(1).trim()
                }
                "• $cleanLine"
            }
            .joinToString("\n")
    }

    return this.copy(
        careerObjective = this.careerObjective.trim().replace(Regex("\\s+"), " "),
        skills = this.skills
            .flatMap { it.split("\n", ",") }
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() },
        experience = this.experience.map { exp ->
            exp.copy(
                description = cleanBulletPoints(exp.description),
                company = exp.company.trim().replace(Regex("\\s+"), " "),
                position = exp.position.trim().replace(Regex("\\s+"), " ")
            )
        },
        projects = this.projects.map { proj ->
            proj.copy(
                description = cleanBulletPoints(proj.description),
                name = proj.name.trim().replace(Regex("\\s+"), " ")
            )
        },
        declaration = this.declaration.copy(
            place = this.declaration.place.trim(),
            date = this.declaration.date.trim()
        )
    )
}

data class PersonalInfo(
    val name: String = "",
    val email: String = "",
    val countryCode: String = "+91",
    val phoneNumber: String = "",
    val location: String = "",
    val profileImageUri: String = "",
    val links: List<LinkItem> = emptyList(),
    val dob: String = "",
    val gender: String = "",
    val maritalStatus: String = "",
    val languages: String = ""
)

data class LinkItem(
    val id: String = UUID.randomUUID().toString(),
    val type: String = "LinkedIn",
    val url: String = ""
)

data class Experience(
    val id: String = UUID.randomUUID().toString(),
    val company: String = "",
    val position: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val description: String = ""
)

data class Education(
    val id: String = UUID.randomUUID().toString(),
    val level: String = "",
    val institution: String = "",
    val board: String = "",
    val degree: String = "",
    val marksType: String = "",
    val marks: String = "",
    val year: String = "",
    val description: String = ""
)

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val links: List<String> = listOf(""),
    val description: String = ""
)

data class Declaration(
    val enabled: Boolean = false,
    val place: String = "",
    val date: String = ""
)

data class VisibleSections(
    val experience: Boolean = true,
    val education: Boolean = true,
    val projects: Boolean = true,
    val skills: Boolean = true,
    val declaration: Boolean = true
)

val FresherResumeData = ResumeData(
    personal = PersonalInfo(
        name = "Mohammed Saif",
        email = "email@example.com",
        countryCode = "+91",
        phoneNumber = "9876543210",
        location = "Bengaluru, Karnataka",
        links = listOf(
            LinkItem(type = "LinkedIn", url = "linkedin.com/in/i-amsaif"),
            LinkItem(type = "GitHub", url = "github.com/i-amsaif")
        )
    ),
    careerObjective = "Passionate Computer Science student with hands-on experience in full-stack development and mobile engineering. Seeking to leverage my skills in React and Kotlin to contribute to impactful software products.",
    education = listOf(
        Education(
            level = "Undergraduate",
            institution = "National Degree College",
            board = "BNU",
            degree = "B.C.A",
            marksType = "CGPA",
            marks = "8.2",
            year = "2026",
            description = "Coursework: Data Structures, DBMS, Web Tech."
        )
    ),
    experience = emptyList(),
    projects = listOf(
        Project(
            name = "StuTool",
            links = listOf("github.com/i-amsaif/StuTool"),
            description = "• Built a cross-platform toolkit with a local PDF tool suite and resume builder.\n• Developed in Kotlin with Jetpack Compose."
        ),
        Project(
            name = "Finvovo",
            links = listOf("github.com/i-amsaif/Finvovo"),
            description = "• Developed a Android App for Personal & Business Ledger Management.\n• Developed in Kotlin with Jetpack Compose."
        )
    ),
    skills = listOf("React", "Kotlin", "Next.js", "Java", "HTML/CSS"),
    declaration = Declaration(
        enabled = true,
        place = "Bengaluru",
        date = "2026-04-03"
    ),
    visibleSections = VisibleSections()
)

val ExperiencedResumeData = ResumeData(
    personal = PersonalInfo(
        name = "Mohammed Saif",
        email = "email@example.com",
        countryCode = "+91",
        phoneNumber = "9876543210",
        location = "Bengaluru, Karnataka",
        links = listOf(
            LinkItem(type = "LinkedIn", url = "linkedin.com/in/i-amsaif"),
            LinkItem(type = "GitHub", url = "github.com/i-amsaif")
        )
    ),
    careerObjective = "Experienced software engineer with 6+ years in full-stack development, seeking a leadership role to drive scalable product engineering.",
    education = listOf(
        Education(
            level = "Postgraduate",
            institution = "IIT Bombay",
            board = "IIT",
            degree = "M.Tech in Software Engineering",
            marksType = "CGPA",
            marks = "9.1",
            year = "2018"
        )
    ),
    experience = listOf(
        Experience(
            company = "Tata Consultancy Services (TCS)",
            position = "Senior Software Engineer",
            startDate = "Mar 2021",
            endDate = "Present",
            description = "• Led a team of 8 engineers building microservices.\n• Reduced system latency by 40% through Redis caching."
        ),
        Experience(
            company = "Infosys Limited",
            position = "Software Engineer",
            startDate = "Jul 2018",
            endDate = "Feb 2021",
            description = "• Maintained 3 enterprise MERN web applications.\n• Improved API response times by 35%."
        )
    ),
    projects = listOf(
        Project(
            name = "Open Source Analytics Dashboard",
            links = listOf("github.com/i-amsaif/analytics-dash"),
            description = "• Built a lightweight dashboard used by 500+ developers."
        )
    ),
    skills = listOf("React", "Next.js", "Node.js", "TypeScript", "AWS", "MongoDB"),
    declaration = Declaration(
        enabled = false,
        place = "",
        date = "2026-04-03"
    ),
    visibleSections = VisibleSections()
)
