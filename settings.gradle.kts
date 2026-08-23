pluginManagement {
    repositories {
        google()
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

rootProject.name = "CoinePro-App"
include(":app")
include(":benchmark")
include(":core:common")
include(":core:model")
include(":core:network")
include(":core:datastore")
include(":core:navigation")
include(":core:designsystem")
include(":core:auth")
include(":core:security")
include(":core:marketdata")
include(":core:signals")
include(":core:notifications")
include(":core:execution")
include(":core:aisignal")
include(":core:aivision")
include(":core:aiassistant")
include(":core:marketintel")
include(":core:database")
include(":feature:auth")
include(":feature:home")
include(":feature:signals")
include(":feature:signal-detail")
include(":feature:connections")
include(":feature:execution")
include(":feature:ai")
include(":feature:ai-vision")
include(":feature:ai-assistant")
include(":feature:news")
include(":feature:calendar")
include(":feature:tools")
include(":feature:activity")
