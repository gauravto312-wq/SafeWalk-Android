package com.example.safewalk.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safewalk.core.common.ServiceLocator
import com.example.safewalk.data.remote.ApiConfig
import com.example.safewalk.data.remote.NetworkClient
import com.example.safewalk.domain.model.UserRole
import com.example.safewalk.ui.theme.SafeWalkBlueSecondary
import com.example.safewalk.ui.theme.SafeWalkGreenSuccess
import com.example.safewalk.ui.theme.SafeWalkRedPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

@Composable
fun UserAvatar(
    name: String,
    role: UserRole,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    isVerified: Boolean = false,
    avatarUrl: String? = null
) {
    var avatarBitmap by remember(avatarUrl) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(avatarUrl) {
        if (!avatarUrl.isNullOrBlank() && !avatarUrl.startsWith("mock_")) {
            withContext(Dispatchers.IO) {
                try {
                    val fullUrl = if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
                        avatarUrl
                    } else {
                        val base = ApiConfig.baseUrl.trimEnd('/')
                        val path = if (avatarUrl.startsWith("/")) avatarUrl else "/$avatarUrl"
                        "$base$path"
                    }
                    val request = Request.Builder().url(fullUrl).build()
                    val client = NetworkClient.getOkHttpClient(ServiceLocator.sessionManager)
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bytes = response.body?.bytes()
                            if (bytes != null && bytes.isNotEmpty()) {
                                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (bmp != null) {
                                    avatarBitmap = bmp.asImageBitmap()
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    avatarBitmap = null
                }
            }
        } else {
            avatarBitmap = null
        }
    }

    val initials = name.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifEmpty { "SW" }

    val bgColor = when (role) {
        UserRole.POLICE -> SafeWalkBlueSecondary
        UserRole.HOSPITAL -> SafeWalkGreenSuccess
        UserRole.ADMIN -> Color(0xFF455A64)
        UserRole.USER -> SafeWalkRedPrimary
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (avatarBitmap != null) {
            Image(
                bitmap = avatarBitmap!!,
                contentDescription = "Avatar of $name",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = initials,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.38).sp
                )
            )
        }
    }
}
