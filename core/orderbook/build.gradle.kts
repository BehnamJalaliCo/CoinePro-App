plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.coinepro.core.orderbook"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // `AppResult` and `ErrorKind`, so a feed that publishes no depth refuses in the same shape
    // every other gateway in this app fails in rather than in one invented for this module.
    api(project(":core:common"))
    // `ApiErrors`, so the depth route's `TYR-…` codes are read with the same parser every other
    // gateway uses rather than a second one that would drift from the four error shapes it knows.
    implementation(project(":core:network"))
    implementation(libs.gson)
    implementation(libs.okhttp.core)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
