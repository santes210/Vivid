pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.google.devtools.ksp") {
                useModule("com.google.devtools.ksp:symbol-processing-gradle-plugin:${requested.version}")
            }
        }
    }
    repositories {
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
// TODO [KMP]: Descomentar cuando el módulo shared esté listo para integrarse
// con el build de Android:
// include(":shared")
