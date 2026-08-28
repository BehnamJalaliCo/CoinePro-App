plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.coinepro.core.marketdata"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // `UiMessage`, so this module's failures reach a screen as owned copy rather than as the
    // platform's own English exception text.
    api(project(":core:common"))
    implementation(project(":core:model"))
    api(project(":core:symbols"))
    implementation(libs.okhttp.core)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
