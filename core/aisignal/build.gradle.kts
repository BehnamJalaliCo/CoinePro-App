plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.coinepro.core.aisignal"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:model"))
    // The symbol picker reaches the same universe the markets screen searches rather than a second
    // hand-written list; `core:marketdata` re-exports `core:symbols`, which is where the classifier,
    // the liquidity ranking and the fuzzy matcher live.
    api(project(":core:marketdata"))
    implementation(project(":core:network"))
    implementation(libs.retrofit.core)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
