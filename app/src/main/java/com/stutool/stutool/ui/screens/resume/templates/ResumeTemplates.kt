package com.stutool.stutool.ui.screens.resume.templates

import androidx.compose.runtime.Composable
import com.stutool.stutool.ui.screens.resume.models.ResumeData
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density

@Composable
fun RenderResume(
    data: ResumeData,
    template: String,
    pageWidth: Dp,
    modifier: Modifier = Modifier
) {
    // Virtual Base Width = 595f for A4 
    val virtualBaseWidth = 595f
    val scale = pageWidth.value / virtualBaseWidth
    val currentDensity = LocalDensity.current.density
    
    // Scale the density proportionally!
    // This allows all internal fixed DPs to act as relative percentages
    val customDensity = Density(density = currentDensity * scale, fontScale = 1f)
    
    CompositionLocalProvider(
        LocalDensity provides customDensity
    ) {
        // Inside this provider, 595.dp evaluates exactly to `pageWidth.toPx()` 
        Box(
            modifier = modifier
                .width(virtualBaseWidth.dp)
                .fillMaxHeight()
                .background(Color.White)
        ) {
            when (resolveResumeTemplateKey(template)) {
                "minimal" -> MinimalTemplate(data = data)
                "modern" -> ModernTemplate(data = data)
                "classic" -> ClassicTemplate(data = data)
                "persona" -> PersonaTemplate(data = data)
                "career_timeline" -> CareerTimelineTemplate(data = data)
                "executive_slate" -> ExecutiveSlateTemplate(data = data)
                else -> MinimalTemplate(data = data)
            }
        }
    }
}

@Composable
fun ResumeTemplateRenderer(
    template: String,
    data: ResumeData,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(Color.White)) {
        when (resolveResumeTemplateKey(template)) {
            "minimal" -> MinimalTemplate(data = data)
            "modern" -> ModernTemplate(data = data)
            "classic" -> ClassicTemplate(data = data)
            "persona" -> PersonaTemplate(data = data)
            "career_timeline" -> CareerTimelineTemplate(data = data)
            "executive_slate" -> ExecutiveSlateTemplate(data = data)
            else -> MinimalTemplate(data = data)
        }
    }
}

internal fun resolveResumeTemplateKey(template: String): String {
    return when (template.trim().lowercase()) {
        "minimal" -> "minimal"
        "modern" -> "modern"
        "classic" -> "classic"
        "persona" -> "persona"
        "career_timeline" -> "career_timeline"
        "executive_slate" -> "executive_slate"
        else -> "minimal"
    }
}


