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
        versionName = "1.0"
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
    implementation("androidx.camera:camera-mlkit-vision:1.6.2")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.glance:glance-appwidget:1.2.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
