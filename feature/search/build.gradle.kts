plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.coinepro.feature.search"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:marketdata"))
    // The preview's own chart: `Candle`, `CandleSeries` and `ChartReading`, which is the reading the
    // sheet's one summary line is. The engine module and not `:chart-ui` — this sheet draws a line,
    // not a terminal, and pulling the Compose chart in for it would put the whole plot, its
    // drawings and its viewport on the markets tab's classpath.
    implementation(project(":chart-core"))
    // api rather than implementation: `SurfaceAccess` carries a `MarketPlatform`, and it is a
    // parameter of `SearchScreen`, so whoever builds one needs the type.
    api(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    // The watchlist panel reads and writes the reader's own lists. api rather than implementation:
    // WatchlistStore is a parameter of MarketsScreen, so whoever builds one needs the type.
    api(project(":core:datastore"))
    // The sync control the watchlist panel draws. api rather than implementation: the controller
    // is a parameter of MarketsScreen, so whoever builds one needs the type on their classpath.
    api(project(":core:watchlistsync"))
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
}
