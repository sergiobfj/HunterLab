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
    }
}

rootProject.name = "HunterLab"

// Ordem de dependência: protocol <- core <- app
include(":protocol") // Kotlin/JVM puro: pacotes de 20 bytes, CRC, builders, catálogo de ícones
include(":core")     // Kotlin/JVM puro: provedores de navegação, simuladores, árbitro de tela, modelos BLE
include(":app")      // Android + Jetpack Compose: UI e camada BLE (somente leitura)
