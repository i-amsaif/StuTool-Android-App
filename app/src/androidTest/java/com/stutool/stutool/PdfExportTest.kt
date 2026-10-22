package com.stutool.stutool

import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stutool.stutool.ui.screens.resume.engine.PdfExporter
import com.stutool.stutool.ui.screens.resume.models.ResumeData
import com.stutool.stutool.ui.screens.resume.models.Experience
import com.stutool.stutool.ui.screens.resume.models.Education
import com.stutool.stutool.ui.screens.resume.models.Project
import com.stutool.stutool.ui.screens.resume.templates.TemplateType
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfExportTest {

    @Test
    fun generateAllPdfs() = runBlocking {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var activity: ComponentActivity? = null
        scenario.onActivity { activity = it }
        
        val longExp = (1..6).map { 
            Experience(company = "Company \", position = "Role \", startDate = "2020", endDate = "2021", description = "• Developed microservices using Spring Boot.\n• Reduced latency by 40% using Redis.\n• Led a team of 5 engineers.\n• Migrated legacy monolithic architecture to AWS.") 
        }
        val data = ResumeData(
            careerObjective = "This is a very long objective to test wrapping. ".repeat(15),
            experience = longExp,
            education = listOf(Education(institution = "National University", level = "Bachelors", description = "• GPA 3.8\n• Relevant coursework: DS, Algo")),
            projects = listOf(Project(name = "Finvovo App", description = "• Built an Android ledger app.\n• 1000+ active users."))
        )

        for (template in TemplateType.entries) {
            var file: File? = null
            scenario.onActivity {
                runBlocking {
                    file = PdfExporter.generatePdfToCache(activity!!, data, template, template.name + ".pdf")
                }
            }
            while(file == null) { kotlinx.coroutines.delay(100) }
            
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val externalDir = context.getExternalFilesDir(null)
            val dest = File(externalDir, template.name + ".pdf")
            file?.copyTo(dest, overwrite = true)
        }
    }
}
