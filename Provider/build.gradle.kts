// ktlint-disable max-line-length
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
    id("org.jlleitschuh.gradle.ktlint")
    kotlin("plugin.serialization")
    id("signing")
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
}

object Versions {
    const val openFeatureSDK = "0.3.0"
    const val okHttp = "4.10.0"
    const val kotlinxSerialization = "1.6.0"
    const val coroutines = "1.7.3"
    const val junit = "4.13.2"
    const val kotlinMockito = "4.1.0"
    const val mockWebServer = "4.9.1"
}

val providerVersion = project.extra["version"].toString()

android {
    namespace = "com.spotify.confidence.openfeature"
    compileSdk = 33

    defaultConfig {
        minSdk = 21
        version = providerVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SDK_VERSION", "\"" + providerVersion + "\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }

    publishing {
        singleVariant("release") {
            withJavadocJar()
            withSourcesJar()
        }
    }
}

dependencies {
    api("dev.openfeature:android-sdk:${Versions.openFeatureSDK}")
    implementation("com.squareup.okhttp3:okhttp:${Versions.okHttp}")
    implementation(
        "org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}"
    )
    implementation("androidx.lifecycle:lifecycle-process:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}")
    api(project(":Confidence"))
    testImplementation("junit:junit:${Versions.junit}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.coroutines}")
    testImplementation("org.mockito.kotlin:mockito-kotlin:${Versions.kotlinMockito}")
    testImplementation("com.squareup.okhttp3:mockwebserver:${Versions.mockWebServer}")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.extra["groupId"].toString()
            artifactId = "openfeature-provider-android"
            version = providerVersion

            pom {
                name.set("Confidence Openfeature Provider Android")
                description.set("An Openfeature Provider for Confidence, made for the Android SDK")
                url.set("https://github.com/spotify/confidence-sdk-android")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("vahidlazio")
                        name.set("Vahid Torkaman")
                        email.set("vahidt@spotify.com")
                    }
                    developer {
                        id.set("fabriziodemaria")
                        name.set("Fabrizio Demaria")
                        email.set("fdema@spotify.com")
                    }
                    developer {
                        id.set("nicklasl")
                        name.set("Nicklas Lundin")
                        email.set("nicklasl@spotify.com")
                    }
                    developer {
                        id.set("nickybondarenko")
                        name.set("Nicky Bondarenko")
                        email.set("nickyb@spotify.com")
                    }
                }
                scm {
                    connection.set(
                        "scm:git:git://spotify/confidence-sdk-android.git"
                    )
                    developerConnection.set(
                        "scm:git:ssh://spotify/confidence-sdk-android.git"
                    )
                    url.set("https://github.com/spotify/confidence-sdk-android")
                }
            }
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}

signing {
    sign(publishing.publications["release"])
}