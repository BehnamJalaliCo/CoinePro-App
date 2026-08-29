plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.coinepro.feature.dom"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // `OrderBookController` is a parameter of the screen, so whoever builds one needs the type.
    api(project(":core:orderbook"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    // The screen writes the reader's ladder preference from a `rememberCoroutineScope`, so the
    // coroutine builders are used here directly rather than only reached through a transitive edge
    // that a dependency change elsewhere could quietly remove.
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
}
