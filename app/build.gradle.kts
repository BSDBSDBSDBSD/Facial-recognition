plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.faceunlock.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.faceunlock.app"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // Do not compress the tflite model so TFLite can mmap it directly.
    androidResources {
        noCompress.add("tflite")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // CameraX
    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // ML Kit face detection (bundled, no Google Play Services needed)
    implementation("com.google.mlkit:face-detection:16.1.6")

    // TensorFlow Lite for the face-recognition embedding model
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
}
