package com.stutool.stutool.ui.screens.resume.templates

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stutool.stutool.ui.screens.resume.models.ResumeData

@Composable
fun PersonaTemplate(data: ResumeData) {
    val context = LocalContext.current
    var profileBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    
    // Load image safely for UI. Note: In PDF export context, this relies on LocalContext handling or fallback.
    LaunchedEffect(data.personal.profileImageUri) {
        if (data.personal.profileImageUri.isNotBlank()) {
            try {
                val uri = Uri.parse(data.personal.profileImageUri)
                val inputStream = context.contentResolver.openInputStream(uri)
                profileBitmap = BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
                inputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            profileBitmap = null
        }
    }

    val brandDark = Color(0xFF222026) // Dark header bar
    val bgLeft = Color(0xFFE5E5E5)    // Light gray background
    val primaryFont = FontFamily.SansSerif

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // Layout: Left column 35%, Right column 65%. FillMaxHeight ensures it spans the page.
        Row(modifier = Modifier.fillMaxSize()) {
            // -- LEFT COLUMN --
            Column(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxHeight()
                    .background(bgLeft)
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(180.dp)) // Space for the overlapping header

                if (data.careerObjective.isNotBlank()) {
                    PersonaSectionTitle("ABOUT ME")
                    Text(
                        text = data.careerObjective,
                        fontSize = 9.sp,
                        color = Color.Black.copy(alpha = 0.7f),
                        fontFamily = primaryFont,
                        lineHeight = 14.sp
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                }

                if (data.visibleSections.education && data.education.isNotEmpty()) {
                    PersonaSectionTitle("EDUCATION")
                    data.education.forEach { edu ->
                        Text(
                            text = listOf(edu.degree, edu.level).filter { it.isNotBlank() }.joinToString(" "),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            fontFamily = primaryFont
                        )
                        Text(
                            text = edu.institution,
                            fontSize = 9.sp,
                            color = Color.Black.copy(alpha = 0.7f),
                            fontFamily = primaryFont,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        if (edu.year.isNotBlank() || edu.marks.isNotBlank()) {
                            val dtinfo = listOf(edu.year, edu.marks).filter{it.isNotBlank()}.joinToString(" | ")
                            Text(
                                text = dtinfo,
                                fontSize = 8.sp,
                                color = Color.Black.copy(alpha = 0.6f),
                                fontFamily = primaryFont
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (data.visibleSections.skills && data.skills.isNotEmpty()) {
                    PersonaSectionTitle("SKILLS")
                    data.skills.forEach { skill ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                            Text(
                                text = skill,
                                fontSize = 9.sp,
                                color = Color.Black.copy(alpha = 0.8f),
                                modifier = Modifier.weight(1f)
                            )
                            // Hardcoded bar visual for premium look
                            Box(modifier = Modifier.weight(0.7f).height(4.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))) {
                                Box(modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight().background(Color.Black))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                if (data.personal.languages.isNotBlank()) {
                    PersonaSectionTitle("LANGUAGE")
                    val langs = data.personal.languages.split(",").map{it.trim()}.filter{it.isNotBlank()}
                    langs.forEach { lang ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                            Box(modifier = Modifier.size(3.dp).background(Color.Black, CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = lang, fontSize = 9.sp, color = Color.Black.copy(alpha = 0.8f))
                        }
                    }
                }
            }

            // -- RIGHT COLUMN --
            Column(
                modifier = Modifier
                    .weight(0.65f)
                    .fillMaxHeight()
                    .padding(horizontal = 32.dp)
            ) {
                Spacer(modifier = Modifier.height(180.dp))

                // Contact grid
                Column(modifier = Modifier.fillMaxWidth()) {
                    val cItems = listOf(
                        Pair("📞", if (data.personal.phoneNumber.isNotBlank()) "${data.personal.countryCode} ${data.personal.phoneNumber}".trim() else ""),
                        Pair("🌐", data.personal.links.firstOrNull()?.url ?: ""),
                        Pair("✉", data.personal.email),
                        Pair("📍", data.personal.location)
                    ).filter { it.second.isNotBlank() }

                    val chunks = cItems.chunked(2)
                    chunks.forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            rowItems.forEach { item ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier.size(16.dp).background(Color.Black, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = item.first, color = Color.White, fontSize = 8.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = item.second,
                                        fontSize = 8.sp,
                                        color = Color.Black.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(28.dp))

                if (data.visibleSections.experience && data.experience.isNotEmpty()) {
                    PersonaSectionTitle("EXPERIENCE")
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    data.experience.forEachIndexed { index, exp ->
                        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                            // Dot & Line Timeline
                            Box(modifier = Modifier.width(20.dp).fillMaxHeight()) {
                                Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 9.dp)) {
                                    if (index < data.experience.size - 1) {
                                        drawLine(
                                            color = Color.Black.copy(alpha = 0.5f),
                                            start = Offset(1.dp.toPx(), 4.dp.toPx()),
                                            end = Offset(1.dp.toPx(), size.height),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }
                                    
                                    // Empty circle dot
                                    drawCircle(
                                        color = Color.White,
                                        radius = 4.dp.toPx(),
                                        center = Offset(1.dp.toPx(), 6.dp.toPx())
                                    )
                                    drawCircle(
                                        color = Color.Black,
                                        radius = 4.dp.toPx(),
                                        center = Offset(1.dp.toPx(), 6.dp.toPx()),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f.dp.toPx())
                                    )
                                }
                            }
                            
                            // Experience Content
                            Column(modifier = Modifier.weight(1f).padding(bottom = 20.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        text = exp.position,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                    Text(
                                        text = "${exp.startDate} - ${exp.endDate}",
                                        fontSize = 9.sp,
                                        color = Color.Black.copy(alpha = 0.6f),
                                    )
                                }
                                Text(
                                    text = exp.company,
                                    fontSize = 10.sp,
                                    color = Color.Black.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                                )
                                if (exp.description.isNotBlank()) {
                                    // Bullet formatting
                                    val lines = exp.description.split("\n").filter { it.isNotBlank() }
                                    for (line in lines) {
                                        Text(
                                            text = "• " + line.trim().removePrefix("•").removePrefix("-").trim(),
                                            fontSize = 9.sp,
                                            color = Color.Black.copy(alpha = 0.6f),
                                            lineHeight = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                

            }
        }

        // -- HEADER BANNER (Top Overlay) --
        Box(modifier = Modifier.fillMaxWidth().height(160.dp).padding(top = 30.dp)) {
            // Dark Banner bridging over right column
            Box(modifier = Modifier.fillMaxSize().padding(start = 130.dp).background(brandDark)) {
                Column(modifier = Modifier.fillMaxSize().padding(start = 32.dp), verticalArrangement = Arrangement.Center) {
                    Text(
                        text = data.personal.name.uppercase(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 2.sp
                    )
                    val profession = data.experience.firstOrNull()?.position?.takeIf { it.isNotBlank() } ?: ""
                    if (profession.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = profession.uppercase(),
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            letterSpacing = 1.5.sp
                        )
                    }
                }
            }
            
            // Profile Circular Photo Intersecting Left
            Box(
                modifier = Modifier
                    .padding(start = 32.dp, top = 0.dp) // Aligned slightly from left edge
                    .size(130.dp)
                    .background(bgLeft, CircleShape) // Seamless blend
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(126.dp) // Outer stroke illusion
                        .background(Color.White, CircleShape)
                        .padding(8.dp) // Gap
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(0xFFCCCCCC)) // Placeholder background
                    ) {
                        if (profileBitmap != null) {
                            Image(
                                bitmap = profileBitmap!!,
                                contentDescription = "Profile",
                                contentScale = ContentScale.Crop, // Matches 1:1 perfectly
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            // Dummy placeholder if no photo is uploaded
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.fillMaxSize().padding(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonaSectionTitle(title: String) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            letterSpacing = 1.5.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.fillMaxWidth().height(1.dp),
            color = Color.Black.copy(alpha = 0.2f)
        )
    }
}
