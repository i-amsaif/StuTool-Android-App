package com.stutool.stutool

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import java.io.InputStream
import java.io.File

fun checkMethods() {
    val methods = PDDocument::class.java.methods
    methods.forEach { m ->
        if (m.name == "load") {
            println("load method: ${m.parameterTypes.joinToString { it.name }}")
        }
    }
}
