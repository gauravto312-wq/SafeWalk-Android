package com.example.safewalk.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.safewalk.ui.auth.LoginScreen
import com.example.safewalk.ui.auth.RegisterScreen
import com.example.safewalk.ui.auth.SplashScreen
import com.example.safewalk.ui.components.NavigationTab
import com.example.safewalk.ui.emergency.EmergencyDetailsScreen
import com.example.safewalk.ui.emergency.LiveEmergencyScreen
import com.example.safewalk.ui.history.HistoryScreen
import com.example.safewalk.ui.home.HomeScreen
import com.example.safewalk.ui.profile.ProfileScreen
import com.example.safewalk.ui.requester.RequesterEmergencyScreen
import com.example.safewalk.ui.responder.ResponderEmergencyScreen
import com.example.safewalk.ui.settings.SettingsScreen
import com.example.safewalk.core.common.ServiceLocator

@Composable
fun SafeWalkNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Splash.route
) {
    val handleLogout: () -> Unit = {
        navController.navigate(Screen.Login.route) {
            popUpTo(0) { inclusive = true }
        }
    }

    val navigateToTab: (NavigationTab) -> Unit = { tab ->
        when (tab) {
            NavigationTab.HOME -> {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Home.route) { inclusive = false }
                    launchSingleTop = true
                }
            }
            NavigationTab.HISTORY -> {
                navController.navigate(Screen.History.route) {
                    popUpTo(Screen.Home.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            NavigationTab.PROFILE -> {
                navController.navigate(Screen.Profile.route) {
                    popUpTo(Screen.Home.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            NavigationTab.SETTINGS -> {
                navController.navigate(Screen.Settings.route) {
                    popUpTo(Screen.Home.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onTriggerSos = { emergencyId ->
                    navController.navigate(Screen.RequesterEmergency.createRoute(emergencyId))
                },
                onEmergencyClick = { emergencyId ->
                    val user = ServiceLocator.sessionManager?.getUser()
                    val emg = ServiceLocator.emergencyRepository.activeEmergencies.value.firstOrNull { it.id == emergencyId || it.id == "EMG-$emergencyId" }
                    if (emg != null && user != null && emg.isUserRequester(user.id)) {
                        navController.navigate(Screen.RequesterEmergency.createRoute(emergencyId))
                    } else {
                        navController.navigate(Screen.EmergencyDetails.createRoute(emergencyId))
                    }
                },
                onNavigateToHistory = {
                    navigateToTab(NavigationTab.HISTORY)
                },
                onNavigateToProfile = {
                    navigateToTab(NavigationTab.PROFILE)
                },
                onNavigateToSettings = {
                    navigateToTab(NavigationTab.SETTINGS)
                },
                onNavigateToNotifications = {
                    navController.navigate(Screen.Notifications.route)
                },
                onNavigateToLogin = handleLogout
            )
        }

        composable(
            route = Screen.EmergencyDetails.route,
            arguments = listOf(navArgument("emergencyId") { type = NavType.StringType })
        ) { backStackEntry ->
            val emergencyId = backStackEntry.arguments?.getString("emergencyId") ?: ""
            EmergencyDetailsScreen(
                emergencyId = emergencyId,
                onNavigateToResponderFlow = { id ->
                    navController.navigate(Screen.ResponderEmergency.createRoute(id))
                },
                onNavigateToLiveEmergency = { id ->
                    navController.navigate(Screen.LiveEmergency.createRoute(id))
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.RequesterEmergency.route,
            arguments = listOf(navArgument("emergencyId") { type = NavType.StringType })
        ) { backStackEntry ->
            val emergencyId = backStackEntry.arguments?.getString("emergencyId") ?: ""
            RequesterEmergencyScreen(
                emergencyId = emergencyId,
                onNavigateHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.ResponderEmergency.route,
            arguments = listOf(navArgument("emergencyId") { type = NavType.StringType })
        ) { backStackEntry ->
            val emergencyId = backStackEntry.arguments?.getString("emergencyId") ?: ""
            ResponderEmergencyScreen(
                emergencyId = emergencyId,
                onNavigateHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.LiveEmergency.route,
            arguments = listOf(navArgument("emergencyId") { type = NavType.StringType })
        ) { backStackEntry ->
            val emergencyId = backStackEntry.arguments?.getString("emergencyId") ?: ""
            LiveEmergencyScreen(
                emergencyId = emergencyId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                onEmergencyClick = { emergencyId ->
                    navController.navigate(Screen.EmergencyDetails.createRoute(emergencyId))
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToTab = navigateToTab
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onLogout = handleLogout,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToTab = navigateToTab
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onLogout = handleLogout,
                onNavigateToTab = navigateToTab
            )
        }

        composable(Screen.Notifications.route) {
            com.example.safewalk.ui.notifications.NotificationCenterScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToEmergency = { emergencyId ->
                    navController.navigate(Screen.EmergencyDetails.createRoute(emergencyId))
                }
            )
        }
    }
}
