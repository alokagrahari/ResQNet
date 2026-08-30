plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.resqnet.app"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        applicationId = "com.resqnet.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        vectorDrawables {
            useSupportLibrary = true
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":mesh"))
    implementation(project(":location"))
    implementation(project(":storage"))
    implementation(project(":ble"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.googlecode.libphonenumber:libphonenumber:8.13.55")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
