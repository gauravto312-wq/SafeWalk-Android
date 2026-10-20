package com.example.safewalk.domain.model

/**
 * Permanent account roles for SafeWalk users.
 * A user retains this account role across all sessions.
 */
enum class UserRole(val displayName: String) {
    USER("Normal User"),
    POLICE("Police Officer"),
    HOSPITAL("Hospital / Medical"),
    ADMIN("System Administrator");

    val isAuthority: Boolean
        get() = this == POLICE || this == HOSPITAL || this == ADMIN
}
