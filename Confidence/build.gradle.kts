plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
    id("org.jlleitschuh.gradle.ktlint")
    id("org.jetbrains.kotlin.plugin.serialization") apply true
    id("signing")
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
    id("org.jetbrains.kotlinx.kover")
}

val providerVersion = project.extra["version"].toString()

object Versions {
    const val okHttp = "4.10.0"
    const val kotlinxSerialization = "1.6.0"
    const val coroutines = "1.7.3"
    const val junit = "4.13.2"
    const val kotlinMockito = "4.1.0"
    const val mockWebServer = "4.9.1"
}

android {
    namespace = "com.spotify.confidence"
    compileSdk = 33

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "SDK_VERSION", "\"" + providerVersion + "\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    publishing {
        singleVariant("release") {
            withJavadocJar()
            withSourcesJar()
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.okHttp)
    implementation(libs.kotlinxSerialization)
    implementation(libs.coroutines)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines)
    testImplementation(libs.kotlinMockito)
    testImplementation(libs.mockWebServer)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinxCoroutinesTest)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.extra["groupId"].toString()
            artifactId = "confidence-sdk-android"
            version = providerVersion

            pom {
                name.set("Confidence SDK Android")
                description.set("Android SDK for Confidence")
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

kover {
    reports {
        verify {
            rule {
                minBound(78)
            }
        }
    }
}