package com.stutool.stutool.ui.screens.resume.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stutool.stutool.ui.screens.resume.models.Achievement
import com.stutool.stutool.ui.screens.resume.models.Certification
import com.stutool.stutool.ui.screens.resume.models.Education
import com.stutool.stutool.ui.screens.resume.models.Experience
import com.stutool.stutool.ui.screens.resume.models.Extracurricular
import com.stutool.stutool.ui.screens.resume.models.Project
import com.stutool.stutool.ui.screens.resume.models.ResumeData
import com.stutool.stutool.ui.screens.resume.utils.PageBreakAwareBlock

private val SlateMastheadBg = Color(0xFF1E293B)
private val SlateMastheadAccent = Color(0xFF38BDF8)
private val SlateMastheadTextSubtle = Color(0xFFCBD5E1)
private val SlateMastheadTextMuted = Color(0xFF94A3B8)

private val SlateHeadingColor = Color(0xFF1E293B)
private val SlatePrimaryText = Color(0xFF0F172A)
private val SlateSecondaryText = Color(0xFF334155)
private val SlateMutedText = Color(0xFF64748B)
private val SlateAccentBar = Color(0xFF475569)
private val SlateLightBar = Color(0xFF94A3B8)
private val SlateRuleColor = Color(0xFFCBD5E1)
private val SlateTagBg = Color(0xFFF1F5F9)
private val SlateTagBorder = Color(0xFFCBD5E1)

internal data class ExecutiveSlateHeaderModel(
    val name: String,
    val professionalTitle: String,
    val contactItems: List<String>,
    val linkItems: List<String>,
    val personalDetailItems: List<String>
) {
    val hasContent: Boolean
        get() = name.isNotBlank() ||
            professionalTitle.isNotBlank() ||
            contactItems.isNotEmpty() ||
            linkItems.isNotEmpty() ||
            personalDetailItems.isNotEmpty()
}

internal data class ExecutiveSlateExperienceItem(
    val id: String,
    val primaryTitle: String,
    val organization: String,
    val dateRange: String,
    val bulletLines: List<String>
)

internal data class ExecutiveSlateEducationItem(
    val id: String,
    val primaryTitle: String,
    val yearText: String,
    val qualificationLine: String,
    val marksLine: String,
    val bulletLines: List<String>
)

internal data class ExecutiveSlateProjectItem(
    val id: String,
    val name: String,
    val linksText: String,
    val bulletLines: List<String>
)

internal data class ExecutiveSlateCertificationItem(
    val id: String,
    val primaryTitle: String,
    val issuerText: String,
    val dateText: String,
    val linkText: String
)

internal data class ExecutiveSlateAchievementItem(
    val id: String,
    val primaryTitle: String,
    val dateText: String,
    val bulletLines: List<String>
)

internal data class ExecutiveSlateExtracurricularItem(
    val id: String,
    val primaryTitle: String,
    val organizationText: String,
    val dateText: String,
    val bulletLines: List<String>
)

internal data class ExecutiveSlateDeclarationModel(
    val statement: String,
    val place: String,
    val date: String,
    val signatureName: String
)

internal data class ExecutiveSlateRenderModel(
    val header: ExecutiveSlateHeaderModel,
    val careerObjective: String?,
    val experienceItems: List<ExecutiveSlateExperienceItem>,
    val educationItems: List<ExecutiveSlateEducationItem>,
    val projectItems: List<ExecutiveSlateProjectItem>,
    val skills: List<String>,
    val certificationItems: List<ExecutiveSlateCertificationItem>,
    val achievementItems: List<ExecutiveSlateAchievementItem>,
    val extracurricularItems: List<ExecutiveSlateExtracurricularItem>,
    val languages: String?,
    val declaration: ExecutiveSlateDeclarationModel?
)

