package com.firmlyplanted.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.firmlyplanted.app.ui.about.AboutLicensesScreen
import com.firmlyplanted.app.ui.home.HomeScreen
import com.firmlyplanted.app.ui.newproject.NewProjectScreen
import com.firmlyplanted.app.ui.session.SessionScreen
import com.firmlyplanted.app.ui.settings.ProjectSettingsScreen
import com.firmlyplanted.app.ui.today.TodayScreen
import com.firmlyplanted.app.ui.webreader.ReadMoreScreen

private object Routes {
    const val HOME = "home"
    const val NEW_PROJECT = "newProject"
    const val TODAY = "today/{projectId}"
    const val SESSION = "session/{projectId}"
    const val SETTINGS = "settings/{projectId}"
    const val ABOUT = "about"
    const val READ_MORE = "readMore/{projectId}"

    fun today(id: String) = "today/$id"
    fun session(id: String) = "session/$id"
    fun settings(id: String) = "settings/$id"
    fun readMore(id: String) = "readMore/$id"
}

@Composable
fun FirmlyPlantedNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenProject = { navController.navigate(Routes.today(it)) },
                onNewProject = { navController.navigate(Routes.NEW_PROJECT) },
                onAbout = { navController.navigate(Routes.ABOUT) },
            )
        }
        composable(Routes.NEW_PROJECT) {
            NewProjectScreen(
                onCreated = { projectId ->
                    navController.navigate(Routes.today(projectId)) {
                        popUpTo(Routes.HOME)
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(
            Routes.TODAY,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            TodayScreen(
                projectId = projectId,
                onStartSession = { navController.navigate(Routes.session(projectId)) },
                onSettings = { navController.navigate(Routes.settings(projectId)) },
                onReadMore = { navController.navigate(Routes.readMore(projectId)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            Routes.SESSION,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            SessionScreen(projectId = projectId, onDone = { navController.popBackStack() })
        }
        composable(
            Routes.SETTINGS,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            ProjectSettingsScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() },
                onProjectRemoved = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
            )
        }
        composable(
            Routes.READ_MORE,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            ReadMoreScreen(projectId = projectId, onBack = { navController.popBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutLicensesScreen(onBack = { navController.popBackStack() })
        }
    }
}
