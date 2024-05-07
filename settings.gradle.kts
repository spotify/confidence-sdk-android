import org.gradle.api.initialization.resolve.RepositoriesMode

include(":confidence")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()

        maven("https://jitpack.io")
    }
}

rootProject.name = "Confidence"
include(":Provider", ":ConfidenceDemoApp")