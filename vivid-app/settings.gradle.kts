pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.google.devtools.ksp") {
                useModule("com.google.devtools.ksp:symbol-processing-gradle-plugin:${requested.version}")
            }
        }
    }
    repositories {
        // KSP plugin marker lives in Maven Central. The explicit Maven URL avoids Gradle resolving
        // com.google.devtools.ksp only against Google's Android repository in some environments.
        maven(url = "https://repo.maven.apache.org/maven2") {
            name = "MavenCentralExplicit"
        }
        mavenCentral()
        google {
            content {
                excludeGroup("com.google.devtools.ksp")
            }
        }
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Vivid"
include(":app")