package com.stutool.stutool

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GestureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @OptIn(ExperimentalFoundationApi::class)
    @Test
    fun testPinchGesture() {
        var finalScale = 1f

        composeTestRule.setContent {
            val pagerState = rememberPagerState(pageCount = { 3 })
            
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { index ->
                val isActive = index == pagerState.currentPage
                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                var isTransforming by remember { mutableStateOf(false) }

                LaunchedEffect(isActive) {
                    println("LOG: LaunchedEffect(isActive=$isActive)")
                    if (!isActive) {
                        scale = 1f
                        offset = Offset.Zero
                    }
                }
                
                LaunchedEffect(scale) {
                    println("LOG: Recomposition detected, scale=$scale")
                }

                val viewConfiguration = LocalViewConfiguration.current
                val MIN_ZOOM = 1f
                val MAX_ZOOM = 3f

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("zoomable_$index")
                        .pointerInput(Unit) {
                            val touchSlop = viewConfiguration.touchSlop
                            
                            awaitEachGesture {
                                awaitFirstDown()
                                println("LOG: Gesture started, awaitFirstDown triggered")
                                var zoom = 1f
                                var pan = Offset.Zero
                                var pastSlop = false
                                isTransforming = true
                                
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val canceled = event.changes.any { it.isConsumed }
                                    
                                    val pointersCount = event.changes.count { it.pressed }
                                    println("LOG: Loop Iteration - Pointers: $pointersCount, Canceled: $canceled")
                                    
                                    if (canceled) {
                                        println("LOG: Loop break due to canceled!")
                                        break
                                    }

                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    
                                    println("LOG: Calculate - zoomChange=$zoomChange, panChange=$panChange")

                                    if (!pastSlop) {
                                        zoom *= zoomChange
                                        pan += panChange
                                        val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                        val zoomMotion = kotlin.math.abs(1 - zoom) * centroidSize
                                        val panMotion = pan.getDistance()

                                        // OLD LOGIC REPRODUCTION (Before my fix)
                                        if (zoomMotion > touchSlop || panMotion > touchSlop) {
                                            if (pointersCount > 1 || scale > 1f) {
                                                pastSlop = true
                                                println("LOG: pastSlop set true (OLD LOGIC)")
                                            }
                                        }
                                        // NEW LOGIC REPRODUCTION
                                        /*
                                        if (pointersCount > 1) {
                                            pastSlop = true
                                            println("LOG: pastSlop set true because pointersCount > 1 (NEW LOGIC)")
                                        } else if (zoomMotion > touchSlop || panMotion > touchSlop) {
                                            if (scale > 1f) {
                                                pastSlop = true
                                            }
                                        }
                                        */
                                    }

                                    if (pastSlop) {
                                        var consume = false
                                        if (pointersCount > 1) {
                                            consume = true
                                            println("LOG: Consuming for Pinch")
                                        } else if (scale > 1f) {
                                            consume = true
                                        }

                                        if (consume) {
                                            event.changes.forEach { if (it.pressed) it.consume() }
                                            
                                            val zoomFactor = 1.5f
                                            val actualZoom = 1f + (zoomChange - 1f) * zoomFactor
                                            val newScale = (scale * actualZoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                            
                                            println("LOG: Scale update: $scale -> $newScale (actualZoom=$actualZoom)")
                                            scale = newScale
                                            finalScale = newScale
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                                
                                println("LOG: Gesture ended natively")
                                isTransforming = false
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    println("LOG: Double tap")
                                    scale = 2.5f
                                }
                            )
                        }
                        .background(if (isActive) Color.Red else Color.Blue)
                ) {
                    // dummy content
                }
            }
        }

        println("LOG: Starting simulated swipe/pinch race condition...")
        // Simulate dragging one finger first, then placing second
        composeTestRule.onNodeWithTag("zoomable_0").performTouchInput {
            down(0, center)
            moveBy(0, Offset(50f, 0f)) // Exceed touch slop, horizontal pager grabs it!
            down(1, center + Offset(0f, 100f)) // Second finger lands
            moveBy(0, Offset(-50f, 0f)) // Pinch apart
            moveBy(1, Offset(50f, 0f))
            up(0)
            up(1)
        }
    }
}
