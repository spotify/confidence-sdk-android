// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.ktlint) apply true
    alias(libs.plugins.kotlinSerialization) apply true
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.nexusPublish) apply true
    alias(libs.plugins.binaryCompatibilityValidation) apply false
    alias(libs.plugins.kover) apply true
}

allprojects {
    extra["groupId"] = "com.spotify.confidence"
// x-release-please-start-version
    ext["version"] = "0.5.1"
// x-release-please-end
}
group = project.extra["groupId"].toString()
version = project.extra["version"].toString()

nexusPublishing {
    this.repositories {
        sonatype {
            username = System.getenv("OSSRH_USERNAME")
            password = System.getenv("OSSRH_PASSWORD")
        }
    }
}