internal fun buildExecutiveSlateModel(data: ResumeData): ExecutiveSlateRenderModel {
    val validExperiences: List<Experience> = if (data.visibleSections.experience) {
        data.experience.filter {
            it.position.isNotBlank() ||
                it.company.isNotBlank() ||
                it.description.isNotBlank() ||
                it.startDate.isNotBlank() ||
                it.endDate.isNotBlank()
        }
    } else {
        emptyList()
    }

    val professionalTitle = validExperiences
        .firstOrNull { it.position.isNotBlank() }
        ?.position
        ?.trim()
        ?: ""

    val contactItems = mutableListOf<String>()
    data.personal.email.trim().takeIf { it.isNotBlank() }?.let { contactItems.add(it) }
    if (data.personal.phoneNumber.isNotBlank()) {
        val phoneStr = "${data.personal.countryCode.trim()} ${data.personal.phoneNumber.trim()}".trim()
        if (phoneStr.isNotBlank()) contactItems.add(phoneStr)
    }
    data.personal.location.trim().takeIf { it.isNotBlank() }?.let { contactItems.add(it) }

    val linkItems = data.personal.links
        .map { it.url.trim() }
        .filter { it.isNotBlank() }
        .map { it.removePrefix("https://").removePrefix("http://").removePrefix("www.") }

    val personalDetailItems = mutableListOf<String>()
    data.personal.dob.trim().takeIf { it.isNotBlank() }?.let { personalDetailItems.add("DOB: $it") }
    data.personal.gender.trim().takeIf { it.isNotBlank() }?.let { personalDetailItems.add(it) }
    data.personal.maritalStatus.trim().takeIf { it.isNotBlank() }?.let { personalDetailItems.add(it) }

    val header = ExecutiveSlateHeaderModel(
        name = data.personal.name.trim(),
        professionalTitle = professionalTitle,
        contactItems = contactItems,
        linkItems = linkItems,
        personalDetailItems = personalDetailItems
    )

    val careerObjective = data.careerObjective.trim().takeIf { it.isNotBlank() }

    val experienceItems = validExperiences.map { exp ->
        val pos = exp.position.trim()
        val comp = exp.company.trim()
        val primaryTitle = pos.ifBlank { comp }
        val organization = if (pos.isNotBlank()) comp else ""
        val dateRange = listOf(exp.startDate.trim(), exp.endDate.trim())
            .filter { it.isNotBlank() }
            .joinToString(" — ")

        ExecutiveSlateExperienceItem(
            id = exp.id,
            primaryTitle = primaryTitle,
            organization = organization,
            dateRange = dateRange,
            bulletLines = extractCleanBullets(exp.description)
        )
    }

    val validEducations: List<Education> = if (data.visibleSections.education) {
        data.education.filter {
            it.institution.isNotBlank() ||
                it.level.isNotBlank() ||
                it.degree.isNotBlank() ||
                it.board.isNotBlank() ||
                it.marks.isNotBlank() ||
                it.year.isNotBlank() ||
                it.description.isNotBlank()
        }
    } else {
        emptyList()
    }

    val educationItems = validEducations.map { edu ->
        val inst = edu.institution.trim()
        val deg = edu.degree.trim()
        val lvl = edu.level.trim()
        val brd = edu.board.trim()

        val qualParts = mutableListOf<String>()
        if (deg.isNotBlank()) qualParts.add(deg)
        if (lvl.isNotBlank() && !lvl.equals(deg, ignoreCase = true)) qualParts.add(lvl)
        if (brd.isNotBlank()) qualParts.add(brd)

        val primaryTitle = inst.ifBlank { qualParts.joinToString("  |  ") }
        val qualificationLine = if (inst.isNotBlank()) qualParts.joinToString("  |  ") else ""

        val marksLine = if (edu.marks.isNotBlank()) {
            val formattedMarks = edu.displayMarks.trim()
            val mType = edu.marksType.trim()
            if (mType.isNotBlank() && !formattedMarks.startsWith(mType, ignoreCase = true)) {
                "$mType: $formattedMarks"
            } else {
                formattedMarks
            }
        } else {
            ""
        }

        ExecutiveSlateEducationItem(
            id = edu.id,
            primaryTitle = primaryTitle,
            yearText = edu.year.trim(),
            qualificationLine = qualificationLine,
            marksLine = marksLine,
            bulletLines = extractCleanBullets(edu.description)
        )
    }

    val validProjects: List<Project> = if (data.visibleSections.projects) {
        data.projects.filter {
            it.name.isNotBlank() ||
                it.description.isNotBlank() ||
                it.links.any { link -> link.isNotBlank() }
        }
    } else {
        emptyList()
    }

    val projectItems = validProjects.map { proj ->
        val linksText = proj.links
            .map { it.trim().removePrefix("https://").removePrefix("http://").removePrefix("www.") }
            .filter { it.isNotBlank() }
            .joinToString("  |  ")

        ExecutiveSlateProjectItem(
            id = proj.id,
            name = proj.name.trim(),
            linksText = linksText,
            bulletLines = extractCleanBullets(proj.description)
        )
    }

    val skills = if (data.visibleSections.skills) {
        data.skills.map { it.trim() }.filter { it.isNotBlank() }
    } else {
        emptyList()
    }

    val validCertifications: List<Certification> = if (data.visibleSections.certifications) {
        data.certifications.filter { it.name.isNotBlank() || it.issuer.isNotBlank() }
    } else {
        emptyList()
    }

    val certificationItems = validCertifications.map { cert ->
        val name = cert.name.trim()
        val issuer = cert.issuer.trim()
        val primaryTitle = name.ifBlank { issuer }
        val issuerText = if (name.isNotBlank()) issuer else ""
        val cleanLink = cert.link.trim().removePrefix("https://").removePrefix("http://").removePrefix("www.")

        ExecutiveSlateCertificationItem(
            id = cert.id,
            primaryTitle = primaryTitle,
            issuerText = issuerText,
            dateText = cert.date.trim(),
            linkText = cleanLink
        )
    }

    val validAchievements: List<Achievement> = if (data.visibleSections.achievements) {
        data.achievements.filter { it.title.isNotBlank() || it.description.isNotBlank() }
    } else {
        emptyList()
    }

    val achievementItems = validAchievements.map { ach ->
        ExecutiveSlateAchievementItem(
            id = ach.id,
            primaryTitle = ach.title.trim(),
            dateText = ach.date.trim(),
            bulletLines = extractCleanBullets(ach.description)
        )
    }

    val validExtracurriculars: List<Extracurricular> = if (data.visibleSections.extracurriculars) {
        data.extracurriculars.filter {
            it.title.isNotBlank() || it.organization.isNotBlank() || it.description.isNotBlank()
        }
    } else {
        emptyList()
    }

    val extracurricularItems = validExtracurriculars.map { extra ->
        val title = extra.title.trim()
        val org = extra.organization.trim()
        val primaryTitle = title.ifBlank { org }
        val organizationText = if (title.isNotBlank()) org else ""

        ExecutiveSlateExtracurricularItem(
            id = extra.id,
            primaryTitle = primaryTitle,
            organizationText = organizationText,
            dateText = extra.date.trim(),
            bulletLines = extractCleanBullets(extra.description)
        )
    }

    val languages = data.personal.languages.trim().takeIf { it.isNotBlank() }

    val declaration = if (data.visibleSections.declaration && data.declaration.enabled) {
        ExecutiveSlateDeclarationModel(
            statement = "I hereby declare that all the information provided is true and correct to the best of my knowledge.",
            place = data.declaration.place.trim(),
            date = data.declaration.date.trim(),
            signatureName = data.personal.name.trim()
        )
    } else {
        null
    }

    return ExecutiveSlateRenderModel(
        header = header,
        careerObjective = careerObjective,
        experienceItems = experienceItems,
        educationItems = educationItems,
        projectItems = projectItems,
        skills = skills,
        certificationItems = certificationItems,
        achievementItems = achievementItems,
        extracurricularItems = extracurricularItems,
        languages = languages,
        declaration = declaration
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExecutiveSlateTemplate(data: ResumeData) {
    val model = buildExecutiveSlateModel(data)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // --- Full-Width Deep Slate/Navy Masthead (First Page Top) ---
        if (model.header.hasContent) {
            PageBreakAwareBlock(isHeader = false) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SlateMastheadBg)
                        .padding(horizontal = 36.dp, vertical = 22.dp)
                ) {
                    if (model.header.name.isNotBlank()) {
                        Text(
                            text = model.header.name.uppercase(),
                            fontSize = 23.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 1.2.sp
                        )
                    }

                    if (model.header.professionalTitle.isNotBlank()) {
                        Text(
                            text = model.header.professionalTitle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = SlateMastheadAccent,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }

                    if (model.header.name.isNotBlank() || model.header.professionalTitle.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp, bottom = 6.dp)
                                .width(36.dp)
                                .height(2.dp)
                                .background(SlateMastheadAccent, RoundedCornerShape(1.dp))
                        )
                    }

                    if (model.header.contactItems.isNotEmpty()) {
                        Text(
                            text = model.header.contactItems.joinToString("   |   "),
                            fontSize = 9.5.sp,
                            color = SlateMastheadTextSubtle,
                            fontFamily = FontFamily.SansSerif,
                            lineHeight = 13.5.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    if (model.header.linkItems.isNotEmpty()) {
                        Text(
                            text = model.header.linkItems.joinToString("   |   "),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.9f),
                            fontFamily = FontFamily.SansSerif,
                            lineHeight = 13.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    if (model.header.personalDetailItems.isNotEmpty()) {
                        Text(
                            text = model.header.personalDetailItems.joinToString("   |   "),
                            fontSize = 9.sp,
                            color = SlateMastheadTextMuted,
                            fontFamily = FontFamily.SansSerif,
                            lineHeight = 13.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        // --- Single-Column White Body ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp, vertical = 22.dp)
        ) {
            var hasRenderedSection = false

            // --- Executive Summary / Career Objective ---
            if (model.careerObjective != null) {
                Column(modifier = Modifier.padding(top = if (hasRenderedSection) 18.dp else 0.dp)) {
                    ExecutiveSlateSectionHeader("EXECUTIVE SUMMARY")
                    PageBreakAwareBlock(isHeader = false) {
                        Text(
                            text = model.careerObjective,
                            fontSize = 10.sp,
                            color = SlateSecondaryText,
                            fontFamily = FontFamily.SansSerif,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                hasRenderedSection = true
            }

            // --- Experience ---
            if (model.experienceItems.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = if (hasRenderedSection) 18.dp else 0.dp)) {
                    ExecutiveSlateSectionHeader("EXPERIENCE")
                    for (exp in model.experienceItems) {
                        PageBreakAwareBlock(isHeader = false) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                                    .height(IntrinsicSize.Min)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(2.5.dp)
                                        .fillMaxHeight()
                                        .background(SlateAccentBar, RoundedCornerShape(1.dp))
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 10.dp)
                                ) {
                                    if (exp.primaryTitle.isNotBlank() || exp.dateRange.isNotBlank()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            if (exp.primaryTitle.isNotBlank()) {
                                                Text(
                                                    text = exp.primaryTitle,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SlatePrimaryText,
                                                    fontFamily = FontFamily.SansSerif,
                                                    lineHeight = 15.sp,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(end = 8.dp)
                                                )
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }

                                            if (exp.dateRange.isNotBlank()) {
                                                Text(
                                                    text = exp.dateRange,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = SlateSecondaryText,
                                                    fontFamily = FontFamily.SansSerif
                                                )
                                            }
                                        }
                                    }

                                    if (exp.organization.isNotBlank()) {
                                        Text(
                                            text = exp.organization,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = SlateMutedText,
                                            fontFamily = FontFamily.SansSerif,
                                            modifier = Modifier.padding(top = 1.dp)
                                        )
                                    }

                                    if (exp.bulletLines.isNotEmpty()) {
                                        ExecutiveSlateBulletList(exp.bulletLines)
                                    }
                                }
                            }
                        }
                    }
                }
                hasRenderedSection = true
            }

            // --- Education ---
            if (model.educationItems.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = if (hasRenderedSection) 18.dp else 0.dp)) {
                    ExecutiveSlateSectionHeader("EDUCATION")
                    for (edu in model.educationItems) {
                        PageBreakAwareBlock(isHeader = false) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                                    .height(IntrinsicSize.Min)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(2.5.dp)
                                        .fillMaxHeight()
                                        .background(SlateLightBar, RoundedCornerShape(1.dp))
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 10.dp)
                                ) {
                                    if (edu.primaryTitle.isNotBlank() || edu.yearText.isNotBlank()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            if (edu.primaryTitle.isNotBlank()) {
                                                Text(
                                                    text = edu.primaryTitle,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SlatePrimaryText,
                                                    fontFamily = FontFamily.SansSerif,
                                                    lineHeight = 14.5.sp,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(end = 8.dp)
                                                )
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }

                                            if (edu.yearText.isNotBlank()) {
                                                Text(
                                                    text = edu.yearText,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = SlateSecondaryText,
                                                    fontFamily = FontFamily.SansSerif
                                                )
                                            }
                                        }
                                    }

                                    if (edu.qualificationLine.isNotBlank()) {
                                        Text(
                                            text = edu.qualificationLine,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = SlateSecondaryText,
                                            fontFamily = FontFamily.SansSerif,
                                            modifier = Modifier.padding(top = 1.dp)
                                        )
                                    }

                                    if (edu.marksLine.isNotBlank()) {
                                        Text(
                                            text = edu.marksLine,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = SlateMutedText,
                                            fontFamily = FontFamily.SansSerif,
                                            modifier = Modifier.padding(top = 1.dp)
                                        )
                                    }

                                    if (edu.bulletLines.isNotEmpty()) {
                                        ExecutiveSlateBulletList(edu.bulletLines)
                                    }
                                }
                            }
                        }
                    }
                }
                hasRenderedSection = true
            }

            // --- Projects ---
            if (model.projectItems.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = if (hasRenderedSection) 18.dp else 0.dp)) {
                    ExecutiveSlateSectionHeader("PROJECTS")
                    for (proj in model.projectItems) {
                        PageBreakAwareBlock(isHeader = false) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                                    .height(IntrinsicSize.Min)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .fillMaxHeight()
                                        .background(SlateLightBar.copy(alpha = 0.7f), RoundedCornerShape(1.dp))
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 10.dp)
                                ) {
                                    if (proj.name.isNotBlank()) {
                                        Text(
                                            text = proj.name,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SlatePrimaryText,
                                            fontFamily = FontFamily.SansSerif,
                                            lineHeight = 14.5.sp
                                        )
                                    }

                                    if (proj.linksText.isNotBlank()) {
                                        Text(
                                            text = proj.linksText,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = SlateMutedText,
                                            fontFamily = FontFamily.SansSerif,
                                            modifier = Modifier.padding(top = 1.dp)
                                        )
                                    }

                                    if (proj.bulletLines.isNotEmpty()) {
                                        ExecutiveSlateBulletList(proj.bulletLines)
                                    }
                                }
                            }
                        }
                    }
                }
                hasRenderedSection = true
            }

            // --- Skills ---
            if (model.skills.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = if (hasRenderedSection) 18.dp else 0.dp)) {
                    ExecutiveSlateSectionHeader("SKILLS")
                    PageBreakAwareBlock(isHeader = false) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (skill in model.skills) {
                                Box(
                                    modifier = Modifier
                                        .background(SlateTagBg, RoundedCornerShape(4.dp))
                                        .border(1.dp, SlateTagBorder, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 9.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = skill,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SlateHeadingColor,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }
                            }
                        }
                    }
                }
                hasRenderedSection = true
            }

            // --- Certifications ---
            if (model.certificationItems.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = if (hasRenderedSection) 18.dp else 0.dp)) {
                    ExecutiveSlateSectionHeader("CERTIFICATIONS")
                    for (cert in model.certificationItems) {
                        PageBreakAwareBlock(isHeader = false) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .height(IntrinsicSize.Min)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .fillMaxHeight()
                                        .background(SlateLightBar.copy(alpha = 0.7f), RoundedCornerShape(1.dp))
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        if (cert.primaryTitle.isNotBlank()) {
                                            Text(
                                                text = cert.primaryTitle,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SlatePrimaryText,
                                                fontFamily = FontFamily.SansSerif,
                                                lineHeight = 14.sp,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(end = if (cert.dateText.isNotBlank()) 8.dp else 0.dp)
                                            )
                                        }
                                        if (cert.dateText.isNotBlank()) {
                                            Text(
                                                text = cert.dateText,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = SlateMutedText,
                                                fontFamily = FontFamily.SansSerif
                                            )
                                        }
                                    }
                                    val subLine = listOf(cert.issuerText, cert.linkText).filter { it.isNotBlank() }.joinToString(" | ")
                                    if (subLine.isNotBlank()) {
                                        Text(
                                            text = subLine,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = SlateMutedText,
                                            fontFamily = FontFamily.SansSerif,
                                            modifier = Modifier.padding(top = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                hasRenderedSection = true
            }

            // --- Achievements ---
            if (model.achievementItems.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = if (hasRenderedSection) 18.dp else 0.dp)) {
                    ExecutiveSlateSectionHeader("ACHIEVEMENTS")
                    for (ach in model.achievementItems) {
                        PageBreakAwareBlock(isHeader = false) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .height(IntrinsicSize.Min)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .fillMaxHeight()
                                        .background(SlateLightBar.copy(alpha = 0.7f), RoundedCornerShape(1.dp))
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        if (ach.primaryTitle.isNotBlank()) {
                                            Text(
                                                text = ach.primaryTitle,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SlatePrimaryText,
                                                fontFamily = FontFamily.SansSerif,
                                                lineHeight = 14.sp,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(end = if (ach.dateText.isNotBlank()) 8.dp else 0.dp)
                                            )
                                        }
                                        if (ach.dateText.isNotBlank()) {
                                            Text(
                                                text = ach.dateText,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = SlateMutedText,
                                                fontFamily = FontFamily.SansSerif
                                            )
                                        }
                                    }
                                    if (ach.bulletLines.isNotEmpty()) {
                                        ExecutiveSlateBulletList(ach.bulletLines)
                                    }
                                }
                            }
                        }
                    }
                }
                hasRenderedSection = true
            }

            // --- Extracurricular Activities ---
            if (model.extracurricularItems.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = if (hasRenderedSection) 18.dp else 0.dp)) {
                    ExecutiveSlateSectionHeader("EXTRACURRICULAR ACTIVITIES")
                    for (ext in model.extracurricularItems) {
                        PageBreakAwareBlock(isHeader = false) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .height(IntrinsicSize.Min)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .fillMaxHeight()
                                        .background(SlateLightBar.copy(alpha = 0.7f), RoundedCornerShape(1.dp))
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        if (ext.primaryTitle.isNotBlank()) {
                                            Text(
                                                text = ext.primaryTitle,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SlatePrimaryText,
                                                fontFamily = FontFamily.SansSerif,
                                                lineHeight = 14.sp,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(end = if (ext.dateText.isNotBlank()) 8.dp else 0.dp)
                                            )
                                        }
                                        if (ext.dateText.isNotBlank()) {
                                            Text(
                                                text = ext.dateText,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = SlateMutedText,
                                                fontFamily = FontFamily.SansSerif
                                            )
                                        }
                                    }
                                    if (ext.organizationText.isNotBlank()) {
                                        Text(
                                            text = ext.organizationText,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = SlateAccentBar,
                                            fontFamily = FontFamily.SansSerif,
                                            modifier = Modifier.padding(top = 1.dp)
                                        )
                                    }
                                    if (ext.bulletLines.isNotEmpty()) {
                                        ExecutiveSlateBulletList(ext.bulletLines)
                                    }
                                }
                            }
                        }
                    }
                }
                hasRenderedSection = true
            }

            // --- Languages ---
            if (model.languages != null) {
                Column(modifier = Modifier.padding(top = if (hasRenderedSection) 18.dp else 0.dp)) {
                    ExecutiveSlateSectionHeader("LANGUAGES")
                    PageBreakAwareBlock(isHeader = false) {
                        Text(
                            text = model.languages,
                            fontSize = 10.sp,
                            color = SlateSecondaryText,
                            fontFamily = FontFamily.SansSerif,
                            lineHeight = 14.5.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                        )
                    }
                }
                hasRenderedSection = true
            }

            // --- Declaration ---
            if (model.declaration != null) {
                Column(modifier = Modifier.padding(top = if (hasRenderedSection) 22.dp else 0.dp)) {
                    ExecutiveSlateSectionHeader("DECLARATION")
                    PageBreakAwareBlock(isHeader = false) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = model.declaration.statement,
                                fontSize = 10.sp,
                                color = SlateSecondaryText,
                                fontFamily = FontFamily.SansSerif,
                                lineHeight = 14.5.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            if (model.declaration.place.isNotBlank() ||
                                model.declaration.date.isNotBlank() ||
                                model.declaration.signatureName.isNotBlank()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        if (model.declaration.place.isNotBlank()) {
                                            Text(
                                                text = "Place: ${model.declaration.place}",
                                                fontSize = 9.5.sp,
                                                color = SlateMutedText,
                                                fontFamily = FontFamily.SansSerif
                                            )
                                        }
                                        if (model.declaration.date.isNotBlank()) {
                                            Text(
                                                text = "Date: ${model.declaration.date}",
                                                fontSize = 9.5.sp,
                                                color = SlateMutedText,
                                                fontFamily = FontFamily.SansSerif
                                            )
                                        }
                                    }
                                    if (model.declaration.signatureName.isNotBlank()) {
                                        Text(
                                            text = "(${model.declaration.signatureName})",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SlatePrimaryText,
                                            fontFamily = FontFamily.SansSerif,
                                            modifier = Modifier.align(Alignment.Bottom)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutiveSlateSectionHeader(title: String) {
    PageBreakAwareBlock(isHeader = true) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = SlateHeadingColor,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 1.6.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
            HorizontalDivider(
                color = SlateRuleColor,
                thickness = 1.dp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun ExecutiveSlateBulletList(lines: List<String>) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        for (line in lines) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Text(
                    text = "•",
                    fontSize = 10.sp,
                    color = SlateAccentBar,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = line,
                    fontSize = 10.sp,
                    color = SlateSecondaryText,
                    fontFamily = FontFamily.SansSerif,
                    lineHeight = 14.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
