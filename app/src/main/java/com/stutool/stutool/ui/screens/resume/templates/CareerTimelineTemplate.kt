package com.stutool.stutool.ui.screens.resume.templates

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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

private val TimelineTextPrimary = Color(0xFF0F172A)
private val TimelineTextBody = Color(0xFF334155)
private val TimelineTextMuted = Color(0xFF64748B)
private val TimelineAccentBlue = Color(0xFF1D4ED8)
private val TimelineConnectorBlue = Color(0xFFBFDBFE)
private val TimelineDividerColor = Color(0xFFE2E8F0)
private val TimelineChipBg = Color(0xFFEFF6FF)
private val TimelineChipBorder = Color(0xFFBFDBFE)

internal data class CareerTimelineHeaderModel(
    val name: String,
    val contactItems: List<String>,
    val linkItems: List<String>,
    val personalDetailItems: List<String>
) {
    val hasContent: Boolean
        get() = name.isNotBlank() ||
            contactItems.isNotEmpty() ||
            linkItems.isNotEmpty() ||
            personalDetailItems.isNotEmpty()
}

internal data class CareerTimelineExperienceItem(
    val id: String,
    val primaryTitle: String,
    val secondarySubtitle: String,
    val dateRange: String,
    val bulletLines: List<String>,
    val hasConnectorBelow: Boolean
)

internal data class CareerTimelineEducationItem(
    val id: String,
    val primaryTitle: String,
    val yearText: String,
    val qualificationLine: String,
    val marksLine: String,
    val bulletLines: List<String>,
    val hasConnectorBelow: Boolean
)

internal data class CareerTimelineProjectItem(
    val id: String,
    val name: String,
    val linksText: String,
    val bulletLines: List<String>,
    val hasConnectorBelow: Boolean
)

internal data class CareerTimelineCertificationItem(
    val id: String,
    val primaryTitle: String,
    val issuerText: String,
    val dateText: String,
    val linkText: String,
    val hasConnectorBelow: Boolean
)

internal data class CareerTimelineAchievementItem(
    val id: String,
    val primaryTitle: String,
    val dateText: String,
    val bulletLines: List<String>,
    val hasConnectorBelow: Boolean
)

internal data class CareerTimelineExtracurricularItem(
    val id: String,
    val primaryTitle: String,
    val organizationText: String,
    val dateText: String,
    val bulletLines: List<String>,
    val hasConnectorBelow: Boolean
)

internal data class CareerTimelineDeclarationModel(
    val statement: String,
    val place: String,
    val date: String,
    val signatureName: String
)

internal data class CareerTimelineRenderModel(
    val header: CareerTimelineHeaderModel,
    val careerObjective: String?,
    val experienceItems: List<CareerTimelineExperienceItem>,
    val educationItems: List<CareerTimelineEducationItem>,
    val projectItems: List<CareerTimelineProjectItem>,
    val skills: List<String>,
    val certificationItems: List<CareerTimelineCertificationItem>,
    val achievementItems: List<CareerTimelineAchievementItem>,
    val extracurricularItems: List<CareerTimelineExtracurricularItem>,
    val languages: String?,
    val declaration: CareerTimelineDeclarationModel?
)

internal fun extractCleanBullets(description: String): List<String> {
    return description.split("\n")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { line ->
            var clean = line
            while (clean.startsWith("•") || clean.startsWith("-") || clean.startsWith("*")) {
                clean = clean.substring(1).trim()
            }
            clean
        }
        .filter { it.isNotBlank() }
}

