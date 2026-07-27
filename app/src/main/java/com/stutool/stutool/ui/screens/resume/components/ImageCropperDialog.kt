package com.stutool.stutool.ui.screens.resume.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.min

@Composable
fun ImageCropperDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onCropped: (String) -> Unit
) {
    val context = LocalContext.current
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(uri) {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
    }

    if (originalBitmap == null) return

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var touchAreaSize by remember { mutableStateOf(IntSize.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false // Allow drawing to edges, but must manually handle insets
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 5f)
                            offset += pan
                        }
                    }
                    .onSizeChanged { touchAreaSize = it },
                contentAlignment = Alignment.Center
            ) {
                originalBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "To crop",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y,
                                rotationZ = rotation
                            )
                    )
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = min(size.width, size.height) * 0.45f
                    val center = Offset(size.width / 2, size.height / 2)
                    
                    val maskPath = Path().apply {
                        addRect(Rect(0f, 0f, size.width, size.height))
                        addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
                        fillType = PathFillType.EvenOdd
                    }
                    drawPath(maskPath, Color.White.copy(alpha = 0.8f))
                    drawCircle(Color.White, radius, center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111111))
                    .navigationBarsPadding() // FIX: Ensures buttons aren't grounded behind system nav bar
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color.White)
                }
                
                androidx.compose.material3.IconButton(
                    onClick = { rotation += 90f }
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Rotate",
                        tint = Color.White
                    )
                }
                
                Button(
                    onClick = {
                        val finalBmp = createCroppedBitmap(
                            originalBitmap!!, touchAreaSize, scale, offset, rotation
                        )
                        val fileUri = saveBitmapToLocalFile(context, finalBmp)
                        onCropped(fileUri)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48))
                ) {
                    Text("Apply Crop")
                }
            }
        }
    }
}

private fun createCroppedBitmap(original: Bitmap, containerSize: IntSize, scale: Float, offset: Offset, rotation: Float): Bitmap {
    if (containerSize.width == 0 || containerSize.height == 0) return original

    val radius = min(containerSize.width, containerSize.height) * 0.45f
    val circleSize = (radius * 2).toInt()
    
    val outputSize = 800
    val cropBitmap = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(cropBitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    
    val clipPath = android.graphics.Path().apply {
        addCircle(outputSize / 2f, outputSize / 2f, outputSize / 2f, android.graphics.Path.Direction.CCW)
    }
    canvas.clipPath(clipPath)
    
    val matrix = Matrix()
    
    // Fit scaling
    val bmpDisplayWidth = original.width.toFloat()
    val bmpDisplayHeight = original.height.toFloat()
    
    val fitScaleRatio = min(
        containerSize.width.toFloat() / bmpDisplayWidth,
        containerSize.height.toFloat() / bmpDisplayHeight
    )
    
    val displayW = bmpDisplayWidth * fitScaleRatio
    val displayH = bmpDisplayHeight * fitScaleRatio
    
    val initialTx = (containerSize.width - displayW) / 2f
    val initialTy = (containerSize.height - displayH) / 2f
    
    matrix.postTranslate(initialTx, initialTy)
    
    // Scale and Transform based on User inputs
    val screenCenterX = containerSize.width / 2f
    val screenCenterY = containerSize.height / 2f
    
    // Important: Image composable scales after translating, relative to its center.
    // wait: graphicsLayer applies scale around TransformOrigin (default Center), 
    // then translation applies. So we apply scale first, then translation.
    
    matrix.postScale(fitScaleRatio, fitScaleRatio) // apply fit scale
    // Move to center of container (equivalent to the initial location given by fit mode) is already handled if we just map properly
    matrix.reset()
    
    // Reconstruct EXACT matrix:
    // 1. apply fit
    matrix.postScale(fitScaleRatio, fitScaleRatio)
    matrix.postTranslate((containerSize.width - displayW) / 2f, (containerSize.height - displayH) / 2f)
    
    // 2. apply graphicsLayer transformations
    // graphicsLayer scales and rotates around its center (screenCenterX, screenCenterY since fillMaxSize)
    matrix.postRotate(rotation, screenCenterX, screenCenterY)
    matrix.postScale(scale, scale, screenCenterX, screenCenterY)
    matrix.postTranslate(offset.x, offset.y)
    
    // Map screen container coordinates to our 800x800 output bitmap.
    // The screen has a circular mask at (screenCenterX, screenCenterY) with 'radius'.
    // We want the area inside this circle to map exactly to the (0..800, 0..800) area of our cropBitmap.
    
    val screenToOutputRatio = outputSize.toFloat() / (radius * 2f)
    
    // Shift screen center to output center
    matrix.postTranslate(outputSize/2f - screenCenterX, outputSize/2f - screenCenterY)
    
    // Scale up
    matrix.postScale(screenToOutputRatio, screenToOutputRatio, outputSize/2f, outputSize/2f)
    
    // Draw
    canvas.drawBitmap(original, matrix, paint)
    
    return cropBitmap
}

private fun saveBitmapToLocalFile(context: Context, bitmap: Bitmap): String {
    val file = File(context.filesDir, "profile_crop_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    return "file://${file.absolutePath}"
}
