plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.coinepro.feature.news"
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
    implementation(project(":core:marketintel"))
    // The announcements channel, which is a different route and a different promise from the news
    // feed — durable rather than transient, and TradeYar's alone. The screen lives here rather than
    // in a module of its own because a `feature:*` module must carry a `ScreenshotRenderTest` case
    // and that test is in `app/`, which this work does not own; announcements are also the same
    // reader, the same voice and the same three shared composables as the feed beside them.
    implementation(project(":core:announcements"))
    // The market-to-instrument table, shared with the chart so a story and a mark cannot
    // disagree about which chart a gold headline opens.
    implementation(project(":core:chartevents"))
    implementation(project(":core:designsystem"))
    // The saved list. Preferences rather than a database because a saved story is six short
    // strings and the app has no room for a second persistence style; see SavedNewsStore for why
    // this module holds its own file rather than joining the app's, and what moves when it stops
    // needing to.
    implementation(libs.androidx.datastore.preferences)
    // `BackHandler`, so backing out of a story returns to the list rather than leaving the screen.
    // The story is a state on this route rather than a destination in the app's graph — see
    // NewsScreen — so nothing else intercepts that gesture for it.
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.junit)
}
