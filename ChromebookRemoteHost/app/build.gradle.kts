plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.chromebookremotehost"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.chromebookremotehost"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.13.0")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("org.nanohttpd:nanohttpd-websocket:2.3.1")

    // Official ngrok Java Agent SDK. The Android native runtime is architecture-specific.
    // See NGROK_ANDROID.md for the supported native-library packaging step.
    implementation("com.ngrok:ngrok-java:1.1.1")
    implementation(files("libs/ngrok-java-native-classes.jar"))
}
