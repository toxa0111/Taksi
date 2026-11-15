plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "uz.taksi"
    compileSdk = 35

    defaultConfig {
        applicationId = "uz.taksi"
        minSdk = 26
        targetSdk = 34
        versionCode = (project.findProperty("appVersionCode") as String? ?: "1").toInt()
        versionName = "1.0." + (project.findProperty("appVersionCode") as String? ?: "1")
        val mapkitApiKey = System.getenv("YANDEX_MAPKIT_API_KEY")?.trim().orEmpty()
        buildConfigField("String", "MAPKIT_API_KEY", "\"${mapkitApiKey}\"")
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    // Yandex MapKit Full: xarita, tirbandlik va avtomobil marshrutlari
    implementation("com.yandex.android:maps.mobile:4.45.0-full")
}
