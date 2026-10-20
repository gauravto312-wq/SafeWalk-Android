package com.example.safewalk.core.navigation

/**
 * Centralized type-safe route definitions for SafeWalk.
 */
sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Register : Screen("register")
    object Home : Screen("home")
    object History : Screen("history")
    object Profile : Screen("profile")
    object Settings : Screen("settings")
    object Notifications : Screen("notifications")

    object EmergencyDetails : Screen("emergency_details/{emergencyId}") {
        fun createRoute(emergencyId: String) = "emergency_details/$emergencyId"
    }

    object RequesterEmergency : Screen("requester_emergency/{emergencyId}") {
        fun createRoute(emergencyId: String) = "requester_emergency/$emergencyId"
    }

    object ResponderEmergency : Screen("responder_emergency/{emergencyId}") {
        fun createRoute(emergencyId: String) = "responder_emergency/$emergencyId"
    }

    object LiveEmergency : Screen("live_emergency/{emergencyId}") {
        fun createRoute(emergencyId: String) = "live_emergency/$emergencyId"
    }
}
