package com.example.safewalk.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import com.example.safewalk.domain.model.EmergencyRole

/**
 * Generates custom, crisp vector-style Bitmaps for MapLibre markers.
 * Provides distinct visual identities for:
 * 1. Emergency Requester (Red beacon with SOS badge)
 * 2. Current User (Blue location pin / beacon)
 * 3. Individual Responders (Role-colored pins: Citizen, Police, Hospital)
 */
object MapMarkerBitmapHelper {

    fun createRequesterMarker(context: Context): Bitmap {
        val density = context.resources.displayMetrics.density
        val size = (42 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val center = size / 2f

        // Outer radar pulse circle
        val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E53935")
            alpha = 60
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center * 0.95f, pulsePaint)

        // Main SOS red circle
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D32F2F")
            style = Paint.Style.FILL
            setShadowLayer(4 * density, 0f, 2 * density, Color.parseColor("#80000000"))
        }
        canvas.drawCircle(center, center, center * 0.65f, fillPaint)

        // White border ring
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }
        canvas.drawCircle(center, center, center * 0.65f, strokePaint)

        // "SOS" text badge inside
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textBounds = Rect()
        textPaint.getTextBounds("SOS", 0, 3, textBounds)
        val textY = center + (textBounds.height() / 2f)
        canvas.drawText("SOS", center, textY, textPaint)

        return bitmap
    }

    fun createCurrentUserMarker(context: Context): Bitmap {
        val density = context.resources.displayMetrics.density
        val size = (34 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val center = size / 2f

        // Outer blue glow ring
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1976D2")
            alpha = 50
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center * 0.95f, glowPaint)

        // White background circle for high contrast
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(3 * density, 0f, 2 * density, Color.parseColor("#60000000"))
        }
        canvas.drawCircle(center, center, center * 0.60f, bgPaint)

        // Solid Blue center dot
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1976D2")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center * 0.45f, dotPaint)

        return bitmap
    }

    fun createResponderMarker(
        context: Context,
        role: EmergencyRole = EmergencyRole.RESPONDER,
        initial: String = "R",
        userName: String = ""
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val size = (38 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val center = size / 2f

        val isPolice = userName.contains("Police", ignoreCase = true) ||
                userName.contains("Inspector", ignoreCase = true) ||
                userName.contains("Officer", ignoreCase = true) ||
                initial.equals("P", ignoreCase = true)
        val isHospital = userName.contains("Hospital", ignoreCase = true) ||
                userName.contains("Doctor", ignoreCase = true) ||
                userName.contains("Dr.", ignoreCase = true) ||
                userName.contains("Ambulance", ignoreCase = true) ||
                initial.equals("H", ignoreCase = true)

        // Pin color according to role / authority indicator
        val colorHex = when {
            isPolice -> "#1A237E"    // Police Navy Blue
            isHospital -> "#1B5E20"  // Hospital Dark Green
            else -> "#F57C00"        // Amber / Orange Responder
        }
        val mainColor = Color.parseColor(colorHex)

        // Outer halo
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mainColor
            alpha = 50
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center * 0.95f, haloPaint)

        // Main body
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mainColor
            style = Paint.Style.FILL
            setShadowLayer(3 * density, 0f, 2 * density, Color.parseColor("#80000000"))
        }
        canvas.drawCircle(center, center, center * 0.65f, fillPaint)

        // White border
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }
        canvas.drawCircle(center, center, center * 0.65f, strokePaint)

        // Initial letter
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val text = when {
            isPolice -> "P"
            isHospital -> "H"
            else -> initial.take(1).uppercase().ifBlank { "R" }
        }
        val textBounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        val textY = center + (textBounds.height() / 2f)
        canvas.drawText(text, center, textY, textPaint)

        return bitmap
    }
}
