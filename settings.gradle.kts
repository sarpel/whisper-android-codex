pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "whisper-android"
include(
    ":app",
    ":core",
    ":feature-audio",
    ":feature-transcription",
    ":feature-settings",
    ":ui-components",
    ":native"
)
