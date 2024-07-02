// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.library").version("7.4.2").apply(false)
    id("org.jetbrains.kotlin.android").version("1.8.0").apply(false)
    id("org.jlleitschuh.gradle.ktlint").version("11.3.2").apply(true)
    kotlin("plugin.serialization").version("1.8.10").apply(true)
    id("com.android.application") version "7.4.2" apply false
    id("io.github.gradle-nexus.publish-plugin").version("1.3.0").apply(true)
}

allprojects {
    extra["groupId"] = "com.spotify.confidence"
// x-release-please-start-version
    ext["version"] = "0.3.6"
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