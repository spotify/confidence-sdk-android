// ktlint-disable max-line-length
plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    id("maven-publish")
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotlinSerialization) apply true
    id("signing")
    alias(libs.plugins.binaryCompatibilityValidation)
    alias(libs.plugins.kover)
}

val providerVersion = project.extra["version"].toString()

android {
    namespace = "com.spotify.confidence.openfeature"
    compileSdk = 33

    defaultConfig {
        minSdk = 21
        version = providerVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    buildFeatures.buildConfig = false
}

dependencies {
    api(libs.openFeatureSDK)
    implementation(libs.okHttp)
    implementation(libs.kotlinxSerialization)
    implementation(libs.coroutines)
    api(project(":Confidence"))
    testImplementation(libs.junit)
    testImplementation(libs.coroutines)
    testImplementation(libs.kotlinMockito)
    testImplementation(libs.mockWebServer)
    testImplementation(libs.kotlinxCoroutinesTest)
    testImplementation(libs.mockk)
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

kover {
    reports {
        verify {
            rule {
                minBound(80)
            }
        }
    }
}