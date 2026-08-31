plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.coinepro.feature.explore"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:model"))
    // The catalogue, the day's figures and the day's shape — every card on this screen. Nothing
    // here is a fixture: `MarketSearchController` holds the same catalogue the markets tab shows,
    // `MarketTickerStore` the same rollup the heat map reads, and `SparklineStore` the same
    // twenty-four closes a market row draws. See ExploreScreen.
    implementation(project(":core:marketdata"))
    // Classification into crypto / forex / metal / index / energy, which is what the chip row is,
    // and `SymbolArtwork.covers`, which is why no card can be a blank square.
    implementation(project(":core:symbols"))
    // The stories under the cards. The same controller `feature:news` uses, so the headline here
    // and the headline there can never be two different readings of one feed.
    implementation(project(":core:marketintel"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.junit)
}
