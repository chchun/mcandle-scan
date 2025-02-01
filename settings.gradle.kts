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
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS) // ✅ 기존 `FAIL_ON_PROJECT_REPOS` → `PREFER_SETTINGS`로 변경
    repositories {
        google()
        mavenCentral()
        flatDir { dirs("libs") } // ✅ `flatDir` 추가하여 AAR 라이브러리 포함
    }
}

rootProject.name = "bleapp"
include(":app")
