package com.neomods.libeditor.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.neomods.libeditor.ui.screens.about.AboutScreen
import com.neomods.libeditor.ui.screens.editor.EditorScreen
import com.neomods.libeditor.ui.screens.filepicker.FilePickerScreen
import com.neomods.libeditor.ui.screens.home.HomeScreen
import com.neomods.libeditor.ui.screens.permission.PermissionScreen
import com.neomods.libeditor.ui.screens.settings.SettingsScreen
import com.neomods.libeditor.ui.screens.splash.SplashScreen

@Composable
fun LibEditorNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onSplashComplete = {
                    navController.navigate(Screen.Permission.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Permission.route) {
            PermissionScreen(
                onPermissionGranted = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Permission.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onLibSelected = { path ->
                    navController.navigate("editor/${java.net.URLEncoder.encode(path, "UTF-8")}")
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToAbout = {
                    navController.navigate(Screen.About.route)
                },
                onNavigateToFilePicker = {
                    navController.navigate(Screen.FilePicker.route)
                }
            )
        }

        composable(Screen.FilePicker.route) {
            FilePickerScreen(
                onFileSelected = { path ->
                    navController.popBackStack()
                    navController.navigate("editor/${java.net.URLEncoder.encode(path, "UTF-8")}")
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("editor/{libPath}") { backStackEntry ->
            val libPath = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("libPath") ?: "",
                "UTF-8"
            )
            EditorScreen(
                libPath = libPath,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToAbout = {
                    navController.navigate(Screen.About.route)
                }
            )
        }

        composable(Screen.About.route) {
            AboutScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Permission : Screen("permission")
    object Home : Screen("home")
    object FilePicker : Screen("file_picker")
    object Editor : Screen("editor/{libPath}")
    object Settings : Screen("settings")
    object About : Screen("about")
}
