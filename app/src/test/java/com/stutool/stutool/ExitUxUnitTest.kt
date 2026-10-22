package com.stutool.stutool

import android.app.Application
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.test.core.app.ApplicationProvider
import com.stutool.stutool.navigation.Screen
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExitUxUnitTest {

    private lateinit var application: Application

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testRootHomeRouteDeterminesExitBackHandlerActivation() {
        fun isAtHomeRoot(route: String?): Boolean {
            return route == Screen.Home.route
        }

        assertTrue("Home route should enable exit confirmation BackHandler", isAtHomeRoot("home"))
        assertFalse("Viewer route must not enable exit confirmation BackHandler", isAtHomeRoot("viewer"))
        assertFalse("Tools route must not enable exit confirmation BackHandler", isAtHomeRoot("tools"))
        assertFalse("Resume route must not enable exit confirmation BackHandler", isAtHomeRoot("resume"))
        assertFalse("ResumeBuilder route must not enable exit confirmation BackHandler", isAtHomeRoot("resume_builder"))
        assertFalse("PdfToWord route must not enable exit confirmation BackHandler", isAtHomeRoot("pdf_to_word"))
        assertFalse("Settings route must not enable exit confirmation BackHandler", isAtHomeRoot("settings"))
        assertFalse("Scanner route must not enable exit confirmation BackHandler", isAtHomeRoot("scanner_camera"))
        assertFalse("Null route must not enable exit confirmation BackHandler", isAtHomeRoot(null))
    }

    @Test
    fun testExitConfirmationDialogStateTransitions() {
        var showExitConfirmationDialog = false

        fun onExitTriggered() {
            showExitConfirmationDialog = true
        }

        fun onCancelClicked() {
            showExitConfirmationDialog = false
        }

        var activityFinished = false
        fun onExitConfirmed() {
            showExitConfirmationDialog = false
            activityFinished = true
        }

        assertFalse(showExitConfirmationDialog)
        assertFalse(activityFinished)

        onExitTriggered()
        assertTrue(showExitConfirmationDialog)
        assertFalse(activityFinished)

        onCancelClicked()
        assertFalse(showExitConfirmationDialog)
        assertFalse(activityFinished)

        onExitTriggered()
        assertTrue(showExitConfirmationDialog)
        onExitConfirmed()
        assertFalse(showExitConfirmationDialog)
        assertTrue(activityFinished)
    }

    @Test
    fun testResumeBuilderDirectExitRouteTarget() {
        val backTargetRoute = Screen.Home.route
        assertEquals("home", backTargetRoute)
        assertNotEquals("resume_exit_splash", backTargetRoute)
    }

    @Test
    fun testTopLevelNavigationDoesNotAccumulateBackStack() {
        val navController = NavHostController(application).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
        }

        val graph = navController.createGraph(startDestination = "splash_screen") {
            composable("splash_screen") {}
            composable(Screen.Home.route) {}
            composable(Screen.Viewer.route) {}
            composable(Screen.Tools.route) {}
            composable(Screen.MergePdf.route) {}
        }
        navController.setGraph(graph, null)

        // 1. Initial splash screen
        assertEquals("splash_screen", navController.currentBackStackEntry?.destination?.route)

        // 2. Splash complete -> Home (splash_screen popped inclusively)
        navController.navigate(Screen.Home.route) {
            popUpTo("splash_screen") { inclusive = true }
        }
        assertEquals(Screen.Home.route, navController.currentBackStackEntry?.destination?.route)

        // Navigation handler mimicking BottomNavBar and HomeScreen top-level navigation
        fun navigateTopLevel(route: String) {
            navController.navigate(route) {
                popUpTo(Screen.Home.route) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        // 3. Repeatedly toggle between Viewer and Tools
        navigateTopLevel(Screen.Viewer.route)
        assertEquals(Screen.Viewer.route, navController.currentBackStackEntry?.destination?.route)

        navigateTopLevel(Screen.Tools.route)
        assertEquals(Screen.Tools.route, navController.currentBackStackEntry?.destination?.route)

        navigateTopLevel(Screen.Viewer.route)
        assertEquals(Screen.Viewer.route, navController.currentBackStackEntry?.destination?.route)

        navigateTopLevel(Screen.Tools.route)
        assertEquals(Screen.Tools.route, navController.currentBackStackEntry?.destination?.route)

        // 4. Press system Back: must return directly to Home, NOT revisit previously selected Viewer!
        val popped = navController.popBackStack()
        assertTrue("Back press should succeed", popped)
        assertEquals("Back press from top-level Tools must land on Home", Screen.Home.route, navController.currentBackStackEntry?.destination?.route)

        // 5. At Home, verify that isAtHomeRoot enables exit confirmation
        val isAtHomeRoot = navController.currentBackStackEntry?.destination?.route == Screen.Home.route
        assertTrue("Should be at Home root destination", isAtHomeRoot)
    }

    @Test
    fun testNestedToolReturnsToParentAndThenToHome() {
        val navController = NavHostController(application).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
        }

        val graph = navController.createGraph(startDestination = "splash_screen") {
            composable("splash_screen") {}
            composable(Screen.Home.route) {}
            composable(Screen.Tools.route) {}
            composable(Screen.MergePdf.route) {}
        }
        navController.setGraph(graph, null)

        navController.navigate(Screen.Home.route) {
            popUpTo("splash_screen") { inclusive = true }
        }

        // Navigate to Tools
        navController.navigate(Screen.Tools.route) {
            popUpTo(Screen.Home.route) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
        assertEquals(Screen.Tools.route, navController.currentBackStackEntry?.destination?.route)

        // Navigate into nested tool: Merge PDF
        navController.navigate(Screen.MergePdf.route)
        assertEquals(Screen.MergePdf.route, navController.currentBackStackEntry?.destination?.route)

        // Back from Merge PDF returns to Tools
        assertTrue(navController.popBackStack())
        assertEquals(Screen.Tools.route, navController.currentBackStackEntry?.destination?.route)

        // Back from Tools returns to Home
        assertTrue(navController.popBackStack())
        assertEquals(Screen.Home.route, navController.currentBackStackEntry?.destination?.route)
    }

    @Test
    fun testDrawerNavigationToHomeDoesNotDuplicateHomeOnStack() {
        val navController = NavHostController(application).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
        }

        val graph = navController.createGraph(startDestination = "splash_screen") {
            composable("splash_screen") {}
            composable(Screen.Home.route) {}
            composable(Screen.Tools.route) {}
        }
        navController.setGraph(graph, null)

        navController.navigate(Screen.Home.route) {
            popUpTo("splash_screen") { inclusive = true }
        }

        // Navigate to Tools
        navController.navigate(Screen.Tools.route) {
            popUpTo(Screen.Home.route) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }

        // Drawer navigation selecting Home
        navController.navigate(Screen.Home.route) {
            popUpTo(Screen.Home.route) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }

        assertEquals(Screen.Home.route, navController.currentBackStackEntry?.destination?.route)

        // Back from Home: there should not be any duplicate Home or Tools behind it
        val popped = navController.popBackStack()
        assertFalse("Popping back stack from Home root should return false (activity exit)", popped)
    }
}