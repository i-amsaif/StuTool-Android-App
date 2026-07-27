package com.stutool.stutool

import com.stutool.stutool.ui.screens.scanner.ScannerViewModel
import com.stutool.stutool.ui.screens.scanner.models.CropMode
import org.junit.Test
import org.junit.Assert.*

class ScannerViewModelTest {

    @Test
    fun testModeTogglesStress() {
        val viewModel = ScannerViewModel()
        
        // Initial mode should be perspective
        assertEquals(CropMode.PERSPECTIVE, viewModel.cropMode.value)
        
        // 100+ mode toggles
        for (i in 1..200) {
            val newMode = if (viewModel.cropMode.value == CropMode.PERSPECTIVE) {
                CropMode.RECTANGULAR
            } else {
                CropMode.PERSPECTIVE
            }
            viewModel.setCropMode(newMode)
        }
        
        assertEquals(CropMode.PERSPECTIVE, viewModel.cropMode.value)
        assertTrue("Stress test completed successfully without crashing", true)
    }
}
