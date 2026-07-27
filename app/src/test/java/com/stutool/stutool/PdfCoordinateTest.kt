package com.stutool.stutool

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import org.junit.Test
import java.io.File

class PdfCoordinateTest {
    @Test
    fun testPdfCoordinates() {
        val pdfFile = File("C:/Users/Lenovo/AndroidStudioProjects/StuTool/chat/all_chat_sum.pdf")
        if (!pdfFile.exists()) {
            println("PDF not found at ${pdfFile.absolutePath}")
            return
        }

        PDDocument.load(pdfFile).use { doc ->
            val page = doc.getPage(0) // First page
            val cropBox = page.cropBox
            val mediaBox = page.mediaBox
            println("Page 1 Details:")
            println("  MediaBox: $mediaBox (W=${mediaBox.width}, H=${mediaBox.height}, LLX=${mediaBox.lowerLeftX}, LLY=${mediaBox.lowerLeftY}, URX=${mediaBox.upperRightX}, URY=${mediaBox.upperRightY})")
            println("  CropBox:  $cropBox (W=${cropBox.width}, H=${cropBox.height}, LLX=${cropBox.lowerLeftX}, LLY=${cropBox.lowerLeftY}, URX=${cropBox.upperRightX}, URY=${cropBox.upperRightY})")
            println("  Rotation: ${page.rotation}")

            var count = 0
            val stripper = object : PDFTextStripper() {
                override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
                    if (textPositions != null && count < 5) {
                        for (pos in textPositions) {
                            if (count >= 5) break
                            println("--- Char: '${pos.unicode}' ---")
                            println("Raw TextPosition:")
                            println("  x=${pos.x}, y=${pos.y}")
                            println("  xDirAdj=${pos.xDirAdj}, yDirAdj=${pos.yDirAdj}")
                            println("  width=${pos.width}, height=${pos.height}")
                            println("  widthDirAdj=${pos.widthDirAdj}, heightDir=${pos.heightDir}")

                            // Compute as in ViewModel
                            val elementX = pos.xDirAdj - cropBox.lowerLeftX
                            val relativeY = pos.yDirAdj - (mediaBox.upperRightY - cropBox.upperRightY)
                            val elementY = relativeY - pos.heightDir

                            println("Generated Element:")
                            println("  elementX=$elementX")
                            println("  elementY=$elementY (relativeY=$relativeY)")
                            println("  elementHeight=${pos.heightDir * 1.1f}")
                            
                            count++
                        }
                    }
                }
            }
            stripper.sortByPosition = true
            stripper.startPage = 1
            stripper.endPage = 1
            stripper.getText(doc)
        }
    }
}
