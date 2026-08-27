plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.coinepro.core.guest"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
    // api: the guest gateways implement MarketCatalogGateway and CandleGateway, so anything
    // that hands one to a screen needs those types on its own classpath.
    api(project(":core:marketdata"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(libs.retrofit.core)
    implementation(libs.gson)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
