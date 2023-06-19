

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
    id("org.jlleitschuh.gradle.ktlint")
    kotlin("plugin.serialization")
}

object Versions {
    const val openFeatureSDK = "0.0.1-SNAPSHOT"
    const val okHttp = "4.10.0"
    const val kotlinxSerialization = "1.5.1"
    const val gson = "2.10"
    const val coroutines = "1.7.1"
    const val dateTime = "0.4.0"
    const val junit = "4.13.2"
    const val kotlinMockito = "4.1.0"
    const val mockWebServer = "4.9.1"
    const val providerVersion = "0.0.1-SNAPSHOT"
}

android {
    namespace = "dev.openfeature.contrib.providers"
    compileSdk = 33

    defaultConfig {
        minSdk = 21
        version = "0.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
<<<<<<< HEAD
    api("dev.openfeature:kotlin-sdk:${Versions.openFeatureSDK}")
    implementation("com.squareup.okhttp3:okhttp:${Versions.okHttp}")
    implementation(
        "org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}"
    )
    implementation("com.google.code.gson:gson:${Versions.gson}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}")
    testImplementation("junit:junit:${Versions.junit}")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.dateTime}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.coroutines}")
    testImplementation("org.mockito.kotlin:mockito-kotlin:${Versions.kotlinMockito}")
    testImplementation("com.squareup.okhttp3:mockwebserver:${Versions.mockWebServer}")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "dev.openfeature.contrib.providers"
            artifactId = "confidence"
            version = Versions.providerVersion
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}