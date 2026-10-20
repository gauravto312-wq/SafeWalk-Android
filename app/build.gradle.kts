import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.safewalk"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.safewalk"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Single documented configuration point for SafeWalk backend API
        val railwayBackendUrl = "\"https://safewalk-backend-production-e3a1.up.railway.app/\""
        buildConfigField("String", "DEV_BACKEND_BASE_URL", railwayBackendUrl)
        buildConfigField("String", "PROD_BACKEND_BASE_URL", railwayBackendUrl)
        buildConfigField("String", "BACKEND_BASE_URL", railwayBackendUrl)

        val localProps = Properties().apply {
            val propFile = rootProject.file("local.properties")
            if (propFile.exists()) {
                propFile.inputStream().use { load(it) }
            }
        }
        val mapTilerKey = localProps.getProperty("MAPTILER_API_KEY", "")
        buildConfigField("String", "MAPTILER_API_KEY", "\"$mapTilerKey\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "DEV_BACKEND_BASE_URL", "\"https://safewalk-backend-production-e3a1.up.railway.app/\"")
            buildConfigField("String", "BACKEND_BASE_URL", "\"https://safewalk-backend-production-e3a1.up.railway.app/\"")
        }
        release {
            buildConfigField("String", "DEV_BACKEND_BASE_URL", "\"https://safewalk-backend-production-e3a1.up.railway.app/\"")
            buildConfigField("String", "BACKEND_BASE_URL", "\"https://safewalk-backend-production-e3a1.up.railway.app/\"")
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    implementation(libs.androidx.security.crypto)
    implementation(libs.play.services.location)
    implementation(libs.maplibre.android.sdk)
    implementation(libs.maplibre.annotation.plugin)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}