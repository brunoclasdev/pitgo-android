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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Mercado Pago Android SDK is not published to Maven Central, and it transitively pulls
        // other com.mercadolibre.* modules (e.g. com.mercadolibre.android.device) from this same
        // repository — restrict by namespace prefix, not just the top-level SDK group.
        maven {
            url = uri("https://artifacts.mercadolibre.com/repository/android-releases")
            content {
                includeGroupByRegex("com\\.mercadopago\\..*")
                includeGroupByRegex("com\\.mercadolibre\\..*")
            }
        }
    }
}

rootProject.name = "pitgo"
include(":app")
 