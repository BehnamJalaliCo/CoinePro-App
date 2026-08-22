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
include(":feature:auth")
include(":feature:home")
include(":feature:signals")
include(":feature:ai")
include(":feature:tools")
include(":feature:activity")
