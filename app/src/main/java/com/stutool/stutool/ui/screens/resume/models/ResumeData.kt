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
    val achievements: List<Achievement> = emptyList(),
    val certifications: List<Certification> = emptyList(),
    val extracurriculars: List<Extracurricular> = emptyList(),
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

        // Optional sections bonus (allows fresher/student profiles without full-time experience to reach up to 100)
        val validCerts = certifications.count { it.name.isNotBlank() || it.issuer.isNotBlank() }
        if (validCerts > 0) score += (validCerts * 5).coerceAtMost(10)

        val validAch = achievements.count { it.title.isNotBlank() || it.description.isNotBlank() }
        if (validAch > 0) score += (validAch * 5).coerceAtMost(10)

        val validExtra = extracurriculars.count { it.title.isNotBlank() || it.organization.isNotBlank() || it.description.isNotBlank() }
        if (validExtra > 0) score += (validExtra * 5).coerceAtMost(10)

        return score.coerceAtMost(100)
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

        val achArray = JSONArray()
        achievements.forEach { ach ->
            achArray.put(JSONObject().apply {
                put("id", ach.id)
                put("title", ach.title)
                put("date", ach.date)
                put("description", ach.description)
            })
        }
        json.put("achievements", achArray)

        val certArray = JSONArray()
        certifications.forEach { cert ->
            certArray.put(JSONObject().apply {
                put("id", cert.id)
                put("name", cert.name)
                put("issuer", cert.issuer)
                put("date", cert.date)
                put("link", cert.link)
            })
        }
        json.put("certifications", certArray)

        val extraArray = JSONArray()
        extracurriculars.forEach { extra ->
            extraArray.put(JSONObject().apply {
                put("id", extra.id)
                put("title", extra.title)
                put("organization", extra.organization)
                put("date", extra.date)
                put("description", extra.description)
            })
        }
        json.put("extracurriculars", extraArray)

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
            put("achievements", visibleSections.achievements)
            put("certifications", visibleSections.certifications)
            put("extracurriculars", visibleSections.extracurriculars)
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

                val achievements = mutableListOf<Achievement>()
                val achArray = json.optJSONArray("achievements")
                if (achArray != null) {
                    for (i in 0 until achArray.length()) {
                        val obj = achArray.optJSONObject(i) ?: continue
                        achievements.add(
                            Achievement(
                                id = obj.optString("id", UUID.randomUUID().toString()).ifBlank { UUID.randomUUID().toString() },
                                title = obj.optString("title", ""),
                                date = obj.optString("date", ""),
                                description = obj.optString("description", "")
                            )
                        )
                    }
                }

                val certifications = mutableListOf<Certification>()
                val certArray = json.optJSONArray("certifications")
                if (certArray != null) {
                    for (i in 0 until certArray.length()) {
                        val obj = certArray.optJSONObject(i) ?: continue
                        certifications.add(
                            Certification(
                                id = obj.optString("id", UUID.randomUUID().toString()).ifBlank { UUID.randomUUID().toString() },
                                name = obj.optString("name", ""),
                                issuer = obj.optString("issuer", ""),
                                date = obj.optString("date", ""),
                                link = obj.optString("link", "")
                            )
                        )
                    }
                }

                val extracurriculars = mutableListOf<Extracurricular>()
                val extraArray = json.optJSONArray("extracurriculars")
                if (extraArray != null) {
                    for (i in 0 until extraArray.length()) {
                        val obj = extraArray.optJSONObject(i) ?: continue
                        extracurriculars.add(
                            Extracurricular(
                                id = obj.optString("id", UUID.randomUUID().toString()).ifBlank { UUID.randomUUID().toString() },
                                title = obj.optString("title", ""),
                                organization = obj.optString("organization", ""),
                                date = obj.optString("date", ""),
                                description = obj.optString("description", "")
                            )
                        )
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
                    achievements = vJson?.optBoolean("achievements", true) ?: true,
                    certifications = vJson?.optBoolean("certifications", true) ?: true,
                    extracurriculars = vJson?.optBoolean("extracurriculars", true) ?: true,
                    declaration = vJson?.optBoolean("declaration", true) ?: true
                )

                ResumeData(
                    personal = personal,
                    careerObjective = parsedCareerObjective,
                    experience = experience,
                    education = education,
                    projects = projects,
                    skills = skills,
                    achievements = achievements,
                    certifications = certifications,
                    extracurriculars = extracurriculars,
                    declaration = declaration,
                    visibleSections = visibleSections
                )
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
        achievements = this.achievements.map { ach ->
            ach.copy(
                title = ach.title.trim().replace(Regex("\\s+"), " "),
                date = ach.date.trim().replace(Regex("\\s+"), " "),
                description = cleanBulletPoints(ach.description)
            )
        },
        certifications = this.certifications.map { cert ->
            cert.copy(
                name = cert.name.trim().replace(Regex("\\s+"), " "),
                issuer = cert.issuer.trim().replace(Regex("\\s+"), " "),
                date = cert.date.trim().replace(Regex("\\s+"), " "),
                link = cert.link.trim()
            )
        },
        extracurriculars = this.extracurriculars.map { extra ->
            extra.copy(
                title = extra.title.trim().replace(Regex("\\s+"), " "),
                organization = extra.organization.trim().replace(Regex("\\s+"), " "),
                date = extra.date.trim().replace(Regex("\\s+"), " "),
                description = cleanBulletPoints(extra.description)
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
) {
    val displayMarks: String 
        get() = if (marksType == "Percentage" && marks.isNotBlank() && !marks.endsWith("%")) "$marks%" else marks
}

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val links: List<String> = listOf(""),
    val description: String = ""
)

data class Achievement(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val date: String = "",
    val description: String = ""
)

data class Certification(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val issuer: String = "",
    val date: String = "",
    val link: String = ""
)

data class Extracurricular(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val organization: String = "",
    val date: String = "",
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
    val achievements: Boolean = true,
    val certifications: Boolean = true,
    val extracurriculars: Boolean = true,
    val declaration: Boolean = true
)

val FresherResumeData = ResumeData(
    personal = PersonalInfo(
        name = "Mohammed Saif (Fresher Sample)",
        email = "saif.sample@example.com",
        countryCode = "+91",
        phoneNumber = "9876543210",
        location = "Bengaluru, Karnataka",
        languages = "English, Hindi, Kannada",
        links = listOf(
            LinkItem(type = "LinkedIn", url = "linkedin.com/in/i-amsaif"),
            LinkItem(type = "GitHub", url = "github.com/i-amsaif")
        )
    ),
    careerObjective = "Computer Applications graduate with hands-on project experience in Android and full-stack web development. Seeking an entry-level software engineering role to build reliable user-facing applications and contribute to collaborative product engineering.",
    education = listOf(
        Education(
            level = "Undergraduate",
            institution = "National Degree College (Sample)",
            board = "Bengaluru North University",
            degree = "B.C.A",
            marksType = "CGPA",
            marks = "8.6",
            year = "2026",
            description = "Coursework: Data Structures, DBMS, Operating Systems, Web & Mobile Development."
        ),
        Education(
            level = "12th",
            institution = "National PU College (Sample)",
            board = "State Pre-University Board",
            degree = "Class XII (CEBA)",
            marksType = "Percentage",
            marks = "89.5",
            year = "2023"
        ),
        Education(
            level = "10th",
            institution = " High School (Sample)",
            board = "State Secondary Education Board",
            degree = "Class X (SSLC)",
            marksType = "Percentage",
            marks = "92.0",
            year = "2021"
        )
    ),
    experience = emptyList(),
    projects = listOf(
        Project(
            name = "CampusFlow — Student Document & Utility Suite",
            links = listOf("github.com/aarav-sharma-sample/campusflow"),
            description = "• Built an offline-first Android utility app for viewing PDF, DOCX, and XLSX documents with local storage.\n• Implemented clean MVVM state management and responsive Material 3 screens in Kotlin and Jetpack Compose."
        ),
        Project(
            name = "FinTrack — Personal Expense & Ledger Tracker",
            links = listOf("github.com/aarav-sharma-sample/fintrack"),
            description = "• Designed a full-stack budget tracking web app with category analytics, recurring reminders, and CSV export.\n• Developed RESTful APIs and responsive dashboard views using React, Next.js, and SQLite."
        )
    ),
    skills = listOf(
        "Kotlin",
        "Jetpack Compose",
        "Java",
        "React",
        "Next.js",
        "JavaScript",
        "SQL / SQLite",
        "REST APIs",
        "Git & GitHub",
        "HTML/CSS"
    ),
    achievements = listOf(
        Achievement(
            title = "Finalist — Inter-Collegiate App Innovation Challenge (Sample)",
            date = "2025",
            description = "• Selected among the top 5 student teams out of 45 entries for building an offline study planner prototype."
        ),
        Achievement(
            title = "Academic Merit Scholarship Recipient (Sample)",
            date = "2024",
            description = "• Awarded departmental merit recognition for maintaining a top-10% CGPA across four consecutive semesters."
        )
    ),
    certifications = listOf(
        Certification(
            name = "Android App Development Foundations (Sample)",
            issuer = "OpenLearn Tech Academy",
            date = "2025",
            link = "example.com/verify/android-sample"
        ),
        Certification(
            name = "Relational Database & SQL Essentials (Sample)",
            issuer = "CodePath Learning Lab",
            date = "2024",
            link = ""
        )
    ),
    extracurriculars = listOf(
        Extracurricular(
            title = "Student Coordinator",
            organization = "College Coding & Open-Source Club (Sample)",
            date = "2024 — 2026",
            description = "• Organized weekend coding workshops, peer code-review sessions, and an annual campus hackathon for 120+ students."
        )
    ),
    declaration = Declaration(
        enabled = true,
        place = "Bengaluru",
        date = "2026-04-03"
    ),
    visibleSections = VisibleSections()
)

val ExperiencedResumeData = ResumeData(
    personal = PersonalInfo(
        name = "Mohammed Saif (Experienced Sample)",
        email = "iamsaif@example.com",
        countryCode = "+91",
        phoneNumber = "9876543210",
        location = "Bengaluru, Karnataka",
        languages = "English, Hindi",
        links = listOf(
            LinkItem(type = "LinkedIn", url = "linkedin.com/in/i-amsaif"),
            LinkItem(type = "GitHub", url = "github.com/i-amsaif")
        )
    ),
    careerObjective = "Results-driven Senior Software Engineer with 6+ years of experience designing high-availability cloud microservices and scalable web platforms. Proven track record of mentoring engineering teams, optimizing distributed data pipelines, and delivering resilient production systems.",
    education = listOf(
        Education(
            level = "Postgraduate",
            institution = "National Institute of Technology (Sample)",
            board = "Autonomous",
            degree = "M.Tech in Software Systems",
            marksType = "CGPA",
            marks = "9.1",
            year = "2019",
            description = "Specialization: Distributed Computing & Cloud Architecture."
        ),
        Education(
            level = "Undergraduate",
            institution = "Crescent Engineering College (Sample)",
            board = "State Technological University",
            degree = "B.E. in Computer Science",
            marksType = "CGPA",
            marks = "8.5",
            year = "2017"
        )
    ),
    experience = listOf(
        Experience(
            company = "Apex Cloud Technologies (Sample)",
            position = "Senior Software Engineer",
            startDate = "Mar 2022",
            endDate = "Present",
            description = "• Architected and maintained multi-tenant backend microservices handling 15K+ requests per minute with 99.95% uptime.\n• Reduced p95 API latency by 38% by redesigning PostgreSQL indexes and introducing distributed Redis caching.\n• Mentored 6 junior engineers and standardized CI/CD deployment pipelines across 4 product squads."
        ),
        Experience(
            company = "NovaStack Solutions (Sample)",
            position = "Software Engineer",
            startDate = "Jul 2019",
            endDate = "Feb 2022",
            description = "• Developed customer-facing analytics dashboards and event-driven webhook services using Node.js, TypeScript, and React.\n• Cut report generation time by 45% by migrating synchronous batch queries to asynchronous worker queues."
        )
    ),
    projects = listOf(
        Project(
            name = "PulseMetrics — Real-Time Service Telemetry Dashboard",
            links = listOf("github.com/rohan-verma-sample/pulse-metrics"),
            description = "• Built a lightweight observability dashboard for tracking service latency, error budgets, and queue depth in real time.\n• Packaged as a containerized deployment with configurable alerting rules and Prometheus metric exporters."
        ),
        Project(
            name = "SyncBridge — Distributed Webhook Delivery Engine",
            links = listOf("github.com/rohan-verma-sample/sync-bridge"),
            description = "• Implemented an idempotent webhook dispatcher with exponential backoff, dead-letter replay, and tenant rate limiting."
        )
    ),
    skills = listOf(
        "TypeScript",
        "Node.js",
        "React",
        "Next.js",
        "Go",
        "PostgreSQL",
        "Redis",
        "Docker & Kubernetes",
        "AWS",
        "System Design"
    ),
    achievements = listOf(
        Achievement(
            title = "Engineering Excellence Award — Platform Reliability (Sample)",
            date = "2024",
            description = "• Recognized for leading a zero-downtime database migration across 12 production microservices."
        ),
        Achievement(
            title = "Guest Speaker — Regional Cloud Engineering Meetup (Sample)",
            date = "2023",
            description = "• Delivered a technical session on resilient event-driven architectures to 150+ software engineers."
        )
    ),
    certifications = listOf(
        Certification(
            name = "Cloud Solutions Architect — Professional Track (Sample)",
            issuer = "CloudScale Certification Institute",
            date = "2024",
            link = "example.com/verify/cloud-architect-sample"
        ),
        Certification(
            name = "Kubernetes & Cloud-Native Operations (Sample)",
            issuer = "Container Native Academy",
            date = "2023",
            link = ""
        )
    ),
    extracurriculars = listOf(
        Extracurricular(
            title = "Open-Source Maintainer & Technical Mentor",
            organization = "Bengaluru Developers Guild (Sample)",
            date = "2021 — Present",
            description = "• Review community pull requests and conduct monthly system-design study circles for early-career developers."
        )
    ),
    declaration = Declaration(
        enabled = true,
        place = "Bengaluru",
        date = "2026-04-03"
    ),
    visibleSections = VisibleSections()
)
