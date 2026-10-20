# SafeWalk Production ProGuard / R8 Rules

# Keep data models and DTOs used by Gson serialization
-keep class com.example.safewalk.data.model.** { *; }
-keep class com.example.safewalk.domain.model.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Retrofit & OkHttp
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**

# MapLibre Native SDK
-dontwarn org.maplibre.**
-keep class org.maplibre.** { *; }

# Firebase & Play Services
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Strip verbose and debug logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
