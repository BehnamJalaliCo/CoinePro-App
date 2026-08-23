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
include(":feature:auth")
include(":feature:home")
include(":feature:signals")
include(":feature:signal-detail")
include(":feature:connections")
include(":feature:execution")
include(":feature:ai")
include(":feature:tools")
include(":feature:activity")
