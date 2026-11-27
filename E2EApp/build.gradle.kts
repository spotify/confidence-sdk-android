plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.spotify.confidence.e2e"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.spotify.confidence.e2e"
        minSdk = 21
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
}

dependencies {
    implementation(project(":Provider"))
    implementation(libs.coroutines)

    androidTestImplementation(libs.jUnitTest)
    androidTestImplementation(libs.androidXTestRunner)
    androidTestImplementation(libs.mockWebServer)
}
