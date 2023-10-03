// ktlint-disable max-line-length
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
    id("org.jlleitschuh.gradle.ktlint")
    kotlin("plugin.serialization")
}

object Versions {
    const val openFeatureSDK = "v0.0.2"
    const val okHttp = "4.10.0"
    const val kotlinxSerialization = "1.5.1"
    const val coroutines = "1.7.1"
    const val junit = "4.13.2"
    const val kotlinMockito = "4.1.0"
    const val mockWebServer = "4.9.1"
    const val providerVersion = "0.1.0"
}

android {
    namespace = "com.spotify.confidence"
    compileSdk = 33

    defaultConfig {
        minSdk = 21
        version = Versions.providerVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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
    api("com.github.open-feature:kotlin-sdk:${Versions.openFeatureSDK}")
    implementation("com.squareup.okhttp3:okhttp:${Versions.okHttp}")
    implementation(
        "org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}"
    )
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}")
    testImplementation("junit:junit:${Versions.junit}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.coroutines}")
    testImplementation("org.mockito.kotlin:mockito-kotlin:${Versions.kotlinMockito}")
    testImplementation("com.squareup.okhttp3:mockwebserver:${Versions.mockWebServer}")
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                groupId = "com.spotify"
                artifactId = "confidence-openfeature-provider-android"
                version = Versions.providerVersion

                from(components["release"])
                artifact(androidSourcesJar.get())

                pom {
                    name.set("SpotifyConfidenceProvider")
                }
            }
        }
    }
}

val androidSourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(android.sourceSets.getByName("main").java.srcDirs)
}

// Assembling should be performed before publishing package
tasks.named("publish") {
    dependsOn("assemble")
}