import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

object Versions {
    const val coroutines = "1.7.1"
    const val liveData = "1.2.0"
    const val core = "1.7.0"
    const val lifecycleRuntime = "2.3.1"
    const val activityCompose = "1.3.1"
    const val composeUi = "1.2.0"
    const val composeUiToolingPreview = "1.2.0"
    const val composeMaterial = "1.2.0"
    const val jUnit = "4.13.2"
    const val jUnitTest = "1.1.3"
    const val espresso = "3.4.0"
    const val jUnitUiTest = "1.2.0"
    const val composeUiTooling = "1.2.0"
    const val uiTestManifest = "1.2.0"
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
        buildConfigField("String","CLIENT_SECRET", "\"$clientSecret\"")
        applicationId = "com.example.confidencedemoapp"
        minSdk = 21
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.1"
    }
    packagingOptions {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":Provider"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}")
    implementation( "androidx.compose.runtime:runtime-livedata:${Versions.liveData}")
    implementation( "androidx.core:core-ktx:${Versions.core}")
    implementation( "androidx.lifecycle:lifecycle-runtime-ktx:${Versions.lifecycleRuntime}")
    implementation( "androidx.activity:activity-compose:${Versions.activityCompose}")
    implementation( "androidx.compose.ui:ui:${Versions.composeUi}")
    implementation( "androidx.compose.ui:ui-tooling-preview:${Versions.composeUiToolingPreview}")
    implementation( "androidx.compose.material:material:${Versions.composeMaterial}")
    testImplementation( "junit:junit:${Versions.jUnit}")
    androidTestImplementation( "androidx.test.ext:junit:${Versions.jUnitTest}")
    androidTestImplementation( "androidx.test.espresso:espresso-core:${Versions.espresso}")
    androidTestImplementation( "androidx.compose.ui:ui-test-junit4:${Versions.jUnitUiTest}")
    debugImplementation("androidx.compose.ui:ui-tooling:${Versions.composeUiTooling}")
    debugImplementation("androidx.compose.ui:ui-test-manifest:${Versions.uiTestManifest}")
}