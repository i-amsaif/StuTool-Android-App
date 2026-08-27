package com.stutool.stutool.ui.screens.resume.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.stutool.stutool.ui.screens.resume.EnhanceState
import com.stutool.stutool.ui.screens.resume.ResumeViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumeForm(
    viewModel: ResumeViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val enhanceState by viewModel.enhanceState.collectAsState()
    val data = state.resumeData

    val context = LocalContext.current
    LaunchedEffect(enhanceState) {
        if (enhanceState is EnhanceState.Error) {
            Toast.makeText(context, (enhanceState as EnhanceState.Error).message, Toast.LENGTH_SHORT).show()
            viewModel.resetEnhanceState()
        }
    }

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
        }
    }

    if (selectedImageUri != null) {
        ImageCropperDialog(
            uri = selectedImageUri!!,
            onDismiss = { selectedImageUri = null },
            onCropped = { croppedPath -> 
                viewModel.updateProfileImage(croppedPath)
            }
        )
    }

    if (enhanceState is EnhanceState.Success) {
        val suggestedText = (enhanceState as EnhanceState.Success).suggestedText
        AlertDialog(
            onDismissRequest = { viewModel.resetEnhanceState() },
            title = { Text("AI Suggestion", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = { Text(suggestedText, fontSize = 14.sp, lineHeight = 20.sp) },
            confirmButton = {},
            dismissButton = {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = { viewModel.resetEnhanceState() }) { 
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) 
                    }
                    TextButton(onClick = { 
                        viewModel.enhanceCareerObjective(data.careerObjective) 
                    }) { 
                        Text("Try Again") 
                    }
                    TextButton(onClick = { 
                        viewModel.updateCareerObjective(suggestedText)
                        viewModel.resetEnhanceState()
                    }) { 
                        Text("Edit", color = MaterialTheme.colorScheme.primary) 
                    }
                    Button(
                        onClick = {
                            viewModel.updateCareerObjective(suggestedText)
                            viewModel.resetEnhanceState()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { 
                        Text("Use this") 
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // Resume Score
        item {
            val score = data.calculateScore()
            val scoreColor = when {
                score < 40 -> Color(0xFFEF4444)
                score < 70 -> Color(0xFFF59E0B)
                else -> Color(0xFF10B981)
            }
            val scoreText = when {
                score < 40 -> "Needs Work"
                score < 70 -> "Good"
                else -> "Excellent"
            }
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RESUME SCORE",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp
                        )
                        Box(
                            modifier = Modifier
                                .background(scoreColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = scoreText,
                                color = scoreColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val animatedProgress by animateFloatAsState(targetValue = score / 100f, label = "progress")
                    
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = scoreColor,
                        trackColor = Color(0xFF333333)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "$score%",
                        color = scoreColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }

        // Personal Info
        item {
            var expanded by remember { mutableStateOf(false) }
            FormSectionCard(
                title = "Personal Info",
                icon = Icons.Default.Person,
                expanded = expanded,
                onToggle = { expanded = !expanded }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.selectedTemplate.equals("persona", ignoreCase = true)) {
                        Button(
                            onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (data.personal.profileImageUri.isNotBlank()) "Change Profile Image" else "Upload Profile Image")
                        }
                    }
                    DarkFormInput("Full Name", "e.g. Mohammed Saif", data.personal.name) { viewModel.updatePersonal("name", it) }
                    DarkFormInput("Email Address", "e.g. email@example.com", data.personal.email) { viewModel.updatePersonal("email", it) }
                    DarkPhoneInput(
                        countryCode = data.personal.countryCode,
                        phoneNumber = data.personal.phoneNumber,
                        onCodeChange = { viewModel.updatePersonal("countryCode", it) },
                        onNumberChange = { viewModel.updatePersonal("phoneNumber", it) }
                    )
                    DarkFormInput("Location", "e.g. Bengaluru, Karnataka", data.personal.location) { viewModel.updatePersonal("location", it) }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("CONTACT & LINKS", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    data.personal.links.forEach { link ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            DarkDropdownSelector(
                                label = "Type",
                                options = listOf("LinkedIn", "GitHub", "Portfolio", "Other"),
                                selectedValue = link.type,
                                modifier = Modifier.weight(0.4f),
                                onValueChange = { viewModel.updateLink(link.id, "type", it) }
                            )
                            DarkFormInput(
                                label = "URL",
                                placeholder = "e.g. linkedin.com/...",
                                value = link.url,
                                modifier = Modifier.weight(0.6f)
                            ) { viewModel.updateLink(link.id, "url", it) }
                            IconButton(onClick = { viewModel.removeLink(link.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove Link", tint = Color(0xFFEF4444))
                            }
                        }
                    }
                    if (data.personal.links.size < 5) {
                        TextButton(onClick = { viewModel.addLink() }) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add more", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("ADDITIONAL DETAILS (OPTIONAL)", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    DarkDateInput("Date of Birth", data.personal.dob) { viewModel.updatePersonal("dob", it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DarkDropdownSelector("Gender", listOf("Male", "Female", "Other", "Prefer not to say"), data.personal.gender, modifier = Modifier.weight(1f)) { viewModel.updatePersonal("gender", it) }
                        DarkDropdownSelector("Marital Status", listOf("Single", "Married"), data.personal.maritalStatus, modifier = Modifier.weight(1f)) { viewModel.updatePersonal("maritalStatus", it) }
                    }
                    DarkFormInput("Languages Known", "e.g. English, Hindi...", data.personal.languages) { viewModel.updatePersonal("languages", it) }
                }
            }
        }

        // Summary & Objective
        item {
            var expanded by remember { mutableStateOf(false) }
            FormSectionCard(
                title = "Summary & Objective",
                icon = Icons.Default.Description,
                expanded = expanded,
                onToggle = { expanded = !expanded }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Keep it short (2–3 lines). Focus on skills and goals relevant to the role.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    DarkFormInput(
                        label = "Write a Brief Career Objective", 
                        placeholder = "Motivated student seeking an opportunity to apply my knowledge in [Field] to contribute to [Goal] and grow within a dynamic organization.", 
                        value = data.careerObjective,
                        singleLine = false,
                        lines = 4
                    ) { viewModel.updateCareerObjective(it) }

                    val isEnhancing = enhanceState is EnhanceState.Loading

                    Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Icon(
                                    Icons.Default.Info, 
                                    contentDescription = null, 
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), 
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Uses AI • Internet required • Start with a rough objective",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }

                            val primaryColor = MaterialTheme.colorScheme.primary
                            var lastClickTime by remember { mutableStateOf(0L) }

                            Box(
                                modifier = Modifier
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(
                                        if (isEnhancing) primaryColor.copy(alpha = 0.08f)
                                        else primaryColor.copy(alpha = 0.15f)
                                    )
                                    .clickable(
                                        enabled = !isEnhancing,
                                        onClick = { 
                                            val now = System.currentTimeMillis()
                                            if (now - lastClickTime < 800) return@clickable
                                            lastClickTime = now
                                            
                                            if (data.careerObjective.isBlank()) {
                                                Toast.makeText(context, "Please enter a career objective first", Toast.LENGTH_SHORT).show()
                                            } else if (data.careerObjective.trim().length < 15) {
                                                Toast.makeText(context, "Write a bit more detail for better results", Toast.LENGTH_SHORT).show()
                                            } else {
                                                viewModel.enhanceCareerObjective(data.careerObjective) 
                                            }
                                        }
                                    )
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = if (isEnhancing) primaryColor.copy(alpha = 0.5f) else primaryColor
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isEnhancing) "Enhancing..." else "Enhance",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isEnhancing) primaryColor.copy(alpha = 0.5f) else primaryColor
                                    )
                                }
                            }
                        }
                        
                        if (isEnhancing) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Enhancing your objective... usually takes a few seconds",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Experience
        item {
            var expanded by remember { mutableStateOf(false) }
            FormSectionCard(
                title = "Professional Experience",
                icon = Icons.Default.Work,
                expanded = expanded,
                onToggle = { expanded = !expanded },
                isVisible = data.visibleSections.experience,
                onVisibilityToggle = { viewModel.toggleVisibility("experience") }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    data.experience.forEachIndexed { index, exp ->
                        FormItemCard(
                            title = "Role ${index + 1}",
                            onDelete = { viewModel.removeExperience(exp.id) }
                        ) {
                            DarkFormInput("Company", "e.g. TCS, Infosys, Wipro", exp.company) { viewModel.updateExperience(exp.id, "company", it) }
                            DarkFormInput("Job Title", "e.g. Software Engineer", exp.position) { viewModel.updateExperience(exp.id, "position", it) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DarkDateInput("Start Date", exp.startDate, modifier = Modifier.weight(1f)) { viewModel.updateExperience(exp.id, "startDate", it) }
                                DarkDateInput("End Date", exp.endDate, modifier = Modifier.weight(1f)) { viewModel.updateExperience(exp.id, "endDate", it) }
                            }
                            DarkFormInput("Description", "- Managed marketing campaigns...", exp.description, singleLine = false, lines = 4) { viewModel.updateExperience(exp.id, "description", it) }
                        }
                    }
                    AddButton("Add Experience") { viewModel.addExperience() }
                }
            }
        }

        // Education
        item {
            var expanded by remember { mutableStateOf(false) }
            FormSectionCard(
                title = "Education History",
                icon = Icons.Default.School,
                expanded = expanded,
                onToggle = { expanded = !expanded },
                isVisible = data.visibleSections.education,
                onVisibilityToggle = { viewModel.toggleVisibility("education") }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    data.education.forEachIndexed { index, edu ->
                        FormItemCard(
                            title = "Education ${index + 1}",
                            onDelete = { viewModel.removeEducation(edu.id) }
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DarkDropdownSelector("Level", listOf("10th", "12th", "Undergraduate", "Postgraduate", "Diploma", "Other"), edu.level, modifier = Modifier.weight(1f)) { viewModel.updateEducation(edu.id, "level", it) }
                                DarkFormInput("Passing Year", "e.g. 2023", edu.year, modifier = Modifier.weight(1f)) { viewModel.updateEducation(edu.id, "year", it) }
                            }
                            DarkFormInput("Institution Name", "e.g. Delhi Public School, VTU", edu.institution) { viewModel.updateEducation(edu.id, "institution", it) }
                            DarkFormInput("Board / University", "e.g. CBSE, ICSE, VTU", edu.board) { viewModel.updateEducation(edu.id, "board", it) }
                            DarkFormInput("Degree", "e.g. B.Tech, B.Com", edu.degree) { viewModel.updateEducation(edu.id, "degree", it) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DarkDropdownSelector("Marks Type", listOf("CGPA", "Percentage"), edu.marksType, modifier = Modifier.weight(1f)) { viewModel.updateEducation(edu.id, "marksType", it) }
                                DarkFormInput("Marks", if (edu.marksType == "Percentage") "e.g. 85%" else "e.g. 9.5", edu.marks, modifier = Modifier.weight(1f)) { viewModel.updateEducation(edu.id, "marks", it) }
                            }
                            DarkFormInput("Additional Details (Optional)", "Coursework, Thesis, etc.", edu.description, singleLine = false, lines = 3) { viewModel.updateEducation(edu.id, "description", it) }
                        }
                    }
                    AddButton("Add Education") { viewModel.addEducation() }
                }
            }
        }

        // Projects
        item {
            var expanded by remember { mutableStateOf(false) }
            FormSectionCard(
                title = "Notable Projects",
                icon = Icons.Default.Code,
                expanded = expanded,
                onToggle = { expanded = !expanded },
                isVisible = data.visibleSections.projects,
                onVisibilityToggle = { viewModel.toggleVisibility("projects") }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    data.projects.forEachIndexed { index, proj ->
                        FormItemCard(
                            title = "Project ${index + 1}",
                            onDelete = { viewModel.removeProject(proj.id) }
                        ) {
                            DarkFormInput("Project Name", "Portfolio API", proj.name) { viewModel.updateProject(proj.id, "name", it) }
                            proj.links.forEachIndexed { linkIndex, linkUrl ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        DarkFormInput(
                                            label = "Link (URL)",
                                            placeholder = "https://...",
                                            value = linkUrl,
                                            onValueChange = { viewModel.updateProjectLink(proj.id, linkIndex, it) }
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.removeProjectLink(proj.id, linkIndex) },
                                        modifier = Modifier.padding(start = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove Link",
                                            tint = Color.Red.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                            TextButton(
                                onClick = { viewModel.addProjectLink(proj.id) },
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Link", fontSize = 12.sp)
                            }
                            DarkFormInput("Description", "- Built a REST API...", proj.description, singleLine = false, lines = 3) { viewModel.updateProject(proj.id, "description", it) }
                        }
                    }
                    AddButton("Add Project") { viewModel.addProject() }
                }
            }
        }

        // Skills
        item {
            var expanded by remember { mutableStateOf(false) }
            FormSectionCard(
                title = "Technical Skills",
                icon = Icons.Default.Star,
                expanded = expanded,
                onToggle = { expanded = !expanded },
                isVisible = data.visibleSections.skills,
                onVisibilityToggle = { viewModel.toggleVisibility("skills") }
            ) {
                var newSkill by remember { mutableStateOf("") }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = newSkill,
                            onValueChange = { newSkill = it },
                            placeholder = { Text("e.g. Digital Marketing, SEO, React...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newSkill.isNotBlank() && !data.skills.contains(newSkill.trim())) {
                                    viewModel.updateSkills(data.skills + newSkill.trim())
                                    newSkill = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Add")
                        }
                    }

                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        data.skills.forEach { skill ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        RoundedCornerShape(20.dp)
                                    )
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        RoundedCornerShape(20.dp)
                                    )
                                    .clickable {
                                        viewModel.updateSkills(data.skills.filter { it != skill })
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(skill, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Declaration
        item {
            var expanded by remember { mutableStateOf(false) }
            FormSectionCard(
                title = "Declaration",
                icon = Icons.Default.Gavel,
                expanded = expanded,
                onToggle = { expanded = !expanded },
                isVisible = data.visibleSections.declaration,
                onVisibilityToggle = { viewModel.toggleVisibility("declaration") }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Include Declaration in Resume", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = data.declaration.enabled,
                            onCheckedChange = { viewModel.updateDeclaration(enabled = it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onBackground,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    AnimatedVisibility(visible = data.declaration.enabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            DarkFormInput("Place", "e.g. Bengaluru", data.declaration.place) { viewModel.updateDeclaration(place = it) }
                            DarkFormInput("Date", "e.g. YYYY-MM-DD", data.declaration.date) { viewModel.updateDeclaration(date = it) }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(120.dp)) }
    }
}

@Composable
private fun FormSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    isVisible: Boolean? = null,
    onVisibilityToggle: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val opacity = if (isVisible == false) 0.5f else 1f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = opacity)
                )
                if (isVisible != null && onVisibilityToggle != null) {
                    IconButton(onClick = onVisibilityToggle, modifier = Modifier.size(28.dp)) {
                        Icon(
                            if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle Visibility",
                            tint = if (isVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun FormItemCard(
    title: String,
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f), fontSize = 14.sp)
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun DarkFormInput(
    label: String,
    placeholder: String,
    value: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    lines: Int = 1,
    onValueChange: (String) -> Unit
) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 14.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            minLines = if (singleLine) 1 else lines,
            maxLines = if (singleLine) 1 else 10,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Composable
private fun AddButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

data class CountryChoice(val name: String, val code: String)

@Composable
private fun DarkPhoneInput(
    countryCode: String,
    phoneNumber: String,
    onCodeChange: (String) -> Unit,
    onNumberChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val countries = listOf(
        CountryChoice("India", "+91"),
        CountryChoice("United States", "+1"),
        CountryChoice("Canada", "+1"),
        CountryChoice("United Arab Emirates", "+971"),
        CountryChoice("United Kingdom", "+44"),
        CountryChoice("Australia", "+61"),
        CountryChoice("Singapore", "+65"),
        CountryChoice("Saudi Arabia", "+966"),
        CountryChoice("Germany", "+49"),
        CountryChoice("New Zealand", "+64"),
        CountryChoice("Qatar", "+974"),
        CountryChoice("Kuwait", "+965"),
        CountryChoice("Oman", "+968"),
        CountryChoice("Malaysia", "+60"),
        CountryChoice("South Africa", "+27"),
        CountryChoice("Ireland", "+353"),
        CountryChoice("Netherlands", "+31"),
        CountryChoice("France", "+33"),
        CountryChoice("Sweden", "+46"),
        CountryChoice("Italy", "+39"),
        CountryChoice("Maldives", "+960")
    )
    
    val currentDisplay = countryCode.ifBlank { "+91" }

    Column {
        Text("PHONE NUMBER", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.padding(bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(0.35f)) {
                OutlinedTextField(
                    value = currentDisplay,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        disabledTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = RoundedCornerShape(8.dp),
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                )
                Box(modifier = Modifier.matchParentSize().clickable { expanded = true })
                MaterialTheme(
                    shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(12.dp)),
                    colorScheme = MaterialTheme.colorScheme.copy(surface = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    DropdownMenu(
                        expanded = expanded, 
                        onDismissRequest = { expanded = false }, 
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .padding(6.dp)
                    ) {
                        countries.forEach { choice ->
                            val isSelected = currentDisplay == choice.code
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        text = "${choice.name} (${choice.code})", 
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    ) 
                                },
                                onClick = { 
                                    onCodeChange(choice.code)
                                    expanded = false 
                                },
                                trailingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                                } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerLow)
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { digits ->
                    val cleanDigits = digits.filter { it.isDigit() }.take(15)
                    onNumberChange(cleanDigits)
                },
                placeholder = { Text("9876543210", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 14.sp) },
                modifier = Modifier.weight(0.65f),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            )
        }
    }
}

@Composable
private fun DarkDropdownSelector(
    label: String,
    options: List<String>,
    selectedValue: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(label.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.padding(bottom = 6.dp))
        Box {
            OutlinedTextField(
                value = selectedValue,
                onValueChange = {},
                readOnly = true,
                placeholder = { Text("Select", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 14.sp) },
                modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground, unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    disabledTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedBorderColor = MaterialTheme.colorScheme.outline, unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(8.dp),
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            )
            Box(modifier = Modifier.matchParentSize().clickable { expanded = true })
            MaterialTheme(
                shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(12.dp)),
                colorScheme = MaterialTheme.colorScheme.copy(surface = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                DropdownMenu(
                    expanded = expanded, 
                    onDismissRequest = { expanded = false }, 
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(6.dp)
                ) {
                    options.forEach { option ->
                        val isSelected = selectedValue == option
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    text = option, 
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    fontSize = 14.sp
                                ) 
                            },
                            onClick = { onValueChange(option); expanded = false },
                            trailingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                            } else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerLow)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DarkDateInput(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    Column(modifier = modifier) {
        Text(label.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("MM/YYYY or Present", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 14.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onBackground, unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary, focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = RoundedCornerShape(8.dp),
            trailingIcon = {
                IconButton(onClick = { showDialog = true }) {
                    Icon(Icons.Default.CalendarToday, contentDescription = "Select Date", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showDialog) {
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(millis))
                        onValueChange(date)
                    }
                    showDialog = false
                }) { Text("OK", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onBackground, headlineContentColor = MaterialTheme.colorScheme.onBackground,
                    weekdayContentColor = MaterialTheme.colorScheme.onSurfaceVariant, dayContentColor = MaterialTheme.colorScheme.onBackground,
                    selectedDayContainerColor = MaterialTheme.colorScheme.primary, selectedDayContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    }
}
