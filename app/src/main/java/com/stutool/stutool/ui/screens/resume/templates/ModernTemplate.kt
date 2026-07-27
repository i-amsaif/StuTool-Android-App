package com.stutool.stutool.ui.screens.resume.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stutool.stutool.ui.screens.resume.models.ResumeData

private val SidebarBg = Color(0xFFF0F0F0) 
private val AccentBlue = Color(0xFF2563EB) 
private val AccentRedDot = Color(0xFFE11D48) 
private val AccentBlueDot = Color(0xFF6366F1) 

@Composable
fun ModernTemplate(data: ResumeData) {
    Row(
        modifier = Modifier.fillMaxSize()
    ) {
        // ===== LEFT SIDEBAR =====
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.32f)
                .background(SidebarBg)
                .padding(horizontal = 16.dp, vertical = 28.dp)
        ) {
            // Name
            if (data.personal.name.isNotBlank()) {
                Text(
                    text = data.personal.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black,
                    fontFamily = FontFamily.SansSerif,
                    lineHeight = 22.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(3.dp)
                        .background(AccentBlue, RoundedCornerShape(2.dp))
                )
            }

            // --- Contact Section ---
            val contactItems = mutableListOf<String>()
            data.personal.email.takeIf { it.isNotBlank() }?.let { contactItems.add(it) }
            val phoneStr = "${data.personal.countryCode} ${data.personal.phoneNumber}".trim()
            if (data.personal.phoneNumber.isNotBlank()) contactItems.add("Ph: $phoneStr")
            data.personal.links.filter { it.url.isNotBlank() }.forEach { link ->
                contactItems.add(link.url.removePrefix("https://").removePrefix("http://").removePrefix("www."))
            }

            if (contactItems.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = 20.dp)) {
                    Text(
                        text = "CONTACT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    for (item in contactItems) {
                        Text(
                            text = item,
                            fontSize = 8.sp,
                            color = Color.DarkGray,
                            fontFamily = FontFamily.SansSerif,
                            lineHeight = 12.sp,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
            }

            // --- Objective Section (in sidebar per rules) ---
            if (data.careerObjective.isNotBlank()) {
                Column(modifier = Modifier.padding(top = 20.dp)) {
                    Text(
                        text = "OBJECTIVE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = data.careerObjective,
                        fontSize = 8.sp,
                        color = Color.DarkGray,
                        fontFamily = FontFamily.SansSerif,
                        lineHeight = 12.sp
                    )
                }
            }

            // --- Skills Section (in sidebar) ---
            if (data.visibleSections.skills && data.skills.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = 20.dp)) {
                    Text(
                        text = "SKILLS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    for (skill in data.skills) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(AccentBlue, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = skill,
                                fontSize = 8.sp,
                                color = Color.DarkGray,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }
            }
        }

        // ===== RIGHT MAIN COLUMN =====
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.68f)
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 28.dp)
        ) {

            // --- Experience Section ---
            val validExperience = data.experience.filter { it.company.isNotBlank() || it.position.isNotBlank() }
            if (data.visibleSections.experience && validExperience.isNotEmpty()) {
                ModernSectionHeader("WORK EXPERIENCE")
                for (exp in validExperience) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(AccentRedDot, RoundedCornerShape(1.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = exp.position,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                        
                        val detailsRow = buildString {
                            if (exp.company.isNotBlank()) append(exp.company)
                            if (exp.startDate.isNotBlank() || exp.endDate.isNotBlank()) {
                                if (isNotEmpty()) append("  |  ")
                                append("${exp.startDate} — ${exp.endDate}")
                            }
                        }
                        if (detailsRow.isNotBlank()) {
                            Text(
                                text = detailsRow,
                                fontSize = 10.sp,
                                color = AccentBlue,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier.padding(start = 14.dp, top = 2.dp)
                            )
                        }

                        if (exp.description.isNotBlank()) {
                            ModernBulletPoints(exp.description)
                        }
                    }
                }
            }

            // --- Education Section ---
            val validEducation = data.education.filter { it.institution.isNotBlank() || it.level.isNotBlank() }
            val eduPad = if (data.visibleSections.experience && validExperience.isNotEmpty()) 24.dp else 0.dp
            if (data.visibleSections.education && validEducation.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = eduPad)) {
                    ModernSectionHeader("EDUCATION")
                    for (edu in validEducation) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(AccentBlueDot)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = edu.institution,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                            
                            val eduRow = buildString {
                                val edInfo = listOf(edu.level, edu.degree, edu.board).filter { it.isNotBlank() }.joinToString(" | ")
                                if (edInfo.isNotBlank()) append(edInfo)
                                if (edu.year.isNotBlank()) {
                                    if (isNotEmpty()) append("  |  ")
                                    append(edu.year)
                                }
                            }
                            if (eduRow.isNotBlank()) {
                                Text(
                                    text = eduRow,
                                    fontSize = 10.sp,
                                    color = Color.DarkGray,
                                    fontFamily = FontFamily.SansSerif,
                                    fontStyle = FontStyle.Italic,
                                    modifier = Modifier.padding(start = 14.dp, top = 2.dp)
                                )
                            }
                            
                            val marksInfo = listOf(edu.marksType, edu.marks).filter { it.isNotBlank() }.joinToString(": ")
                            if (marksInfo.isNotBlank()) {
                                Text(
                                    text = marksInfo,
                                    fontSize = 10.sp,
                                    color = Color.DarkGray,
                                    fontFamily = FontFamily.SansSerif,
                                    modifier = Modifier.padding(start = 14.dp, top = 2.dp)
                                )
                            }

                            if (edu.description.isNotBlank()) {
                                ModernBulletPoints(edu.description)
                            }
                        }
                    }
                }
            }

            // --- Projects Section ---
            val validProjects = data.projects.filter { it.name.isNotBlank() || it.description.isNotBlank() }
            val projPad = if ((data.visibleSections.experience && validExperience.isNotEmpty()) || (data.visibleSections.education && validEducation.isNotEmpty())) 24.dp else 0.dp
            if (data.visibleSections.projects && validProjects.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = projPad)) {
                    ModernSectionHeader("PROJECTS")
                    for (proj in validProjects) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(1.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = proj.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                            if (proj.link.isNotBlank()) {
                                Text(
                                    text = proj.link,
                                    fontSize = 10.sp,
                                    color = AccentBlue,
                                    fontFamily = FontFamily.SansSerif,
                                    modifier = Modifier.padding(start = 14.dp, top = 2.dp)
                                )
                            }
                            if (proj.description.isNotBlank()) {
                                ModernBulletPoints(proj.description)
                            }
                        }
                    }
                }
            }

            // --- Languages Section (in Right Column per rules) ---
            val hasPrev = (data.visibleSections.experience && validExperience.isNotEmpty()) || 
                          (data.visibleSections.education && validEducation.isNotEmpty()) || 
                          (data.visibleSections.projects && validProjects.isNotEmpty())
            val langPad = if (hasPrev) 24.dp else 0.dp
            if (data.personal.languages.isNotBlank()) {
                Column(modifier = Modifier.padding(top = langPad)) {
                    ModernSectionHeader("LANGUAGES")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        Text(
                            text = data.personal.languages,
                            fontSize = 11.sp,
                            color = Color.Black,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }

            // --- Declaration Section ---
            if (data.visibleSections.declaration && data.declaration.enabled) {
                val decPad = if (hasPrev || data.personal.languages.isNotBlank()) 24.dp else 0.dp
                Column(modifier = Modifier.padding(top = decPad)) {
                    ModernSectionHeader("DECLARATION")
                    Text(
                        text = "I hereby declare that all the information provided is true and correct to the best of my knowledge.",
                        fontSize = 10.sp,
                        color = Color.DarkGray,
                        fontFamily = FontFamily.SansSerif,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            if (data.declaration.place.isNotBlank()) {
                                Text("Place: ${data.declaration.place}", fontSize = 9.sp, color = Color.DarkGray, fontFamily = FontFamily.SansSerif)
                            }
                            if (data.declaration.date.isNotBlank()) {
                                Text("Date: ${data.declaration.date}", fontSize = 9.sp, color = Color.DarkGray, fontFamily = FontFamily.SansSerif)
                            }
                        }
                        if (data.personal.name.isNotBlank()) {
                            Text(
                                text = "(${data.personal.name})",
                                fontSize = 10.sp,
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
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

@Composable
private fun ModernSectionHeader(title: String) {
    Column {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.Black,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 1.sp
        )
        HorizontalDivider(
            color = Color.LightGray,
            thickness = 1.dp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ModernBulletPoints(description: String) {
    val lines = description.split("\n").filter { it.isNotBlank() }
    Column(modifier = Modifier.padding(top = 4.dp, start = 14.dp)) {
        for (line in lines) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "•",
                    fontSize = 10.sp,
                    color = Color.DarkGray,
                    modifier = Modifier.padding(end = 6.dp)
                )
                val cleanLine = line.trim().removePrefix("•").removePrefix("-").trim()
                Text(
                    cleanLine,
                    fontSize = 10.sp,
                    color = Color.DarkGray,
                    lineHeight = 14.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
