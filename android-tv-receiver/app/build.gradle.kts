plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tvbridge.receiver"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tvbridge.receiver"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Jetpack Compose for TV — foco D-Pad y componentes Leanback-style
    implementation("androidx.tv:tv-foundation:1.0.0-alpha12")
    implementation("androidx.tv:tv-material:1.0.0")

    // WebRTC para Android (org.webrtc.*); google-webrtc no está en Maven Central
    implementation("io.getstream:stream-webrtc-android:1.3.10")

    // OkHttp — WebSocket hacia el servidor de señalización Node.js
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ZXing core — generación de códigos QR (sin cámara)
    implementation("com.google.zxing:core:3.5.3")
}
