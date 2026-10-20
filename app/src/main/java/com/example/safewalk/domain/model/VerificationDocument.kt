package com.example.safewalk.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Representation of a verification credential document (e.g. Police ID, Department Proof, Hospital License).
 */
data class VerificationDocument(
    val id: String,
    val title: String,
    val fileName: String,
    val uploadedAt: Long = System.currentTimeMillis()
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            return sdf.format(Date(uploadedAt))
        }
}
