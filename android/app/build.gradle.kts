plugins {
    id("com.android.application")
    // Flutter Gradle Plugin must come after the Android plugin.
    // It applies the Kotlin Android plugin internally.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.yourcompany.hirobin"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.yourcompany.hirobin"

        // CAPABILITY_SELF_MANAGED and PROPERTY_SELF_MANAGED both require API 26.
        // FOREGROUND_SERVICE_MICROPHONE (used in the manifest) requires API 34,
        // but we guard that attribute with tools:targetApi in the manifest if needed.
        minSdk = 26
        targetSdk = 35

        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // TODO: replace with a real signing config before publishing.
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}

dependencies {
    // Kotlin stdlib (explicit pin keeps it aligned with the Kotlin plugin version)
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.20")

    // Kotlin coroutines — used for off-main-thread audio and AI inference work
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Core KTX — idiomatic Kotlin extensions for Android framework APIs
    implementation("androidx.core:core-ktx:1.16.0")

    // Lifecycle / ViewModel KTX — for coroutineScope tied to Activity/Service lifetime
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-service:2.9.1")

    // WebSocket client for streaming PCM to and receiving TTS from the backend
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
