package com.stutool.stutool.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared First-Time Consent Dialog for Online OCR in StuTool v1.2.0.
 *
 * Presented only when an eligible file is selected online and the user has not yet consented.
 * Dismissing or choosing "Process Offline" proceeds with local OCR for the current operation
 * without uploading the file and without persisting consent.
 */
@Composable
fun OcrCloudConsentDialog(
    onProceedOnline: () -> Unit,
    onProcessOffline: () -> Unit,
    onDismiss: () -> Unit = onProcessOffline
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.CloudUpload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Use Online OCR?",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Text(
                text = "Online OCR uses an internet connection and sends the selected document or image to an OCR service to extract its text.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        confirmButton = {
            Button(
                onClick = onProceedOnline,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Proceed Online")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onProcessOffline
            ) {
                Text("Process Offline", color = MaterialTheme.colorScheme.outline)
            }
        }
    )
}
