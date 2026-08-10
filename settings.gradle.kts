pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Allows validating publish against a core jar from ./gradlew publishToMavenLocal
        mavenLocal()
    }
}

// Opt-in only: ./gradlew -PuseLocalKlaudeAuth=true …
// Default uses Maven Central / mavenLocal coordinates for io.github.tom-spalding:klaude-auth
// so CI and publish do not require a sibling checkout.
val useLocalKlaudeAuth = providers.gradleProperty("useLocalKlaudeAuth")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)
    .get()

if (useLocalKlaudeAuth) {
    includeBuild("../klaude-auth") {
        dependencySubstitution {
            substitute(module("io.github.tom-spalding:klaude-auth"))
                .using(project(":klaude-auth"))
        }
    }
}

rootProject.name = "klaude-auth-android"
include(":klaude-auth-android")
