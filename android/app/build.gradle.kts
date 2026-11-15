plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "uz.taksi"
    compileSdk = 34

    defaultConfig {
        applicationId = "uz.taksi"
        minSdk = 24
        targetSdk = 34
        versionCode = (project.findProperty("appVersionCode") as String? ?: "1").toInt()
        versionName = "1.0." + (project.findProperty("appVersionCode") as String? ?: "1")
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

    // Ikki ilova bitta koddan: "user" (yo'lovchi/haydovchi) va "admin" (boshqaruv, alohida applicationId)
    flavorDimensions += "target"
    productFlavors {
        create("user") {
            dimension = "target"
            buildConfigField("boolean", "IS_ADMIN", "false")
            manifestPlaceholders["appLabel"] = "TOXA"
        }
        create("admin") {
            dimension = "target"
            applicationIdSuffix = ".admin"
            buildConfigField("boolean", "IS_ADMIN", "true")
            manifestPlaceholders["appLabel"] = "TOXA Admin"
        }
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
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    // Xarita: OpenStreetMap (API kalit va to'lov shart emas)
    implementation("org.osmdroid:osmdroid-android:6.1.18")
}
