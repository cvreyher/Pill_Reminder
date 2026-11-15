plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.vonreyher.medicationtracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.vonreyher.medicationtracker"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    // Release signing for the GitHub builds. The keystore comes from CI secrets via environment
    // variables; without them (e.g. on F-Droid, which signs itself) the release APK stays unsigned.
    val releaseKeystore = System.getenv("RELEASE_KEYSTORE_FILE")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    // F-Droid requires APKs without the Google-encrypted dependency metadata block.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")

    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // 1.5 brings the Material 3 Expressive components (wavy progress, MaterialShapes, button groups).
    implementation("androidx.compose.material3:material3:1.5.0-rc01")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    // Open-source barcode reader (Apache-2.0): DataMatrix of the medication plan, PZN and package codes.
    implementation("io.github.zxing-cpp:android:3.1.1")
    implementation("androidx.glance:glance-appwidget:1.2.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