internal fun buildCareerTimelineModel(data: ResumeData): CareerTimelineRenderModel {
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

    val header = CareerTimelineHeaderModel(
        name = data.personal.name.trim(),
        contactItems = contactItems,
        linkItems = linkItems,
        personalDetailItems = personalDetailItems
    )

    val careerObjective = data.careerObjective.trim().takeIf { it.isNotBlank() }

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

    val experienceItems = validExperiences.mapIndexed { index, exp ->
        val pos = exp.position.trim()
        val comp = exp.company.trim()
        val primaryTitle = pos.ifBlank { comp }
        val secondarySubtitle = if (pos.isNotBlank()) comp else ""
        val dateRange = listOf(exp.startDate.trim(), exp.endDate.trim())
            .filter { it.isNotBlank() }
            .joinToString(" — ")

        CareerTimelineExperienceItem(
            id = exp.id,
            primaryTitle = primaryTitle,
            secondarySubtitle = secondarySubtitle,
            dateRange = dateRange,
            bulletLines = extractCleanBullets(exp.description),
            hasConnectorBelow = index < validExperiences.lastIndex
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

    val educationItems = validEducations.mapIndexed { index, edu ->
        val inst = edu.institution.trim()
        val deg = edu.degree.trim()
        val lvl = edu.level.trim()
        val brd = edu.board.trim()

        val qualParts = mutableListOf<String>()
        if (deg.isNotBlank()) qualParts.add(deg)
        if (lvl.isNotBlank() && !lvl.equals(deg, ignoreCase = true)) qualParts.add(lvl)
        if (brd.isNotBlank()) qualParts.add(brd)

        val primaryTitle = inst.ifBlank { qualParts.joinToString("  •  ") }
        val qualificationLine = if (inst.isNotBlank()) qualParts.joinToString("  •  ") else ""

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

        CareerTimelineEducationItem(
            id = edu.id,
            primaryTitle = primaryTitle,
            yearText = edu.year.trim(),
            qualificationLine = qualificationLine,
            marksLine = marksLine,
            bulletLines = extractCleanBullets(edu.description),
            hasConnectorBelow = index < validEducations.lastIndex
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

    val projectItems = validProjects.mapIndexed { index, proj ->
        val linksText = proj.links
            .map { it.trim().removePrefix("https://").removePrefix("http://").removePrefix("www.") }
            .filter { it.isNotBlank() }
            .joinToString("  •  ")

        CareerTimelineProjectItem(
            id = proj.id,
            name = proj.name.trim(),
            linksText = linksText,
            bulletLines = extractCleanBullets(proj.description),
            hasConnectorBelow = index < validProjects.lastIndex
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

    val certificationItems = validCertifications.mapIndexed { index, cert ->
        val name = cert.name.trim()
        val issuer = cert.issuer.trim()
        val primaryTitle = name.ifBlank { issuer }
        val issuerText = if (name.isNotBlank()) issuer else ""
        val cleanLink = cert.link.trim().removePrefix("https://").removePrefix("http://").removePrefix("www.")

        CareerTimelineCertificationItem(
            id = cert.id,
            primaryTitle = primaryTitle,
            issuerText = issuerText,
            dateText = cert.date.trim(),
            linkText = cleanLink,
            hasConnectorBelow = index < validCertifications.lastIndex
        )
    }

    val validAchievements: List<Achievement> = if (data.visibleSections.achievements) {
        data.achievements.filter { it.title.isNotBlank() || it.description.isNotBlank() }
    } else {
        emptyList()
    }

    val achievementItems = validAchievements.mapIndexed { index, ach ->
        CareerTimelineAchievementItem(
            id = ach.id,
            primaryTitle = ach.title.trim(),
            dateText = ach.date.trim(),
            bulletLines = extractCleanBullets(ach.description),
            hasConnectorBelow = index < validAchievements.lastIndex
        )
    }

    val validExtracurriculars: List<Extracurricular> = if (data.visibleSections.extracurriculars) {
        data.extracurriculars.filter {
            it.title.isNotBlank() || it.organization.isNotBlank() || it.description.isNotBlank()
        }
    } else {
        emptyList()
    }

    val extracurricularItems = validExtracurriculars.mapIndexed { index, extra ->
        val title = extra.title.trim()
        val org = extra.organization.trim()
        val primaryTitle = title.ifBlank { org }
        val organizationText = if (title.isNotBlank()) org else ""

        CareerTimelineExtracurricularItem(
            id = extra.id,
            primaryTitle = primaryTitle,
            organizationText = organizationText,
            dateText = extra.date.trim(),
            bulletLines = extractCleanBullets(extra.description),
            hasConnectorBelow = index < validExtracurriculars.lastIndex
        )
    }

    val languages = data.personal.languages.trim().takeIf { it.isNotBlank() }

    val declaration = if (data.visibleSections.declaration && data.declaration.enabled) {
        CareerTimelineDeclarationModel(
            statement = "I hereby declare that all the information provided is true and correct to the best of my knowledge.",
            place = data.declaration.place.trim(),
            date = data.declaration.date.trim(),
            signatureName = data.personal.name.trim()
        )
    } else {
        null
    }

    return CareerTimelineRenderModel(
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
fun CareerTimelineTemplate(data: ResumeData) {
    val model = buildCareerTimelineModel(data)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 36.dp, vertical = 32.dp)
    ) {
        // --- Header ---
        if (model.header.hasContent) {
            PageBreakAwareBlock(isHeader = false) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (model.header.name.isNotBlank()) {
                        Text(
                            text = model.header.name,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TimelineTextPrimary,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 0.3.sp
                        )
                    }

                    if (model.header.contactItems.isNotEmpty()) {
                        Text(
                            text = model.header.contactItems.joinToString("  •  "),
                            fontSize = 10.sp,
                            color = TimelineTextBody,
                            fontFamily = FontFamily.SansSerif,
                            lineHeight = 14.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (model.header.linkItems.isNotEmpty()) {
                        Text(
                            text = model.header.linkItems.joinToString("  •  "),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = TimelineAccentBlue,
                            fontFamily = FontFamily.SansSerif,
                            lineHeight = 13.5.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    if (model.header.personalDetailItems.isNotEmpty()) {
                        Text(
                            text = model.header.personalDetailItems.joinToString("  •  "),
                            fontSize = 9.5.sp,
                            color = TimelineTextMuted,
                            fontFamily = FontFamily.SansSerif,
                            lineHeight = 13.5.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    HorizontalDivider(
                        color = TimelineDividerColor,
                        thickness = 1.dp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }

        // --- Career Objective ---
        if (model.careerObjective != null) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                CareerTimelineSectionHeader("CAREER OBJECTIVE")
                PageBreakAwareBlock(isHeader = false) {
                    Text(
                        text = model.careerObjective,
                        fontSize = 10.sp,
                        color = TimelineTextBody,
                        fontFamily = FontFamily.SansSerif,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        // --- Experience Timeline ---
        if (model.experienceItems.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 18.dp)) {
                CareerTimelineSectionHeader("EXPERIENCE")
                Spacer(modifier = Modifier.height(8.dp))

                for (exp in model.experienceItems) {
                    PageBreakAwareBlock(isHeader = false) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                        ) {
                            // Left rail with solid milestone node and connector segment
                            Box(
                                modifier = Modifier
                                    .width(22.dp)
                                    .fillMaxHeight()
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val centerX = 6.dp.toPx()
                                    val centerY = 7.dp.toPx()

                                    if (exp.hasConnectorBelow) {
                                        drawLine(
                                            color = TimelineConnectorBlue,
                                            start = Offset(centerX, centerY),
                                            end = Offset(centerX, size.height),
                                            strokeWidth = 1.5.dp.toPx()
                                        )
                                    }

                                    // Soft outer halo + solid deep-blue milestone dot
                                    drawCircle(
                                        color = TimelineChipBg,
                                        radius = 5.5.dp.toPx(),
                                        center = Offset(centerX, centerY)
                                    )
                                    drawCircle(
                                        color = TimelineAccentBlue,
                                        radius = 3.5.dp.toPx(),
                                        center = Offset(centerX, centerY)
                                    )
                                }
                            }

                            // Experience entry content
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(bottom = if (exp.hasConnectorBelow) 14.dp else 2.dp)
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
                                                color = TimelineTextPrimary,
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
                                                color = TimelineAccentBlue,
                                                fontFamily = FontFamily.SansSerif
                                            )
                                        }
                                    }
                                }

                                if (exp.secondarySubtitle.isNotBlank()) {
                                    Text(
                                        text = exp.secondarySubtitle,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TimelineTextMuted,
                                        fontFamily = FontFamily.SansSerif,
                                        modifier = Modifier.padding(top = 1.dp)
                                    )
                                }

                                if (exp.bulletLines.isNotEmpty()) {
                                    CareerTimelineBulletList(exp.bulletLines)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Education Timeline ---
        if (model.educationItems.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 18.dp)) {
                CareerTimelineSectionHeader("EDUCATION")
                Spacer(modifier = Modifier.height(8.dp))

                for (edu in model.educationItems) {
                    PageBreakAwareBlock(isHeader = false) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                        ) {
                            // Visually lighter timeline rail (hollow ring node + thin connector)
                            Box(
                                modifier = Modifier
                                    .width(22.dp)
                                    .fillMaxHeight()
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val centerX = 6.dp.toPx()
                                    val centerY = 7.dp.toPx()

                                    if (edu.hasConnectorBelow) {
                                        drawLine(
                                            color = TimelineConnectorBlue,
                                            start = Offset(centerX, centerY),
                                            end = Offset(centerX, size.height),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }

                                    drawCircle(
                                        color = Color.White,
                                        radius = 3.5.dp.toPx(),
                                        center = Offset(centerX, centerY)
                                    )
                                    drawCircle(
                                        color = TimelineAccentBlue,
                                        radius = 3.5.dp.toPx(),
                                        center = Offset(centerX, centerY),
                                        style = Stroke(width = 1.5.dp.toPx())
                                    )
                                }
                            }

                            // Education entry content
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(bottom = if (edu.hasConnectorBelow) 12.dp else 2.dp)
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
                                                color = TimelineTextPrimary,
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
                                                color = TimelineAccentBlue,
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
                                        color = TimelineTextBody,
                                        fontFamily = FontFamily.SansSerif,
                                        modifier = Modifier.padding(top = 1.dp)
                                    )
                                }

                                if (edu.marksLine.isNotBlank()) {
                                    Text(
                                        text = edu.marksLine,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TimelineTextMuted,
                                        fontFamily = FontFamily.SansSerif,
                                        modifier = Modifier.padding(top = 1.dp)
                                    )
                                }

                                if (edu.bulletLines.isNotEmpty()) {
                                    CareerTimelineBulletList(edu.bulletLines)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Projects ---
        if (model.projectItems.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 18.dp)) {
                CareerTimelineSectionHeader("PROJECTS")
                Spacer(modifier = Modifier.height(8.dp))

                for (proj in model.projectItems) {
                    PageBreakAwareBlock(isHeader = false) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                        ) {
                            // Subtle timeline motif aligned with the left rail
                            Box(
                                modifier = Modifier
                                    .width(22.dp)
                                    .fillMaxHeight()
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val centerX = 6.dp.toPx()
                                    val centerY = 7.dp.toPx()

                                    if (proj.hasConnectorBelow) {
                                        drawLine(
                                            color = TimelineConnectorBlue.copy(alpha = 0.7f),
                                            start = Offset(centerX, centerY),
                                            end = Offset(centerX, size.height),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }

                                    drawCircle(
                                        color = TimelineAccentBlue,
                                        radius = 2.5.dp.toPx(),
                                        center = Offset(centerX, centerY)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(bottom = if (proj.hasConnectorBelow) 12.dp else 2.dp)
                            ) {
                                if (proj.name.isNotBlank()) {
                                    Text(
                                        text = proj.name,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TimelineTextPrimary,
                                        fontFamily = FontFamily.SansSerif,
                                        lineHeight = 14.5.sp
                                    )
                                }

                                if (proj.linksText.isNotBlank()) {
                                    Text(
                                        text = proj.linksText,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TimelineAccentBlue,
                                        fontFamily = FontFamily.SansSerif,
                                        modifier = Modifier.padding(top = 1.dp)
                                    )
                                }

                                if (proj.bulletLines.isNotEmpty()) {
                                    CareerTimelineBulletList(proj.bulletLines)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Skills ---
        if (model.skills.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 18.dp)) {
                CareerTimelineSectionHeader("SKILLS")
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
                                    .background(TimelineChipBg, RoundedCornerShape(6.dp))
                                    .border(1.dp, TimelineChipBorder, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = skill,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TimelineAccentBlue,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Certifications ---
        if (model.certificationItems.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 18.dp)) {
                CareerTimelineSectionHeader("CERTIFICATIONS")
                Spacer(modifier = Modifier.height(8.dp))

                for (cert in model.certificationItems) {
                    PageBreakAwareBlock(isHeader = false) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(22.dp)
                                    .fillMaxHeight()
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val centerX = 6.dp.toPx()
                                    val centerY = 7.dp.toPx()

                                    if (cert.hasConnectorBelow) {
                                        drawLine(
                                            color = TimelineConnectorBlue.copy(alpha = 0.7f),
                                            start = Offset(centerX, centerY),
                                            end = Offset(centerX, size.height),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }

                                    drawCircle(
                                        color = TimelineAccentBlue,
                                        radius = 2.5.dp.toPx(),
                                        center = Offset(centerX, centerY)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(bottom = if (cert.hasConnectorBelow) 10.dp else 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = cert.primaryTitle,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TimelineTextPrimary,
                                        fontFamily = FontFamily.SansSerif,
                                        lineHeight = 14.5.sp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                    )
                                    if (cert.dateText.isNotBlank()) {
                                        Text(
                                            text = cert.dateText,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TimelineAccentBlue,
                                            fontFamily = FontFamily.SansSerif
                                        )
                                    }
                                }

                                val subMeta = listOf(cert.issuerText, cert.linkText)
                                    .filter { it.isNotBlank() }
                                    .joinToString("  •  ")
                                if (subMeta.isNotBlank()) {
                                    Text(
                                        text = subMeta,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TimelineTextMuted,
                                        fontFamily = FontFamily.SansSerif,
                                        modifier = Modifier.padding(top = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Achievements ---
        if (model.achievementItems.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 18.dp)) {
                CareerTimelineSectionHeader("ACHIEVEMENTS")
                Spacer(modifier = Modifier.height(8.dp))

                for (ach in model.achievementItems) {
                    PageBreakAwareBlock(isHeader = false) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(22.dp)
                                    .fillMaxHeight()
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val centerX = 6.dp.toPx()
                                    val centerY = 7.dp.toPx()

                                    if (ach.hasConnectorBelow) {
                                        drawLine(
                                            color = TimelineConnectorBlue.copy(alpha = 0.7f),
                                            start = Offset(centerX, centerY),
                                            end = Offset(centerX, size.height),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }

                                    drawCircle(
                                        color = TimelineAccentBlue,
                                        radius = 2.5.dp.toPx(),
                                        center = Offset(centerX, centerY)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(bottom = if (ach.hasConnectorBelow) 10.dp else 2.dp)
                            ) {
                                if (ach.primaryTitle.isNotBlank() || ach.dateText.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        if (ach.primaryTitle.isNotBlank()) {
                                            Text(
                                                text = ach.primaryTitle,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TimelineTextPrimary,
                                                fontFamily = FontFamily.SansSerif,
                                                lineHeight = 14.5.sp,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(end = 8.dp)
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }

                                        if (ach.dateText.isNotBlank()) {
                                            Text(
                                                text = ach.dateText,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TimelineAccentBlue,
                                                fontFamily = FontFamily.SansSerif
                                            )
                                        }
                                    }
                                }

                                if (ach.bulletLines.isNotEmpty()) {
                                    CareerTimelineBulletList(ach.bulletLines)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Extracurricular Activities ---
        if (model.extracurricularItems.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 18.dp)) {
                CareerTimelineSectionHeader("EXTRACURRICULAR ACTIVITIES")
                Spacer(modifier = Modifier.height(8.dp))

                for (extra in model.extracurricularItems) {
                    PageBreakAwareBlock(isHeader = false) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(22.dp)
                                    .fillMaxHeight()
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val centerX = 6.dp.toPx()
                                    val centerY = 7.dp.toPx()

                                    if (extra.hasConnectorBelow) {
                                        drawLine(
                                            color = TimelineConnectorBlue.copy(alpha = 0.7f),
                                            start = Offset(centerX, centerY),
                                            end = Offset(centerX, size.height),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }

                                    drawCircle(
                                        color = TimelineAccentBlue,
                                        radius = 2.5.dp.toPx(),
                                        center = Offset(centerX, centerY)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(bottom = if (extra.hasConnectorBelow) 10.dp else 2.dp)
                            ) {
                                if (extra.primaryTitle.isNotBlank() || extra.dateText.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        if (extra.primaryTitle.isNotBlank()) {
                                            Text(
                                                text = extra.primaryTitle,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TimelineTextPrimary,
                                                fontFamily = FontFamily.SansSerif,
                                                lineHeight = 14.5.sp,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(end = 8.dp)
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }

                                        if (extra.dateText.isNotBlank()) {
                                            Text(
                                                text = extra.dateText,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TimelineAccentBlue,
                                                fontFamily = FontFamily.SansSerif
                                            )
                                        }
                                    }
                                }

                                if (extra.organizationText.isNotBlank()) {
                                    Text(
                                        text = extra.organizationText,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TimelineTextMuted,
                                        fontFamily = FontFamily.SansSerif,
                                        modifier = Modifier.padding(top = 1.dp)
                                    )
                                }

                                if (extra.bulletLines.isNotEmpty()) {
                                    CareerTimelineBulletList(extra.bulletLines)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Languages ---
        if (model.languages != null) {
            Column(modifier = Modifier.padding(top = 18.dp)) {
                CareerTimelineSectionHeader("LANGUAGES")
                PageBreakAwareBlock(isHeader = false) {
                    Text(
                        text = model.languages,
                        fontSize = 10.sp,
                        color = TimelineTextBody,
                        fontFamily = FontFamily.SansSerif,
                        lineHeight = 14.5.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    )
                }
            }
        }

        // --- Declaration ---
        if (model.declaration != null) {
            Column(modifier = Modifier.padding(top = 22.dp)) {
                CareerTimelineSectionHeader("DECLARATION")
                PageBreakAwareBlock(isHeader = false) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = model.declaration.statement,
                            fontSize = 10.sp,
                            color = TimelineTextBody,
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
                                            color = TimelineTextMuted,
                                            fontFamily = FontFamily.SansSerif
                                        )
                                    }
                                    if (model.declaration.date.isNotBlank()) {
                                        Text(
                                            text = "Date: ${model.declaration.date}",
                                            fontSize = 9.5.sp,
                                            color = TimelineTextMuted,
                                            fontFamily = FontFamily.SansSerif
                                        )
                                    }
                                }
                                if (model.declaration.signatureName.isNotBlank()) {
                                    Text(
                                        text = "(${model.declaration.signatureName})",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TimelineTextPrimary,
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

@Composable
private fun CareerTimelineSectionHeader(title: String) {
    PageBreakAwareBlock(isHeader = true) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(13.dp)
                        .background(TimelineAccentBlue, RoundedCornerShape(1.5.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TimelineAccentBlue,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 1.5.sp
                )
            }
            HorizontalDivider(
                color = TimelineDividerColor,
                thickness = 1.dp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun CareerTimelineBulletList(lines: List<String>) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        for (line in lines) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Text(
                    text = "•",
                    fontSize = 10.sp,
                    color = TimelineAccentBlue,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = line,
                    fontSize = 10.sp,
                    color = TimelineTextBody,
                    fontFamily = FontFamily.SansSerif,
                    lineHeight = 14.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
