package com.stutool.stutool.ui.screens.resume.models

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResumeDataTest {

    @Test
    fun `test legacy project single link migration`() {
        val legacyJson = """
        {
            "projects": [
                {
                    "id": "1",
                    "name": "Single Link Project",
                    "link": "https://github.com/test",
                    "description": "test"
                }
            ]
        }
        """.trimIndent()

        val data = ResumeData.fromJson(legacyJson)
        assertEquals(1, data.projects.size)
        val proj = data.projects[0]
        assertEquals(1, proj.links.size)
        assertEquals("https://github.com/test", proj.links[0])
    }

    @Test
    fun `test legacy project exactly preserved without space`() {
        val legacyJson = """
        {
            "projects": [
                {
                    "id": "1",
                    "name": "No Space Link Project",
                    "link": "github.com/test",
                    "description": "test"
                }
            ]
        }
        """.trimIndent()

        val data = ResumeData.fromJson(legacyJson)
        val proj = data.projects[0]
        assertEquals(1, proj.links.size)
        assertEquals("github.com/test", proj.links[0])
    }

    @Test
    fun `test legacy project multiple links extraction`() {
        val legacyJson = """
        {
            "projects": [
                {
                    "id": "1",
                    "name": "Multi Link Project",
                    "link": "Here are my links: https://github.com/test1 www.test.com and github.com/test2",
                    "description": "test"
                }
            ]
        }
        """.trimIndent()

        val data = ResumeData.fromJson(legacyJson)
        val proj = data.projects[0]
        assertEquals(3, proj.links.size)
        assertEquals("https://github.com/test1", proj.links[0])
        assertEquals("www.test.com", proj.links[1])
        assertEquals("github.com/test2", proj.links[2])
    }

    @Test
    fun `test legacy project multiple space string exactly preserved if no url found`() {
        val legacyJson = """
        {
            "projects": [
                {
                    "id": "1",
                    "name": "Bad Link Project",
                    "link": "this is just some text with spaces",
                    "description": "test"
                }
            ]
        }
        """.trimIndent()

        val data = ResumeData.fromJson(legacyJson)
        val proj = data.projects[0]
        assertEquals(1, proj.links.size)
        assertEquals("this is just some text with spaces", proj.links[0])
    }

    @Test
    fun `test new project links format serialization and deserialization`() {
        val originalData = ResumeData(
            projects = listOf(
                Project(
                    name = "New Proj",
                    links = listOf("https://link1.com", "https://link2.com")
                )
            )
        )
        val json = originalData.toJson()
        val parsedData = ResumeData.fromJson(json)

        assertEquals(1, parsedData.projects.size)
        assertEquals(2, parsedData.projects[0].links.size)
        assertEquals("https://link1.com", parsedData.projects[0].links[0])
        assertEquals("https://link2.com", parsedData.projects[0].links[1])
    }
}
