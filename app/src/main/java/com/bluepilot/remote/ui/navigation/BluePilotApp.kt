package com.bluepilot.remote.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bluepilot.remote.ui.screens.connection.ConnectionScreen
import com.bluepilot.remote.ui.screens.devices.DevicesScreen
import com.bluepilot.remote.ui.screens.home.HomeScreen
import com.bluepilot.remote.ui.screens.permission.PermissionScreen
import com.bluepilot.remote.ui.screens.gamepad.GamepadScreen
import com.bluepilot.remote.ui.screens.gamepadbuilder.GamepadBuilderScreen
import com.bluepilot.remote.ui.screens.help.HelpScreen
import com.bluepilot.remote.ui.screens.keyboard.KeyboardScreen
import com.bluepilot.remote.ui.screens.editor.LayoutEditorScreen
import com.bluepilot.remote.ui.screens.layouts.LayoutsScreen
import com.bluepilot.remote.ui.screens.macros.MacrosScreen
import com.bluepilot.remote.ui.screens.mouse.MouseScreen
import com.bluepilot.remote.ui.screens.multimedia.MultimediaScreen
import com.bluepilot.remote.ui.screens.numpad.NumpadScreen
import com.bluepilot.remote.ui.screens.presenter.PresenterScreen
import com.bluepilot.remote.ui.screens.settings.SettingsScreen
import com.bluepilot.remote.ui.screens.themes.ThemeGalleryScreen

/**
 * Central navigation graph.
 *
 * Routes are type-safe constants in [Routes]. Each module adds its own
 * destinations here (Module 2: permission/connection/devices/scanner,
 * Module 4: control screens, Module 6: layout editor, Module 7: combo profiles).
 */
object Routes {
    const val HOME = "home"
    // Placeholders — implemented in later modules:
    const val PERMISSIONS = "permissions"
    const val CONNECTION = "connection"
    const val DEVICES = "devices"
    const val SCANNER = "scanner"
    const val MOUSE = "mouse"
    const val KEYBOARD = "keyboard"
    const val NUMPAD = "numpad"
    const val MULTIMEDIA = "multimedia"
    const val PRESENTER = "presenter"
    const val GAMEPAD = "gamepad"
    const val SETTINGS = "settings"
    const val HELP = "help"
    const val LAYOUTS = "layouts"
    const val LAYOUT_EDITOR = "layout_editor"
    const val COMBO_PROFILES = "combo_profiles"
    const val MACROS = "macros"
    const val THEMES = "themes"
    const val GAMEPAD_BUILDER = "gamepad_builder"
}

@Composable
fun BluePilotApp() {
    val navController = rememberNavController()

    // SECTION 3B — smooth screen transitions: horizontal slide + fade,
    // mirrored for back navigation. 260ms tween ≈ Material motion spec.
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = {
            slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(260))
        },
        exitTransition = {
            slideOutHorizontally(tween(260)) { -it / 4 } + fadeOut(tween(200))
        },
        popEnterTransition = {
            slideInHorizontally(tween(260)) { -it / 4 } + fadeIn(tween(260))
        },
        popExitTransition = {
            slideOutHorizontally(tween(260)) { it / 4 } + fadeOut(tween(200))
        }
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable(Routes.PERMISSIONS) {
            PermissionScreen(
                onGranted = {
                    navController.navigate(Routes.CONNECTION) {
                        popUpTo(Routes.PERMISSIONS) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.CONNECTION) {
            ConnectionScreen(
                onBack = { navController.popBackStack() },
                onOpenDevices = { navController.navigate(Routes.DEVICES) }
            )
        }
        composable(Routes.DEVICES) {
            DevicesScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenThemes = { navController.navigate(Routes.THEMES) }
            )
        }
        composable(Routes.MOUSE) { MouseScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.KEYBOARD) { KeyboardScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.NUMPAD) { NumpadScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.MULTIMEDIA) { MultimediaScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.PRESENTER) { PresenterScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.GAMEPAD) { GamepadScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.HELP) { HelpScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.LAYOUTS) {
            LayoutsScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate("${Routes.LAYOUT_EDITOR}/$id") }
            )
        }
        composable(Routes.MACROS) { MacrosScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.THEMES) { ThemeGalleryScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.GAMEPAD_BUILDER) { GamepadBuilderScreen(onBack = { navController.popBackStack() }) }
        composable(
            route = "${Routes.LAYOUT_EDITOR}/{profileId}",
            arguments = listOf(navArgument("profileId") { type = NavType.LongType })
        ) {
            LayoutEditorScreen(onBack = { navController.popBackStack() })
        }
        // Layout editor (Module 6) and combo profiles (Module 7) append here.
    }
}
