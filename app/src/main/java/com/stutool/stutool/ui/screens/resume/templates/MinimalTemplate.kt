package com.stutool.stutool.ui.screens.resume.templates

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


@Composable
fun MinimalTemplate(data: ResumeData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(all = 40.dp)
    ) {
        // --- Header: Name ---
        // If blank, do not render a placeholder. Name is usually required but we can just skip if empty to avoid ghost text.
        if (data.personal.name.isNotBlank()) {
            Text(
                text = data.personal.name,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.SansSerif
            )
        }

        // --- Contact info ---
        val contactItems = mutableListOf<String>()
        data.personal.email.takeIf { it.isNotBlank() }?.let { contactItems.add(it) }
        val phoneStr = "${data.personal.countryCode} ${data.personal.phoneNumber}".trim()
        if (data.personal.phoneNumber.isNotBlank()) contactItems.add(phoneStr)
        data.personal.location.takeIf { it.isNotBlank() }?.let { contactItems.add(it) }
        data.personal.dob.takeIf { it.isNotBlank() }?.let { contactItems.add(it) }
        data.personal.links.filter { it.url.isNotBlank() }.forEach { link ->
            contactItems.add(link.url.removePrefix("https://").removePrefix("http://").removePrefix("www."))
        }

        if (contactItems.isNotEmpty()) {
            Text(
                text = contactItems.joinToString("  •  "),
                fontSize = 10.sp,
                color = Color.DarkGray,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.SansSerif
            )
        }

        // --- Objective ---
        if (data.careerObjective.isNotBlank()) {
            Column(modifier = Modifier.padding(top = 20.dp)) {
                TemplateSectionHeader("OBJECTIVE")
                Text(
                    text = data.careerObjective,
                    fontSize = 10.sp,
                    color = Color.DarkGray,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // --- Experience ---
        val validExperience = data.experience.filter { it.company.isNotBlank() || it.position.isNotBlank() }
        if (data.visibleSections.experience && validExperience.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 20.dp)) {
                TemplateSectionHeader("EXPERIENCE")
                for (exp in validExperience) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append(exp.position)
                                }
                                if (exp.company.isNotBlank()) {
                                    append(" at ")
                                    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                                        append(exp.company)
                                    }
                                }
                            },
                            fontSize = 11.sp,
                            color = Color.Black,
                            modifier = Modifier.weight(1f)
                        )
                        if (exp.startDate.isNotBlank() || exp.endDate.isNotBlank()) {
                            Text(
                                text = "${exp.startDate} — ${exp.endDate}",
                                fontSize = 10.sp,
                                color = Color.DarkGray
                            )
                        }
                    }
                    if (exp.description.isNotBlank()) {
                        TemplateBulletPoints(exp.description)
                    }
                }
            }
        }

        // --- Education ---
        val validEducation = data.education.filter { it.institution.isNotBlank() || it.level.isNotBlank() }
        if (data.visibleSections.education && validEducation.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 20.dp)) {
                TemplateSectionHeader("EDUCATION")
                for (edu in validEducation) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append(edu.institution)
                                }
                            },
                            fontSize = 11.sp,
                            color = Color.Black,
                            modifier = Modifier.weight(1f)
                        )
                        if (edu.year.isNotBlank()) {
                            Text(
                                text = edu.year,
                                fontSize = 10.sp,
                                color = Color.DarkGray
                            )
                        }
                    }
                    val edInfo = listOf(edu.level, edu.degree, edu.board).filter { it.isNotBlank() }.joinToString(" | ")
                    if (edInfo.isNotBlank()) {
                        Text(
                            text = edInfo,
                            fontSize = 11.sp,
                            color = Color.DarkGray,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    val marksInfo = listOf(edu.marksType, edu.marks).filter { it.isNotBlank() }.joinToString(": ")
                    if (marksInfo.isNotBlank()) {
                        Text(
                            text = marksInfo,
                            fontSize = 10.sp,
                            color = Color.DarkGray,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    if (edu.description.isNotBlank()) {
                        TemplateBulletPoints(edu.description)
                    }
                }
            }
        }

        // --- Projects ---
        val validProjects = data.projects.filter { it.name.isNotBlank() || it.description.isNotBlank() }
        if (data.visibleSections.projects && validProjects.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 20.dp)) {
                TemplateSectionHeader("PROJECTS")
                for (proj in validProjects) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append(proj.name)
                                }
                                if (proj.link.isNotBlank()) {
                                    append(" | ${proj.link}")
                                }
                            },
                            fontSize = 11.sp,
                            color = Color.Black
                        )
                    }
                    if (proj.description.isNotBlank()) {
                        TemplateBulletPoints(proj.description)
                    }
                }
            }
        }

        // --- Skills ---
        if (data.visibleSections.skills && data.skills.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 20.dp)) {
                TemplateSectionHeader("SKILLS")
                Text(
                    text = data.skills.joinToString("  •  "),
                    fontSize = 11.sp,
                    color = Color.Black,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
            }
        }

        // --- Languages ---
        if (data.personal.languages.isNotBlank()) {
            Column(modifier = Modifier.padding(top = 20.dp)) {
                TemplateSectionHeader("LANGUAGES")
                Text(
                    text = data.personal.languages,
                    fontSize = 11.sp,
                    color = Color.Black,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
            }
        }

        // --- Declaration ---
        if (data.visibleSections.declaration && data.declaration.enabled) {
            Column(modifier = Modifier.padding(top = 30.dp)) {
                TemplateSectionHeader("DECLARATION")
                Text(
                    text = "I hereby declare that all the information provided is true and correct to the best of my knowledge.",
                    fontSize = 10.sp,
                    color = Color.DarkGray,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        if (data.declaration.place.isNotBlank()) {
                            Text("Place: ${data.declaration.place}", fontSize = 10.sp, color = Color.DarkGray)
                        }
                        if (data.declaration.date.isNotBlank()) {
                            Text("Date: ${data.declaration.date}", fontSize = 10.sp, color = Color.DarkGray)
                        }
                    }
                    if (data.personal.name.isNotBlank()) {
                        Text(
                            text = "(${data.personal.name})",
                            fontSize = 10.sp,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Bottom)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TemplateSectionHeader(title: String) {
    Column {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        HorizontalDivider(
            color = Color.LightGray,
            thickness = 1.dp,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
        )
    }
}

@Composable
fun TemplateBulletPoints(description: String) {
    val lines = description.split("\n").filter { it.isNotBlank() }
    Column(modifier = Modifier.padding(top = 4.dp, start = 8.dp)) {
        for (line in lines) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "•",
                    fontSize = 10.sp,
                    color = Color.Black,
                    modifier = Modifier.padding(end = 6.dp, top = 1.dp)
                )
                val cleanLine = line.trim().removePrefix("•").removePrefix("-").trim()
                Text(
                    cleanLine,
                    fontSize = 10.sp,
                    color = Color.DarkGray,
                    lineHeight = 14.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
