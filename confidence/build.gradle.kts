plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    kotlin("plugin.serialization").version("1.8.10").apply(true)
}

val providerVersion = project.extra["version"].toString()

sourceSets {
    create("testUtil") {
        java.srcDir("src/testUtil/kotlin")
        resources.srcDir("src/testUtil/resources")
    }
}

tasks.register<Jar>("testUtilJar") {
    archiveClassifier.set("test-util")
    from(sourceSets["testUtil"].output)
}

artifacts {
    add("archives", tasks["testUtilJar"])
}

object Versions {
    const val openFeatureSDK = "0.2.3"
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
}

dependencies {
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