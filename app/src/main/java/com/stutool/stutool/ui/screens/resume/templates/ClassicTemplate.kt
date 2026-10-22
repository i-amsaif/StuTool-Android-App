package com.stutool.stutool.ui.screens.resume.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stutool.stutool.ui.screens.resume.models.ResumeData
import com.stutool.stutool.ui.screens.resume.utils.PageBreakAwareBlock

/**
 * Classic Template:
 * - Formal, executive-style single column layout
 * - SERIF typography (Times-like) throughout
 * - Name centered with decorative horizontal rules
 * - Section headers: uppercase, bold, serif, centered with flanking lines
 * - Double-rule dividers for a distinguished print feel
 * - Dark navy accent color for headings
 */

private val ClassicNavy = Color(0xFF1B2A4A)
private val ClassicGray = Color(0xFF555555)
private val ClassicRuleColor = Color(0xFF333333)
private val ClassicLightRule = Color(0xFFAAAAAA)

@Composable
fun ClassicTemplate(data: ResumeData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 36.dp)
    ) {
        // --- Header: Name with decorative rules ---
        if (data.personal.name.isNotBlank()) {
            // Top decorative rule
            HorizontalDivider(
                color = ClassicRuleColor,
                thickness = 2.dp
            )
            Spacer(modifier = Modifier.height(2.dp))
            HorizontalDivider(
                color = ClassicRuleColor,
                thickness = 0.5.dp
            )

            Text(
                text = data.personal.name.uppercase(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = ClassicNavy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Serif,
                letterSpacing = 3.sp
            )

            // Bottom decorative rule
            HorizontalDivider(
                color = ClassicRuleColor,
                thickness = 0.5.dp
            )
            Spacer(modifier = Modifier.height(2.dp))
            HorizontalDivider(
                color = ClassicRuleColor,
                thickness = 2.dp
            )
        }

        // --- Contact info in a formal centered block ---
        val contactItems = mutableListOf<String>()
        data.personal.email.takeIf { it.isNotBlank() }?.let { contactItems.add(it) }
        val phoneStr = "${data.personal.countryCode} ${data.personal.phoneNumber}".trim()
        if (data.personal.phoneNumber.isNotBlank()) contactItems.add(phoneStr)
        data.personal.location.takeIf { it.isNotBlank() }?.let { contactItems.add(it) }
        data.personal.links.filter { it.url.isNotBlank() }.forEach { link ->
            contactItems.add(link.url.removePrefix("https://").removePrefix("http://").removePrefix("www."))
        }

        if (contactItems.isNotEmpty()) {
            Text(
                text = contactItems.joinToString("   ◆   "),
                fontSize = 9.sp,
                color = ClassicGray,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Serif,
                letterSpacing = 0.5.sp
            )
        }

        // --- Objective ---
        if (data.careerObjective.isNotBlank()) {
            Column(modifier = Modifier.padding(top = 22.dp)) {
                ClassicSectionHeader("OBJECTIVE")
                PageBreakAwareBlock {
                    Text(
                        text = data.careerObjective,
                        fontSize = 11.sp,
                        color = ClassicGray,
                        fontFamily = FontFamily.Serif,
                        lineHeight = 16.sp,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                        textAlign = TextAlign.Justify
                    )
                }
            }
        }

        // --- Professional Experience ---
        val validExperience = data.experience.filter { it.company.isNotBlank() || it.position.isNotBlank() }
        if (data.visibleSections.experience && validExperience.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 22.dp)) {
                ClassicSectionHeader("PROFESSIONAL EXPERIENCE")
                for (exp in validExperience) {
                    com.stutool.stutool.ui.screens.resume.utils.PageBreakAwareBlock { androidx.compose.foundation.layout.Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = exp.position,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ClassicNavy,
                                fontFamily = FontFamily.Serif
                            )
                            if (exp.company.isNotBlank()) {
                                Text(
                                    text = exp.company,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    fontStyle = FontStyle.Italic,
                                    color = ClassicGray,
                                    fontFamily = FontFamily.Serif
                                )
                            }
                        }
                        if (exp.startDate.isNotBlank() || exp.endDate.isNotBlank()) {
                            Text(
                                text = "${exp.startDate} — ${exp.endDate}",
                                fontSize = 10.sp,
                                color = ClassicGray,
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                    if (exp.description.isNotBlank()) {
                        ClassicBulletPoints(exp.description)
                    }
                    } }
                }
            }
        }

        // --- Education ---
        val validEducation = data.education.filter { it.institution.isNotBlank() || it.level.isNotBlank() }
        if (data.visibleSections.education && validEducation.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 22.dp)) {
                ClassicSectionHeader("EDUCATION")
                for (edu in validEducation) {
                    com.stutool.stutool.ui.screens.resume.utils.PageBreakAwareBlock { androidx.compose.foundation.layout.Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = edu.institution,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ClassicNavy,
                                fontFamily = FontFamily.Serif
                            )
                            val edInfo = listOf(edu.level, edu.degree, edu.board).filter { it.isNotBlank() }.joinToString(" | ")
                            if (edInfo.isNotBlank()) {
                                Text(
                                    text = edInfo,
                                    fontSize = 11.sp,
                                    fontStyle = FontStyle.Italic,
                                    color = ClassicGray,
                                    fontFamily = FontFamily.Serif
                                )
                            }
                            val marksInfo = listOf(edu.marksType, edu.marks).filter { it.isNotBlank() }.joinToString(": ")
                            if (marksInfo.isNotBlank()) {
                                Text(
                                    text = marksInfo,
                                    fontSize = 10.sp,
                                    color = ClassicGray,
                                    fontFamily = FontFamily.Serif
                                )
                            }
                        }
                        if (edu.year.isNotBlank()) {
                            Text(
                                text = edu.year,
                                fontSize = 10.sp,
                                color = ClassicGray,
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                    if (edu.description.isNotBlank()) {
                        ClassicBulletPoints(edu.description)
                    }
                    } }
                }
            }
        }

        // --- Skills ---
        val validSkills = data.skills.map { it.trim() }.filter { it.isNotBlank() }
        if (data.visibleSections.skills && validSkills.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 22.dp)) {
                ClassicSectionHeader("SKILLS")
                PageBreakAwareBlock {
                    Text(
                        text = validSkills.joinToString("   •   "),
                        fontSize = 11.sp,
                        color = Color.Black,
                        fontFamily = FontFamily.Serif,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        textAlign = TextAlign.Center,
                        letterSpacing = 0.3.sp
                    )
                }
            }
        }

        // --- Projects ---
        val validProjects = data.projects.filter { it.name.isNotBlank() || it.description.isNotBlank() }
        if (data.visibleSections.projects && validProjects.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 22.dp)) {
                ClassicSectionHeader("PROJECTS")
                for (proj in validProjects) {
                    com.stutool.stutool.ui.screens.resume.utils.PageBreakAwareBlock { androidx.compose.foundation.layout.Column {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ClassicNavy)) {
                                append(proj.name)
                            }
                            if (proj.links.isNotEmpty() && proj.links.first().isNotBlank()) {
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = ClassicGray)) {
                                    val linksString = proj.links.filter { it.isNotBlank() }.joinToString("  |  ")
                                    if (linksString.isNotEmpty()) {
                                        append("  —  $linksString")
                                    }
                                }
                            }
                        },
                        fontSize = 11.sp,
                        color = Color.Black,
                        fontFamily = FontFamily.Serif,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    if (proj.description.isNotBlank()) {
                        ClassicBulletPoints(proj.description)
                    }
                    } }
                }
            }
        }

        // --- Certifications ---
        val validCertifications = data.certifications.filter { it.name.isNotBlank() || it.issuer.isNotBlank() }
        if (data.visibleSections.certifications && validCertifications.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 22.dp)) {
                ClassicSectionHeader("CERTIFICATIONS")
                for (cert in validCertifications) {
                    PageBreakAwareBlock {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = buildAnnotatedString {
                                    val primary = cert.name.ifBlank { cert.issuer }
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ClassicNavy)) {
                                        append(primary)
                                    }
                                    if (cert.name.isNotBlank() && cert.issuer.isNotBlank()) {
                                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = ClassicGray)) {
                                            append("  —  ${cert.issuer}")
                                        }
                                    }
                                    val cleanLink = cert.link.trim().removePrefix("https://").removePrefix("http://").removePrefix("www.")
                                    if (cleanLink.isNotBlank()) {
                                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = ClassicGray, fontSize = 10.sp)) {
                                            append("  |  $cleanLink")
                                        }
                                    }
                                },
                                fontSize = 11.sp,
                                color = Color.Black,
                                fontFamily = FontFamily.Serif,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            if (cert.date.isNotBlank()) {
                                Text(
                                    text = cert.date,
                                    fontSize = 10.sp,
                                    color = ClassicGray,
                                    fontFamily = FontFamily.Serif,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Achievements ---
        val validAchievements = data.achievements.filter { it.title.isNotBlank() || it.description.isNotBlank() }
        if (data.visibleSections.achievements && validAchievements.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 22.dp)) {
                ClassicSectionHeader("ACHIEVEMENTS")
                for (ach in validAchievements) {
                    PageBreakAwareBlock {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            if (ach.title.isNotBlank() || ach.date.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (ach.title.isNotBlank()) {
                                        Text(
                                            text = ach.title,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ClassicNavy,
                                            fontFamily = FontFamily.Serif,
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                    if (ach.date.isNotBlank()) {
                                        Text(
                                            text = ach.date,
                                            fontSize = 10.sp,
                                            color = ClassicGray,
                                            fontFamily = FontFamily.Serif,
                                            fontStyle = FontStyle.Italic
                                        )
                                    }
                                }
                            }
                            if (ach.description.isNotBlank()) {
                                ClassicBulletPoints(ach.description)
                            }
                        }
                    }
                }
            }
        }

        // --- Extracurricular Activities ---
        val validExtracurriculars = data.extracurriculars.filter {
            it.title.isNotBlank() || it.organization.isNotBlank() || it.description.isNotBlank()
        }
        if (data.visibleSections.extracurriculars && validExtracurriculars.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 22.dp)) {
                ClassicSectionHeader("EXTRACURRICULAR ACTIVITIES")
                for (extra in validExtracurriculars) {
                    PageBreakAwareBlock {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            if (extra.title.isNotBlank() || extra.organization.isNotBlank() || extra.date.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val headingText = buildAnnotatedString {
                                        val primary = extra.title.ifBlank { extra.organization }
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ClassicNavy)) {
                                            append(primary)
                                        }
                                        if (extra.title.isNotBlank() && extra.organization.isNotBlank()) {
                                            withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = ClassicGray)) {
                                                append("  —  ${extra.organization}")
                                            }
                                        }
                                    }
                                    if (headingText.text.isNotBlank()) {
                                        Text(
                                            text = headingText,
                                            fontSize = 11.sp,
                                            color = Color.Black,
                                            fontFamily = FontFamily.Serif,
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                    if (extra.date.isNotBlank()) {
                                        Text(
                                            text = extra.date,
                                            fontSize = 10.sp,
                                            color = ClassicGray,
                                            fontFamily = FontFamily.Serif,
                                            fontStyle = FontStyle.Italic
                                        )
                                    }
                                }
                            }
                            if (extra.description.isNotBlank()) {
                                ClassicBulletPoints(extra.description)
                            }
                        }
                    }
                }
            }
        }

        // --- Languages ---
        if (data.personal.languages.isNotBlank()) {
            Column(modifier = Modifier.padding(top = 22.dp)) {
                ClassicSectionHeader("LANGUAGES")
                PageBreakAwareBlock {
                    Text(
                        text = data.personal.languages,
                        fontSize = 11.sp,
                        color = Color.Black,
                        fontFamily = FontFamily.Serif,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // --- Declaration ---
        if (data.visibleSections.declaration && data.declaration.enabled) {
            Column(modifier = Modifier.padding(top = 28.dp)) {
                ClassicSectionHeader("DECLARATION")
                PageBreakAwareBlock {
                    Column {
                        Text(
                            text = "I hereby declare that all the information provided is true and correct to the best of my knowledge.",
                            fontSize = 10.sp,
                            color = ClassicGray,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 8.dp),
                            textAlign = TextAlign.Justify
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                if (data.declaration.place.isNotBlank()) {
                                    Text("Place: ${data.declaration.place}", fontSize = 10.sp, color = ClassicGray, fontFamily = FontFamily.Serif)
                                }
                                if (data.declaration.date.isNotBlank()) {
                                    Text("Date: ${data.declaration.date}", fontSize = 10.sp, color = ClassicGray, fontFamily = FontFamily.Serif)
                                }
                            }
                            if (data.personal.name.isNotBlank()) {
                                Text(
                                    text = "(${data.personal.name})",
                                    fontSize = 10.sp,
                                    color = ClassicNavy,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif,
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

/**
 * Classic section header: centered text flanked by horizontal rules on both sides
 */
@Composable
private fun ClassicSectionHeader(title: String) {
    com.stutool.stutool.ui.screens.resume.utils.PageBreakAwareBlock(isHeader = true) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = ClassicLightRule,
            thickness = 0.5.dp
        )
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = ClassicNavy,
            fontFamily = FontFamily.Serif,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(horizontal = 12.dp),
            textAlign = TextAlign.Center
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = ClassicLightRule,
            thickness = 0.5.dp
        )
    }
    }
}

@Composable
private fun ClassicBulletPoints(description: String) {
    val lines = description.split("\n").filter { it.isNotBlank() }
    Column(modifier = Modifier.padding(top = 4.dp, start = 12.dp)) {
        for (line in lines) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "—",
                    fontSize = 10.sp,
                    color = ClassicNavy,
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier.padding(end = 6.dp, top = 1.dp)
                )
                val cleanLine = line.trim().removePrefix("•").removePrefix("-").trim()
                Text(
                    cleanLine,
                    fontSize = 10.sp,
                    color = ClassicGray,
                    lineHeight = 14.sp,
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
