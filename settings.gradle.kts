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
        // `wasm-opt`, for the web terminal's production bundle, and nothing else: the filter keeps
        // this repository from ever answering for any other module. See build.gradle.kts.
        exclusiveContent {
            forRepository {
                ivy("https://github.com/WebAssembly/binaryen/releases/download") {
                    name = "Binaryen"
                    patternLayout { artifact("version_[revision]/binaryen-version_[revision]-[classifier].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("com.github.webassembly", "binaryen") }
        }
    }
}

rootProject.name = "CoinePro-App"
include(":app")
// The chart, split by what it needs from its platform: the engine (nothing) and the Compose
// layer (Android and the browser). Named by role rather than by tier so the web terminal can take
// the first without the second — docs/engineering/MODULES.md.
include(":chart-core")
project(":chart-core").projectDir = file("chart/core")
include(":chart-ui")
project(":chart-ui").projectDir = file("chart/ui")
// The terminal at pro-chart.com/terminal/: the same chart, in a browser — web/build.gradle.kts.
include(":web")
// The indicator language, likewise platform-free; `:core:script` is its Android host.
include(":namascript")
include(":benchmark")
include(":core:common")
// The part of the design system a browser can have — see core/tokens/build.gradle.kts.
include(":core:tokens")
include(":core:model")
include(":core:network")
include(":core:datastore")
include(":core:navigation")
include(":core:designsystem")
include(":core:auth")
include(":core:security")
include(":core:marketdata")
include(":core:orderbook")
include(":core:webhook")
include(":core:membership")
include(":core:chartevents")
include(":core:script")
include(":core:help")
include(":core:symbols")
include(":core:signals")
include(":core:notifications")
include(":core:execution")
include(":core:portfolio")
include(":core:academy")
include(":core:community")
include(":core:aisignal")
include(":core:aivision")
include(":core:aiassistant")
include(":core:marketintel")
include(":core:announcements")
include(":core:account")
include(":core:guest")
include(":core:journal")
include(":core:papertrade")
include(":core:backtest")
include(":core:diagnostics")
include(":core:database")
include(":core:export")
include(":core:watchlistsync")
include(":core:update")
include(":feature:admin")
include(":feature:auth")
include(":feature:home")
include(":feature:script")
include(":feature:screener")
include(":feature:search")
include(":feature:chart")
include(":feature:portfolio")
include(":feature:academy")
include(":feature:terminal")
include(":feature:signals")
include(":feature:signal-detail")
include(":feature:connections")
include(":feature:execution")
include(":feature:profile")
include(":feature:kyc")
include(":feature:account")
include(":feature:guest")
include(":feature:membership")
include(":feature:notifications")
include(":feature:alerts")
include(":feature:dom")
include(":feature:heatmap")
include(":feature:journal")
include(":feature:papertrade")
include(":feature:ai")
include(":feature:ai-vision")
include(":feature:ai-assistant")
include(":feature:news")
include(":feature:calendar")
include(":feature:explore")
include(":feature:community")
include(":feature:tools")
include(":feature:activity")
include(":feature:menu")
include(":feature:legal")
