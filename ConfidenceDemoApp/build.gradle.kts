import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
}

val localPropertiesFile = File(rootProject.projectDir, "local.properties")
val localProperties = Properties()

// Load the properties from local.properties file
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

val clientSecret: String = localProperties.getProperty("CLIENT_SECRET")?: "CLIENT_SECRET"

android {
    namespace = "com.example.confidencedemoapp"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.example.confidencedemoapp"
        minSdk = 21
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        manifestPlaceholders["CLIENT_SECRET"] = clientSecret
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":Provider"))
    implementation(libs.lifecycleProcess)
    implementation(libs.coroutines)
    implementation(libs.liveData)
    implementation(libs.core)
    implementation(libs.lifecycleRuntime)
    implementation(libs.activityCompose)
    implementation(libs.composeUi)
    implementation(libs.composeUiToolingPreview)
    implementation(libs.composeMaterial)
    testImplementation(libs.junit)
    androidTestImplementation(libs.jUnitTest)
    androidTestImplementation(libs.espresso)
    androidTestImplementation(libs.jUnitUiTest)
    debugImplementation(libs.composeUiTooling)
    debugImplementation(libs.uiTestManifest)
}