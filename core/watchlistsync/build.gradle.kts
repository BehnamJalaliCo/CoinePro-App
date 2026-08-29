plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.coinepro.core.watchlistsync"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // api on both: `WatchlistSnapshot` and `MarketPlatform` are in this module's own signatures, so
    // anything that builds the controller or reads its state needs them on its own classpath.
    api(project(":core:datastore"))
    api(project(":core:model"))
    // `toPersianDigits` and `AppLanguage`, for the notices that carry a count.
    implementation(project(":core:common"))
    // Gson directly, not through the converter: the payload is opaque to the server and is built
    // and read as a `JsonObject` by hand. See `WatchlistPayload`.
    implementation(libs.gson)
    implementation(libs.retrofit.core)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    // A real `HttpException` needs a real okhttp response body to be built around.
    testImplementation(libs.okhttp.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
