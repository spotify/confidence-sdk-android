// ktlint-disable max-line-length
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
    id("org.jlleitschuh.gradle.ktlint")
    kotlin("plugin.serialization")
    id("signing")
}

object Versions {
    const val openFeatureSDK = "v0.0.2"
    const val okHttp = "4.10.0"
    const val kotlinxSerialization = "1.5.1"
    const val coroutines = "1.7.1"
    const val junit = "4.13.2"
    const val kotlinMockito = "4.1.0"
    const val mockWebServer = "4.9.1"

    // x-release-please-start-version
    const val providerVersion = "0.1.1"
    // x-release-please-end
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

    publishing {
        singleVariant("release") {
            withJavadocJar()
            withSourcesJar()
        }
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

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.spotify.confidence"
            artifactId = "openfeature-provider-android"
            version = Versions.providerVersion

            repositories {
                maven {
                    name = "Sonatype"
                    url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
                    credentials {
                        username = System.getenv("OSSRH_USERNAME")
                        password = System.getenv("OSSRH_PASSWORD")
                    }
                }
            }
            pom {
                name.set("Confidence Openfeature Provider Android")
                description.set("An Openfeature Provider for Confidence, made for the Android SDK")
                url.set("https://github.com/spotify/confidence-openfeature-provider-kotlin")
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
                        "scm:git:git://spotify/confidence-openfeature-provider-kotlin.git"
                    )
                    developerConnection.set(
                        "scm:git:ssh://spotify/confidence-openfeature-provider-kotlin.git"
                    )
                    url.set("https://github.com/spotify/confidence-openfeature-provider-kotlin")
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